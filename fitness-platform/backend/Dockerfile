FROM python:3.13-slim AS runtime
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_LINK_MODE=copy \
    PATH="/app/.venv/bin:$PATH"
WORKDIR /app
RUN pip install --no-cache-dir uv==0.10.0 \
    && groupadd --system fitness \
    && useradd --system --gid fitness --create-home fitness
COPY pyproject.toml uv.lock README.md ./
COPY src ./src
COPY tests ./tests
COPY alembic.ini ./
COPY alembic ./alembic
RUN uv sync --frozen --extra dev \
    && chown -R fitness:fitness /app
USER fitness
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)"
CMD ["sh", "-c", "alembic upgrade head && uvicorn fitness_platform.main:app --host 0.0.0.0 --port 8000"]
