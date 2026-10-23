package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 图片读取的体积护栏：超过上限时只回元数据，不加载成多模态。
 */
public class WorkspaceReadImageLimitTest {

    private Path workspaceRoot;
    private WorkspaceReadTool readTool;

    @Before
    public void setUp() throws Exception {
        workspaceRoot = Files.createTempDirectory("reactor-ws-image-limit");
        readTool = newReadTool(0L);
    }

    @Test
    public void loadsSmallImageAsMultimodalContent() throws Exception {
        Files.write(workspaceRoot.resolve("small.png"), new byte[]{1, 2, 3, 4});

        ToolResultPayload payload = (ToolResultPayload) readTool.execute(Map.of("path", "small.png"));

        Assert.assertNotNull(payload.getBase64Image());
        Assert.assertTrue(payload.getBase64Image().startsWith("data:image/png;base64,"));
        Assert.assertEquals("image/png", payload.getImageMimeType());
        Assert.assertNotEquals(Boolean.TRUE, payload.getFailed());
    }

    @Test
    public void refusesOversizedImageWithoutReadingIt() throws Exception {
        // 上限 8 字节，文件 16 字节：必须走拒绝分支。
        readTool = newReadTool(8L);
        Files.write(workspaceRoot.resolve("big.png"), new byte[16]);

        ToolResultPayload payload = (ToolResultPayload) readTool.execute(Map.of("path", "big.png"));

        Assert.assertNull("超大图不得加载成多模态", payload.getBase64Image());
        Map<?, ?> data = (Map<?, ?>) payload.getLlmData();
        Assert.assertEquals("image_too_large", data.get("type"));
        Assert.assertEquals(16L, data.get("size"));
        Assert.assertEquals(8L, data.get("maxBytes"));
        // 软失败：ok=false 但不标记 failed，避免模型把工具当成崩溃而反复重试。
        Assert.assertEquals(Boolean.FALSE, data.get("ok"));
        Assert.assertNotEquals(Boolean.TRUE, payload.getFailed());
        Assert.assertTrue(String.valueOf(data.get("message")).contains("不要重复读取同一路径"));
    }

    @Test
    public void acceptsImageExactlyAtLimit() throws Exception {
        readTool = newReadTool(8L);
        Files.write(workspaceRoot.resolve("edge.png"), new byte[8]);

        ToolResultPayload payload = (ToolResultPayload) readTool.execute(Map.of("path", "edge.png"));

        Assert.assertNotNull("边界值应当放行", payload.getBase64Image());
    }

    private WorkspaceReadTool newReadTool(long maxImageBytes) {
        WorkspaceRuntimeOptions options = WorkspaceRuntimeOptions.builder()
                .enabled(true)
                .rootTemplate(workspaceRoot.toString())
                .maxReadChars(10000)
                .maxImageBytes(maxImageBytes)
                .build();
        WorkspaceService service = new WorkspaceService(options, new WorkspacePathGuard(), null,
                new SkillVirtualPaths(SkillRuntimeOptions.builder().enabled(false).build()));
        WorkspaceReadTool tool = new WorkspaceReadTool(service, options);
        tool.setAgentContext(AgentContext.builder()
                .requestId("req-image-limit")
                .sessionId("session-image-limit")
                .workspaceRoot(workspaceRoot.toString())
                .build());
        return tool;
    }
}
