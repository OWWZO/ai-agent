package org.wwz.ai.domain.agent.reactor.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.agent.enums.AutoBotsResultStatus;
import org.wwz.ai.domain.agent.reactor.agent.enums.ResponseTypeEnum;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.handler.AgentResponseHandler;
import org.wwz.ai.domain.agent.reactor.model.dto.AutoBotsResult;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.reactor.service.IMultiAgentService;
import org.wwz.ai.domain.agent.reactor.util.ChateiUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MultiAgentServiceImpl implements IMultiAgentService {
    @Autowired
    private ReactorConfig reactorConfig;
    @Autowired
    private Map<AgentType, AgentResponseHandler> handlerMap;

    @Override
    public AutoBotsResult searchForAgentRequest(GptQueryReq gptQueryReq, SseEmitter sseEmitter) {
        AgentRequest agentRequest = buildAgentRequest(gptQueryReq);
        log.info("{} start handle Agent request: {}", gptQueryReq.getRequestId(), JSON.toJSONString(agentRequest));
        try {
            handleMultiAgentRequest(agentRequest, sseEmitter);
        } catch (Exception e) {
            log.error("{}, error in requestMultiAgent, deepThink: {}, errorMsg: {}", gptQueryReq.getRequestId(), gptQueryReq.getDeepThink(), e.getMessage(), e);
            throw e;
        } finally {
            log.info("{}, agent.query.web.singleRequest end, requestId: {}", gptQueryReq.getRequestId(), JSON.toJSONString(gptQueryReq));
        }

        return ChateiUtils.toAutoBotsResult(agentRequest, AutoBotsResultStatus.loading.name());
    }

    /**
     * 处理多智能体请求，核心逻辑：
     * 1. 构建HTTP请求
     * 2. 异步调用外部服务（通过OkHttpClient）
     * 3. 解析SSE格式的响应流（处理心跳、业务数据、结束标识）
     * 4. 通过SSE发射器将处理结果实时推送给客户端
     *
     * @param autoReq      智能体请求对象，包含请求ID、智能体类型、业务参数等核心信息
     * @param sseEmitter   SSE发射器，用于向客户端实时推送响应数据（Server-Sent Events）
     */
    public void handleMultiAgentRequest(AgentRequest autoReq, SseEmitter sseEmitter) {
        // 记录请求开始时间，用于统计整个任务的耗时
        long startTime = System.currentTimeMillis();

        // 1. 构建调用外部服务的HTTP请求对象（将业务请求对象转换为OkHttp的Request）
        Request request = buildHttpRequest(autoReq);

        // 打印请求日志：记录请求ID和完整的HTTP请求信息（JSON格式），便于问题排查
        log.info("{} agentRequest:{}", autoReq.getRequestId(), JSON.toJSONString(request));

        // 2. 初始化OkHttpClient客户端，配置各类超时参数（适配SSE长连接场景）
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)        // 连接超时：60秒（建立TCP连接的超时时间）
                .readTimeout(reactorConfig.getSseClientReadTimeout(), TimeUnit.SECONDS)  // 读取超时：从配置获取（SSE长连接需设置较长时间）
                .writeTimeout(1800, TimeUnit.SECONDS)        // 写入超时：30分钟（适配大请求体的写入场景）
                .callTimeout(reactorConfig.getSseClientConnectTimeout(), TimeUnit.SECONDS) // 调用总超时：从配置获取（整个请求的生命周期超时）
                .build();

        // 3. 异步执行HTTP请求（非阻塞，避免占用线程池）
        client.newCall(request).enqueue(new Callback() {
            /**
             * HTTP请求失败回调（如连接超时、网络异常、服务端拒绝连接等）
             * @param call  当前的HTTP调用对象
             * @param e     失败异常信息
             */
            @Override
            public void onFailure(Call call, IOException e) {
                // 打印失败日志：包含异常信息和堆栈，便于定位问题
                log.error("onFailure {}", e.getMessage(), e);
            }

            /**
             * HTTP请求成功响应回调（服务端返回状态码，无论2xx/4xx/5xx都会进入此方法）
             * @param call     当前的HTTP调用对象
             * @param response 服务端返回的响应对象
             */
            @Override
            public void onResponse(Call call, Response response) {
                // 存储智能体响应列表：用于聚合多批次的响应数据
                List<AgentResponse> agentRespList = new ArrayList<>();
                // 事件结果对象：存储当前请求的处理状态、业务结果等
                EventResult eventResult = new EventResult();
                // 获取响应体（SSE数据流的载体）
                ResponseBody responseBody = response.body();

                // 防御性判断：响应体为空时记录日志并返回
                if (responseBody == null) {
                    log.error("{} auto agent empty response body", autoReq.getRequestId());
                    return;
                }

                try {
                    // 检查响应是否成功（HTTP状态码200-299为成功）
                    if (!response.isSuccessful()) {
                        // 非成功响应：打印错误日志（包含响应体内容），便于排查服务端错误
                        log.error("{}, response body is failed: {}", autoReq.getRequestId(), responseBody.string());
                        return;
                    }

                    // 4. 解析SSE格式的响应流（按行读取，SSE数据格式为 "data: 内容"）
                    String line;
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseBody.byteStream())  // 将响应体字节流转换为字符流，便于按行读取
                    );

                    // 循环读取每一行SSE数据，直到流结束
                    while ((line = reader.readLine()) != null) {
                        // 过滤非SSE标准格式的数据（SSE规范要求数据以"data:"开头）
                        if (!line.startsWith("data:")) {
                            continue;
                        }

                        // 截取"data:"后的实际数据内容
                        String data = line.substring(5);

                        // 5. 处理SSE结束标识：服务端返回[DONE]表示数据流结束
                        if (data.equals("[DONE]")) {
                            log.info("{} data equals with [DONE] {}:", autoReq.getRequestId(), data);
                            break; // 退出循环，结束数据读取
                        }

                        // 6. 处理心跳数据：维持SSE长连接，避免连接被断开
                        if (data.startsWith("heartbeat")) {
                            // 构建心跳响应结果
                            GptProcessResult result = buildHeartbeatData(autoReq.getRequestId());
                            // 通过SSE发射器向客户端推送心跳数据
                            sseEmitter.send(result);
                            // 记录心跳日志，便于监控连接状态
                            log.info("{} heartbeat-data: {}", autoReq.getRequestId(), data);
                            continue; // 跳过后续业务处理，继续读取下一行
                        }

                        // 7. 处理业务数据：解析智能体响应并推送结果
                        // 打印原始业务数据日志，便于排查业务问题
                        log.info("{} recv from autocontroller: {}", autoReq.getRequestId(), data);
                        // 将JSON格式的响应数据解析为智能体响应对象
                        AgentResponse agentResponse = JSON.parseObject(data, AgentResponse.class);
                        // 将请求中的智能体类型编码转换为枚举对象
                        AgentType agentType = AgentType.fromCode(autoReq.getAgentType());
                        // 根据智能体类型获取对应的处理器（策略模式：不同类型智能体用不同处理器）
                        AgentResponseHandler handler = handlerMap.get(agentType);
                        if (handler == null) {
                            log.error("{} no AgentResponseHandler found for agentType: {}", autoReq.getRequestId(), agentType);
                            GptProcessResult result = buildDefaultAutobotsResult(autoReq, "unsupported agentType: " + agentType);
                            sseEmitter.send(result);
                            continue;
                        }
                        // 调用处理器处理响应数据，返回需要推送给客户端的结果
                        GptProcessResult result = handler.handle(autoReq, agentResponse, agentRespList, eventResult);
                        // 通过SSE发射器向客户端推送业务处理结果
                        sseEmitter.send(result);

                        // 8. 检查任务是否完成：如果处理器标记为完成，则结束SSE连接
                        if (result.isFinished()) {
                            // 记录任务总耗时，便于性能监控和优化
                            log.info("{} task total cost time:{}ms", autoReq.getRequestId(), System.currentTimeMillis() - startTime);
                            // 完成SSE连接：通知客户端连接结束，释放资源
                            sseEmitter.complete();
                        }
                    }
                } catch (Exception e) {
                    // 捕获所有异常：避免单个请求的异常导致整个线程崩溃
                    log.error("{} handle multi agent request exception", autoReq.getRequestId(), e);
                }
            }
        });
    }

    private Request buildHttpRequest(AgentRequest autoReq) {
        String reqId = autoReq.getRequestId();
        autoReq.setRequestId(autoReq.getRequestId());
        String url = "http://127.0.0.1:8100/AutoAgent";
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                JSONObject.toJSONString(autoReq)
        );
        autoReq.setRequestId(reqId);
        return new Request.Builder().url(url).post(body).build();
    }

    private GptProcessResult buildDefaultAutobotsResult(AgentRequest autoReq, String errMsg) {
        GptProcessResult result = new GptProcessResult();
        boolean isRouter = AgentType.ROUTER.getValue().equals(autoReq.getAgentType());
        if (isRouter) {
            result.setStatus("success");
            result.setFinished(true);
            result.setResponse(errMsg);
            result.setTraceId(autoReq.getRequestId());
        } else {
            result.setResultMap(new HashMap<>());
            result.setStatus("failed");
            result.setFinished(true);
            result.setErrorMsg(errMsg);
        }
        return result;
    }

    private AgentRequest buildAgentRequest(GptQueryReq req) {
        AgentRequest request = new AgentRequest();
        // requestId 继续使用 traceId 作为单次请求唯一标识
        request.setRequestId(req.getTraceId());
        // 会话维度的 sessionId，前端已保证同一会话内复用
        request.setSessionId(req.getSessionId());
        request.setErp(req.getUser());
        request.setQuery(req.getQuery());
        // 当前轮上传附件直接沿用既有 sessionFiles -> productFiles 链路，不重新做文件解析。
        request.setSessionFiles(req.getSessionFiles());

        // 根据前端选择的产品形态 + 深度研究，统一用 AgentType 枚举标识
        // 聊天模式：COMPREHENSIVE -> FixedAgentExecuteStrategy
        if ("chat".equalsIgnoreCase(req.getOutputStyle())) {
            request.setAgentType(AgentType.WORKFLOW.getValue());
//            request.setBasePrompt(reactorConfig.getReactorBasePrompt());
            request.setSopPrompt("");
        } else {
            // 非聊天：沿用原有 deepThink=0 -> REACT，deepThink=1 -> PLAN_SOLVE
            Integer agentType = (req.getDeepThink() == null || req.getDeepThink() == 0)
                    ? AgentType.REACT.getValue()
                    : AgentType.PLAN_SOLVE.getValue();
            request.setAgentType(agentType);
            request.setSopPrompt(agentType.equals(AgentType.PLAN_SOLVE.getValue()) ? reactorConfig.getReactorSopPrompt() : "");
            request.setBasePrompt(agentType.equals(AgentType.REACT.getValue()) ? reactorConfig.getReactorBasePrompt() : "");
        }

        request.setIsStream(true);
        request.setOutputStyle(req.getOutputStyle());

        return request;
    }


    private GptProcessResult buildHeartbeatData(String requestId) {
        GptProcessResult result = new GptProcessResult();
        result.setFinished(false);
        result.setStatus("success");
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setResponse("");
        result.setResponseAll("");
        result.setUseTimes(0);
        result.setUseTokens(0);
        result.setReqId(requestId);
        result.setPackageType("heartbeat");
        result.setEncrypted(false);
        return result;
    }
}
