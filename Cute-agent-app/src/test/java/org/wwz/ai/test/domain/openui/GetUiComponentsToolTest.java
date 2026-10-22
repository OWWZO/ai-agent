package org.wwz.ai.test.domain.openui;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.openui.GetUiComponentsTool;

import java.util.List;
import java.util.Map;

/**
 * get_ui_components 只读目录工具：返回扩展组件文本，且不重复收录常驻组件。
 */
public class GetUiComponentsToolTest {

    @Test
    public void shouldExposeEmptyParameterSchema() {
        GetUiComponentsTool tool = new GetUiComponentsTool();

        Assert.assertEquals("get_ui_components", tool.getName());

        Map<?, ?> parameters = (Map<?, ?>) tool.toParams();
        Assert.assertEquals("object", parameters.get("type"));
        Assert.assertEquals(List.of(), parameters.get("required"));
        Assert.assertTrue(((Map<?, ?>) parameters.get("properties")).isEmpty());
    }

    @Test
    public void shouldReturnExtendedCatalogAsPlainText() {
        ToolResultPayload result = (ToolResultPayload) new GetUiComponentsTool().execute(Map.of());

        Assert.assertFalse(result.getFailed());
        Assert.assertTrue(result.getLlmData() instanceof String);
        String catalog = (String) result.getLlmData();

        Assert.assertTrue(catalog.contains("Map("));
        Assert.assertTrue(catalog.contains("Svg("));
        Assert.assertTrue(catalog.contains("HtmlView("));
        // 常驻组件不应在扩展目录里重复出现（避免模型看到两份签名）。
        Assert.assertFalse(catalog.contains("Col(children"));
        Assert.assertFalse(catalog.contains("Title(text"));
    }
}
