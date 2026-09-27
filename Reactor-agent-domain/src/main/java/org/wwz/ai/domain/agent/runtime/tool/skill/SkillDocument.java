package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * skill_view 主文档加载结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDocument {

    private SkillDescriptor descriptor;

    private String content;

    @Builder.Default
    private List<SkillFileDescriptor> linkedFiles = new ArrayList<>();
}
