# Observability, Metrics & Health (Deep Dive)

## Introduction

This document provides a focused deep dive into how **observability, metrics, and system health** are implemented in my SpringQueuePro system.

While earlier deep dives explain **how tasks are persisted**, **how execution is coordinated**, and **how workers process tasks**, this document focuses on a different—but equally critical—question:

> **How do we know the system is behaving correctly while it’s running?**

SpringQueuePro treats observability as a **first-class architectural concern**, not an afterthought. Metrics, health signals, and runtime visibility are intentionally woven into:

- task execution paths
- retry orchestration
- worker lifecycle management
- API surfaces
- frontend monitoring pages

This document explains:

- What is measured and why
- How Micrometer metrics are defined and consumed
- How runtime execution is made visible without logs
- How health and metrics are exposed safely via REST
- How the frontend dashboard reflects backend truth

**NOTE**: This document does **not** re-explain task execution logic, Redis locking, or persistence invariants. Those concerns are covered in earlier deep dives. The focus here is strictly on **visibility, diagnostics, and operational confidence**.

---

## Table of Contents

- [Why Observability Matters in SpringQueuePro](#why-observability-matters-in-springqueuepro)
- [Metrics as a First-Class System Interface](#metrics-as-a-first-class-system-interface)
- [Processing Metrics Design](#processing-metrics-design)
- [Worker Runtime Visibility](#worker-runtime-visibility)
- [Processing Event Timeline](#processing-event-timeline)
- [System Health & Actuator Integration](#system-health--actuator-integration)
- [Frontend Monitoring Pages](#frontend-monitoring-pages)
- [Error Handling & Failure Visibility](#error-handling--failure-visibility)
- [Steps Taken to Mimic Production Quality](#steps-taken-to-mimic-production-quality)
- [PostScript: A Quick Note on Grafana (Deferred for Now)](#postscript-a-quick-note-on-grafana-deferred-for-now)

---

## Why Observability Matters in SpringQueuePro

SpringQueuePro is a **concurrent, asynchronous system**. Tasks are:
- created by users
- claimed by workers
- executed asynchronously
- retried with backoff
- potentially failed permanently

In such systems:
- failures are not immediate
- execution is non-deterministic
- logs alone are insufficient
- race conditions may only appear under load

Without strong observability:
- failures become silent
- retries become opaque
- throughput is impossible to reason about
- diagnosing issues requires invasive debugging

For this reason, SpringQueuePro is designed so that:

> **Every meaningful system action emits a signal that can be observed externally.**

## Metrics as a First-Class System Interface

SpringQueuePro uses **Micrometer** as its metrics façade. Rather than treating metrics as implementation details, they are treated as part of the system’s **public operational interface**.

Metrics are:
- explicitly named
- consistently incremented
- semantically meaningful
- safe to query at runtime
- exposed in a controlled manner

This allows the backend, dashboards, and operators to speak a **shared language** about system behavior.

## Processing Metrics Design

All processing-related metrics are defined centrally in `ProcessingMetricsConfig`.

### Why a Dedicated Metrics Configuration

Metrics are defined in one place to ensure:
- consistent naming
- discoverability
- intentional scope
- ease of audit and removal

Nothing is implicitly auto-generated or inferred.

### Core Task Lifecycle Counters

SpringQueuePro tracks the **full lifecycle of task execution** using monotonic counters:

- **`springqpro_tasks_submitted_total`**
  Incremented when a task enters the system.

- **`springqpro_tasks_claimed_total`**
  Incremented only after a task is *successfully claimed* via an atomic database transition.
  This is a critical signal that distinguishes *attempted execution* from *actual ownership*.

- **`springqpro_tasks_completed_total`**
  Incremented only after handler execution completes successfully and the task is persisted as `COMPLETED`.

- **`springqpro_tasks_failed_total`**
  Incremented when a task execution throws and is persisted as `FAILED`.

- **`springqpro_tasks_retried_total`**
  Incremented only when a retry is *scheduled*, not merely attempted.

These counters allow operators to answer questions like:

- Are tasks being created faster than they’re completed?
- Are failures spiking?
- Are retries increasing over time?

### Execution Timing Metrics

SpringQueuePro measures **task execution duration** using a Micrometer `Timer`:

- **`springqpro_task_processing_duration`**

This timer:

- wraps only the handler execution
- excludes claim, lock, and persistence overhead
- publishes percentile histograms (p50, p90, p95, p99)

This mirrors production systems where **latency distributions matter more than averages**.

### API-Origin Metrics

SpringQueuePro also tracks where work originates:

- **`springqpro_api_task_create_total`**

This counter distinguishes:

- task creation via GraphQL
- potential future REST or batch sources

This separation becomes important in multi-client or multi-ingress systems.

### Legacy & Transitional Metrics

Some metrics exist for transparency during architectural evolution:

- **`springqpro_queue_enqueue_total`**
- **`springqpro_queue_enqueue_by_id_total`**
- **`springqpro_queue_memory_size` (Gauge)**

These reflect:

- legacy in-memory queue paths
- transitional execution models
- historical debugging needs

They are intentionally preserved but clearly labeled to avoid confusion.

## Worker Runtime Visibility

SpringQueuePro exposes **worker runtime state** directly from the `ThreadPoolExecutor`.

Rather than guessing or inferring worker behavior, the system reports:

- active worker count
- idle worker count
- in-flight task count

This data is exposed via:

- `QueueService.getExecutor()`
- REST endpoints in `ProcessingEventsController`
- frontend monitoring dashboards

This design ensures that:

> **What the UI displays is a direct reflection of runtime truth.**

---

## Processing Event Timeline

In addition to numeric metrics, SpringQueuePro maintains a **bounded in-memory event log** inside `ProcessingService`.

Each significant execution milestone emits an event:

- CLAIM_START
- CLAIM_SUCCESS
- LOCK_ACQUIRED
- PROCESSING
- COMPLETED
- FAILED
- RETRY_SCHEDULED
- LOCK_RELEASE

### Why an Event Log Exists

Metrics answer **“how much”** and **“how often.”**
Events answer **“what just happened.”**

The event log:

- is size-bounded
- is thread-safe
- requires no external logging system
- is safe to expose via REST

This allows developers to:

- debug execution flow without backend logs
- correlate retries and failures visually
- understand system behavior in real time

## System Health & Actuator Integration

SpringQueuePro exposes system health via Spring Boot Actuator, wrapped in a controlled REST API.

### Health Endpoint Design

The `/api/internal/health` endpoint:

- returns overall system status
- enumerates component health
- avoids exposing internal Actuator endpoints directly
- remains authentication-protected

This ensures:

- observability without overexposure
- consistency across environments
- predictable JSON output for UIs

---

### Metric Snapshot Endpoint

The `/api/internal/metric/{name}` endpoint provides:

- lightweight metric access
- runtime introspection
- support for counters, timers, and gauges

Rather than exposing the entire Actuator metrics surface, SpringQueuePro:

- exposes *exactly what the UI needs*
- maintains a stable contract
- avoids leaking implementation details

---

## Frontend Monitoring Pages

SpringQueuePro’s frontend includes two observability-focused pages:

### Processing Overview Page

This page surfaces:

- worker thread pool status
- queue depth
- total completed tasks
- total failed tasks
- live processing events

The UI polls every few seconds, reinforcing that:

> **Observability is continuous, not snapshot-based.**

---

### System Health Page

This page provides:

- Spring Boot health status
- component-level health indicators
- metrics snapshots
- manual refresh capability

This mirrors production dashboards where:

- health and metrics are viewed together
- operators can distinguish between *functional* and *performance* issues

---

## Error Handling & Failure Visibility

SpringQueuePro uses a centralized `GlobalExceptionHandler` to ensure that:

- errors are shaped consistently
- clients receive structured responses
- failures are visible but controlled
- internal details are not leaked

This matters for observability because:

- predictable error shapes simplify frontend handling
- failures are diagnosable without stack traces
- system behavior remains transparent without being noisy

---

## Steps Taken to Mimic Production Quality

### 1. Observability Is Designed In

Metrics and events are not bolted on—they are integrated into:

- task creation
- claim transitions
- execution
- retries
- completion

---

### 2. Metrics Reflect Domain Semantics

Counters and timers align with **meaningful lifecycle boundaries**, not arbitrary code paths.

---

### 3. Runtime State Is Exposed Explicitly

Worker state and execution flow are reported directly, not inferred.

---

### 4. Health Is Centralized and Safe

Actuator is wrapped, secured, and normalized for consumption.

---

### 5. UI Reflects Backend Truth

The frontend dashboards display **real system signals**, not approximations.

---

## Closing Summary

Observability in SpringQueuePro is not cosmetic—it is **foundational**.

By:

- instrumenting task lifecycles
- exposing worker runtime state
- providing live execution events
- surfacing health and metrics cleanly
- aligning backend truth with frontend visibility

SpringQueuePro ensures that:

> **If the system is running, it can be understood.
> If it fails, it can be diagnosed.
> If it scales, it can be measured.**

This commitment to visibility is what elevates SpringQueuePro from a functional task queue to a **production-adjacent system**.

---

## PostScript: A Quick Note on Grafana (Deferred for Now)

Grafana was meant to be part of this project from the beginning.

The original plan was to pair the Prometheus metrics with a clean Grafana dashboard so you could visually explore throughput, failures, retries, and worker behavior at a glance. As the system evolved, though, it became clear that rushing that layer would do more harm than good (*also, I really just started running way behind on schedule*).

I did prototype a few Grafana dashboards locally, but rather than ship something half-baked or potentially misleading, I decided to leave Grafana as a follow-up step and treat this phase as *metrics-first, visualization-later*.

The important part is that the **metrics surface is already there and stable**. All of the signals you’d expect — task creation, claims, completions, failures, retries, execution time, worker activity — are already instrumented and exposed via `/actuator/prometheus`. Grafana would simply sit on top of that; it wouldn’t require changes to how tasks are processed or measured.

This section is mainly here for transparency, and it’s also something I’d remove entirely once Grafana is integrated.

---
