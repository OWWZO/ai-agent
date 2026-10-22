package org.wwz.ai.domain.agent.runtime.tool.cli;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一次 CLI 调用的产物物化结果。
 * <p>
 * 成功项和失败项分开返回：部分成功时成功文件必须可用，失败文件不得创建 artifact。
 */
@Value
@Builder
public class CliArtifactMaterialization {

    private static final CliArtifactMaterialization EMPTY =
            new CliArtifactMaterialization(List.of(), List.of());

    List<MaterializedCliArtifact> artifacts;

    List<CliArtifactFailure> failures;

    public static CliArtifactMaterialization empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return artifacts.isEmpty() && failures.isEmpty();
    }

    public int successCount() {
        return artifacts.size();
    }

    /** 可直接放进工具结果的安全 artifact 视图。 */
    public List<Map<String, Object>> artifactMaps() {
        List<Map<String, Object>> maps = new ArrayList<>(artifacts.size());
        for (MaterializedCliArtifact artifact : artifacts) {
            maps.add(artifact.toMap());
        }
        return maps;
    }

    public List<Map<String, Object>> failureMaps() {
        List<Map<String, Object>> maps = new ArrayList<>(failures.size());
        for (CliArtifactFailure failure : failures) {
            maps.add(failure.toMap());
        }
        return maps;
    }
}
