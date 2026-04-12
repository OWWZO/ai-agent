package org.wwz.ai.domain.agent.reactor.agent.tool.skill;

import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.agent.dto.skill.ScriptRunnerToolRequest;
import org.wwz.ai.domain.agent.reactor.agent.dto.skill.ScriptRunnerToolResponse;
import org.wwz.ai.domain.agent.reactor.agent.util.OkHttpUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

/**
 * Skill 脚本执行客户端，负责调用 reactor-tool。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillScriptRunnerClient {

    private final ReactorConfig reactorConfig;

    public ScriptRunnerToolResponse run(ScriptRunnerToolRequest request) {
        try {
            String baseUrl = normalizeBaseUrl(reactorConfig.getCodeInterpreterUrl());
            if (baseUrl.isBlank()) {
                throw new SkillLoadException("reactor-tool url is not configured");
            }

            long timeoutSeconds = Math.max(readTimeoutSeconds(request) + 30L, 60L);
            String responseText = OkHttpUtil.postJson(
                    baseUrl + "/v1/tool/script_runner",
                    JSONObject.toJSONString(request),
                    null,
                    timeoutSeconds
            );
            if (responseText == null || responseText.isBlank()) {
                throw new SkillLoadException("script runner returned empty response");
            }
            ScriptRunnerToolResponse response = JSONObject.parseObject(responseText, ScriptRunnerToolResponse.class);
            if (response == null) {
                throw new SkillLoadException("script runner returned invalid response");
            }
            return response;
        } catch (SkillLoadException e) {
            throw e;
        } catch (Exception e) {
            log.error("script runner call failed, request={}", JSONObject.toJSONString(request), e);
            throw new SkillLoadException("script runner call failed", e);
        }
    }

    private long readTimeoutSeconds(ScriptRunnerToolRequest request) {
        return request == null || request.getTimeoutSeconds() == null
                ? 120L
                : request.getTimeoutSeconds().longValue();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
