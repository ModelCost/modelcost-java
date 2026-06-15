# Changelog

All notable changes to `modelcost-sdk` will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-06-07

PHI/PII hardening: prompt/response content and raw identifiers are now
**architecturally incapable** of reaching ModelCost servers, not merely gated by a
flag. See `egress-allowlist.json` and the repo's `SECURITY_HARDENING` docs.

### Removed (breaking)
- **`POST /api/v1/governance/scan` and `ModelCostClient.scanText(...)`** — the only
  path that transmitted raw prompt/message text. Governance is now enforced
  **entirely client-side**; only de-identified metadata signals are sent.
  `ModelCost.scanPii(...)` now returns a local `PiiScanner.FullScanResult` (no network).
- **`contentPrivacy` config / `MODELCOST_CONTENT_PRIVACY`** — obsolete now that no
  code path transmits content. `ModelCostConfig.Builder.contentPrivacy(...)` throws
  `UnsupportedOperationException` (clear migration error, not a silent no-op).
- **`metadata` field** on `TrackRequest` — an unbounded PII channel.
  `TrackRequest.Builder.metadata(...)` throws.
- `GovernanceScanRequest` / `GovernanceScanResponse` model classes.

### Added
- **`ai.modelcost.sdk.Identifiers`**: `customer_id` / `user_id` are validated to
  reject raw direct identifiers and pseudonymized via **HMAC-SHA256** with an optional
  customer-held `identifierSecret` (`MODELCOST_IDENTIFIER_SECRET`) that never leaves
  the environment. Bare SHA-256 is intentionally not used.
- **`ai.modelcost.sdk.governance.GovernanceEnforcer`**: shared local-only enforcement.
- **Enforced egress invariant tests** (`EgressInvariantTest`) that inspect real
  outbound bytes, plus `egress-allowlist.json` and a CI guard
  (`scripts/egress_guard.sh`) against reintroducing content-transmitting paths.

### Changed
- Governance signals are aggregated to one request per distinct violation kind
  (no per-hit N+1).

## [0.1.0] - 2026-03-01

### Added
- Initial alpha release
- Cost tracking with automatic provider wrapping (OpenAI, Anthropic, Google)
- Budget enforcement with configurable actions (alert, throttle, block)
- PII detection scanning for SSN, email, phone, API keys, and credit cards
- Token bucket rate limiting
- Automatic background telemetry flushing
- Java 17+ support with OkHttp and Jackson
