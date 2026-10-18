package org.wwz.ai.test.domain.openui;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.prompt.AgentPrompt;
import org.wwz.ai.domain.agent.runtime.prompt.PlanSolvePrompt;

/**
 * 主 agent 的 openui 输出通道注入：只注入主 agent，幂等，且不破坏既有终答约定。
 */
public class OpenUiProtocolPromptTest {

    @Test
    public void planSolvePromptCarriesOpenUiProtocolAndExistingContracts() {
        String prompt = AgentPrompt.composePlanSolveSystemPrompt();

        Assert.assertTrue(prompt.contains(AgentPrompt.OPENUI_PROTOCOL_MARKER));
        Assert.assertTrue(prompt.contains("]]>openui:content"));
        Assert.assertTrue(prompt.contains("## Syntax Rules"));
        Assert.assertTrue(prompt.contains("get_ui_components"));
        // 既有约定必须还在
        Assert.assertTrue(prompt.contains(PlanSolvePrompt.ORCHESTRATION_MARKER));
        Assert.assertTrue(prompt.contains(AgentPrompt.USER_FACING_REPLY_CONTRACT_MARKER));
        // 生成产物里的构建元信息不应进模型上下文
        Assert.assertFalse(prompt.contains("# generated from library.ts@"));
    }

    @Test
    public void openUiProtocolInjectionIsIdempotent() {
        String once = AgentPrompt.ensureOpenUiProtocol("base prompt");
        String twice = AgentPrompt.ensureOpenUiProtocol(once);

        Assert.assertEquals(once, twice);
        Assert.assertEquals(1, countOccurrences(twice, AgentPrompt.OPENUI_PROTOCOL_MARKER));
    }

    /**
     * 普通对话走 ReAct 链路（日志里 agentName=react），协议必须也能挂到 REACT 底座上。
     * 这是 applyOpenUiProtocol() 的落点，之前只有 PlanSolve 有，导致真机首测整段空白。
     */
    @Test
    public void reactBaseCanCarryOpenUiProtocol() {
        String react = AgentPrompt.ensureOpenUiProtocol(AgentPrompt.REACT_SYSTEM_PROMPT);

        Assert.assertTrue(react.contains(AgentPrompt.OPENUI_PROTOCOL_MARKER));
        Assert.assertTrue(react.contains("]]>openui:content"));
        Assert.assertTrue(react.contains("## Syntax Rules"));
        // REACT 底座本身不能被吃掉
        Assert.assertTrue(react.contains(AgentPrompt.REACT_SYSTEM_PROMPT.trim()));
    }

    /**
     * 系统提示只放"协议 + 核心组件"，扩展组件定义必须留在 get_ui_components 里按需取。
     * 这条是成本红线：全量塞进提示词会让每轮多几千 tokens。
     */
    @Test
    public void residentPromptCarriesCoreOnlyNotTheWholeCatalog() {
        String resident = AgentPrompt.ensureOpenUiProtocol("base");

        // 核心组件必须在（模型不查工具也要能写出基本界面）
        Assert.assertTrue(resident.contains("Col(children: Node[]"));
        Assert.assertTrue(resident.contains("Button(label: string"));
        Assert.assertTrue(resident.contains("Card(children: Node[]"));
        // 扩展组件必须不在（只能由 get_ui_components 返回）
        Assert.assertFalse(resident.contains("SegmentedControl("));
        Assert.assertFalse(resident.contains("Timer(seconds:"));
        Assert.assertFalse(resident.contains("TextArea(value:"));
        Assert.assertFalse(resident.contains("Carousel(children:"));

        // 但工具目录里确实要有，否则模型无从查起
        String catalog = org.wwz.ai.domain.agent.runtime.tool.common.openui.OpenUiResources
                .componentsCatalog();
        Assert.assertTrue(catalog.contains("SegmentedControl("));
        Assert.assertTrue(catalog.contains("Timer(seconds:"));
        Assert.assertTrue(catalog.contains("TextArea(value:"));
    }

    @Test
    public void subAgentPromptStaysFreeOfOpenUiProtocol() {
        // 子 agent 的终答回给协调者，不应被"本轮产出可以是一个界面"影响
        String subAgent = AgentPrompt.composeSubAgentSystemPrompt(null);

        Assert.assertFalse(subAgent.contains(AgentPrompt.OPENUI_PROTOCOL_MARKER));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
