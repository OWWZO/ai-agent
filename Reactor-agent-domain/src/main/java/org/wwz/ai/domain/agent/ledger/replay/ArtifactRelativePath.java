package org.wwz.ai.domain.agent.ledger.replay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactFormatter;

import java.util.Map;

/**
 * 从 artifact 元数据还原工作区相对路径，供历史回放的 fileInfo / artifactRefs 建树。
 */
public final class ArtifactRelativePath {

    private ArtifactRelativePath() {
    }

    public static String resolve(ArtifactView artifact) {
        if (artifact == null) {
            return "";
        }
        String fromMeta = readMetadataPath(artifact.getMetadataJson());
        if (StringUtils.isNotBlank(fromMeta)) {
            return ToolArtifactFormatter.normalizeWorkspacePath(fromMeta);
        }
        return ToolArtifactFormatter.normalizeWorkspacePath(artifact.getFileName());
    }

    public static void putOn(Map<String, Object> target, ArtifactView artifact) {
        if (target == null) {
            return;
        }
        String relativePath = resolve(artifact);
        if (StringUtils.isBlank(relativePath)) {
            return;
        }
        target.putIfAbsent("relativePath", relativePath);
        target.putIfAbsent("originFileName", relativePath);
    }

    private static String readMetadataPath(String metadataJson) {
        if (StringUtils.isBlank(metadataJson)) {
            return null;
        }
        try {
            JSONObject object = JSON.parseObject(metadataJson);
            if (object == null) {
                return null;
            }
            String relativePath = object.getString("relativePath");
            if (StringUtils.isNotBlank(relativePath)) {
                return relativePath;
            }
            String originFileName = object.getString("originFileName");
            if (StringUtils.isNotBlank(originFileName)
                    && (originFileName.contains("/") || originFileName.contains("\\"))) {
                return originFileName;
            }
            String description = object.getString("description");
            if (StringUtils.startsWith(description, "workspace:")) {
                return description.substring("workspace:".length());
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }
}
