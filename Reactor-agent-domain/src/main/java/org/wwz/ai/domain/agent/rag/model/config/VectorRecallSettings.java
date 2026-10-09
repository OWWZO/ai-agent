package org.wwz.ai.domain.agent.rag.model.config;

import lombok.Data;

/** Whether schema-vector recall participates in the Data Query workflow. */
@Data
public class VectorRecallSettings {
    private Boolean enable;
}
