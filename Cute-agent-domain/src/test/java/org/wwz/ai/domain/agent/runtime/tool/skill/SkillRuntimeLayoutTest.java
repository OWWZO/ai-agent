package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.junit.Assert;
import org.junit.Test;

public class SkillRuntimeLayoutTest {

    @Test
    public void shouldRenderPlaceholders() {
        SkillRuntimeLayout layout = new SkillRuntimeLayout(
                SkillRuntimeOptions.builder().runtimePython("python3").build());

        String out = layout.render("generate-ppt", "run ${PYTHON} ${SKILL_DIR}/scripts/split.py");

        Assert.assertEquals("run python3 skills/generate-ppt/scripts/split.py", out);
        Assert.assertEquals("skills/generate-ppt", layout.dirOf("generate-ppt"));
    }

    @Test
    public void shouldSanitizeSkillNameSegment() {
        SkillRuntimeLayout layout = new SkillRuntimeLayout(SkillRuntimeOptions.builder().build());
        Assert.assertEquals("my-skill", layout.segmentOf("my skill"));
        Assert.assertEquals("skill", layout.segmentOf(".."));
    }
}
