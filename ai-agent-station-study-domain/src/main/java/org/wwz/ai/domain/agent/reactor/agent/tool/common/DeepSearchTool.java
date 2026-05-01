package org.wwz.ai.domain.agent.reactor.agent.tool.common;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.context.ApplicationContext;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.reactor.agent.dto.DeepSearchRequest;
import org.wwz.ai.domain.agent.reactor.agent.dto.DeepSearchrResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.FileRequest;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Data

public class DeepSearchTool implements BaseTool {

    /**
     * deep_search 保底超时时间，避免外部流式接口异常时导致 future.get() 长时间阻塞。
     */
    private static final long DEEP_SEARCH_TIMEOUT_MINUTES = 20L;
    /**
     * deep_search HTTP 连接超时时间。
     */
    private static final long DEEP_SEARCH_CONNECT_TIMEOUT_SECONDS = 30L;
    /**
     * deep_search HTTP 读写超时时间。
     */
    private static final long DEEP_SEARCH_IO_TIMEOUT_MINUTES = 20L;

    private AgentContext agentContext;
    /**
     * 当前正在执行的 deep_search SSE 连接，用于超时后主动取消。
     */
    private volatile EventSource activeEventSource;

    @Override
    public String getName() {
        return "deep_search";
    }

    @Override
    public String getDescription() {
        String desc = "这是一个搜索工具，可以通过搜索内外网知识";
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        return reactorConfig.getDeepSearchToolDesc().isEmpty() ? desc : reactorConfig.getDeepSearchToolDesc();
    }

    @Override
    public Map<String, Object> toParams() {

        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        if (!reactorConfig.getDeepSearchToolParams().isEmpty()) {
            return reactorConfig.getDeepSearchToolParams();
        }

        Map<String, Object> taskParam = new HashMap<>();
        taskParam.put("type", "string");
        taskParam.put("description", "需要搜索的query");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("query", taskParam);
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("query"));

