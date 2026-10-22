package org.wwz.ai.domain.agent.runtime.llm;

import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain-side tool ordering utility.
 * <p>
 * Spring AI callback construction is implemented by Infrastructure. The stable
 * signature remains here because prompt-cache identity is a domain runtime concern.
 */
public final class LlmToolCallbackProvider {

    private LlmToolCallbackProvider() {
    }

    public static String buildToolSignature(ToolCollection tools) {
        if (tools == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        if (tools.getToolMap() != null) {
            names.addAll(tools.getToolMap().keySet());
        }
        if (tools.getMcpToolMap() != null) {
            for (String name : tools.getMcpToolMap().keySet()) {
                names.add("mcp:" + name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join("|", names);
    }
}
