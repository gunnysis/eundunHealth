#!/usr/bin/env python3
"""Verify that an Azure Container Apps deployment, not an older revision, is live."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections.abc import Callable
from dataclasses import dataclass


@dataclass(frozen=True)
class DeploymentState:
    latest_revision: str
    latest_ready_revision: str
    running_status: str
    image: str
    fqdn: str

    @classmethod
    def from_json(cls, raw: str) -> DeploymentState:
        value = json.loads(raw)
        return cls(
            latest_revision=value.get("latestRevisionName") or "",
            latest_ready_revision=value.get("latestReadyRevisionName") or "",
            running_status=value.get("runningStatus") or "",
            image=value.get("image") or "",
            fqdn=value.get("fqdn") or "",
        )


def read_state(resource_group: str, app_name: str) -> DeploymentState:
    az_executable = shutil.which("az") or shutil.which("az.cmd")
    if az_executable is None:
        raise RuntimeError("Azure CLI executable was not found on PATH")
    query = (
        "{latestRevisionName:properties.latestRevisionName,"
        "latestReadyRevisionName:properties.latestReadyRevisionName,"
        "runningStatus:properties.runningStatus,"
        "image:properties.template.containers[0].image,"
        "fqdn:properties.configuration.ingress.fqdn}"
    )
    result = subprocess.run(
        [
            az_executable,
            "containerapp",
            "show",
            "--name",
            app_name,
            "--resource-group",
            resource_group,
            "--query",
            query,
            "--output",
            "json",
        ],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return DeploymentState.from_json(result.stdout)


def state_matches(state: DeploymentState, expected_image: str) -> tuple[bool, str]:
    if state.image != expected_image:
        return False, f"template image mismatch: expected={expected_image} actual={state.image}"
    if not state.latest_revision:
        return False, "latest revision is empty"
    if state.latest_revision != state.latest_ready_revision:
        return (
            False,
            f"target revision is not ready: latest={state.latest_revision} ready={state.latest_ready_revision or '<none>'}",
        )
    if state.running_status.lower() != "running":
        return False, f"container app is not running: {state.running_status or '<empty>'}"
    if not state.fqdn:
        return False, "ingress FQDN is empty"
    return True, "ready"


def wait_for_target(
    resource_group: str,
    app_name: str,
    expected_image: str,
    timeout_seconds: int,
    interval_seconds: int,
    state_reader: Callable[[str, str], DeploymentState] = read_state,
) -> DeploymentState:
    deadline = time.monotonic() + timeout_seconds
    last_reason = "state not read"
    while True:
        state = state_reader(resource_group, app_name)
        matched, last_reason = state_matches(state, expected_image)
        print(
            "deployment-state "
            f"latest={state.latest_revision or '-'} "
            f"ready={state.latest_ready_revision or '-'} "
            f"status={state.running_status or '-'} "
            f"image={state.image or '-'}"
        )
        if matched:
            return state
        if time.monotonic() >= deadline:
            raise TimeoutError(last_reason)
        time.sleep(interval_seconds)


def probe(url: str, expected_status: str | None = None) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "eundunhealth-deploy-verifier/1"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            if response.status != 200:
                raise RuntimeError(f"{url} returned HTTP {response.status}")
            body = response.read()
    except urllib.error.URLError as exc:
        raise RuntimeError(f"{url} request failed: {exc.reason}") from exc

    if expected_status is not None:
        try:
            actual = json.loads(body).get("status")
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise RuntimeError(f"{url} did not return JSON") from exc
        if actual != expected_status:
            raise RuntimeError(f"{url} status mismatch: expected={expected_status} actual={actual}")
    print(f"probe-ok {url}")


def verify_endpoints(fqdn: str) -> None:
    base_url = f"https://{fqdn}"
    probe(f"{base_url}/health", "ok")
    probe(f"{base_url}/health/ready", "ready")
    probe(f"{base_url}/app-compatibility?platform=android&versionCode=44")
    probe(f"{base_url}/privacy")
    probe(f"{base_url}/account-deletion")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resource-group", required=True)
    parser.add_argument("--app", required=True)
    parser.add_argument("--expected-image", required=True)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--interval-seconds", type=int, default=5)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        state = wait_for_target(
            args.resource_group,
            args.app,
            args.expected_image,
            args.timeout_seconds,
            args.interval_seconds,
        )
        verify_endpoints(state.fqdn)
    except (RuntimeError, TimeoutError, subprocess.CalledProcessError) as exc:
        print(f"deployment verification failed: {exc}", file=sys.stderr)
        return 1
    print(f"deployment verified: revision={state.latest_ready_revision} image={state.image}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
