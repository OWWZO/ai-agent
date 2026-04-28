package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 负责执行底层 HTTP / SSE 流。
 */
@Component
public class StreamExecutor {

    @Resource
    private ReactorConfig reactorConfig;

    public void execute(AgentRequest agentRequest,
                        String sessionId,
                        String requestId,
                        Long messageId,
                        SseEmitter emitter,
                        ActiveSessionStreamRegistry streamRegistry,
                        StreamCallback callback) {
        Request request = buildHttpRequest(agentRequest);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(reactorConfig.getSseClientReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(1800, TimeUnit.SECONDS)
                .callTimeout(reactorConfig.getSseClientConnectTimeout(), TimeUnit.SECONDS)
                .build();

        Call call = client.newCall(request);
        streamRegistry.register(sessionId, requestId, messageId, call, emitter);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                try {
                    callback.onError(e, streamRegistry.isStopRequested(requestId) || call.isCanceled());
                } finally {
                    streamRegistry.unregister(requestId);
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                ResponseBody responseBody = response.body();
                boolean completed = false;
                try {
                    if (responseBody == null) {
                        throw new IOException("empty response body");
                    }
                    if (!response.isSuccessful()) {
                        throw new IOException(responseBody.string());
                    }

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) {
                                continue;
                            }
                            String data = line.substring(5);
                            if ("[DONE]".equals(data)) {
                                completed = true;
                                break;
                            }
                            if (data.startsWith("heartbeat")) {
                                callback.onHeartbeat(buildHeartbeatData(agentRequest.getRequestId()));
                                continue;
                            }

                            AgentResponse agentResponse = JSON.parseObject(data, AgentResponse.class);
                            if (callback.onAgentResponse(agentResponse)) {
                                completed = true;
                                break;
                            }
                        }
                    }

                    if (completed) {
                        callback.onCompleted();
                    } else {
                        callback.onError(new IOException("stream closed before completion"), false);
                    }
                } catch (Exception e) {
                    IOException error = e instanceof IOException
                            ? (IOException) e
                            : new IOException(e.getMessage(), e);
                    callback.onError(error, streamRegistry.isStopRequested(requestId) || call.isCanceled());
                } finally {
                    streamRegistry.unregister(requestId);
                }
            }
        });
    }

    private Request buildHttpRequest(AgentRequest agentRequest) {
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                JSONObject.toJSONString(agentRequest)
        );
        return new Request.Builder()
                .url("http://127.0.0.1:8100/AutoAgent")
                .post(body)
                .build();
    }

    private GptProcessResult buildHeartbeatData(String requestId) {
        GptProcessResult result = new GptProcessResult();
        result.setFinished(false);
        result.setStatus("success");
        result.setResponse("");
        result.setResponseAll("");
        result.setUseTimes(0);
        result.setUseTokens(0);
        result.setReqId(requestId);
        result.setPackageType("heartbeat");
        result.setEncrypted(false);
        return result;
    }

    public interface StreamCallback {
        void onHeartbeat(GptProcessResult heartbeat) throws Exception;

        boolean onAgentResponse(AgentResponse agentResponse) throws Exception;

        void onCompleted();

        void onError(IOException error, boolean forceStopped);
    }
}
