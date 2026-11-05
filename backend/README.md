# Backend PoC

This directory now contains the formal backend module skeleton plus an isolated technical proof of concept. Production modules are intentionally empty boundaries until their domain designs are implemented.

Formal structure:

```text
building-blocks/
contracts/                    cross-process events and external specifications only
services/
  platform-service/
    identity / tenant / organization / authorization / localization / configuration / audit
    adapters/
    application/
    architecture-tests/
```

Domain modules expose their in-process Java API from the module root package and keep implementation under `internal`. The term `contract` is reserved for independently versioned cross-process messages and external API specifications.

The `poc-app` module remains isolated validation code and must not become a shared dumping ground for business code.

Run:

```bash
mvn verify
```

Run the real MySQL 8.4 container tests explicitly:

```bash
TESTCONTAINERS_RYUK_DISABLED=true ./mvnw verify -Pcontainer-tests
```

The environment variable is only needed when the Ryuk image cannot be pulled. CI should keep resource cleanup enabled when the image is available.
