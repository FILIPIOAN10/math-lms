# The tutor's HTTP service (tutor.api). Only the backend calls it, on the internal network.
FROM python:3.13-slim

COPY --from=ghcr.io/astral-sh/uv:0.12.18 /uv /bin/uv

# OS security fixes published since the base image was built. Changes on every release build (build-push.yml), so
# this layer is never served stale from the build cache.
ARG APT_CACHE_BUST=local
RUN echo "apt cache bust: $APT_CACHE_BUST" && apt-get update && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*
# The app runs from the uv-built venv; the base image's own pip is unused and vendors old copies of msgpack, urllib3
# and setuptools that the image scan (rightly) reports.
RUN python -m pip uninstall -y pip

WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy PYTHONUNBUFFERED=1

# Dependencies first, so a code change does not reinstall them.
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project

COPY src ./src
RUN uv sync --locked --no-dev --no-editable

# `docker compose exec tutor python -m tutor.build_chapter AI` then runs in the venv (docs/DEPLOY.md §10).
ENV PATH="/app/.venv/bin:$PATH"
RUN useradd --system --uid 10001 tutor
USER tutor

EXPOSE 8001
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
    CMD ["/app/.venv/bin/python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8001/health', timeout=3)"]
CMD ["/app/.venv/bin/uvicorn", "tutor.api:app", "--host", "0.0.0.0", "--port", "8001"]
