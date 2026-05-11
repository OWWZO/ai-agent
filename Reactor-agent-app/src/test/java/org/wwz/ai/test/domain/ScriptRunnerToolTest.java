package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.skill.ScriptRunnerToolResponse;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.ScriptRunnerTool;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptRunnerClient;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ScriptRunnerToolOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

/**
 * script_runner_tool typed output 回归。
 */
public class ScriptRunnerToolTest {

    @Test
    public void shouldReturnNativeOutputJsonForScriptRunner() {
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        SkillScriptRunnerClient client = Mockito.mock(SkillScriptRunnerClient.class);

        SkillDefinition skillDefinition = SkillDefinition.builder()
                .name("sales")
                .basePath(Path.of("D:/skills/sales"))
                .build();
        SkillScriptDefinition scriptDefinition = SkillScriptDefinition.builder()
                .scriptName("export_report")
                .relativePath("scripts/export_report.py")
                .runtime("python")
                .build();
        ScriptRunnerToolResponse response = ScriptRunnerToolResponse.builder()
                .skillName("sales")
                .scriptName("export_report")
                .runtime("python")
                .success(true)
                .exitCode(0)
                .stdout("ok")
                .stderr("")
                .summary("导出完成")
                .fileInfo(java.util.List.of(ScriptRunnerToolResponse.FileInfo.builder()
                        .fileName("sales-report.md")
                        .ossUrl("https://file.example.com/sales-report.md")
                        .domainUrl("https://file.example.com/preview/sales-report.md")
                        .fileSize(512)
                        .build()))
                .build();

        Mockito.when(skillRegistry.getRequiredSkill("sales")).thenReturn(skillDefinition);
        Mockito.when(skillRegistry.getRequiredScript("sales", "export_report")).thenReturn(scriptDefinition);
        Mockito.when(client.run(Mockito.any())).thenReturn(response);

        ScriptRunnerTool tool = new ScriptRunnerTool(
                skillRegistry,
                SkillRuntimeOptions.builder().defaultScriptTimeoutSeconds(120).build(),
                client
        );

        AgentContext context = AgentContext.builder()
                .requestId("req-script-001")
                .sessionId("session-script-001")
                .toolCollection(new ToolCollection())
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .build();
        context.getToolCollection().setAgentContext(context);
        tool.setAgentContext(context);

        ToolArtifactSource artifactSource = ToolArtifactSource.builder()
                .sessionId(context.getSessionId())
                .requestId(context.getRequestId())
                .toolCallId("call-script-001")
                .toolName("script_runner_tool")
                .build();

        ToolResultPayload payload;
        context.bindCurrentToolArtifactSource(artifactSource);
        try {
            payload = (ToolResultPayload) tool.execute(Map.of(
                    "skill_name", "sales",
                    "script_name", "export_report"
            ));
        } finally {
            context.clearCurrentToolArtifactSource();
        }

        ScriptRunnerToolOutput structuredOutput = (ScriptRunnerToolOutput) payload.getStructuredOutput();
        Assert.assertTrue(payload.getToolResult().contains("技能：sales"));
        Assert.assertNotNull(structuredOutput);
        Assert.assertFalse(payload.getFailed());
        Assert.assertEquals("export_report", structuredOutput.getScriptName());
        Assert.assertEquals("ok", structuredOutput.getStdout());
        Assert.assertEquals(1, context.getTaskProductFiles().size());
        Assert.assertEquals("sales-report.md", context.getTaskProductFiles().get(0).getFileName());
    }
}
