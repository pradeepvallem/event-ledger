# Event Ledger

An enterprise-style distributed event-ledger system built with Java 21,
Spring Boot 3, Maven, Spring Data JPA, H2, Micrometer Tracing,
OpenTelemetry, Resilience4j, Prometheus, JUnit 5, Mockito, WireMock,
and Docker Compose.

## Architecture

The system contains two independently runnable services:

### Event Gateway

Public-facing event-ingestion service.

Responsibilities:

- Accept transaction events through `POST /events`
- Validate incoming payloads
- Store local event history
- Enforce database-backed idempotency
- Forward accepted transactions to the Account Service
- Provide local event lookup during downstream outages
- Protect downstream calls with a Resilience4j circuit breaker
- Expose health, tracing, logs, and metrics

Default port:

```text
8080