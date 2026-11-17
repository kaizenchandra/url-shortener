"""Repeatable local HTTP lifecycle check; never follows destination redirects."""
import json
import os
import time
import urllib.error
import urllib.request
import uuid


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


base = os.environ.get("BASE_URL", "http://localhost:8081").rstrip("/")
token = os.environ["TOKEN"]
client = urllib.request.build_opener(NoRedirect)


def request(path, expected, method="GET", data=None, headers=None, auth=True):
    hdrs = {"Authorization": "Bearer " + token} if auth else {}
    hdrs.update(headers or {})
    if data is not None:
        hdrs["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, method=method, headers=hdrs,
                                 data=json.dumps(data).encode() if data is not None else None)
    try:
        response = client.open(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read()
        assert response.status == expected, (method, path, response.status, expected, body)
        print(f"PASS {method} {path}: HTTP {response.status}")
        return body, response.headers


for attempt in range(60):
    try:
        request("/actuator/health/readiness", 200, auth=False)
        break
    except (OSError, AssertionError):
        if attempt == 59:
            raise
        time.sleep(1)

request("/api/v1/links", 401, auth=False)
key = "smoke-" + uuid.uuid4().hex
payload = {"originalUrl": "https://example.com/smoke"}
body, _ = request("/api/v1/links", 201, "POST", payload, {"Idempotency-Key": key})
created = json.loads(body)
assert created["shortUrl"] == base + "/" + created["shortCode"], "Unexpected public short URL"
path = "/api/v1/links/" + created["id"]
version = created["version"]
deleted = False
try:
    replay, _ = request("/api/v1/links", 201, "POST", payload, {"Idempotency-Key": key})
    assert json.loads(replay) == created, "Idempotency replay changed the link"
    request("/api/v1/links", 409, "POST", {"originalUrl": "https://example.org"},
            {"Idempotency-Key": key})
    request(path, 200)
    request("/api/v1/links", 200)
    redirect = "/" + created["shortCode"]
    _, headers = request(redirect, 302, auth=False)
    assert headers["Location"] == payload["originalUrl"]
    assert headers["Cache-Control"] == "no-store"
    body, _ = request(path, 200, "PATCH", {"originalUrl": "https://example.org/updated", "version": version})
    version = json.loads(body)["version"]
    request(path, 409, "PATCH", {"originalUrl": "https://example.org/stale", "version": version - 1})
    time.sleep(2.2)  # The documented redirect cache staleness bound.
    _, headers = request(redirect, 302, auth=False)
    assert headers["Location"] == "https://example.org/updated"
    request(path + "/analytics", 200)
    request(path + "?version=" + str(version), 204, "DELETE")
    deleted = True
    time.sleep(2.2)
    request(redirect, 410, auth=False)
finally:
    if not deleted:
        request(path + "?version=" + str(version), 204, "DELETE")
print("Smoke test passed: authentication, creation, idempotency, redirects, update, analytics, deletion.")
