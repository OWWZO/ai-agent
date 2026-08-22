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
      eventSeq?: number
      retryMs?: number
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
      planThoughtFinal?: boolean
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
      chapterId?: string
      chapterTitle?: string
      chapterContent?: string
      chapterOrder?: number
      chapterSummary?: string
      chapterStreaming?: boolean
      /** deep_search 分章总结缓存，key 为 chapterId 或章节标题 */
      chapters?: Record<string, DeepSearchChapterState>
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
      /** GenUI tree envelope from emit_ui_tree */
      tree?: Record<string, unknown>
      /** GenUI patches from emit_ui_patch */
      patches?: Array<{ op?: string; path?: string; value?: unknown }>
      canvas_id?: string
      seq?: number
      status?: string
      toolName?: string
      toolCallId?: string
      toolInvocationId?: string | number
      toolProvider?: string
      dispatchIndex?: number
      summary?: string
      /** tool_call 流式阶段的当前累计参数文本，可能尚未形成合法 JSON */
      argumentsText?: string
      /** 对齐 LeAgent：累计原始入参（与 argumentsText 同义，优先用于展示） */
      argumentsRaw?: string
      /** 流式 tool_call 卡片稳定键（后端 streamKey，messageId 同源） */
      streamToolKey?: string
      streamToolIndex?: number
      /** 参数流是否仍在增长（tool_call_delta 期间 true；终态 false） */
      argsStreaming?: boolean
      errorMsg?: string
      input?: Record<string, unknown>
      toolParam?: Record<string, unknown>
      /** 子 Agent 工具事件挂到父 Agent tool_use 下（cc-haha 嵌套） */
      parentToolUseId?: string
      subAgentId?: string
      subAgentType?: string
      subAgentDescription?: string
      /** 后台子 Agent（Dock）；也可出现在 input.run_in_background */
      runInBackground?: boolean
      run_in_background?: boolean
      /**
       * subagent_progress 投影字段（挂在父 Agent 卡片 resultMap 上，不进时间线）。
       * kind: heartbeat | text | line
       */
      subAgentProgressKind?: 'heartbeat' | 'text' | 'line' | string
      subAgentPhase?: string
      subAgentElapsedMs?: number
      subAgentLiveText?: string
      subAgentProgressLines?: string[]
      /** 工具流式输出行（未来 tool_output / 现有 codeOutput 归一） */
      toolOutputLines?: string[]
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

    interface DeepSearchChapterState {
      chapterId?: string
      chapterTitle?: string
      chapterContent?: string
      chapterOrder?: number
      summary?: string
      streaming?: boolean
      queries?: string[]
      docs?: Doc[][]
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
