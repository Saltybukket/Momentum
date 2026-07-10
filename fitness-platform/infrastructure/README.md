# Infrastructure

The root `docker-compose.yml` is the reproducible local environment. Staging and production are intentionally represented as configuration contracts rather than provider-specific Terraform in this scaffold. Production should use managed PostgreSQL/Redis, TLS termination, secret management, backups, observability, and separate migration jobs.

Environment profiles:

- `local`: Docker Compose, interactive OpenAPI docs enabled.
- `test`: isolated SQLite for fast tests and disposable PostgreSQL in CI integration jobs.
- `staging`: production-like data services with synthetic data only.
- `production`: docs disabled by default, externally managed secrets, strict CORS, TLS and audit retention.
