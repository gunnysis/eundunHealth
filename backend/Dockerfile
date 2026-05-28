FROM python:3.12-slim

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
