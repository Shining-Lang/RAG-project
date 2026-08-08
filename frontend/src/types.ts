export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface KnowledgeBase {
  id: number;
  name: string;
  description?: string;
  departmentId?: string;
  isPublic?: boolean;
  permission?: string;
}

export interface KbDocument {
  id: number;
  kbId: number;
  fileName: string;
  fileType?: string;
  fileSize?: number;
  status?: string;
  chunkCount?: number;
  tokenCount?: number;
  indexedAt?: string;
  errorMsg?: string;
  version?: number;
}

export interface RagSource {
  chunkId?: number;
  docId?: number;
  docName?: string;
  pageNum?: number;
  sectionTitle?: string;
  excerpt?: string;
  score?: number;
}

export interface RagResponse {
  answer: string;
  sources: RagSource[];
  latencyMs: number;
  notFound?: boolean;
}

export interface SalesAgentResponse {
  sessionId: string;
  answer: string;
  route: string;
  salesContext?: string;
  knowledgeAnswer?: string;
  sources?: RagSource[];
  toolTraces?: string[];
  latencyMs: number;
}

export interface TokenStats {
  embeddingTokens: number;
  contextTokens: number;
  generationTokens: number;
  totalTokens: number;
  estimatedCostCny: number;
}

export interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
  latencyMs?: number;
  sources?: RagSource[];
  toolTraces?: string[];
  route?: string;
}
