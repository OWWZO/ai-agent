package org.wwz.ai.domain.agent.service.armory.node.factory.element;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import org.wwz.ai.domain.agent.model.valobj.AiClientAdvisorVO;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConstants;
import org.wwz.ai.domain.agent.reactor.data.dto.VectorRecallReq;
import org.wwz.ai.domain.agent.reactor.service.VectorService;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RagAnswerAdvisor implements BaseAdvisor {

    /** 现有成熟的 Qdrant 检索服务，统一承接知识库召回。 */
    private final VectorService vectorService;

    /** 顾问配置中的 RAG 检索参数。 */
    private final AiClientAdvisorVO.RagAnswer ragAnswer;

    /**
     * RAG提示词模板，用于约束大模型的回答规则
     * 占位符{question_answer_context}会被检索到的文档上下文替换
     * 核心规则：仅基于上下文+对话历史回答，禁止使用先验知识，无答案时告知用户
     */
    private final String userTextAdvise;

    /**
     * 构造方法，注入核心依赖并初始化RAG提示词模板
     * @param vectorService Qdrant 向量检索服务
     * @param ragAnswer 顾问 RAG 配置
     */
    public RagAnswerAdvisor(VectorService vectorService, AiClientAdvisorVO.RagAnswer ragAnswer) {
        this.vectorService = vectorService;
        this.ragAnswer = ragAnswer;
        // 初始化RAG提示词模板，明确大模型的回答约束
        this.userTextAdvise = "\nContext information is below, surrounded by ---------------------\n\n---------------------\n{question_answer_context}\n---------------------\n\nGiven the context and provided history information and not prior knowledge,\nreply to the user comment. If the answer is not in the context, inform\nthe user that you can't answer the question.\n";
    }

    /**
     * 前置处理方法（大模型请求发送前执行）：核心RAG检索逻辑
     * 1. 提取用户原始问题，拼接RAG提示词模板
     * 2. 构建带过滤条件的检索请求，从向量库检索相关文档
     * 3. 拼接文档上下文，替换提示词占位符
     * 4. 构建新的对话请求，传递给后续的Advisor/大模型调用链
     *
     * @param chatClientRequest 原始的大模型对话请求对象（包含用户问题、上下文、提示词等）
     * @param advisorChain 顾问链对象，用于调用后续的顾问节点
     * @return 处理后的新对话请求（含补充上下文的提示词、检索文档信息）
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        log.info("RagAnswerAdvisor.before 已触发，将进行向量检索");
        // 1. 获取原始请求的上下文（用于传递自定义过滤条件、业务参数等）
        HashMap<String, Object> context = new HashMap<>(chatClientRequest.context());

        // 2. 提取用户原始问题，拼接RAG提示词模板，生成新的用户提示
        String userText = chatClientRequest.prompt().getUserMessage().getText();
        String advisedUserText = userText + System.lineSeparator() + this.userTextAdvise;

        // 3. 构建最终的检索请求：复用基础模板配置 + 绑定用户问题 + 追加上下文过滤条件
        VectorRecallReq vectorRecallReq = buildVectorRecallReq(userText, context);

        // 4. 调用现有 Qdrant 检索服务，获取与用户问题相关的文档列表
        List<Document> documents = this.vectorService.vectorRecall(vectorRecallReq).stream()
                .map(this::toDocument)
                .collect(Collectors.toList());

        // 5. 将检索到的文档存入上下文，供后置处理/后续节点使用
        context.put("qa_retrieved_documents", documents);

        // 6. 拼接所有检索文档的文本内容，作为大模型的回答上下文
        String documentContext = documents.stream()
                .map(Document::getText) // 提取每个文档的核心文本
                .collect(Collectors.joining(System.lineSeparator())); // 跨平台换行符拼接，避免格式问题

        // 7. 构建提示词参数，替换模板中的{question_answer_context}占位符
        context.put("question_answer_context", documentContext);

        // 8. 构建新的对话请求，包含拼接后的提示词和更新后的上下文
        return ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(
                                new UserMessage(advisedUserText), // 新的用户提示（原始问题+RAG模板）
                                new AssistantMessage(JSON.toJSONString(context)) // 传递模板参数，供大模型解析
                        )
                        .build())
                .context(context) // 传递更新后的上下文（含检索文档、模板参数）
                .build();
    }

    /**
     * 后置处理方法（大模型响应返回后执行）
     * 核心作用：将前置检索到的文档列表附加到响应的元数据中，方便业务层获取/展示/追踪检索结果
     *
     * @param chatClientResponse 大模型返回的原始响应对象
     * @param advisorChain 顾问链对象
     * @return 处理后的响应对象（元数据中包含检索文档列表）
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 1. 基于原始响应构建新的ChatResponse，保持原有响应内容不变
        ChatResponse.Builder chatResponseBuilder = ChatResponse.builder()
                .from(chatClientResponse.chatResponse());

        // 2. 向响应元数据中添加检索文档列表，键为"qa_retrieved_documents"，方便后续获取
        chatResponseBuilder.metadata("qa_retrieved_documents", chatClientResponse.context().get("qa_retrieved_documents"));
        ChatResponse chatResponse = chatResponseBuilder.build();

        // 3. 构建新的响应对象，返回更新后的ChatResponse和原有上下文
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(chatClientResponse.context())
                .build();
    }

    /**
     * 同步对话的核心执行链（Spring AI Advisor核心方法）
     * 定义了顾问的执行逻辑：先执行前置处理 → 调用后续顾问/大模型 → 执行后置处理
     *
     * @param chatClientRequest 原始对话请求
     * @param callAdvisorChain 同步调用的顾问链对象
     * @return 经过前置+后置处理后的最终响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        // 1. 执行前置处理，生成带上下文的新请求
        ChatClientRequest processedRequest = this.before(chatClientRequest, callAdvisorChain);
        // 2. 调用顾问链的下一个节点（后续顾问/大模型调用），获取原始响应
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(processedRequest);
        // 3. 执行后置处理，附加检索元数据，返回最终响应
        return this.after(chatClientResponse, callAdvisorChain);
    }

    /**
     * 流式对话的顾问处理方法（默认实现）
     * 若需支持流式RAG检索增强，可重写此方法，实现流式的前置/后置处理
     * 目前复用父类BaseAdvisor的默认实现，不做额外处理
     *
     * @param chatClientRequest 流式对话的原始请求
     * @param streamAdvisorChain 流式调用的顾问链对象
     * @return 流式响应的Flux对象
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return BaseAdvisor.super.adviseStream(chatClientRequest, streamAdvisorChain);
    }

    /**
     * 获取顾问在链中的执行优先级（Spring Ordered接口实现）
     * 返回0表示**最高优先级**，因为RAG检索需要在所有其他顾问/大模型调用前执行
     *
     * @return 优先级数值，0为最高
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 获取顾问的名称（Spring AI Advisor接口要求）
     * 返回类的简单名称，方便日志打印、监控识别、顾问链管理
     *
     * @return 顾问名称：RagAnswerAdvisor
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 构建/解析检索的过滤表达式（受保护方法，子类可重写扩展）
     * 核心逻辑：优先使用上下文传递的自定义过滤表达式，若无则使用基础searchRequest的默认过滤表达式
     * 用于实现**动态过滤检索**（如按业务类型、文档来源、时间等过滤向量库文档）
     *
     * @param context 对话请求的上下文，可传递自定义过滤表达式键：qa_filter_expression
     * @return 向量库可识别的过滤表达式对象，若无可返回null
     */
    protected VectorRecallReq buildVectorRecallReq(String userText, Map<String, Object> context) {
        VectorRecallReq req = new VectorRecallReq();
        req.setCollectionName(DataAgentConstants.SCHEMA_COLLECTION_NAME);
        req.setQuery(userText);
        req.setLimit(resolveTopK());
        req.setKeywordFilterMap(resolveKeywordFilter(context));
        return req;
    }

    /**
     * 当前项目里知识库顾问的过滤表达式实际只在用 `knowledge == 'xxx'` 这一种形式。
     * 这里直接解析成现有 Qdrant keywordFilterMap，避免继续维护 pgvector/Filter AST 语义。
     */
    protected Map<String, Object> resolveKeywordFilter(Map<String, Object> context) {
        String filterExpression = resolveFilterExpression(context);
        if (!StringUtils.hasText(filterExpression)) {
            return null;
        }
        String normalized = filterExpression.trim();
        if (normalized.startsWith("knowledge == '") && normalized.endsWith("'")) {
            String knowledge = normalized.substring("knowledge == '".length(), normalized.length() - 1);
            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("knowledge", knowledge);
            return filters;
        }
        throw new IllegalArgumentException("当前仅支持 knowledge == 'xxx' 形式的知识库过滤表达式: " + filterExpression);
    }

    protected String resolveFilterExpression(Map<String, Object> context) {
        Object filterExpression = context.get("qa_filter_expression");
        if (filterExpression != null && StringUtils.hasText(filterExpression.toString())) {
            return filterExpression.toString();
        }
        return ragAnswer == null ? null : ragAnswer.getFilterExpression();
    }

    protected int resolveTopK() {
        if (ragAnswer == null || ragAnswer.getTopK() <= 0) {
            return 4;
        }
        return ragAnswer.getTopK();
    }

    private Document toDocument(Map<String, Object> payload) {
        Map<String, Object> metadata = new HashMap<>(payload);
        Object text = metadata.remove("content");
        metadata.remove("score");
        metadata.remove("_id");
        return new Document(text == null ? "" : text.toString(), metadata);
    }

}
