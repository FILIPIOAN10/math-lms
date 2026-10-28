# The tutor's HTTP service (tutor.api). Only the backend calls it, on the internal network.
FROM python:3.13-slim

COPY --from=ghcr.io/astral-sh/uv:0.12.18 /uv /bin/uv

WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy PYTHONUNBUFFERED=1

# Dependencies first, so a code change does not reinstall them.
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project

COPY src ./src
RUN uv sync --locked --no-dev --no-editable

RUN useradd --system --uid 10001 tutor
USER tutor

EXPOSE 8001
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
    CMD ["/app/.venv/bin/python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8001/health', timeout=3)"]
CMD ["/app/.venv/bin/uvicorn", "tutor.api:app", "--host", "0.0.0.0", "--port", "8001"]
