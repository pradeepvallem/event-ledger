# Account Service

> Ledger service responsible for maintaining immutable account transactions and calculating balances.

---

# Overview

The Account Service is the core financial component of the Event Ledger system.

It receives validated transaction events from the Gateway Service, stores them as immutable ledger entries, and calculates account balances.

Unlike traditional banking systems that maintain a mutable balance column, this service derives balances from transaction history.

This design provides:

- Complete auditability
- Deterministic balance calculations
- Protection against out-of-order events
- Idempotent processing
- Event replay safety

The service owns its own database and has no direct dependency on the Gateway database.

---

# Responsibilities

The Account Service is responsible for:

- Creating new accounts
- Recording immutable transactions
- Preventing duplicate transactions
- Maintaining chronological ordering
- Calculating balances
- Returning account information
- Currency validation
- Exposing health and metrics endpoints

---

# Architecture

```
Gateway Service
       |
       |
 POST /accounts/{id}/transactions
       |
       |
+-------------------------------+
|      Account Controller       |
+---------------+---------------+
                |
                |
+---------------v---------------+
|      Account Service          |
+---------------+---------------+
                |
                |
      +---------v----------+
      | Account Repository |
      +--------------------+
                |
                |
      +---------v----------+
      |Transaction Repository|
      +----------------------+
                |
                |
             H2 Database
```
---

# Domain Model

## Account

Represents a financial account.

Stores:

- Account ID
- Currency
- Created Timestamp

An account does **not** store its balance.

Balances are derived from transaction history.

---

## Transaction

Represents a single immutable ledger event.

Fields

| Field | Description |
|--------|-------------|
| eventId | Unique event identifier |
| accountId | Account owner |
| type | CREDIT / DEBIT |
| amount | Transaction amount |
| currency | Currency |
| eventTimestamp | Original business timestamp |
| receivedAt | Time received by service |

Transactions are never updated.

---

# REST APIs

## Apply Transaction

```
POST /accounts/{accountId}/transactions
```

Example Request

```json
{
  "eventId": "evt-001",
  "type": "CREDIT",
  "amount": 100.00,
  "currency": "USD",
  "eventTimestamp": "2026-07-15T10:00:00Z"
}
```

Success Response

```
201 Created
```

---

## Retrieve Account

```
GET /accounts/{accountId}
```

Returns

- Account
- Currency
- Transactions

---

## Retrieve Balance

```
GET /accounts/{accountId}/balance
```

Example

```json
{
  "accountId":"acct-100",
  "currency":"USD",
  "balance":125.00
}
```

---

# Error Handling

| Status | Meaning |
|---------|----------|
|400|Validation failed|
|404|Account not found|
|409|Currency conflict|
|409|Duplicate event|
|500|Unexpected server error|

---

# Observability

The service provides

```
GET /health

GET /metrics

GET /prometheus
```

Every request contains

- Trace ID
- Span ID

allowing distributed tracing across services.

---

# Testing Strategy
Run all tests

```bash
mvn clean test
```

---

# Design Decisions

## Why separate database?

Avoids tight coupling between services.

---

## Why immutable transactions?

Supports auditing and reconciliation.

---

## Why calculate balance instead of storing it?

Eliminates synchronization issues.

---

## Why unique event IDs?

Provides exactly-once transaction processing semantics.

---

## Why sort chronologically?

Ensures deterministic balance calculation regardless of arrival order.

---

# Future Enhancements

Potential production improvements

- PostgreSQL
- Optimistic locking
- Event sourcing
- Kafka integration
- Read replicas
- Partitioned transaction tables
