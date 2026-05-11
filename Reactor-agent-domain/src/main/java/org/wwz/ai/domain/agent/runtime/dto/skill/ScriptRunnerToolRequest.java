package org.wwz.ai.domain.agent.runtime.dto.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * script_runner_tool 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptRunnerToolRequest {

    private String requestId;

    private String skillName;

    private String skillBasePath;

    private String scriptName;

    private String scriptPath;

    private String runtime;

    @Builder.Default
    private Map<String, Object> arguments = new LinkedHashMap<>();

    @Builder.Default
    private List<String> argv = new ArrayList<>();

    private Integer timeoutSeconds;
}
