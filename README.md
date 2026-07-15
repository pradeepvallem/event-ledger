# Event Ledger

> An enterprise-grade distributed event processing system built with **Java 21**, **Spring Boot 3**, **Spring Data JPA**, **Micrometer Tracing**, **Resilience4j**, **Docker**, and **JUnit 5**.

---

## Overview

Event Ledger is a distributed microservices application that demonstrates how financial transaction events can be processed reliably, consistently, and fault-tolerantly in an enterprise environment.

The application is composed of two independently deployable Spring Boot services:

- **Gateway Service**
    - Accepts transaction events
    - Validates incoming requests
    - Enforces idempotency
    - Persists event history
    - Forwards transactions to the Account Service
    - Provides graceful degradation using Resilience4j Circuit Breaker

- **Account Service**
    - Maintains an immutable transaction ledger
    - Calculates account balances
    - Handles out-of-order event processing
    - Prevents duplicate transaction application

Each service owns its own database and communicates exclusively over REST APIs.

---

# Architecture

```text
                    +----------------+
                    |     Client     |
                    +--------+-------+
                             |
                             |
                      POST /events
                             |
                             |
               +-------------v--------------+
               |       Gateway Service      |
               |----------------------------|
               | Validation                 |
               | Idempotency                |
               | Event Persistence          |
               | Circuit Breaker            |
               | Metrics                    |
               | Distributed Tracing        |
               +-------------+--------------+
                             |
                             |
                        REST API
                             |
                             |
               +-------------v--------------+
               |       Account Service      |
               |----------------------------|
               | Ledger                     |
               | Balance Calculation        |
               | Duplicate Protection       |
               | Chronological Ordering     |
               +-------------+--------------+
                             |
                             |
                         H2 Database
```

---

# Key Features

### Gateway Service

- REST-based event ingestion
- Bean Validation
- Database-backed idempotency
- Local event history
- Resilience4j Circuit Breaker
- Graceful degradation
- Micrometer metrics
- Distributed tracing
- Structured JSON logging

### Account Service

- Immutable transaction ledger
- Chronological balance calculation
- Duplicate transaction prevention
- Currency validation
- Independent persistence

### Platform

- Multi-module Maven project
- Independent Spring Boot services
- Separate H2 databases
- Docker Compose support
- Comprehensive automated tests

---

# Technology Stack

| Category | Technology |
|----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Build | Maven |
| Database | H2 |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation |
| Resiliency | Resilience4j |
| Observability | Micrometer |
| Tracing | OpenTelemetry |
| Metrics | Prometheus |
| Logging | Logback JSON |
| Testing | JUnit 5, Mockito, MockMvc, WireMock |
| Containers | Docker & Docker Compose |

# Running the Project

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop (optional)

---

## Build

```bash
mvn clean test
```

---

## Run Locally

Start the Account Service

```bash
mvn -pl account-service spring-boot:run
```

Start the Gateway Service

```bash
mvn -pl gateway-service spring-boot:run
```

---

## Run with Docker

Build images

```bash
docker compose build
```

Start services

```bash
docker compose up -d
```

Stop services

```bash
docker compose down
```

---

# REST APIs

## Gateway Service

| Method | Endpoint | Description |
|---------|----------|-------------|
| POST | `/events` | Submit transaction event |
| GET | `/events/{eventId}` | Retrieve event |
| GET | `/events?account={accountId}` | Retrieve account events |

---

## Account Service

| Method | Endpoint | Description |
|---------|----------|-------------|
| POST | `/accounts/{accountId}/transactions` | Apply transaction |
| GET | `/accounts/{accountId}` | Retrieve account |
| GET | `/accounts/{accountId}/balance` | Retrieve balance |

---

# Observability

The project demonstrates enterprise-grade observability.

## Distributed Tracing

- Micrometer Tracing
- OpenTelemetry Bridge
- W3C Trace Context propagation
- Trace ID propagation across services

## Structured Logging

Every log contains

- Timestamp
- Service Name
- Trace ID
- Span ID
- Log Level

## Metrics

Both services expose

- `/health`
- `/metrics`
- `/prometheus`

Gateway additionally exposes Circuit Breaker metrics.

---

# Testing

The project includes multiple testing layers.

- Unit Tests
- Repository Tests
- Controller Tests
- Integration Tests
- WireMock HTTP Tests
- Circuit Breaker Tests
- End-to-End Docker Validation

Run all tests

```bash
mvn clean test
```
---

# Future Enhancements

Potential production improvements include:

- PostgreSQL
- Kafka event streaming
- Dead Letter Queue
- OAuth2 / JWT Security
- Grafana dashboards
- OpenTelemetry Collector

---

# License

This project was created for learning, demonstration, and technical interview purposes.