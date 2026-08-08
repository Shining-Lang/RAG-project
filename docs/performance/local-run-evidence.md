# Local Run Evidence

Date: 2026-08-08

## Services

```text
lsn-kafka       apache/kafka:3.9.1        localhost:9092
lsn-prometheus  prom/prometheus:v2.55.1   localhost:9090
lsn-grafana     grafana/grafana:11.4.0    localhost:3000
Spring Boot     RagKbApplication          localhost:8080
```

## Verified Checks

Health endpoint:

```text
GET http://localhost:8080/actuator/health -> status UP
PostgreSQL -> UP
Redis -> UP
```

Prometheus target:

```text
job=lsn-rag-kb
scrapeUrl=http://host.docker.internal:8080/actuator/prometheus
health=up
```

Grafana:

```text
Dashboard: LSN RAG + Sales Agent Observability
Panels: Request Throughput, P95 Latency, Index and Kafka Results
```

Screenshots:

```text
docs/performance/screenshots/actuator-health.png
docs/performance/screenshots/prometheus-targets.png
docs/performance/screenshots/grafana-dashboard.png
```

## Boundary

This evidence proves local deployment and observability wiring. It is not a formal production pressure-test report. A formal report still needs fixed test data, repeated k6 runs, machine specs, latency/QPS/failure-rate tables, and capacity analysis.

## k6 Smoke Result

Script:

```text
tools/k6/rag-query-load.js
```

Command:

```powershell
Get-Content -Raw tools/k6/rag-query-load.js |
  docker run --rm -i grafana/k6:0.54.0 run `
    -e BASE_URL=http://host.docker.internal:8080 `
    -e KB_IDS=5 `
    -e VUS=1 `
    -e DURATION=5s -
```

Result snapshot:

```text
checks: 100.00% 38 out of 38
http_req_failed: 0.00% 0 out of 13
http_reqs: 13
iterations: 12
rag_query_ok: 100.00% 12 out of 12
rag_query_latency avg: 4544.67 ms
rag_query_latency p95: 7837.1 ms
```

Raw output:

```text
docs/performance/k6-rag-smoke.txt
```

Interpretation:

```text
This is a local smoke/load sanity check with 1 VU, not a capacity benchmark. It proves the authenticated k6 script can hit the real RAG query path and emit measurable latency/failure-rate data. Formal pressure testing still needs repeated runs, larger VU stages, stable test data, and resource monitoring.
```
