package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.common.MultiModalAgent;

/**
 * 多模态工具配置绑定测试。
 */
public class ReactorConfigMultiModalAgentTest {

    @Test
    public void shouldBindMultiModalAgentConfig() {
        MultiModalAgent tool = new MultiModalAgent();
        Assert.assertEquals("本工具用于查询与用户相关的知识，作为在线知识的补充。支持文本和图像等多模态数据检索，能够高效访问和获取用户专属的知识信息。", tool.getDescription());
        java.util.Map<?, ?> properties = (java.util.Map<?, ?>) tool.toParams().get("properties");
        java.util.Map<?, ?> question = (java.util.Map<?, ?>) properties.get("question");
        Assert.assertEquals("查询所需要的question，需要在知识库中进行检索的检索短语或句子。", String.valueOf(question.get("description")));
    }
}
