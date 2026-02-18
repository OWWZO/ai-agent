package org.wwz.ai.trigger.http;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.genie.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.genie.service.IGptProcessService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ReactDemoControllerPlainTest {

    static class FakeGptProcessService implements IGptProcessService {
        GptQueryReq captured;

        @Override
        public SseEmitter queryMultiAgentIncrStream(GptQueryReq req) {
            this.captured = req;
            return new SseEmitter();
        }
    }

    @Test
    public void reactDemo_minimal_unit_test_without_spring_context() throws Exception {
        AiAgentController controller = new AiAgentController();

        FakeGptProcessService fake = new FakeGptProcessService();
        Field f = AiAgentController.class.getDeclaredField("gptProcessService");
        f.setAccessible(true);
        f.set(controller, fake);

        Map<String, Object> body = new HashMap<>();
        body.put("message", "测试ReAct单节点");

        SseEmitter emitter = controller.reactDemo(body);
        Assert.assertNotNull(emitter);

        GptQueryReq passed = fake.captured;
        Assert.assertNotNull(passed);
        Assert.assertEquals("测试ReAct单节点", passed.getQuery());
        Assert.assertEquals(Integer.valueOf(0), passed.getDeepThink());
        Assert.assertEquals("html", passed.getOutputStyle());
        Assert.assertNotNull(passed.getRequestId());
        Assert.assertEquals(passed.getRequestId(), passed.getSessionId());
    }
}

