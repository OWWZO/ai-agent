package org.wwz.ai.domain.agent.runtime.tool.common.dataprep;

import org.wwz.ai.domain.agent.runtime.tool.common.docread.AbstractDocReadTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataMergeTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "data_merge";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/data_merge";
    }

    @Override
    protected String defaultDescription() {
        return "Merge/join tabular datasets: operation=merge|concat, how=inner|outer|left|right|cross. "
                + "Provide left_data/right_data or left_artifact/right_artifact or datasets list.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", stringProp("merge | concat"));
        properties.put("how", stringProp("inner | outer | left | right | cross"));
        properties.put("on", stringProp("Join key column(s)"));
        properties.put("left_data", Map.of("type", "array", "items", objectProp("Left row")));
        properties.put("right_data", Map.of("type", "array", "items", objectProp("Right row")));
        properties.put("left_on", stringProp("Left join keys"));
        properties.put("right_on", stringProp("Right join keys"));
        properties.put("datasets", Map.of("type", "array", "description", "Datasets for concat", "items", objectProp("Dataset")));
        properties.put("axis", stringProp("concat axis: rows | columns"));
        return objectSchema(properties, List.of());
    }
}
