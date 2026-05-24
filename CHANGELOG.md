# Changelog

All notable changes to `info.scoo-va:webhooks` are recorded here.
This project follows [Semantic Versioning](https://semver.org/).

## 1.0.0 — 2026-05-25

First public release.

- `WebhooksClient` — `list()`, `create(url, events)`, `delete(id)` /
  `remove(id)` against `https://api.scoo-va.info/v1/webhooks/*`.
- Top-level `verifyWebhookSignature(body, header, secret)` — HMAC-SHA256
  using `javax.crypto.Mac`, constant-time hex comparison, tolerates the
  `sha256=` header prefix.
- Kotlin coroutines (`suspend`), OkHttp under the hood, JVM 17 target.
- API key resolution: explicit option → `SCOOVA_API_KEY` env → `"demo"`.
- `ScoovaWebhooksError` carries the gateway's structured `status` + `code`.
