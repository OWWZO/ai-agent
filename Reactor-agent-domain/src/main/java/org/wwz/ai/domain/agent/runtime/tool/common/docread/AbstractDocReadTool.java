package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.common.doc.AbstractDocRemoteTool;

import java.util.Map;

/**
 * 文档读取工具的通用远端适配基类。
 * <p>
 * 与生成侧共用 {@link AbstractDocRemoteTool}；这里只保留读取特有的超时、
 * workspace_root 注入和 data/message 字段截断。
 */
public abstract class AbstractDocReadTool extends AbstractDocRemoteTool {

    private static final long DEFAULT_TIMEOUT_SECONDS = 180L;
    private static final int DATA_FIELD_LIMIT = 12_000;

    @Override
    protected long timeoutSeconds() {
        return DEFAULT_TIMEOUT_SECONDS;
    }

    @Override
    protected boolean injectsWorkspaceRoot() {
        return true;
    }

    @Override
    protected String fileEventCommand() {
        return "文档处理";
    }

    @Override
    protected void extraLlmData(JSONObject json, Map<String, Object> data) {
        Object payload = json.get("data");
        if (payload == null) {
            putMessage(json, data);
            return;
        }
        if (payload instanceof String text) {
            data.put("data", truncate(text));
        } else {
            data.put("data", truncate(JSON.toJSONString(payload)));
        }
        putMessage(json, data);
    }

    private void putMessage(JSONObject json, Map<String, Object> data) {
        if (StringUtils.isNotBlank(json.getString("message"))) {
            data.put("message", json.getString("message"));
        }
    }

    private static String truncate(String text) {
        return text.length() > DATA_FIELD_LIMIT
                ? text.substring(0, DATA_FIELD_LIMIT) + "...[truncated]"
                : text;
    }
}
