package org.wwz.ai.domain.agent.rag.model.config;

import lombok.Data;

/** Whether column-value recall participates in the Data Query workflow. */
@Data
public class ColumnValueRecallSettings {
    private Boolean enable;
}
