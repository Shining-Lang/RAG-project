# LSN RAG Knowledge Base + Sales Copilot

企业级 RAG 知识库与销售数据分析 Copilot，融合文档索引、混合检索、重排序、上下文裁剪、LangChain4j Tool Calling、销售数据分析和图表生成。

## 项目定位

这个项目不是单纯的知识库问答，也不是普通销售数据 CRUD。它把两类 AI 应用能力合在一起：

- RAG 知识库：支持 Markdown、TXT、DOCX、PDF 文档解析、分块、向量化、HNSW 向量检索、PostgreSQL GIN 全文检索、RRF 融合和 reranker 精排。
- Tool-Using Sales Copilot：基于 LangChain4j function calling，让模型按问题主动选择销售数据工具、知识库检索工具或图表工具。

## 核心能力

- 多格式文档索引管道：MinIO 文档存储，解析器按文件类型分发，结构感知/滑动窗口分块，异步索引任务落库。
- 混合检索：向量检索负责语义召回，GIN 全文检索负责关键词召回，RRF 融合多路排名，reranker 做最终精排。
- 增量更新：文档版本号隔离旧 chunk，新版本索引完成后切换可用数据，降低更新期间脏读风险。
- LangChain4j Tool Calling：通过 `AiServices.builder(...).tools(...)` 注册销售分析、趋势、异常、图表和知识库检索工具。
- 工具安全校验：对日期、TopN、limit、months、chart dimension 等 function calling 参数做边界约束。
- Tool 可观测性：记录 tool start/done、工具参数和结果长度，响应中返回 `toolTraces` 便于调试。
- 流式接口：销售 Agent 支持 SSE token stream，RAG 查询也支持流式输出。
- 图表生成：Chart 工具输出 `CHART_JSON:` 前缀的 ECharts option，前端可直接渲染趋势图、柱状图和饼图。
- 降级策略：LangChain4j Agent 失败时降级到本地销售分析服务；知识库工具失败时保留结构化销售数据回答。

## Sales Copilot 工具清单

| Tool | 作用 |
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
- Redis, MinIO
- Apache PDFBox, Apache POI, flexmark
- Sa-Token, Actuator, Prometheus Metrics

## 简历表述参考

- 构建企业级 RAG + Tool-Calling Sales Copilot，融合文档知识库检索与结构化销售数据分析，支持 LangChain4j function calling、RRF 混合检索、reranker 精排和 SSE 流式输出。
- 设计销售 Agent 工具层，将订单查询、业绩排行、趋势分析、异常检测、知识库检索和 ECharts 图表生成封装为可调用工具，并加入参数校验、调用日志和失败降级机制。
- 实现多格式文档索引管道，支持 MD/TXT/DOCX/PDF 解析、结构感知分块、向量化、HNSW 向量召回、GIN 全文召回和增量重建索引。
