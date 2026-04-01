package org.wwz.ai.domain.agent.reactor.agent.tool.common;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.context.ApplicationContext;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.DeepSearchRequest;
import org.wwz.ai.domain.agent.reactor.agent.dto.DeepSearchrResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.FileRequest;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
     * 当前正在执行的 deep_search HTTP 调用，用于超时后主动取消。
     */
    private volatile Call activeCall;

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
        if (!reactorConfig.getDeepSearchToolPamras().isEmpty()) {
            return reactorConfig.getDeepSearchToolPamras();
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

            // 调用流式 API
            Future<String> future = callDeepSearchStream(request);
            Object object = future.get(DEEP_SEARCH_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            return object;
        } catch (TimeoutException e) {
            if (activeCall != null && !activeCall.isCanceled()) {
                activeCall.cancel();
            }
            log.error("{} deep_search timeout after {} minutes", agentContext.getRequestId(), DEEP_SEARCH_TIMEOUT_MINUTES, e);
            return "deep_search执行超时，已终止本次搜索，请基于当前已获取的信息继续处理。";
        } catch (Exception e) {

            log.error("{} deep_search agent error", agentContext.getRequestId(), e);
        } finally {
            activeCall = null;
        }
        return null;
    }

    /**
     * 调用 DeepSearch
     */
    public CompletableFuture<String> callDeepSearchStream(DeepSearchRequest searchRequest) {
        CompletableFuture<String> future = new CompletableFuture<>();
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
                    .post(body);
            Request request = requestBuilder.build();

            String[] interval = reactorConfig.getMessageInterval().getOrDefault("search", "5,20").split(",");
            int firstInterval = Integer.parseInt(interval[0]);
            int sendInterval = Integer.parseInt(interval[1]);

            Call call = client.newCall(request);
            activeCall = call;
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("{} deep_search on failure", agentContext.getRequestId(), e);
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) {

                    log.info("{} deep_search response {} {} {}", agentContext.getRequestId(), response, response.code(), response.body());
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            log.error("{} deep_search request error", agentContext.getRequestId());
                            future.completeExceptionally(new IOException("Unexpected response code: " + response));
                            return;
                        }

                        int index = 1;
                        StringBuilder stringBuilderIncr = new StringBuilder();
                        StringBuilder stringBuilderAll = new StringBuilder();
                        String line;
                        BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));
                        String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
                        String result = "搜索结果为空"; // 默认输出
                        String messageId = "";
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (data.equals("[DONE]")) {
                                    break;
                                }
                                if (data.startsWith("heartbeat")) {
                                    continue;
                                }
                                if (index == 1 || index % 100 == 0) {
                                    log.info("{} deep_search recv data: {}", agentContext.getRequestId(), data);
                                }
                                DeepSearchrResponse searchResponse = JSONObject.parseObject(data, DeepSearchrResponse.class);
                                FileTool fileTool = new FileTool();
                                fileTool.setAgentContext(agentContext);
                                // 上传搜索内容到文件中
                                if (searchResponse.getIsFinal()) {
                                    if (agentContext.getIsStream()) {
                                        searchResponse.setAnswer(stringBuilderAll.toString());
                                    }
                                    if (searchResponse.getAnswer().isEmpty()) {
                                        log.error("{} deep search answer empty", agentContext.getRequestId());
                                        break;
                                    }
                                    String fileName = StringUtil.removeSpecialChars(searchResponse.getQuery() + "的搜索结果.md");
                                    String fileDesc = searchResponse.getAnswer()
                                            .substring(0, Math.min(searchResponse.getAnswer().length(), reactorConfig.getDeepSearchToolFileDescTruncateLen())) + "...";
                                    FileRequest fileRequest = FileRequest.builder()
                                            .requestId(agentContext.getRequestId())
                                            .fileName(fileName)
                                            .description(fileDesc)
                                            .content(searchResponse.getAnswer())
                                            .build();
                                    fileTool.uploadFile(fileRequest, false, false);
                                    result = searchResponse.getAnswer().
                                            substring(0, Math.min(searchResponse.getAnswer().length(), reactorConfig.getDeepSearchToolMessageTruncateLen()));

                                    agentContext.getPrinter().send(messageId, "deep_search", searchResponse, digitalEmployee, true);

                                } else {
                                    Map<String, Object> contentMap = new HashMap<>();
                                    for (int idx = 0; idx < searchResponse.getSearchResult().getQuery().size(); idx++) {
                                        contentMap.put(searchResponse.getSearchResult().getQuery().get(idx), searchResponse.getSearchResult().getDocs().get(idx));
                                    }

                                    if ("extend".equals(searchResponse.getMessageType())) {
                                        messageId = StringUtil.getUUID();
                                        searchResponse.setSearchFinish(false);
                                        agentContext.getPrinter().send(messageId, "deep_search", searchResponse, digitalEmployee, true);
                                    } else if ("search".equals(searchResponse.getMessageType())) {
                                        searchResponse.setSearchFinish(true);
                                        agentContext.getPrinter().send(messageId, "deep_search", searchResponse, digitalEmployee, true);
                                        FileRequest fileRequest = FileRequest.builder()
                                                .requestId(agentContext.getRequestId())
                                                .fileName(searchResponse.getQuery() + "_search_result.txt")
                                                .description(searchResponse.getQuery() + "...")
                                                .content(JSON.toJSONString(contentMap))
                                                .build();
                                        fileTool.uploadFile(fileRequest, false, true);
                                    } else if ("report".equals(searchResponse.getMessageType())) {
                                        if (index == 1) {
                                            messageId = StringUtil.getUUID();
                                        }
                                        stringBuilderIncr.append(searchResponse.getAnswer());
                                        stringBuilderAll.append(searchResponse.getAnswer());
                                        if (index == firstInterval || index % sendInterval == 0) {
                                            searchResponse.setAnswer(stringBuilderIncr.toString());
                                            agentContext.getPrinter().send(messageId, "deep_search", searchResponse, digitalEmployee, false);
                                            stringBuilderIncr.setLength(0);
                                        }
                                        index++;
                                    }
                                }
                            }
                        }
                        future.complete(result);
                        activeCall = null;

                    } catch (Exception e) {
                        log.error("{} deep_search request error", agentContext.getRequestId(), e);
                        future.completeExceptionally(e);
                    } finally {
                        activeCall = null;
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} deep_search request error", agentContext.getRequestId(), e);
            future.completeExceptionally(e);
        }

        return future;
    }
}
