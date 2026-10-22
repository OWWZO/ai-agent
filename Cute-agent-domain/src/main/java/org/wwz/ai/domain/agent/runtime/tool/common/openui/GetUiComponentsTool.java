package org.wwz.ai.domain.agent.runtime.tool.common.openui;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * openui-lang 扩展组件目录查询工具。
 *
 * <p>常驻提示词里只有 15 个核心组件；需要 Map、Svg、HtmlView 这类扩展组件时，
 * 模型先调用本工具拿到完整签名再写程序块。只读，无副作用。</p>
 */
@Slf4j
@Data
public class GetUiComponentsTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return OpenUiToolNames.GET_UI_COMPONENTS;
    }

    @Override
    public String getDescription() {
        return "Return the openui-lang component and @function catalog beyond the core set in your system prompt. "
                + "Call it before writing an openui-lang block that needs Map, Svg, HtmlView, Chart variants, "
                + "form controls or @functions you are not sure about. Read-only, no side effects.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Collections.emptyMap());
        parameters.put("required", List.of());
        parameters.put("additionalProperties", false);
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            // 纯文本目录：llmData 是 String 时序列化器原样返回，保留换行，模型读起来和提示词一致。
            return ToolResultPayload.fromData(OpenUiResources.componentsCatalog());
        } catch (IllegalStateException e) {
            log.warn("get_ui_components 无法读取组件目录", e);
            return ToolResultPayload.failureFrom(e.getMessage(), null);
        }
    }
}
