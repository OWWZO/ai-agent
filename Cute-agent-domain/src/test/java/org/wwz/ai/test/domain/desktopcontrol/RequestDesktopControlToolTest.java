package org.wwz.ai.test.domain.desktopcontrol;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlRequiredException;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool;

import java.util.Map;

public class RequestDesktopControlToolTest {

    @Test
    public void missingContextDoesNotThrowYield() {
        RequestDesktopControlTool tool = new RequestDesktopControlTool();
        try {
            Object result = tool.execute(Map.of("reason", "请登录"));
            Assert.assertTrue(result instanceof ToolResultPayload);
            Assert.assertEquals(Boolean.TRUE, ((ToolResultPayload) result).getFailed());
        } catch (DesktopControlRequiredException e) {
            Assert.fail("should not yield without context");
        }
    }
}
