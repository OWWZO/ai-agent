package org.wwz.ai.application.agent.dataquery.command;

import java.util.List;

public record ColumnValueRecallCommand(String query, List<String> modelCodeList, int limit) {
    public ColumnValueRecallCommand {
        modelCodeList = modelCodeList == null ? List.of() : List.copyOf(modelCodeList);
    }
}
