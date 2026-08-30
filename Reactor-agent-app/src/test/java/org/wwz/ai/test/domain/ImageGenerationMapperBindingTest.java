package org.wwz.ai.test.domain;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Map;

/**
 * 生图历史 Mapper 绑定回归测试，不连接数据库。
 */
public class ImageGenerationMapperBindingTest {

    private static final Map<String, String> MAPPERS = Map.of(
            "tool_output_deep_search_mapper.xml", "IToolOutputDeepSearchDao",
            "tool_output_code_interpreter_mapper.xml", "IToolOutputCodeInterpreterDao",
            "tool_output_data_analysis_mapper.xml", "IToolOutputDataAnalysisDao",
            "tool_output_multimodal_agent_mapper.xml", "IToolOutputMultimodalAgentDao",
            "tool_output_image_generation_mapper.xml", "IToolOutputImageGenerationDao",
            "tool_output_canvas_publish_mapper.xml", "IToolOutputCanvasPublishDao",
            "tool_output_emit_ui_tree_mapper.xml", "IToolOutputEmitUiTreeDao",
            "tool_output_emit_ui_patch_mapper.xml", "IToolOutputEmitUiPatchDao");

    @Test
    public void shouldRegisterToolOutputStatements() throws Exception {
        Configuration configuration = new Configuration();
        for (Map.Entry<String, String> mapper : MAPPERS.entrySet()) {
            ClassPathResource resource = new ClassPathResource("mybatis/mapper/" + mapper.getKey());
            Assert.assertTrue("missing mapper resource: " + mapper.getKey(), resource.exists());
            try (InputStream inputStream = resource.getInputStream()) {
                new XMLMapperBuilder(inputStream, configuration, resource.getPath(),
                        configuration.getSqlFragments()).parse();
            }

            String namespace = "org.wwz.ai.infrastructure.dao.reactor." + mapper.getValue();
            Assert.assertTrue(configuration.hasStatement(namespace + ".insert"));
            Assert.assertTrue(configuration.hasStatement(namespace + ".queryByToolInvocationId"));
            Assert.assertTrue(configuration.hasStatement(namespace + ".queryByRequestToolCall"));
        }
        String imageNamespace = "org.wwz.ai.infrastructure.dao.reactor.IToolOutputImageGenerationDao";
        Assert.assertTrue(configuration.hasStatement(imageNamespace + ".countByRequestSource"));
        Assert.assertTrue(configuration.hasStatement(imageNamespace + ".queryPageByRequestSource"));
    }
}
