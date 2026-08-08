import axios from 'axios';
import type {
  ApiResponse,
  ChatTurn,
  KbDocument,
  KnowledgeBase,
  RagResponse,
  SalesAgentResponse,
  TokenStats,
} from './types';

const TOKEN_KEY = 'lsn-console-token';

export const api = axios.create({
  baseURL: '/api/v1',
  timeout: 120000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export function saveToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function readToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function login(username: string, password: string) {
  const res = await api.post<ApiResponse<string>>('/auth/login', { username, password });
  if (res.data.code !== 200) throw new Error(res.data.message);
  saveToken(res.data.data);
  return res.data.data;
}

export async function logout() {
  try {
    await api.post('/auth/logout');
  } finally {
    clearToken();
  }
}

export async function listKnowledgeBases() {
  const res = await api.get<ApiResponse<KnowledgeBase[]>>('/kb');
  return res.data.data || [];
}

export async function createKnowledgeBase(payload: Pick<KnowledgeBase, 'name' | 'description' | 'departmentId' | 'isPublic'>) {
  const res = await api.post<ApiResponse<KnowledgeBase>>('/kb', payload);
  return res.data.data;
}

export async function listDocuments(kbId: number) {
  const res = await api.get<ApiResponse<KbDocument[]>>(`/kb/${kbId}/documents`);
  return res.data.data || [];
}

export async function uploadDocument(kbId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  const res = await api.post<ApiResponse<unknown>>(`/kb/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function reindexDocument(kbId: number, docId: number) {
  const res = await api.post<ApiResponse<string>>(`/kb/${kbId}/documents/${docId}/reindex`);
  return res.data.data;
}

export async function deleteDocument(kbId: number, docId: number) {
  await api.delete(`/kb/${kbId}/documents/${docId}`);
}

export async function askRag(question: string, kbIds: number[], sessionId?: string) {
  const res = await api.post<RagResponse>('/chat', { question, kbIds, sessionId });
  return res.data;
}

export async function askSalesAgent(message: string, kbIds: number[], sessionId?: string) {
  const res = await api.post<ApiResponse<SalesAgentResponse>>('/sales-agent/chat', {
    message,
    kbIds,
    sessionId,
  });
  return res.data.data;
}

export async function getTokenStats() {
  const res = await api.get<ApiResponse<TokenStats>>('/stats/tokens');
  return res.data.data;
}

export async function getSalesToolSnapshot() {
  const today = new Date();
  const start = new Date(today);
  start.setMonth(today.getMonth() - 3);
  const fmt = (d: Date) => d.toISOString().slice(0, 10);
  const [summary, trend, anomalies, chart] = await Promise.all([
    api.get<ApiResponse<string>>('/sales-agent/tools/summary', {
      params: { startDate: fmt(start), endDate: fmt(today) },
    }),
    api.get<ApiResponse<string>>('/sales-agent/tools/trend', { params: { months: 6 } }),
    api.get<ApiResponse<string>>('/sales-agent/tools/anomalies'),
    api.get<ApiResponse<string>>('/sales-agent/tools/chart/line', {
      params: { months: 6, title: '近 6 个月销售趋势' },
    }),
  ]);
  return {
    summary: summary.data.data,
    trend: trend.data.data,
    anomalies: anomalies.data.data,
    chart: chart.data.data,
  };
}

export function parseChartPayload(payload?: string) {
  if (!payload) return null;
  const marker = 'CHART_JSON:';
  const idx = payload.indexOf(marker);
  if (idx < 0) return null;
  try {
    return JSON.parse(payload.slice(idx + marker.length).trim());
  } catch {
    return null;
  }
}

export function nextTurns(turns: ChatTurn[], turn: ChatTurn) {
  return [...turns, turn];
}
