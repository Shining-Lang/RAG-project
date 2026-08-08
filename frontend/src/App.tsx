import {
  Alert,
  App as AntdApp,
  Badge,
  Button,
  Card,
  Col,
  Divider,
  Empty,
  Form,
  Input,
  Layout,
  List,
  Menu,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import type { MenuProps, UploadProps } from 'antd';
import {
  ApiOutlined,
  BarChartOutlined,
  CloudUploadOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  LogoutOutlined,
  MessageOutlined,
  MonitorOutlined,
  PlusOutlined,
  ReloadOutlined,
  RobotOutlined,
  SendOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { useEffect, useMemo, useState } from 'react';
import {
  askRag,
  askSalesAgent,
  clearToken,
  createKnowledgeBase,
  deleteDocument,
  getSalesToolSnapshot,
  getTokenStats,
  listDocuments,
  listKnowledgeBases,
  login,
  logout,
  parseChartPayload,
  readToken,
  reindexDocument,
  uploadDocument,
} from './api';
import type { ChatTurn, KbDocument, KnowledgeBase, TokenStats } from './types';

const { Header, Sider, Content } = Layout;
const { Title, Text, Paragraph } = Typography;

type PageKey = 'dashboard' | 'kb' | 'rag' | 'sales' | 'ops';

const statusColor: Record<string, string> = {
  DONE: 'success',
  PROCESSING: 'processing',
  PENDING: 'warning',
  FAILED: 'error',
};

function formatBytes(size?: number) {
  if (!size) return '-';
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
}

function LoginScreen({ onLogin }: { onLogin: () => void }) {
  const { message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);

  async function handleFinish(values: { username: string; password: string }) {
    setLoading(true);
    try {
      await login(values.username, values.password);
      message.success('登录成功');
      onLogin();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-shell">
      <div className="login-visual">
        <div className="signal-grid">
          <div className="signal-node primary" />
          <div className="signal-node green" />
          <div className="signal-node amber" />
          <div className="signal-node red" />
        </div>
        <Title level={1}>LSN RAG + Sales Agent</Title>
        <Paragraph>
          面向企业知识库检索、销售数据分析和 Agent 工具编排的一体化控制台。
        </Paragraph>
      </div>
      <Card className="login-card" title="登录控制台">
        <Form layout="vertical" initialValues={{ username: 'admin', password: 'demo123' }} onFinish={handleFinish}>
          <Form.Item name="username" label="账号" rules={[{ required: true }]}>
            <Input size="large" prefix={<RobotOutlined />} placeholder="admin / hr001 / tech001" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true }]}>
            <Input.Password size="large" placeholder="demo123" />
          </Form.Item>
          <Button block size="large" type="primary" htmlType="submit" loading={loading}>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}

function DashboardPage({
  kbList,
  tokenStats,
  onRefresh,
}: {
  kbList: KnowledgeBase[];
  tokenStats?: TokenStats;
  onRefresh: () => void;
}) {
  const publicCount = kbList.filter((kb) => kb.isPublic).length;
  return (
    <div className="page-stack">
      <div className="page-heading">
        <div>
          <Title level={2}>项目总览</Title>
          <Text type="secondary">RAG 检索链路、Sales Agent 工具调用和本地可观测性入口。</Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={onRefresh}>
          刷新
        </Button>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="知识库" value={kbList.length} prefix={<DatabaseOutlined />} />
            <div className="stat-foot">
              <Tag color="green">{publicCount} 个公开</Tag>
              <Tag color="blue">{kbList.length - publicCount} 个受限</Tag>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="Token 总消耗" value={tokenStats?.totalTokens || 0} prefix={<ApiOutlined />} />
            <div className="stat-foot">
              估算成本 ¥{tokenStats?.estimatedCostCny?.toFixed?.(4) || '0.0000'}
            </div>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="观测组件" value={3} suffix="项" prefix={<MonitorOutlined />} />
            <div className="stat-foot">Kafka / Prometheus / Grafana</div>
          </Card>
        </Col>
      </Row>
      <Card title="业务链路">
        <div className="pipeline">
          {['上传文档', 'MinIO', 'Kafka 索引任务', '解析分块', 'Embedding', 'HNSW + GIN', 'RRF + rerank', 'Agent 回答'].map(
            (item, index) => (
              <div className="pipeline-step" key={item}>
                <Badge count={index + 1} color={index % 3 === 0 ? '#2563eb' : index % 3 === 1 ? '#059669' : '#d97706'} />
                <span>{item}</span>
              </div>
            ),
          )}
        </div>
      </Card>
      <Alert
        type="info"
        showIcon
        message="面试边界"
        description="当前前端用于本地演示和工程能力展示。压测已有 k6 smoke 与监控看板，但不能包装成生产高并发结论。"
      />
    </div>
  );
}

function KnowledgeBasePage({
  kbList,
  selectedKbId,
  setSelectedKbId,
  reloadKb,
}: {
  kbList: KnowledgeBase[];
  selectedKbId?: number;
  setSelectedKbId: (id: number) => void;
  reloadKb: () => void;
}) {
  const { message } = AntdApp.useApp();
  const [docs, setDocs] = useState<KbDocument[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm();

  async function loadDocs(kbId = selectedKbId) {
    if (!kbId) return;
    setLoading(true);
    try {
      setDocs(await listDocuments(kbId));
    } catch {
      message.error('读取文档失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (selectedKbId) loadDocs(selectedKbId);
  }, [selectedKbId]);

  const uploadProps: UploadProps = {
    showUploadList: false,
    customRequest: async ({ file, onSuccess, onError }) => {
      if (!selectedKbId) {
        message.warning('请先选择知识库');
        return;
      }
      try {
        await uploadDocument(selectedKbId, file as File);
        message.success('上传成功，索引任务已提交');
        onSuccess?.({});
        loadDocs();
      } catch (error) {
        onError?.(error as Error);
        message.error('上传失败');
      }
    },
  };

  async function handleCreate(values: KnowledgeBase) {
    await createKnowledgeBase(values);
    message.success('知识库已创建');
    setCreateOpen(false);
    form.resetFields();
    reloadKb();
  }

  return (
    <div className="page-stack">
      <div className="page-heading">
        <div>
          <Title level={2}>知识库与文档</Title>
          <Text type="secondary">上传文件、查看索引状态、触发重建索引。</Text>
        </div>
        <Space>
          <Button icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            新建知识库
          </Button>
          <Upload {...uploadProps}>
            <Button type="primary" icon={<CloudUploadOutlined />} disabled={!selectedKbId}>
              上传文档
            </Button>
          </Upload>
        </Space>
      </div>

      <Card>
        <Space wrap>
          <Text strong>当前知识库</Text>
          <Select
            className="kb-select"
            value={selectedKbId}
            placeholder="选择知识库"
            onChange={setSelectedKbId}
            options={kbList.map((kb) => ({
              value: kb.id,
              label: `${kb.name} #${kb.id}`,
            }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => loadDocs()}>
            刷新文档
          </Button>
        </Space>
      </Card>

      <Table<KbDocument>
        rowKey="id"
        loading={loading}
        dataSource={docs}
        pagination={{ pageSize: 8 }}
        columns={[
          { title: '文件', dataIndex: 'fileName', render: (name) => <Text strong>{name}</Text> },
          { title: '大小', dataIndex: 'fileSize', width: 110, render: formatBytes },
          {
            title: '状态',
            dataIndex: 'status',
            width: 130,
            render: (status) => <Tag color={statusColor[status] || 'default'}>{status || '-'}</Tag>,
          },
          { title: 'Chunk', dataIndex: 'chunkCount', width: 90, render: (v) => v || 0 },
          { title: 'Token', dataIndex: 'tokenCount', width: 100, render: (v) => v || 0 },
          {
            title: '操作',
            width: 180,
            render: (_, record) => (
              <Space>
                <Button size="small" onClick={() => selectedKbId && reindexDocument(selectedKbId, record.id).then(() => message.success('已提交重建'))}>
                  重建
                </Button>
                <Button
                  size="small"
                  danger
                  onClick={() =>
                    selectedKbId &&
                    deleteDocument(selectedKbId, record.id).then(() => {
                      message.success('已删除');
                      loadDocs();
                    })
                  }
                >
                  删除
                </Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal title="新建知识库" open={createOpen} onCancel={() => setCreateOpen(false)} footer={null}>
        <Form form={form} layout="vertical" onFinish={handleCreate} initialValues={{ departmentId: 'ALL', isPublic: true }}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="例如：销售政策知识库" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="departmentId" label="部门">
            <Input />
          </Form.Item>
          <Form.Item name="isPublic" label="可见性">
            <Select
              options={[
                { label: '公开', value: true },
                { label: '部门/权限控制', value: false },
              ]}
            />
          </Form.Item>
          <Button htmlType="submit" type="primary" block>
            创建
          </Button>
        </Form>
      </Modal>
    </div>
  );
}

function ChatPanel({
  title,
  description,
  icon,
  kbList,
  selectedKbIds,
  setSelectedKbIds,
  turns,
  setTurns,
  onSubmit,
  placeholders,
}: {
  title: string;
  description: string;
  icon: React.ReactNode;
  kbList: KnowledgeBase[];
  selectedKbIds: number[];
  setSelectedKbIds: (ids: number[]) => void;
  turns: ChatTurn[];
  setTurns: (turns: ChatTurn[]) => void;
  onSubmit: (question: string) => Promise<ChatTurn>;
  placeholders: string[];
}) {
  const { message } = AntdApp.useApp();
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);

  async function send(question = input) {
    const text = question.trim();
    if (!text) return;
    if (!selectedKbIds.length) {
      message.warning('请至少选择一个知识库');
      return;
    }
    const next = [...turns, { role: 'user' as const, content: text }];
    setTurns(next);
    setInput('');
    setLoading(true);
    try {
      const answer = await onSubmit(text);
      setTurns([...next, answer]);
    } catch (error) {
      setTurns([
        ...next,
        {
          role: 'assistant',
          content: error instanceof Error ? error.message : '请求失败，请检查后端服务。',
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page-stack">
      <div className="page-heading">
        <div>
          <Title level={2}>
            {icon} {title}
          </Title>
          <Text type="secondary">{description}</Text>
        </div>
      </div>
      <Card>
        <Space wrap>
          <Text strong>知识库</Text>
          <Select
            mode="multiple"
            className="kb-multi"
            value={selectedKbIds}
            onChange={setSelectedKbIds}
            options={kbList.map((kb) => ({ value: kb.id, label: `${kb.name} #${kb.id}` }))}
          />
          {placeholders.map((item) => (
            <Button key={item} onClick={() => send(item)} disabled={loading}>
              {item}
            </Button>
          ))}
        </Space>
      </Card>
      <Card className="chat-card">
        {turns.length === 0 ? (
          <Empty description="还没有对话，选择知识库后开始提问。" />
        ) : (
          <List
            dataSource={turns}
            renderItem={(turn) => (
              <List.Item className={`chat-row ${turn.role}`}>
                <div className="bubble">
                  <div className="bubble-head">
                    <Tag color={turn.role === 'user' ? 'blue' : 'green'}>{turn.role === 'user' ? '用户' : '助手'}</Tag>
                    {turn.route && <Tag>{turn.route}</Tag>}
                    {turn.latencyMs !== undefined && <Text type="secondary">{turn.latencyMs} ms</Text>}
                  </div>
                  <Paragraph className="answer-text">{turn.content}</Paragraph>
                  {turn.toolTraces?.length ? (
                    <>
                      <Divider orientation="left">Tool Traces</Divider>
                      <Space direction="vertical" size={4}>
                        {turn.toolTraces.map((trace, index) => (
                          <Text code key={`${trace}-${index}`}>
                            {trace}
                          </Text>
                        ))}
                      </Space>
                    </>
                  ) : null}
                  {turn.sources?.length ? (
                    <>
                      <Divider orientation="left">Sources</Divider>
                      <Space direction="vertical" size={6}>
                        {turn.sources.map((source, index) => (
                          <Card size="small" key={`${source.chunkId}-${index}`} className="source-card">
                            <Text strong>{source.docName || `文档 ${source.docId}`}</Text>
                            <Paragraph ellipsis={{ rows: 2 }}>{source.excerpt}</Paragraph>
                          </Card>
                        ))}
                      </Space>
                    </>
                  ) : null}
                </div>
              </List.Item>
            )}
          />
        )}
      </Card>
      <div className="composer">
        <Input.TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault();
              send();
            }
          }}
          autoSize={{ minRows: 2, maxRows: 5 }}
          placeholder="输入问题，Enter 发送，Shift + Enter 换行"
        />
        <Tooltip title="发送">
          <Button type="primary" icon={<SendOutlined />} loading={loading} onClick={() => send()} />
        </Tooltip>
      </div>
    </div>
  );
}

function SalesOpsPanel() {
  const { message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState('');
  const [trend, setTrend] = useState('');
  const [anomalies, setAnomalies] = useState('');
  const [chart, setChart] = useState<unknown>(null);

  async function load() {
    setLoading(true);
    try {
      const data = await getSalesToolSnapshot();
      setSummary(data.summary);
      setTrend(data.trend);
      setAnomalies(data.anomalies);
      setChart(parseChartPayload(data.chart));
    } catch {
      message.error('销售工具快照加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  return (
    <div className="page-stack">
      <div className="page-heading">
        <div>
          <Title level={2}>监控与演示证据</Title>
          <Text type="secondary">后端健康、Prometheus、Grafana 与销售工具快照。</Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
          刷新工具快照
        </Button>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card title="本地服务入口">
            <Space direction="vertical">
              <a href="/actuator/health" target="_blank" rel="noreferrer">Spring Boot Health</a>
              <a href="http://localhost:9090/targets" target="_blank" rel="noreferrer">Prometheus Targets</a>
              <a href="http://localhost:3000/d/lsn-rag-agent-observability/lsn-rag-sales-agent-observability" target="_blank" rel="noreferrer">
                Grafana Dashboard
              </a>
            </Space>
          </Card>
        </Col>
        <Col xs={24} lg={16}>
          <Card title="销售趋势图">
            {chart ? <ReactECharts option={chart as object} style={{ height: 320 }} /> : <Empty description="暂无图表数据" />}
          </Card>
        </Col>
      </Row>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card title="销售汇总">
            <Paragraph className="pre-wrap">{summary || '-'}</Paragraph>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="销售趋势">
            <Paragraph className="pre-wrap">{trend || '-'}</Paragraph>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="异常检测">
            <Paragraph className="pre-wrap">{anomalies || '-'}</Paragraph>
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default function App() {
  const { message } = AntdApp.useApp();
  const [authed, setAuthed] = useState(Boolean(readToken()));
  const [page, setPage] = useState<PageKey>('dashboard');
  const [kbList, setKbList] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<number>();
  const [selectedKbIds, setSelectedKbIds] = useState<number[]>([]);
  const [tokenStats, setTokenStats] = useState<TokenStats>();
  const [ragTurns, setRagTurns] = useState<ChatTurn[]>([]);
  const [agentTurns, setAgentTurns] = useState<ChatTurn[]>([]);

  async function reload() {
    if (!readToken()) return;
    try {
      const [kbs, stats] = await Promise.all([listKnowledgeBases(), getTokenStats().catch(() => undefined)]);
      setKbList(kbs);
      setTokenStats(stats);
      if (kbs.length && !selectedKbId) {
        const salesKb = kbs.find((kb) => kb.name?.includes('销售')) || kbs[0];
        setSelectedKbId(salesKb.id);
        setSelectedKbIds([salesKb.id]);
      }
    } catch {
      clearToken();
      setAuthed(false);
      message.warning('登录状态已失效，请重新登录');
    }
  }

  useEffect(() => {
    if (authed) reload();
  }, [authed]);

  const menuItems: MenuProps['items'] = useMemo(
    () => [
      { key: 'dashboard', icon: <DashboardOutlined />, label: '总览' },
      { key: 'kb', icon: <DatabaseOutlined />, label: '知识库' },
      { key: 'rag', icon: <MessageOutlined />, label: 'RAG 问答' },
      { key: 'sales', icon: <RobotOutlined />, label: 'Sales Agent' },
      { key: 'ops', icon: <BarChartOutlined />, label: '监控演示' },
    ],
    [],
  );

  if (!authed) return <LoginScreen onLogin={() => setAuthed(true)} />;

  return (
    <Layout className="app-shell">
      <Sider width={248} breakpoint="lg" collapsedWidth={0}>
        <div className="brand">
          <div className="brand-mark">LSN</div>
          <div>
            <div className="brand-title">RAG Console</div>
            <div className="brand-subtitle">Knowledge + Agent</div>
          </div>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[page]}
          items={menuItems}
          onClick={(item) => setPage(item.key as PageKey)}
        />
      </Sider>
      <Layout>
        <Header className="topbar">
          <Space>
            <Tag color="blue">Spring Boot</Tag>
            <Tag color="green">Kafka Ready</Tag>
            <Tag color="orange">Prometheus</Tag>
          </Space>
          <Button
            icon={<LogoutOutlined />}
            onClick={() =>
              logout().finally(() => {
                setAuthed(false);
                message.success('已退出');
              })
            }
          >
            退出
          </Button>
        </Header>
        <Content className="content">
          {page === 'dashboard' && <DashboardPage kbList={kbList} tokenStats={tokenStats} onRefresh={reload} />}
          {page === 'kb' && (
            <KnowledgeBasePage
              kbList={kbList}
              selectedKbId={selectedKbId}
              setSelectedKbId={setSelectedKbId}
              reloadKb={reload}
            />
          )}
          {page === 'rag' && (
            <ChatPanel
              title="RAG 问答"
              description="同步调用当前后端 RAG 管道，展示答案、引用来源和延迟。"
              icon={<FileSearchOutlined />}
              kbList={kbList}
              selectedKbIds={selectedKbIds}
              setSelectedKbIds={setSelectedKbIds}
              turns={ragTurns}
              setTurns={setRagTurns}
              placeholders={['客户续约异议怎么处理？', '销售管道停留时间过长怎么办？']}
              onSubmit={async (question) => {
                const res = await askRag(question, selectedKbIds);
                return {
                  role: 'assistant',
                  content: res.answer,
                  latencyMs: res.latencyMs,
                  sources: res.sources,
                };
              }}
            />
          )}
          {page === 'sales' && (
            <ChatPanel
              title="Sales Agent"
              description="调用 LangChain4j function calling，展示工具路由、toolTraces 和知识库引用。"
              icon={<RobotOutlined />}
              kbList={kbList}
              selectedKbIds={selectedKbIds}
              setSelectedKbIds={setSelectedKbIds}
              turns={agentTurns}
              setTurns={setAgentTurns}
              placeholders={['本季度销售冠军是谁？', '结合销售手册给我续约建议。']}
              onSubmit={async (question) => {
                const res = await askSalesAgent(question, selectedKbIds);
                return {
                  role: 'assistant',
                  content: res.answer,
                  latencyMs: res.latencyMs,
                  route: res.route,
                  sources: res.sources,
                  toolTraces: res.toolTraces,
                };
              }}
            />
          )}
          {page === 'ops' && <SalesOpsPanel />}
        </Content>
      </Layout>
    </Layout>
  );
}
