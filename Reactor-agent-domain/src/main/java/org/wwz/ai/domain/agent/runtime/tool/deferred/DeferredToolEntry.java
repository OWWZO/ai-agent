package org.wwz.ai.domain.agent.runtime.tool.deferred;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一个延迟工具的不可变描述。实际 BaseTool/MCP 元信息由 catalog 持有，
 * 但工具不会因为搜索而进入 eager tool map。
 */
public final class DeferredToolEntry {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;
    private final DeferredToolSource source;
    private final String sourceName;
    private final BaseTool localTool;
    private final McpToolInfo mcpToolInfo;
    private final String searchHint;

    private DeferredToolEntry(String name,
                              String description,
                              Map<String, Object> parameters,
                              DeferredToolSource source,
                              String sourceName,
                              BaseTool localTool,
                              McpToolInfo mcpToolInfo,
                              String searchHint) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("DeferredToolEntry.name 不能为空");
        }
        if (source == null) {
            throw new IllegalArgumentException("DeferredToolEntry.source 不能为空");
        }
        if (source == DeferredToolSource.LOCAL && localTool == null) {
            throw new IllegalArgumentException("LOCAL deferred tool 必须绑定 BaseTool");
        }
        if (source == DeferredToolSource.MCP && mcpToolInfo == null) {
            throw new IllegalArgumentException("MCP deferred tool 必须绑定 McpToolInfo");
        }
        this.name = name.trim();
        this.description = description == null ? "" : description;
        this.parameters = parameters == null
                ? Map.of()
                : immutableCopy(parameters);
        this.source = source;
        this.sourceName = StringUtils.defaultIfBlank(sourceName, source == DeferredToolSource.LOCAL ? "reactor" : "other");
        this.localTool = localTool;
        this.mcpToolInfo = mcpToolInfo;
        this.searchHint = searchHint == null ? "" : searchHint;
    }

    public static DeferredToolEntry local(BaseTool tool,
                                          String sourceName,
                                          Map<String, Object> parameters,
                                          String searchHint) {
        return new DeferredToolEntry(
                tool.getName(),
                tool.getDescription(),
                parameters,
                DeferredToolSource.LOCAL,
                sourceName,
                tool,
                null,
                searchHint);
    }

    public static DeferredToolEntry mcp(McpToolInfo toolInfo,
                                        String sourceName,
                                        Map<String, Object> parameters,
                                        String searchHint) {
        return new DeferredToolEntry(
                toolInfo.getName(),
                toolInfo.getDesc(),
                parameters,
                DeferredToolSource.MCP,
                sourceName,
                null,
                toolInfo,
                searchHint);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public DeferredToolSource getSource() {
        return source;
    }

    public String getSourceName() {
        return sourceName;
    }

    public BaseTool getLocalTool() {
        return localTool;
    }

    public McpToolInfo getMcpToolInfo() {
        return mcpToolInfo;
    }

    public String getSearchHint() {
        return searchHint;
    }

    public boolean isLocal() {
        return source == DeferredToolSource.LOCAL;
    }

    public boolean isMcp() {
        return source == DeferredToolSource.MCP;
    }

    public DeferredToolEntry withLocalTool(BaseTool tool) {
        if (!isLocal()) {
            return this;
        }
        return new DeferredToolEntry(
                name,
                description,
                parameters,
                source,
                sourceName,
                tool,
                null,
                searchHint);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(immutableValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