        return parameters;
    }

    @Override
    public Object execute(Object input) {
        long startTime = System.currentTimeMillis();

        try {
            ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
            Map<String, Object> params = (Map<String, Object>) input;
            String query = (String) params.get("query");
            Map<String, Object> srcConfig = new HashMap<>();

            Map<String, Object> bingConfig = new HashMap<>();
            bingConfig.put("count", Integer.parseInt(reactorConfig.getDeepSearchPageCount()));
            srcConfig.put("bing", bingConfig);
            DeepSearchRequest request = DeepSearchRequest.builder()
                    .request_id(agentContext.getRequestId() + ":" + StringUtil.generateRandomString(5))
                    .query(query)
                    .agent_id("1")
                    .scene_type("auto_agent")
                    .src_configs(srcConfig)
                    .stream(true)
                    .content_stream(agentContext.getIsStream())
                    .build();
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());

            // 调用流式 API
            Future<ToolResultPayload> future = callDeepSearchStream(request, artifactSource);
            Object object = future.get(DEEP_SEARCH_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            return object;
        } catch (TimeoutException e) {
            if (activeEventSource != null) {
                activeEventSource.cancel();
            }
            log.error("{} deep_search timeout after {} minutes", agentContext.getRequestId(), DEEP_SEARCH_TIMEOUT_MINUTES, e);
            return buildFailurePayload("deep_search执行超时，已终止本次搜索，请基于当前已获取的信息继续处理。");
        } catch (Exception e) {

            log.error("{} deep_search agent error", agentContext.getRequestId(), e);
            return buildFailurePayload("deep_search执行失败：" + StringUtils.defaultIfBlank(e.getMessage(), "未知异常"));
        } finally {
            activeEventSource = null;
        }
    }

    /**
     * 调用 DeepSearch
     */
    public CompletableFuture<ToolResultPayload> callDeepSearchStream(DeepSearchRequest searchRequest,
                                                                     ToolArtifactSource artifactSource) {
        CompletableFuture<ToolResultPayload> future = new CompletableFuture<>();
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(DEEP_SEARCH_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(DEEP_SEARCH_IO_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .writeTimeout(DEEP_SEARCH_IO_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .callTimeout(DEEP_SEARCH_IO_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .build();

            ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
            ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);
            String url = reactorConfig.getDeepSearchUrl() + "/v1/tool/deepsearch";
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    JSONObject.toJSONString(searchRequest)
            );

            log.info("{} deep_search request {}", agentContext.getRequestId(), JSONObject.toJSONString(searchRequest));
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .post(body);
            Request request = requestBuilder.build();

            String[] interval = reactorConfig.getMessageInterval().getOrDefault("search", "5,20").split(",");
            int firstInterval = Integer.parseInt(interval[0]);
            int sendInterval = Integer.parseInt(interval[1]);
            AtomicInteger index = new AtomicInteger(1);
            AtomicReference<String> resultRef = new AtomicReference<>("搜索结果为空");
            AtomicReference<String> messageIdRef = new AtomicReference<>("");
            StringBuilder stringBuilderIncr = new StringBuilder();
            StringBuilder stringBuilderAll = new StringBuilder();
            DeepSearchStructuredResultBuilder resultBuilder = new DeepSearchStructuredResultBuilder(searchRequest.getQuery());
            String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
            EventSource.Factory factory = EventSources.createFactory(client);
            activeEventSource = factory.newEventSource(request, new EventSourceListener() {
                @Override
                public void onOpen(EventSource eventSource, Response response) {
                    log.info("{} deep_search response {} {} {}", agentContext.getRequestId(), response, response.code(), response.body());
                }

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    try {
                        if ("[DONE]".equals(data)) {
                            return;
                        }
                        if (data.startsWith("heartbeat")) {
                            return;
                        }
                        int currentIndex = index.get();
                        if (currentIndex == 1 || currentIndex % 100 == 0) {
                            log.info("{} deep_search recv data: {}", agentContext.getRequestId(), data);
                        }
                        DeepSearchrResponse searchResponse = JSONObject.parseObject(data, DeepSearchrResponse.class);
                        FileTool fileTool = new FileTool();
                        fileTool.setAgentContext(agentContext);
                        // 使用标准 SSE 客户端逐条消费事件，避免 extend 被上游缓冲后延迟透传。
                        if (searchResponse.getIsFinal()) {
                            if (agentContext.getIsStream()) {
                                searchResponse.setAnswer(stringBuilderAll.toString());
                            }
                            if (searchResponse.getAnswer().isEmpty()) {
                                log.error("{} deep search answer empty", agentContext.getRequestId());
                                resultRef.set("搜索结果为空");
                                return;
                            }
                            resultBuilder.recordFinalAnswer(searchResponse.getQuery(), searchResponse.getAnswer());
                            String fileName = StringUtil.removeSpecialChars(searchResponse.getQuery() + "的搜索结果.md");
                            String fileDesc = searchResponse.getAnswer()
                                    .substring(0, Math.min(searchResponse.getAnswer().length(), reactorConfig.getDeepSearchToolFileDescTruncateLen())) + "...";
                            FileRequest fileRequest = FileRequest.builder()
                                    .requestId(agentContext.getRequestId())
                                    .fileName(fileName)
                                    .description(fileDesc)
                                    .content(searchResponse.getAnswer())
                                    .build();
                            fileTool.uploadFile(fileRequest, false, false, artifactSource);
                            resultRef.set(searchResponse.getAnswer()
                                    .substring(0, Math.min(searchResponse.getAnswer().length(), reactorConfig.getDeepSearchToolMessageTruncateLen())));

                            agentContext.getPrinter().send(messageIdRef.get(), "deep_search", searchResponse, digitalEmployee, true);
                            return;
                        }

                        resultBuilder.recordEvent(searchResponse);

                        Map<String, Object> contentMap = new HashMap<>();
                        if (searchResponse.getSearchResult() != null
                                && searchResponse.getSearchResult().getQuery() != null
                                && searchResponse.getSearchResult().getDocs() != null) {
                            for (int idx = 0; idx < searchResponse.getSearchResult().getQuery().size(); idx++) {
                                contentMap.put(searchResponse.getSearchResult().getQuery().get(idx),
                                        searchResponse.getSearchResult().getDocs().get(idx));
                            }
                        }

                        if ("extend".equals(searchResponse.getMessageType())) {
                            messageIdRef.set(StringUtil.getUUID());
                            searchResponse.setSearchFinish(false);
                            agentContext.getPrinter().send(messageIdRef.get(), "deep_search", searchResponse, digitalEmployee, true);
                        } else if ("search".equals(searchResponse.getMessageType())) {
                            searchResponse.setSearchFinish(true);
                            agentContext.getPrinter().send(messageIdRef.get(), "deep_search", searchResponse, digitalEmployee, true);
                            FileRequest fileRequest = FileRequest.builder()
                                    .requestId(agentContext.getRequestId())
                                    .fileName(searchResponse.getQuery() + "_search_result.txt")
                                    .description(searchResponse.getQuery() + "...")
                                    .content(JSON.toJSONString(contentMap))
                                    .build();
                            fileTool.uploadFile(fileRequest, false, true, artifactSource);
                        } else if ("report".equals(searchResponse.getMessageType())) {
                            if (currentIndex == 1 && messageIdRef.get().isEmpty()) {
                                messageIdRef.set(StringUtil.getUUID());
                            }
                            stringBuilderIncr.append(searchResponse.getAnswer());
                            stringBuilderAll.append(searchResponse.getAnswer());
                            if (currentIndex == firstInterval || currentIndex % sendInterval == 0) {
                                searchResponse.setAnswer(stringBuilderIncr.toString());
                                agentContext.getPrinter().send(messageIdRef.get(), "deep_search", searchResponse, digitalEmployee, false);
                                stringBuilderIncr.setLength(0);
                            }
                            index.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.error("{} deep_search request error", agentContext.getRequestId(), e);
                        if (!future.isDone()) {
                            future.completeExceptionally(e);
                        }
                        eventSource.cancel();
                    }
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    activeEventSource = null;
                    if (!future.isDone()) {
                        future.complete(resultBuilder.buildPayload(resultRef.get()));
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    activeEventSource = null;
                    if (t == null && response == null) {
                        if (!future.isDone()) {
                            future.complete(resultBuilder.buildPayload(resultRef.get()));
                        }
                        return;
                    }
                    log.error("{} deep_search on failure", agentContext.getRequestId(), t);
                    if (!future.isDone()) {
                        future.completeExceptionally(t instanceof Exception ? (Exception) t : new RuntimeException(t));
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} deep_search request error", agentContext.getRequestId(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * deep_search 失败时仍返回可解释 observation，避免主智能体拿到空结果。
     */
    private ToolResultPayload buildFailurePayload(String message) {
        return ToolResultPayload.builder()
                .toolResult(message)
                .llmObservation(message)
                .build();
    }
}
