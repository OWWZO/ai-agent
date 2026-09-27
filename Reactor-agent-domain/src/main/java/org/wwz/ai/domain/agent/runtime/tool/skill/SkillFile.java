package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * skill_view(file_path) 加载结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillFile {

    private String name;

    private String path;

    private String content;

    private boolean binary;

    private long size;
}
