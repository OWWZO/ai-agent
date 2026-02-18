package org.wwz.ai.domain.agent.genie.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskSummaryResult {
    private String taskSummary;
    private List<File> files;
}
