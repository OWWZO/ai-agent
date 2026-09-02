declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace CHAT {
    export type ChatItem = ReactorType.Merge<
      Pick<MESSAGE.Question, "sessionId" | "query" | "requestId">,
      {
        files: TFile[];
        generatedFiles?: TFile[];
        plan?: MESSAGE.Plan;
        forceStop: boolean;
        tip?: string;
        multiAgent: MESSAGE.MultiAgent;
        agentType?: MESSAGE.ResultMap["agentType"];
        conclusion?: Task;
        responseType?: string;
        loading: boolean;
        tasks: Task[][];
        thought?: string;
        response?: string;
        taskStatus?: MESSAGE.MsgItem["taskStatus"];
        planList?: PlanItem[];
        timeline?: TimelineEntry[];
        metrics?: {
          event_count?: number;
          status?: string;
        };
        /** 上下文占用（SSE context_usage） */
        contextUsage?: ContextUsage;
        startedAt?: string;
        finishedAt?: string;
      }
    >;

    export type ContextUsage = {
      max: number;
      promptTokens?: number;
    };

    export type TimelineEntry = {
      seq: number;
      type: string;
      subType?: string;
      area: string;
      title: string;
      content?: string;
      taskId?: string;
      taskOrder?: number;
      messageIdExt?: string;
      isFinal: boolean;
      status?: string;
      payload?: Record<string, unknown>;
    };

    type PlanItem = {
      name: string;
      list: string[];
    };

    export type TFile = {
      name: string;
      url: string;
      type: string;
      size: number;
      previewUrl?: string;
      downloadUrl?: string;
      missing?: boolean;
      missingReason?: string;
      resourceKey?: string;
      mimeType?: string | null;
      originFileName?: string;
      relativePath?: string;
    };

    export type TInputInfo = {
      files?: TFile[];
      message: string;
      outputStyle?: string;
      deepThink: boolean;
      /** 本轮模型 modelId 或上游 modelName；空=后端默认 */
      model?: string;
      /** 深度思考（本轮） */
      thinking?: boolean;
      /** low | medium | high */
      thinkingEffort?: string;
    };

    export type TAbortController = {
      signal: AbortSignal;
      abort(reason?: unknown): void;
    };

    export type FetchEventSourceInit = {
      onopen: (event: Event) => void;
      onmessage: (event: unknown) => void;
      onerror: (event?: Event) => void;
      onclose: (event?: Event) => void;
      headers?: Record<string, string>;
      body?: string;
    };

    export type Task = ReactorType.Merge<
      MESSAGE.Task,
      {
        resultMap: ReactorType.Merge<
          MESSAGE.ResultMap,
          {
            searchResult?: ReactorType.Merge<
              MESSAGE.SearchResult,
              {
                docs: MESSAGE.Doc[];
              }
            >;
            code?: string;
          }
        >;
        id: string;
        children?: Task[];
      }
    >;

    export type AgentDetailTarget = {
      tool: Task;
      chat: ChatItem;
    };

    export type OpenTaskHandler = (
      task: Task,
      chat: ChatItem,
      backTarget?: AgentDetailTarget
    ) => void;

    export type OpenFileHandler = (
      file: TFile,
      chat: ChatItem,
      backTarget?: AgentDetailTarget
    ) => void;

    export type DataChatChartItem = Record<string, unknown>;

    export type DataChatItem = {
      query: string;
      loading: boolean;
      think: string;
      chartData?: DataChatChartItem[];
      error: string;
    };

    export type DataChatEvent =
      | {
        eventType: "THINK";
        data: string;
      }
      | {
        eventType: "CHART_DATA";
        data: DataChatChartItem[];
      }
      | {
        eventType: "ERROR";
        data: string;
      }
      | {
        eventType: "READY";
        data?: unknown;
      };

    export type FileList = MESSAGE.FileInfo;

    type PlanStatus = MESSAGE.PlanStatus;

    export type Plan = MESSAGE.Plan;
    export type PlannerRound = MESSAGE.PlannerRound;

    export type Product = {
      name: string;
      img: string;
      type: string;
      placeholder: string;
      color: string;
    };

    export type ConversationHistory = {
      id: string;
      sessionId: string;
      title: string;
      productType: string;
      deepThink: boolean;
      createdAt: number;
      updatedAt: number;
      chatTitle: string;
      chatList: ChatItem[];
      dataChatList: DataChatItem[];
    };

    export type ModelInfo = {
      modelName: string;
      modelCode: string;
      schemaList: { columnComment: string; columnName: string; dataType: string; columnId: string }[];
    };
    export type ConversationSessionItem = import("@/services/agentConversation").ConversationSessionItem;
    export type ConversationHistoryDetail = import("@/services/agentConversation").ConversationHistoryDetail;
    export type ConversationHistoryRunDetail = import("@/services/agentConversation").ConversationHistoryRunDetail;
    export type ConversationReplayFrame = import("@/services/agentConversation").ConversationReplayFrame;
  }
}
