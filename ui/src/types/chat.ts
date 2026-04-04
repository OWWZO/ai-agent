declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace CHAT {
    export type ChatItem = ReactorType.Merge<
      Pick<MESSAGE.Question, "sessionId" | "query" | "requestId">,
      {
        files: TFile[];
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
        renderSnapshot?: RenderSnapshotV1;
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
      messageIdExt?: string;
      isFinal: boolean;
    };

    export type RenderSnapshotV1 = {
      v: 1;
      status: string;
      thought?: string;
      plan?: MESSAGE.Plan;
      tasks?: MESSAGE.Task[][];
      conclusion?: Task;
      timeline: TimelineEntry[];
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
    };

    export type TInputInfo = {
      files?: TFile[];
      message: string;
      outputStyle?: string;
      deepThink: boolean;
    };

    export type TAbortController = {
      signal: AbortSignal;
      abort(reason?: any): void;
    };

    export type FetchEventSourceInit = {
      onopen: (event: Event) => void;
      onmessage: (event: any) => void;
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

    export type FileList = MESSAGE.FileInfo;

    type PlanStatus = MESSAGE.PlanStatus;

    export type Plan = MESSAGE.Plan;

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
      dataChatList: Record<string, any>[];
    };

    export type ConversationHistoryStore = {
      version: number;
      conversations: ConversationHistory[];
    };

    export type ModelInfo = {
      modelName: string;
      modelCode: string;
      schemaList: { columnComment: string; columnName: string; dataType: string; columnId: string }[];
    };
  }
}
