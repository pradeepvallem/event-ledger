# Gateway Service

> Public-facing event ingestion service responsible for request validation, idempotency, event persistence, resiliency, and communication with the Account Service.

---

# Overview

The Gateway Service is the entry point into the Event Ledger system.

It accepts transaction events from clients, validates incoming requests, persists event history, guarantees idempotent processing, and forwards valid transactions to the Account Service.

Unlike the Account Service, the Gateway **does not calculate balances**. Its responsibility is orchestration, resiliency, and maintaining an audit trail of inbound events.

The service owns its own database and communicates with the Account Service exclusively through REST APIs.

---

# Responsibilities

The Gateway Service is responsible for:

- Accepting transaction events
- Request validation
- Idempotency enforcement
- Local event persistence
- Calling the Account Service
- Circuit Breaker protection
- Graceful degradation
- Distributed tracing
- Structured logging
- Metrics collection
- Event history retrieval

---

# Architecture

```
                    Client
                       |
                       |
                POST /events
                       |
                       |
        +--------------v--------------+
        |      Event Controller       |
        +--------------+--------------+
                       |
                       |
        +--------------v--------------+
        |        Event Service        |
        +--------------+--------------+
                       |
          +------------+------------+
          |                         |
          |                         |
          v                         v
 Event Repository          Account Service Client
          |                         |
          |                         |
          v                         |
     Gateway DB                     |
                                    |
                                    v
                         Account Service
```

---

# Event Lifecycle

Every event follows a well-defined lifecycle.

```
                RECEIVED
                    |
                    |
        +-----------+-----------+
        |                       |
        |                       |
     APPLIED                FAILED
        |
        |
 IDPOTENT REPLAY
```

### RECEIVED

The event has been accepted and stored locally.

### APPLIED

The event has been successfully forwarded to the Account Service.

### FAILED

The Account Service could not process the request (for example, due to an outage). The event remains stored locally for auditing.

### IDEMPOTENT REPLAY

A client resubmits an identical event. The Gateway returns the previously stored result instead of processing it again.

---

# Event Persistence

Every inbound request is stored in the Gateway database.

```
EventRecord
```

Typical fields include:

| Field | Description |
|--------|-------------|
| eventId | Business identifier |
| accountId | Account owner |
| type | CREDIT / DEBIT |
| amount | Transaction amount |
| currency | Currency |
| eventTimestamp | Business timestamp |
| status | RECEIVED / APPLIED / FAILED |
| receivedAt | Gateway receive time |
| metadata | Original client metadata |

This local event history enables auditing and graceful degradation.

---

# Idempotency

The Gateway guarantees that identical events are processed only once.

## Strategy

The `eventId` serves as the business key.

The database enforces uniqueness through a primary key or unique constraint.

Processing flow:

```
Client
   |
   v
Is eventId already stored?
      |
 +----+----+
 |         |
 No       Yes
 |         |
Store    Compare payload
 |
Forward
 |
Applied
```

### Identical replay

If the same `eventId` and payload are submitted again:

- No downstream call is made.
- The previous result is returned.

### Conflicting replay

If the same `eventId` is reused with different business data:

- HTTP `409 Conflict`
- Existing event is preserved.

This protects against accidental duplicate submissions and race conditions.

---

# Communication with Account Service

The Gateway communicates with the Account Service using a REST client.

```
POST /accounts/{accountId}/transactions
```

The Gateway never accesses the Account Service database directly.

---

# Circuit Breaker

The outgoing Account Service call is protected by Resilience4j.

```
           CLOSED
              |
         Failures exceed threshold
              |
              v
            OPEN
              |
      Wait duration elapsed
              |
              v
         HALF_OPEN
          /      \
   Success       Failure
     |             |
     v             v
  CLOSED         OPEN
```
---

# Graceful Degradation

If the Account Service is unavailable:

1. The event is stored locally.
2. Status is marked as `FAILED`.
3. Client receives `503 Service Unavailable`.
4. Previously stored events remain queryable.

This ensures that historical event data is never lost, even during downstream outages.

---

# REST APIs

## Submit Event

```
POST /events
```

Example Request

```json
{
  "eventId": "evt-001",
  "accountId": "acct-100",
  "type": "CREDIT",
  "amount": 100.00,
  "currency": "USD",
  "eventTimestamp": "2026-07-15T10:00:00Z",
  "metadata": {
    "source": "mobile-app"
  }
}
```

Possible Responses

| Status | Description |
|--------|-------------|
|201|New event applied|
|200|Idempotent replay|
|400|Validation failure|
|409|Conflicting replay|
|503|Account Service unavailable|

---

## Retrieve Event

```
GET /events/{eventId}
```

Returns the locally stored event.

---

## Retrieve Events for an Account

```
GET /events?account={accountId}
```

Returns events ordered by business timestamp.

---

# Validation

Incoming requests are validated using Jakarta Bean Validation.

Typical validations include:

- Required fields
- Positive amount
- Valid currency
- Supported transaction type
- Valid timestamp

Invalid requests return HTTP `400 Bad Request`.

---

## Structured Logging

Every log entry includes:

- Timestamp
- Service Name
- Log Level
- Trace ID
- Span ID
- Event ID
- Account ID

This enables end-to-end request correlation across services.

---

## Metrics

Micrometer custom metrics expose Gateway activity.

Examples include:

- Total events received
- Successfully applied events
- Failed events
- Idempotent replays
- Conflicting replays

Actuator endpoints:

```
/health
/metrics
/prometheus
```

---

# Error Handling

| Status | Description |
|--------|-------------|
|400|Validation error|
|404|Event not found|
|409|Conflicting replay|
|503|Account Service unavailable|
|500|Unexpected server error|

---

# Testing Strategy

Run all tests:

```bash
mvn clean test
```

---

# Design Decisions

## Why persist events before calling the Account Service?

To ensure every received event has an audit trail, even if downstream processing fails.

## Why database-backed idempotency?

Database constraints provide stronger guarantees than in-memory checks and protect against concurrent duplicate requests.

## Why use a Circuit Breaker?

To prevent repeated calls to an unavailable downstream service and improve system resilience.

## Why maintain a local event history?

To allow clients to retrieve event status even when the Account Service is unavailable.

## Why propagate Trace IDs?

To enable end-to-end observability across distributed services.

---

# Future Enhancements

Potential production improvements:

- Retry queue for failed events
- Kafka-based asynchronous delivery
- Dead Letter Queue (DLQ)
- OAuth2 / JWT authentication
- API versioning
- PostgreSQL persistence
- OpenTelemetry Collector
- Grafana dashboards
- Testcontainers
