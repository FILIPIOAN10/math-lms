proxy_http_version 1.1;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
# $remote_addr already holds the real client (see set_real_ip_from): overwrite, never append a forged chain.
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $public_scheme;
proxy_set_header X-Forwarded-Host $host;
# One id per request, shared with the backend (CorrelationIdFilter) and visible in nginx's own log.
proxy_set_header X-Request-Id $request_id;
proxy_read_timeout 60s;
