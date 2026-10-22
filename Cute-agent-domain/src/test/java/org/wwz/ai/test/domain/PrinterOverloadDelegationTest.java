package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;

import java.util.Map;

/**
 * Printer 重载委托契约。
 * <p>
 * 接口的 5 个便捷重载只是核心 6 参数方法的转发。实现只需覆盖核心方法，
 * 便捷重载由接口 default 提供——锁住它们的转发参数，防止某天有人"顺手"
 * 改掉 messageId 默认值或 isFinal 语义。
 */
public class PrinterOverloadDelegationTest {

    @Test
    public void sendWithTypeAndMessageDelegatesWithNullIdAndFinal() {
        RecordingPrinter printer = new RecordingPrinter();
        printer.send("tool_call", "payload");

        Assert.assertNull("messageId 应默认为 null", printer.messageId);
        Assert.assertEquals("tool_call", printer.messageType);
        Assert.assertEquals("payload", printer.message);
        Assert.assertNull("digitalEmployee 应默认为 null", printer.digitalEmployee);
        Assert.assertTrue("isFinal 应默认为 true", printer.isFinal);
        Assert.assertNull("extraResultMap 应默认为 null", printer.extraResultMap);
    }

    @Test
    public void sendWithDigitalEmployeePassesItThrough() {
        RecordingPrinter printer = new RecordingPrinter();
        printer.send("result", "payload", "数字员工A");

        Assert.assertNull(printer.messageId);
        Assert.assertEquals("result", printer.messageType);
        Assert.assertEquals("数字员工A", printer.digitalEmployee);
        Assert.assertTrue(printer.isFinal);
    }

    @Test
    public void sendWithIdAndFinalKeepsCallerValues() {
        RecordingPrinter printer = new RecordingPrinter();
        printer.send("call-1", "tool_call", "payload", false);

        Assert.assertEquals("call-1", printer.messageId);
        Assert.assertEquals("tool_call", printer.messageType);
        Assert.assertFalse("isFinal 应按调用方传入", printer.isFinal);
        Assert.assertNull(printer.digitalEmployee);
    }

    @Test
    public void sendWithResultMapDelegatesToExtendedCore() {
        RecordingPrinter printer = new RecordingPrinter();
        printer.sendWithResultMap("call-2", "tool_call", "payload", Map.of("k", "v"), true);

        Assert.assertEquals("call-2", printer.messageId);
        Assert.assertEquals("tool_call", printer.messageType);
        Assert.assertTrue("isFinal 应按调用方传入", printer.isFinal);
        Assert.assertNull("digitalEmployee 应默认为 null", printer.digitalEmployee);
        Assert.assertNotNull("extraResultMap 必须透传", printer.extraResultMap);
        Assert.assertEquals("v", printer.extraResultMap.get("k"));
    }

    @Test
    public void sendWithResultMapShortFormDefaultsIdAndFinal() {
        RecordingPrinter printer = new RecordingPrinter();
        printer.sendWithResultMap("tool_call", "payload", Map.of("k", "v"));

        Assert.assertNull(printer.messageId);
        Assert.assertEquals("tool_call", printer.messageType);
        Assert.assertTrue("isFinal 应默认为 true", printer.isFinal);
        Assert.assertNotNull(printer.extraResultMap);
    }

    /** 只覆盖核心方法，便捷重载走接口 default。 */
    private static final class RecordingPrinter implements Printer {
        private String messageId;
        private String messageType;
        private Object message;
        private Map<String, Object> extraResultMap;
        private String digitalEmployee;
        private Boolean isFinal;

        @Override
        public void send(String messageId, String messageType, Object message,
                         String digitalEmployee, Boolean isFinal) {
            this.messageId = messageId;
            this.messageType = messageType;
            this.message = message;
            this.digitalEmployee = digitalEmployee;
            this.isFinal = isFinal;
        }

        @Override
        public void send(String messageId, String messageType, Object message,
                         Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
            this.messageId = messageId;
            this.messageType = messageType;
            this.message = message;
            this.extraResultMap = extraResultMap;
            this.digitalEmployee = digitalEmployee;
            this.isFinal = isFinal;
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }
}
