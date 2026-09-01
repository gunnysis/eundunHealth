FROM python:3.14-slim

# OS 보안 패치 — base 이미지에 미반영된 fixed-available OS CVE 해소(예: openssl CVE-2026-45447,
# libssl3t64 3.5.6-1~deb13u1 → deb13u2). Trivy(CRITICAL/HIGH 차단)가 fixed CVE 를 막으므로
# 빌드 시점에 Debian security 업그레이드를 당겨 base 재빌드 지연으로 인한 deploy 차단을 자가치유한다.
RUN apt-get update \
 && apt-get upgrade -y \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

COPY requirements.txt .
# 의존성 설치 후 **패키지 설치 도구를 런타임 이미지에서 제거**한다.
#
# 왜: Trivy(CRITICAL/HIGH 차단)가 2026-09-01 배포를 막았는데, 걸린 2건이 전부 우리
# requirements.txt 밖의 것이었다 —
#   setuptools 70.3.0  CVE-2025-47273 (path traversal)   ← base 이미지 동봉분
#   msgpack    1.1.2   GHSA-6v7p-g79w-8964               ← **pip 의 vendored 사본**
# msgpack 은 `pip/_vendor/` 안에 있어 우리가 버전을 올릴 수단이 없다(pip 최신도 동일 사본).
# 즉 pip 가 이미지에 남아 있는 한 이 지적은 매 스캔 재현된다.
#
# 런타임은 이 셋 중 무엇도 쓰지 않는다 — entrypoint.sh 는 `alembic upgrade head` 후
# uvicorn 을 exec 할 뿐이다. 설치 도구를 빼는 것이 우회가 아니라 정석이며(공급망 표면 축소),
# 부수적으로 이미지도 작아진다.
#
# 주의: 이 뒤로는 컨테이너 안에서 `pip install` 이 불가능하다. 의존성 추가는
# requirements.txt 를 고쳐 **재빌드**하는 경로만 남는다(원래 그래야 한다).
RUN pip install --no-cache-dir -r requirements.txt \
 && python -m pip uninstall -y pip setuptools wheel 2>/dev/null || true

COPY . .

# entrypoint.sh: CRLF → LF (Windows core.autocrlf=true 안전망) → 실행권한 → 소유권
# .gitattributes (backend/.gitattributes) 가 1차 방어, 이 sed 가 2차 방어. 둘 다 있어야 누락 안 됨.
RUN sed -i 's/\r$//' /app/entrypoint.sh \
 && chmod +x /app/entrypoint.sh \
 && chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# Docker JSONArgsRecommended — exec form 필수 (signal forwarding).
# entrypoint.sh 가 `alembic upgrade head` 후 `exec "$@"` 로 PID 1 교체 → SIGTERM 이 uvicorn 에 직접 도달.
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
