FROM python:3.12-slim

# OS 보안 패치 — base 이미지에 미반영된 fixed-available OS CVE 해소(예: openssl CVE-2026-45447,
# libssl3t64 3.5.6-1~deb13u1 → deb13u2). Trivy(CRITICAL/HIGH 차단)가 fixed CVE 를 막으므로
# 빌드 시점에 Debian security 업그레이드를 당겨 base 재빌드 지연으로 인한 deploy 차단을 자가치유한다.
RUN apt-get update \
 && apt-get upgrade -y \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

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
