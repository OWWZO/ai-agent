package org.wwz.ai.application.agent.dataquery.command;

import java.util.List;

public record ColumnSchemaRecallCommand(
        String query,
        Integer limit,
        Float scoreThreshold,
        Long timeout,
        List<String> modelCodeList) {
    public ColumnSchemaRecallCommand {
        modelCodeList = modelCodeList == null ? List.of() : List.copyOf(modelCodeList);
    }
}
