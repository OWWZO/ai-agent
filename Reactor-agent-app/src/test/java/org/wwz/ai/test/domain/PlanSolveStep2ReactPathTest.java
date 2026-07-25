package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.prompt.PlanSolvePrompt;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.Step2PlanExecuteNode;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.List;
import java.util.Map;

/**
 * PlanSolve 主路径改造回归：单 React 主代理终答解析 + 编排约定注入。
 */
public class PlanSolveStep2ReactPathTest {

    @Test
    public void shouldResolveUserFacingAssistantWithoutToolCalls() {
        ReactImplAgent agent = newReactAgent(null);
        agent.getMemory().addMessage(Message.userMessage("你好", null));
        agent.getMemory().addMessage(Message.assistantMessage("这是最终答复。", null));
        agent.setState(AgentState.FINISHED);

        String answer = Step2PlanExecuteNode.resolveFinalAnswer(agent, "这是最终答复。");
        Assert.assertEquals("这是最终答复。", answer);
    }

    @Test
    public void shouldIgnoreToolCallAssistantAsFinalAnswer() {
        ReactImplAgent agent = newReactAgent(null);
        agent.getMemory().addMessage(Message.userMessage("任务", null));
        ToolCall call = ToolCall.builder()
                .id("c1")
                .type("function")
                .function(ToolCall.Function.builder()
                        .name("Agent")
                        .arguments("{\"prompt\":\"x\"}")
                        .build())
                .build();
        agent.getMemory().addMessage(Message.fromToolCalls("准备调用工具", List.of(call)));
        agent.setState(AgentState.FINISHED);

        String answer = Step2PlanExecuteNode.resolveFinalAnswer(agent, "工具执行结果为: ok");
        Assert.assertTrue(answer.contains("未能生成面向用户") || answer.contains("最终说明"));
    }

    @Test
    public void shouldInjectPlanSolveOrchestrationIntoSystem() {
        ReactImplAgent agent = newReactAgent(null);
        agent.setSystemPrompt(PlanSolvePrompt.ensureOrchestration(agent.getSystemPrompt()));
        Assert.assertTrue(agent.getSystemPrompt().contains(PlanSolvePrompt.ORCHESTRATION_MARKER));
        String twice = PlanSolvePrompt.ensureOrchestration(agent.getSystemPrompt());
        int count = twice.split(PlanSolvePrompt.ORCHESTRATION_MARKER, -1).length - 1;
        Assert.assertEquals(1, count);
    }

    @Test
    public void shouldKeepSopInSystemWhenPresentOnContext() {
        // 独立 session，避免 SessionPromptFreeze 复用无 SOP 的 system 前缀
        ReactImplAgent agent = newReactAgent("必须先核对订单号再答复", "sess-ps2-sop");
        Assert.assertTrue(agent.getSystemPrompt().contains("必须先核对订单号再答复"));
        Assert.assertFalse(agent.getSystemPrompt().contains("{{sopPrompt}}"));
    }

    private ReactImplAgent newReactAgent(String sopPrompt) {
        return newReactAgent(sopPrompt, "sess-ps2");
    }

    private ReactImplAgent newReactAgent(String sopPrompt, String sessionId) {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "reactMaxSteps", 5);
        ReflectionTestUtils.setField(config, "reactModelName", "mock-model");
        ReflectionTestUtils.setField(config, "llmSettingsMap", Map.of(
                "mock-model",
                LLMSettings.builder()
                        .model("mock-model")
                        .maxTokens(1024)
                        .temperature(0)
                        .baseUrl("http://127.0.0.1")
                        .interfaceUrl("/v1/chat/completions")
                        .functionCallType("function_call")
                        .apiKey("test-key")
                        .maxInputTokens(4096)
                        .build()
        ));

        AgentContext context = AgentContext.builder()
                .requestId("req-ps2")
                .sessionId(sessionId)
                .query("q")
                .sopPrompt(sopPrompt)
                .toolCollection(new ToolCollection())
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config))
                .printer(new NoopPrinter())
                .build();
        return new ReactImplAgent(context);
    }

    private static final class NoopPrinter implements Printer {
        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap,
                         String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageType, Object message) {
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message,
                                      Map<String, Object> extraResultMap, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }
}
