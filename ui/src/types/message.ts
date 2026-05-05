declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace MESSAGE {
    type PlanStatus = 'not_started' | 'in_progress' | 'completed'

    type ToolResult = {
      toolName: string
      toolResult: string
      toolCallId?: string
      toolParam?: {
        query: string
      }
    }

    type History = MsgItem[]

    // 接口数据
    type MsgItem = {
      logId: number
      name: string
      createTime: string
      agentId: string
      deleted: boolean
      sessionId: string
      requestId: string
      question: Question
      answer: Answer
      taskStatus: number
    }

    type FileItem =  {
      name: string,
      url: string,
      type: string,
      size: number
    }

    interface Question {
      query: string
      agentId: number
      sessionId: string
      requestId: string
      file: FileInfo
      files: FileInfo[]
      erp: string
      reAnswer: boolean
      token: string
      /**
       * 记录一些数据类型
       */
      extParams: ExtParams
      type: string
      commandCode: string
      multiAgentReAnswer: boolean
      traceId: string
      nextMessageKey: string
      isStream: string
      outputStyle: string
      callbackUrl: string
    }

    interface ExtParams {
      isMultiAgent: boolean
    }
    interface Answer {
      status: string
      response: string
      responseAll: string
      finished: boolean
      useTimes: number
      useTokens: number
      resultMap: ResultMap
      responseType: string
      voiceUrl: string
      traceId: string
      reqId?: string
      encrypted: boolean
      thinkContent?: string
      thinkStatus?: string
      runningLog: string
      query: string
      messages: string
      packageType: string
      errorMsg: string
    }

    interface MultiAgent {
      tasks: Task[][]
      plan?: Plan
      plan_thought?: string
      plannerRounds?: PlannerRound[]
    }

    interface PlannerRound {
      plannerRoundId: string
      planThought?: string
      planThoughtMessageId?: string
      planThoughtTaskId?: string
      plan?: Plan
      planMessageId?: string
      planTaskId?: string
    }

    interface Plan {
      taskId?: string;
      notes: string[]
      stages: string[]
      title: string
      stepStatus: PlanStatus[]
      steps: string[]
    }

    interface Task {
      messageTime: string
      task?: string
      taskId?: string
      messageType: string
      artifactRefs?: ArtifactReference[]
      resultMap: ResultMap
      requestId: string
      messageId: string
      finish: boolean
      isFinal: boolean
      toolThought?: string
      digitalEmployee?: string
      plan?: Plan
      result?: string
      toolResult?: ToolResult
      planThought?: string
      plannerRoundId?: string
      id: string
    }

    interface EventData {
      messageOrder: number
      messageType: string
      artifactRefs?: ArtifactReference[]
      resultMap: Task
      messageId: string
      taskId: string
      taskOrder: number
    }

    interface ArtifactReference {
      artifactType?: string
      displayName?: string
      resourceKey?: string
      downloadUrl?: string | null
      previewUrl?: string | null
      fileSize?: number | null
      mimeType?: string | null
      missing?: boolean
      missingReason?: string | null
    }

    type ToolResultDataType = {
      pageName: string;
      name: string;
      pageContent: string;
      page_content: string;
      sourceUrl: string;
      source_url: string;
    }

    interface ResultMap {
      multiAgent?: MultiAgent
      eventResult?: EventResult
      agentType?: number
      searchResult?: SearchResult
      resultMap?: ResultMap
      messageType?: string
      requestId?: string
      query?: string
      isFinal?: boolean
      searchFinish?: boolean
      answer?: string
      taskSummary?: string
      fileList?: FileInfo[]
      fileInfo?: FileInfo[]
      command?: string
      primaryFileName?: string
      previewUrl?: string
      downloadUrl?: string
      data?: string
      codeOutput?: string
      requestsId?: string
      resultType?: string;
      eventData?: EventData
      code?: string;
      tip?: string;
      task?: string;
      status?: string
      toolName?: string
      toolCallId?: string
      toolInvocationId?: string | number
      toolProvider?: string
      dispatchIndex?: number
      summary?: string
      errorMsg?: string
      input?: Record<string, unknown>
      toolParam?: Record<string, unknown>
      artifactRefs?: ArtifactReference[]
      plannerRoundId?: string
      refList?: {
        name: string
        pageContent: string
        sourceUrl: string
      }[],
      steps?: Steps[]
    }

    interface Steps {
      status: string,
      goal: string
    }

    interface SearchResult {
      docs: Doc[][]
      query: string[]
    }

    interface Doc {
      link: string
      doc_type: string
      title: string
      content: string
    }

    interface FileInfo {
      fileName: string
      ossUrl: string
      fileSize: number
      domainUrl: string
      downloadUrl?: string
      missing?: boolean
      missingReason?: string
      resourceKey?: string
    }

    type EventResult = object
  }

}
