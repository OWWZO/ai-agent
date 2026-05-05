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
        startedAt?: string;
        finishedAt?: string;
      }
    >;

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
    };

    export type TInputInfo = {
      files?: TFile[];
      message: string;
      outputStyle?: string;
      deepThink: boolean;
      aiAgentId?: string;
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
      role?: ConversationRole | null;
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

    export type ConversationRole = {
      agentId: string;
      agentName: string;
      available: boolean;
      defaultRole: boolean;
    };

    export type FixRole = {
      agentId: string;
      agentName: string;
      description?: string;
      defaultRole: boolean;
    };

    export type ConversationSessionItem = import("@/services/agentConversation").ConversationSessionItem;
    export type ConversationHistoryDetail = import("@/services/agentConversation").ConversationHistoryDetail;
    export type ConversationHistoryRunDetail = import("@/services/agentConversation").ConversationHistoryRunDetail;
    export type ConversationReplayFrame = import("@/services/agentConversation").ConversationReplayFrame;
  }
}
