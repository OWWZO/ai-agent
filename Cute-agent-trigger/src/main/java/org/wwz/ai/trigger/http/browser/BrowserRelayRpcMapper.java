package org.wwz.ai.trigger.http.browser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.browser.model.BrowserCommand;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps the internal HTTP RPC JSON envelope to the typed application command.
 */
public class BrowserRelayRpcMapper {

    public ParseResult read(String raw) {
        JSONObject body;
        try {
            body = JSON.parseObject(raw);
        } catch (RuntimeException exception) {
            return ParseResult.invalid(null, "invalid JSON body");
        }
        if (body == null) {
            return ParseResult.invalid(null, "invalid JSON body");
        }
        String requestId = body.getString("id");
        String userId = StringUtils.trimToNull(body.getString("userId"));
        if (userId == null) {
            userId = StringUtils.trimToNull(body.getString("visitorId"));
        }
        String action = StringUtils.trimToNull(body.getString("action"));
        if (userId == null || action == null) {
            return ParseResult.invalid(requestId, "userId and action are required");
        }
        Map<String, Object> parameters = new LinkedHashMap<>(body);
        parameters.remove("userId");
        parameters.remove("visitorId");
        parameters.remove("action");
        parameters.remove("id");
        Object rawDeadlineAt = parameters.remove("deadlineAt");
        Long deadlineAt = rawDeadlineAt instanceof Number number ? number.longValue() : null;
        try {
            BrowserCommand command = BrowserCommand.of(userId, action, parameters, null)
                    .withRpcId(StringUtils.trimToNull(requestId))
                    .withDeadlineAt(deadlineAt);
            return ParseResult.valid(command, requestId);
        } catch (IllegalArgumentException exception) {
            return ParseResult.invalid(requestId, exception.getMessage());
        }
    }

    public Map<String, Object> invalidResponse(ParseResult request) {
        return error(request.requestId(), "invalid_body", request.error());
    }

    public Map<String, Object> response(String requestId, BrowserCommandResult result) {
        if (result == null) {
            return error(requestId, "rpc_failed", "浏览器 RPC 无响应");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", result.rpcId() == null ? requestId : result.rpcId());
        response.put("ok", result.ok());
        response.put("error", result.error());
        response.put("errorCode", result.errorCode());
        response.put("page", result.page());
        response.put("data", result.data());
        return response;
    }

    private Map<String, Object> error(String id, String code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("ok", false);
        response.put("errorCode", code);
        response.put("error", message);
        return response;
    }

    public record ParseResult(BrowserCommand command,
                              String requestId,
                              String error) {

        public static ParseResult valid(BrowserCommand command, String requestId) {
            return new ParseResult(command, requestId, null);
        }

        public static ParseResult invalid(String requestId, String error) {
            return new ParseResult(null, requestId, error);
        }

        public boolean valid() {
            return command != null;
        }
    }
}
