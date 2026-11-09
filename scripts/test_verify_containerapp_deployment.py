import io
import json
import unittest
import urllib.error
from unittest.mock import patch

from verify_containerapp_deployment import (
    DeploymentState,
    probe,
    probe_auth_guarded,
    read_android_version_code,
    state_matches,
    wait_for_target,
)


class DeploymentStateTest(unittest.TestCase):
    def test_from_json_reads_azure_projection(self) -> None:
        state = DeploymentState.from_json(
            json.dumps(
                {
                    "latestRevisionName": "app--2",
                    "latestReadyRevisionName": "app--2",
                    "runningStatus": "Running",
                    "image": "registry/app:abc1234",
                    "fqdn": "app.example.test",
                }
            )
        )
        self.assertEqual("app--2", state.latest_revision)
        self.assertEqual("registry/app:abc1234", state.image)

    def test_rejects_old_ready_revision_false_green(self) -> None:
        state = DeploymentState("app--2", "app--1", "Running", "registry/app:new", "app.example.test")
        matched, reason = state_matches(state, "registry/app:new")
        self.assertFalse(matched)
        self.assertIn("not ready", reason)

    def test_rejects_wrong_image_even_when_revision_is_ready(self) -> None:
        state = DeploymentState("app--2", "app--2", "Running", "registry/app:old", "app.example.test")
        matched, reason = state_matches(state, "registry/app:new")
        self.assertFalse(matched)
        self.assertIn("image mismatch", reason)

    def test_waits_until_expected_revision_is_ready(self) -> None:
        states = iter(
            [
                DeploymentState("app--2", "app--1", "Running", "registry/app:new", "app.example.test"),
                DeploymentState("app--2", "app--2", "Running", "registry/app:new", "app.example.test"),
            ]
        )
        with patch("verify_containerapp_deployment.time.sleep"):
            state = wait_for_target("rg", "app", "registry/app:new", 10, 0, lambda _rg, _app: next(states))
        self.assertEqual("app--2", state.latest_ready_revision)


class ProbeTest(unittest.TestCase):
    def test_probe_rejects_wrong_health_payload(self) -> None:
        response = unittest.mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.read.return_value = b'{"status":"wrong"}'
        with (
            patch("verify_containerapp_deployment.urllib.request.urlopen", return_value=response),
            self.assertRaisesRegex(RuntimeError, "status mismatch"),
        ):
            probe("https://example.test/health", "ok")

    def test_probe_accepts_expected_payload(self) -> None:
        response = unittest.mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.read.return_value = b'{"status":"ok"}'
        with (
            patch("verify_containerapp_deployment.urllib.request.urlopen", return_value=response),
            patch("sys.stdout", new=io.StringIO()),
        ):
            probe("https://example.test/health", "ok")

    def test_probe_auth_guarded_accepts_401_or_403_and_rejects_404(self) -> None:
        err_401 = urllib.error.HTTPError("https://example.test/v2/biometrics/daily", 401, "Unauthorized", None, None)
        with (
            patch("verify_containerapp_deployment.urllib.request.urlopen", side_effect=err_401),
            patch("sys.stdout", new=io.StringIO()),
        ):
            probe_auth_guarded("https://example.test/v2/biometrics/daily")

        err_404 = urllib.error.HTTPError("https://example.test/v2/biometrics/daily", 404, "Not Found", None, None)
        with (
            patch("verify_containerapp_deployment.urllib.request.urlopen", side_effect=err_404),
            self.assertRaisesRegex(RuntimeError, "expected HTTP 401/403"),
        ):
            probe_auth_guarded("https://example.test/v2/biometrics/daily")

    def test_read_android_version_code_returns_positive_int(self) -> None:
        self.assertGreaterEqual(read_android_version_code(), 44)


if __name__ == "__main__":
    unittest.main()
