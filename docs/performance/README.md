# Performance and Observability

This folder keeps reproducible local evidence for interview/demo use. It does not claim production-grade load testing unless a dated report is added under this folder.

## Start Observability Stack

```powershell
docker compose -f docker-compose.observability.yml up -d
```

Open:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000

Grafana default account:

```text
admin / admin
```

The built-in dashboard is provisioned from:

```text
ops/grafana/dashboards/lsn-rag-agent-dashboard.json
```

Local evidence screenshots:

```text
docs/performance/screenshots/actuator-health.png
docs/performance/screenshots/prometheus-targets.png
docs/performance/screenshots/grafana-dashboard.png
```

Prometheus scrapes:

```text
http://host.docker.internal:8080/actuator/prometheus
```

## Run Load Tests

Install k6 locally or run it with Docker.

RAG query:

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e KB_IDS=1 -e VUS=5 -e DURATION=2m tools/k6/rag-query-load.js
```

Sales Agent:

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e KB_IDS=1 -e VUS=3 -e DURATION=2m tools/k6/sales-agent-load.js
```

Docker version:

```powershell
docker run --rm -i grafana/k6 run -e BASE_URL=http://host.docker.internal:8080 -e KB_IDS=1 - < tools/k6/rag-query-load.js
```

## Metrics Added By This Project

- `rag_query_total`: RAG request count by status.
- `rag_query_latency_seconds`: RAG query latency histogram.
- `rag_query_sources`: number of sources returned by RAG.
- `sales_agent_chat_total`: Sales Agent request count by route and status.
- `sales_agent_chat_latency_seconds`: Sales Agent latency histogram.
- `rag_index_task_submitted_total`: index task submissions by task type and channel.
- `rag_index_task_finished_total`: index task completion/failure count.
- `rag_index_kafka_consume_total`: Kafka consumer success/error count.
- `rag_tokens_embedding_total`, `rag_tokens_context_total`, `rag_tokens_generation_total`: token cost counters from `TokenMetrics`.

## Interview Boundary

Safe wording:

```text
I added reproducible local load-test scripts and a Prometheus/Grafana observability stack. The current evidence proves the system exposes measurable latency, throughput, token cost, index task, and Kafka-consumer metrics. It is not yet a production pressure-test report because there is no isolated test environment, fixed dataset, repeated runs, or capacity baseline.
```

Do not claim:

```text
The system has passed production stress testing.
```
