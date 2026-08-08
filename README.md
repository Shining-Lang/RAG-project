# LSN RAG 知识库 + 销售 Copilot

企业级 RAG 知识库与销售数据分析 Copilot，融合文档索引、混合检索、重排序、上下文裁剪、LangChain4j 工具调用、销售数据分析和图表生成。

## 项目定位

这个项目不是单纯的知识库问答，也不是普通销售数据 CRUD。它把两类 AI 应用能力合在一起：

- RAG 知识库：支持 Markdown、TXT、DOCX、PDF 文档解析、分块、向量化、HNSW 向量检索、PostgreSQL GIN 全文检索、RRF 融合和 reranker 精排。
- 工具调用型销售 Copilot：基于 LangChain4j 函数调用，让模型按问题主动选择销售数据工具、知识库检索工具或图表工具。

## 核心能力

- 多格式文档索引管道：MinIO 文档存储，解析器按文件类型分发，结构感知/滑动窗口分块，异步索引任务落库。
- 混合检索：向量检索负责语义召回，GIN 全文检索负责关键词召回，RRF 融合多路排名，reranker 做最终精排。
- 增量更新：文档版本号隔离旧 chunk，新版本索引完成后切换可用数据，降低更新期间脏读风险。
- LangChain4j 工具调用：通过 `AiServices.builder(...).tools(...)` 注册销售分析、趋势、异常、图表和知识库检索工具。
- 工具安全校验：对日期、TopN、limit、months、图表维度等函数调用参数做边界约束。
- 工具调用可观测性：记录工具开始/结束、工具参数和结果长度，响应中返回 `toolTraces` 便于调试。
- 流式接口：销售 Agent 支持 SSE token 流式输出，RAG 查询也支持流式输出。
- 图表生成：图表工具输出 `CHART_JSON:` 前缀的 ECharts 配置，前端可直接渲染趋势图、柱状图和饼图。
- 降级策略：LangChain4j Agent 失败时降级到本地销售分析服务；知识库工具失败时保留结构化销售数据回答。

## 销售 Copilot 工具清单

| 工具 | 作用 |
|---|---|
| `SalesQueryTool` | 查询订单明细，支持时间、大区、销售员和 limit 限制 |
| `SalesSummaryTool` | 查询销售汇总、销售员排名、大区排名、产品排名 |
| `SalesTrendTool` | 查询月度趋势、环比、同比 |
| `AnomalyDetectionTool` | 检测大区下滑、产品零销售、销售员退单率异常 |
| `ChartGeneratorTool` | 生成 ECharts 折线图、柱状图、饼图 JSON |
| `KnowledgeRetrievalTool` | 调用 RAG 管道检索销售流程、话术、制度和手册 |

## 关键接口

```http
POST /api/v1/sales-agent/chat
POST /api/v1/sales-agent/chat/stream
GET  /api/v1/sales-agent/tools/summary
GET  /api/v1/sales-agent/tools/top-reps
GET  /api/v1/sales-agent/tools/trend
GET  /api/v1/sales-agent/tools/anomalies
GET  /api/v1/sales-agent/tools/chart/line
```

示例请求：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"demo123"}' | jq -r '.data')

curl -X POST http://localhost:8080/api/v1/sales-agent/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"sessionId":"demo","message":"本季度销售冠军是谁？顺便给出销售建议","kbIds":[5]}'
```

图表工具示例：

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/sales-agent/tools/chart/line?months=6&title=近6个月销售趋势"
```

## 技术栈

- Java 21, Spring Boot 3.5
- Spring AI, LangChain4j
- PostgreSQL, pgvector, GIN, HNSW
- Redis, MinIO, Kafka
- Apache PDFBox, Apache POI, flexmark
- Sa-Token, Actuator, Prometheus 指标

## Kafka 索引任务队列

Kafka 是可选能力，当前本地演示配置默认开启：`index.kafka.enabled=${INDEX_KAFKA_ENABLED:true}`。
如果需要关闭 Kafka，可设置 `INDEX_KAFKA_ENABLED=false`，索引任务会回退到原来的 `@Async` 本地线程池。
开启 Kafka 后，文档上传/重建会先写入 `kb_index_task`，再向 Kafka 投递轻量任务消息，
由消费者拉取消息后执行 MinIO 下载、解析、分块、向量化和 chunk 落库。

```bash
set INDEX_KAFKA_ENABLED=true
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set INDEX_KAFKA_TOPIC=rag-index-task
mvn spring-boot:run
```

消息链路：

```text
文档上传/重建 -> kb_index_task(PENDING) -> Kafka 主题 rag-index-task
              -> KafkaIndexTaskConsumer -> MinIO 下载 -> 解析/分块/向量化/写入 chunk
              -> kb_index_task(DONE/FAILED) + kb_document 状态更新
```

目前只有 MinIO 文档任务会进入 Kafka。测试/初始化用的纯文本任务仍保留本地异步执行，
因为文本内容只在内存中，不适合放进 Kafka 形成大消息。

## 简历表述参考

- 构建企业级 RAG + 工具调用型销售 Copilot，融合文档知识库检索与结构化销售数据分析，支持 LangChain4j 函数调用、RRF 混合检索、重排模型精排和 SSE 流式输出。
- 设计销售 Agent 工具层，将订单查询、业绩排行、趋势分析、异常检测、知识库检索和 ECharts 图表生成封装为可调用工具，并加入参数校验、调用日志和失败降级机制。
- 实现多格式文档索引管道，支持 MD/TXT/DOCX/PDF 解析、结构感知分块、向量化、HNSW 向量召回、GIN 全文召回和增量重建索引。

## 前端控制台

项目内置了一个融合 RAG 知识库前端与销售 Agent 前端思路后的统一前端控制台，位于 `frontend/`。
它不是简单复制两套前端，而是用 React + Vite + Ant Design 做成一个演示入口，覆盖知识库管理、文档上传/重建、RAG 问答、Sales Agent 对话、销售工具快照和监控入口。

```bash
cd frontend
npm install
npm run dev
```

- 前端地址：http://localhost:5173
- 后端代理：http://localhost:8080
- 演示账号：`admin / demo123`

生产构建：

```bash
cd frontend
npm run build
```

## 可观测性与压测

项目内置本地可观测性组件和可复现的 k6 脚本，用于面试演示和本地验证。

```bash
docker compose -f docker-compose.observability.yml up -d
```

- Prometheus 地址：http://localhost:9090
- Grafana 地址：http://localhost:3000
- Prometheus 抓取目标：`host.docker.internal:8080/actuator/prometheus`
- Grafana 看板配置：`ops/grafana/dashboards/lsn-rag-agent-dashboard.json`

运行 k6 脚本：

```bash
k6 run -e BASE_URL=http://localhost:8080 -e KB_IDS=1 tools/k6/rag-query-load.js
k6 run -e BASE_URL=http://localhost:8080 -e KB_IDS=1 tools/k6/sales-agent-load.js
```

自定义指标：

- `rag_query_total`, `rag_query_latency_seconds`, `rag_query_sources`
- `sales_agent_chat_total`, `sales_agent_chat_latency_seconds`
- `rag_index_task_submitted_total`, `rag_index_task_finished_total`
- `rag_index_kafka_consume_total`
- `rag_tokens_embedding_total`, `rag_tokens_context_total`, `rag_tokens_generation_total`

更多验证流程见 `docs/performance/README.md` 和 `docs/performance/load-test-report-template.md`。当前定位是本地可复现压测与可观测性验证，不代表已经完成生产级压测。
