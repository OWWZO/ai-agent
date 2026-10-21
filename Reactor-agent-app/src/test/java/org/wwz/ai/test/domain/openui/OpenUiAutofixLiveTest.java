package org.wwz.ai.test.domain.openui;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiAutofixService;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixResult;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixTurn;
import org.wwz.ai.infrastructure.adapter.port.OkHttpRemoteHttpAdapter;

import java.util.List;

/**
 * 真机冒烟：验证 Java 侧序列化 53KB config block、真实调用、响应解析。
 *
 * <p>需要 THESYS_API_KEY 环境变量，没有就自动跳过（不污染 CI）。会消耗一次 Autofix 额度。</p>
 */
public class OpenUiAutofixLiveTest {

    @Test
    public void shouldFixRealProgramThroughRealHttp() {
        String key = System.getenv("THESYS_API_KEY");
        Assume.assumeTrue("未设置 THESYS_API_KEY，跳过真机联调", key != null && !key.isBlank());

        OpenUiAutofixService service = new OpenUiAutofixService(
                new OkHttpRemoteHttpAdapter(), null, null, true, false, key,
                "https://api.thesys.dev/v1/autofix", 90L);

        OpenUiFixResult result = service.fix(
                "root = Col([head, a])\nhead = Title(\"Q3 营收\", \"xl\")\na = FancyThing(\"x\")",
                List.of(new OpenUiFixTurn("user", "做一个 Q3 营收小卡片")));

        System.out.println("status=" + result.status());
        System.out.println("content=" + result.content());
        System.out.println("errors=" + result.errors());
        System.out.println("message=" + result.message());

        Assert.assertEquals(OpenUiFixResult.FIXED, result.status());
        Assert.assertTrue(result.hasRepairedContent());
    }
}
