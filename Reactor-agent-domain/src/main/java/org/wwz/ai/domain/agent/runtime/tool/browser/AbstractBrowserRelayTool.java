package org.wwz.ai.domain.agent.runtime.tool.browser;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Setter
public abstract class AbstractBrowserRelayTool implements BaseTool {

    protected AgentContext agentContext;

    protected Duration rpcTimeout() {
        return Duration.ofSeconds(15);
    }

    protected ToolResultPayload call(String action, Map<String, Object> params, boolean snapshotAfter) {
        String visitorId = agentContext == null ? null : StringUtils.trimToNull(agentContext.getVisitorId());
        if (visitorId == null) {
            return ToolResultPayload.failure("缺少访客身份", "缺少访客身份", null, "missing visitorId");
        }
        BrowserRelayPort port = agentContext.getRuntimeDependencies() == null
                ? null
                : agentContext.getRuntimeDependencies().getOptionalBrowserRelayPort();
        if (port == null || !port.isOnline(visitorId)) {
            return ToolResultPayload.failure("浏览器未连接", "浏览器未连接", null, "browser_offline");
        }
        try {
            BrowserRpcResult result = port.call(visitorId, action, params == null ? Map.of() : params, rpcTimeout());
            if (result == null || !result.isOk()) {
                String error = result == null ? "浏览器 RPC 无响应" : StringUtils.defaultIfBlank(result.getError(), "browser_rpc_failed");
                return ToolResultPayload.failure(error, error, null, result == null ? "rpc_failed" : result.getErrorCode());
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            if (result.getData() != null) {
                fields.putAll(result.getData());
            }
            if (result.getPage() != null) {
                fields.put("page", result.getPage());
            }
            if (snapshotAfter && !"snapshot".equals(action)) {
                BrowserRpcResult snap = port.call(visitorId, "snapshot", Map.of(), rpcTimeout());
                if (snap != null && snap.isOk() && snap.getData() != null) {
                    Object tree = snap.getData().get("tree");
                    if (tree != null) fields.put("tree", tree);
                    Object url = snap.getData().get("url");
                    if (url != null) fields.put("url", url);
                    Object title = snap.getData().get("title");
                    if (title != null) fields.put("title", title);
                }
            }
            emitViewport(fields);
            return ToolResultPayload.okData(getName(), fields);
        } catch (Exception e) {
            return ToolResultPayload.failure(e.getMessage(), e.getMessage(), null, "browser_rpc_failed");
        }
    }

    protected void emitViewport(Map<String, Object> fields) {
        if (agentContext == null || agentContext.getPrinter() == null || fields == null || fields.isEmpty()) {
            return;
        }
        if (fields.get("tree") == null && fields.get("url") == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", fields.get("url"));
        payload.put("title", fields.get("title"));
        Object tree = fields.get("tree");
        if (tree instanceof String text) {
            payload.put("tree", text.length() > 4000 ? text.substring(0, 4000) : text);
        }
        String messageId = agentContext.getCurrentToolArtifactSource() == null
                ? agentContext.getRequestId()
                : agentContext.getCurrentToolArtifactSource().getToolCallId();
        agentContext.getPrinter().send(messageId, "browser_viewport", payload, true);
    }
}
