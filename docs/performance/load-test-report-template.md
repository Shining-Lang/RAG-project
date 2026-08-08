# LSN RAG + Sales Agent Load Test Report

## Test Metadata

- Date:
- Commit:
- Machine:
- Backend profile:
- Kafka enabled:
- Dataset / KB IDs:
- LLM / embedding provider:
- k6 script:
- VUs:
- Duration:

## Results

| Scenario | Requests | Failure Rate | Avg Latency | P95 Latency | P99 Latency | Notes |
|---|---:|---:|---:|---:|---:|---|
| RAG query smoke | 13 | 0.00% | 4.19s HTTP / 4.54s custom | 7.76s HTTP / 7.84s custom | | 1 VU local smoke, KB_ID=5 |
| Sales Agent chat | | | | | | |

## Observability Screenshots To Capture

- `/actuator/health`
- `/actuator/prometheus` metric sample
- Prometheus target health
- Grafana dashboard overview
- k6 terminal summary
- Kafka container status if Kafka is enabled

## Analysis

- Bottleneck observed:
- Main failure type:
- Cache hit/miss observation:
- RAG retrieval quality observation:
- Kafka/indexing observation:
- Next optimization:

## Safe Resume Wording

```text
Built k6-based local load-test scripts and Prometheus/Grafana dashboards for RAG and Sales Agent flows, exposing request throughput, p95 latency, token cost, index task status, and Kafka consumer health metrics.
```
