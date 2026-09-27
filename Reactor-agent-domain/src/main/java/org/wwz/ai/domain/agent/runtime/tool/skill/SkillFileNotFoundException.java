package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.util.List;
import java.util.Map;

/**
 * 附属文件不存在时带上可用清单，便于模型改用正确路径。
 */
public class SkillFileNotFoundException extends SkillLoadException {

    private final Map<String, List<String>> availableFiles;

    public SkillFileNotFoundException(String relativePath, Map<String, List<String>> availableFiles) {
        super("File '" + relativePath + "' not found in skill.");
        this.availableFiles = availableFiles == null ? Map.of() : Map.copyOf(availableFiles);
    }

    public Map<String, List<String>> getAvailableFiles() {
        return availableFiles;
    }
}
