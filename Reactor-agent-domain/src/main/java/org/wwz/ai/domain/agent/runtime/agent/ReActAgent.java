package org.wwz.ai.domain.agent.runtime.agent;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct模式智能代理抽象类
 * 继承自BaseAgent（基础智能体抽象类），实现ReAct（Reason + Act）范式的核心骨架：
 * - Reason（思考）：由抽象方法think()定义，子类需实现具体的思考逻辑（如生成工具调用、规划步骤）
 * - Act（行动）：由抽象方法act()定义，子类需实现具体的执行逻辑（如调用工具、推进任务）
 * 核心扩展能力：
 * 1. 标准化的step()方法：串联思考-行动流程，形成ReAct循环的基础单元
 * 2. 数字员工生成能力：根据任务动态生成/更新数字员工工具，扩展智能体的可用工具集
 * 3. 通用的提示词格式化、LLM响应解析工具方法
 *
 * @author （可补充作者信息）
 * @date （可补充日期）
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public abstract class ReActAgent extends BaseAgent {

    /**
     * 数字员工命名只需要识别工具用途，不需要携带整段实现细节。
     */
    private static final int DIGITAL_EMPLOYEE_TOOL_DESC_MAX_LEN = 50;

    /**
     * 控制工具描述总长度，避免兼容网关在超长 prompt 下返回截断响应。
     */
    private static final int DIGITAL_EMPLOYEE_TOOL_PROMPT_MAX_LEN = 1200;

    /**
     * ReAct范式的「思考」阶段抽象方法（子类必须实现）
     * 核心契约：
     * - 子类需实现具体的思考逻辑（如分析上下文、决策工具调用、生成规划步骤）
     * - 思考过程需基于当前上下文、历史记忆、可用工具集
     * @return boolean 是否需要执行后续的「行动」阶段：
     *         - true：思考后需要执行act()方法（如需要调用工具、推进任务）
     *         - false：思考完成，无需执行行动（如任务已完成、无可用工具）
     */
    public abstract boolean think();

    /**
     * ReAct范式的「行动」阶段抽象方法（子类必须实现）
     * 核心契约：
     * - 子类需实现具体的执行逻辑（如调用工具、处理工具结果、更新任务状态）
     * - 行动逻辑需承接think()阶段的决策（如执行think()生成的工具调用列表）
     * @return String 行动执行结果：
     *         子类需返回有业务意义的结果字符串（如工具执行结果、下一步任务指令、任务完成标识）
     */
    public abstract String act();

    /**
     * 执行ReAct循环的单个标准化步骤（思考 → 行动）
     * 该方法封装了ReAct范式的核心流程，子类无需重写，只需实现think()和act()
     * @return String 单步执行结果：
     *         - 思考阶段返回false：返回固定提示语"Thinking complete - no action needed"
     *         - 思考阶段返回true：返回act()方法的执行结果
     */
    @Override
    public String step() {
        // 1. 执行思考阶段，判断是否需要行动
        boolean shouldAct = think();
        if (!shouldAct) {
            return "Thinking complete - no action needed";
        }
        // 2. 执行行动阶段并返回结果
        return act();
    }

    /**
     * 生成/更新数字员工工具（核心业务扩展能力）
     * 核心逻辑：
     * 1. 校验任务参数有效性
     * 2. 格式化数字员工生成的专属提示词
     * 3. 异步调用LLM生成数字员工配置（JSON格式）
     * 4. 解析LLM响应，更新工具集合中的数字员工
     * 5. 同步更新智能体的可用工具集（availableTools）
     * @param task 生成数字员工的目标任务：用于引导LLM生成匹配任务场景的数字员工配置（如"市场洞察分析"）
     */
    public void generateDigitalEmployee(String task) {
        // 1. 参数校验：任务为空时直接返回，避免无效调用
        if (StringUtils.isEmpty(task)) {
            log.warn("requestId: {} generateDigitalEmployee task is empty", context.getRequestId());
            return;
        }

        try {
            // 2. 构建格式化的系统提示词（提取为独立方法，提高可维护性）
            String formattedPrompt = formatSystemPrompt(task);
            // 3. 构建LLM调用的用户消息（内容为格式化后的提示词）
            Message userMessage = Message.userMessage(formattedPrompt, null);

            // 4. 异步调用LLM生成数字员工配置：
            // - 模型侧仍走流式，兼容仅支持 stream 的网关；同时显式关闭前端透传，避免 JSON 混入思考区。
            CompletableFuture<String> summaryFuture = getLlm().ask(
                    context,
                    Collections.singletonList(userMessage), // 仅传入当前用户消息
                    Collections.emptyList(),                // 无额外系统消息
                    true,                                   // 模型侧走流式
                    false,                                  // 禁止向前端透传内部生成内容
                    0.1,                                    // 温度系数：越低结果越确定
                    ExecutionLedgerConstants.CALL_KIND_INTERNAL_DIGITAL_EMPLOYEE); // 标记为内部 ask，避免污染前端回放

            // 5. 同步获取异步结果（阻塞等待LLM响应，可根据业务调整为非阻塞）
            String llmResponse = summaryFuture.get();
            log.info("requestId: {} task:{} generateDigitalEmployee LLM响应: {}", context.getRequestId(), task, llmResponse);

            // 6. 解析LLM响应为JSON对象（处理两种常见格式）
            JSONObject jsonObject = parseDigitalEmployee(llmResponse);
            if (jsonObject != null) {
                // 7. 解析成功：更新工具集合中的数字员工配置
                log.info("requestId:{} generateDigitalEmployee 解析后配置: {}", context.getRequestId(), jsonObject);

                context.getToolCollection().updateDigitalEmployee(jsonObject);
                // 8. 记录当前任务，关联数字员工
                context.getToolCollection().setCurrentTask(task);
                // 9. 同步更新智能体的可用工具集（使新生成的数字员工工具生效）
                availableTools = context.getToolCollection();
            } else {
                // 解析失败：记录错误日志
                log.error("requestId: {} generateDigitalEmployee 响应解析失败，原始响应:{}", context.getRequestId(), llmResponse);
            }

        } catch (Exception e) {
            // 异常捕获：涵盖LLM调用、JSON解析、工具更新等所有异常场景
            log.error("requestId: {} generateDigitalEmployee 执行失败", context.getRequestId(), e);
        }
    }

    /**
     * 解析LLM返回的数字员工配置响应
     * 支持两种常见的JSON格式解析（兼容LLM输出的不规范性）：
     * 格式1：Markdown代码块包裹的JSON（```json + JSON内容 + ```）
     * 格式2：纯JSON字符串（无代码块包裹）
     * @param response LLM返回的原始响应字符串
     * @return JSONObject 解析后的数字员工配置：
     *         - 成功：返回包含数字员工配置的JSON对象（如{"file_tool": "市场洞察专员"}）
     *         - 失败：空响应/解析异常时返回null
     */
    private JSONObject parseDigitalEmployee(String response) {
        // 1. 空值校验：响应为空直接返回null
        if (StringUtils.isBlank(response)) {
            log.warn("requestId: {} parseDigitalEmployee response is blank", context.getRequestId());
            return null;
        }

        // 2. 初始化待解析的JSON字符串（默认使用原始响应）
        String jsonString = response;
        // 3. 正则表达式：匹配```json包裹的JSON内容（非贪婪匹配，避免匹配到多余内容）
        String regex = "```\\s*json([\\d\\D]+?)```";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(response);

        // 4. 匹配到代码块格式：提取内部的JSON内容
        if (matcher.find()) {
            String temp = matcher.group(1).trim(); // 去除首尾空格/换行
            if (!temp.isEmpty()) {
                jsonString = temp; // 替换为提取后的纯JSON内容
            }
        }

        // 5. 解析JSON字符串为JSONObject
        try {
            return JSON.parseObject(jsonString);
        } catch (Exception e) {
            // 解析异常：记录日志并返回null
            log.error("requestId: {} parseDigitalEmployee JSON解析失败，待解析字符串:{}", context.getRequestId(), jsonString, e);
            return null;
        }
    }

    /**
     * 格式化数字员工生成的系统提示词
     * 核心逻辑：
     * 1. 校验数字员工提示词模板是否配置
     * 2. 拼接当前可用工具的名称+描述（填充{{ToolsDesc}}占位符）
     * 3. 替换模板中的所有占位符（{{task}}/{{ToolsDesc}}/{{query}}）
     * @param task 目标任务：用于填充{{task}}占位符
     * @return String 格式化后的完整提示词（可直接作为LLM输入）
     * @throws IllegalStateException 当数字员工提示词模板未配置时抛出（阻断无效的LLM调用）
     */
    private String formatSystemPrompt(String task) {
        // 1. 获取数字员工专用提示词模板（从配置/上下文加载）
        String digitalEmployeePrompt = getDigitalEmployeePrompt();
        // 2. 模板未配置：抛出运行时异常，避免后续无效操作
        if (digitalEmployeePrompt == null) {
            log.error("requestId: {} formatSystemPrompt 数字员工提示词模板未配置", context.getRequestId());
            throw new IllegalStateException("System prompt is not configured");
        }

        // 3. 拼接简化后的工具摘要，避免把整段长描述直接注入数字员工 prompt
        String toolPrompt = buildDigitalEmployeeToolPrompt();

        // 4. 替换模板中的占位符，生成最终提示词
        return digitalEmployeePrompt
                .replace("{{task}}", task)          // 替换目标任务占位符
                .replace("{{ToolsDesc}}", toolPrompt) // 替换工具描述占位符
                .replace("{{query}}", context.getQuery()); // 替换用户原始查询占位符
    }

    /**
     * 数字员工命名提示词只保留每个工具的简短用途，避免把 skill 列表和实现细节整体带入模型。
     */
    private String buildDigitalEmployeeToolPrompt() {
        if (context == null || context.getToolCollection() == null || context.getToolCollection().getToolMap() == null) {
            return "";
        }

        StringBuilder toolPrompt = new StringBuilder();
        for (BaseTool tool : context.getToolCollection().getToolMap().values()) {
            if (tool == null) {
                continue;
            }
            String summarizedDescription = summarizeToolDescription(tool.getDescription());
            String toolLine = String.format("工具名：%s 工具描述：%s%n", tool.getName(), summarizedDescription);
            if (toolPrompt.length() + toolLine.length() > DIGITAL_EMPLOYEE_TOOL_PROMPT_MAX_LEN) {
                toolPrompt.append("[工具描述已截断，保留核心工具摘要]");
                break;
            }
            toolPrompt.append(toolLine);
        }
        return toolPrompt.toString();
    }

    /**
     * 仅保留首行摘要，并裁剪过长文本，避免把实现细节和可用 skills 列表带入 prompt。
     */
    private String summarizeToolDescription(String description) {
        if (StringUtils.isBlank(description)) {
            return "";
        }

        String firstLine = description;
        int newlineIndex = description.indexOf('\n');
        if (newlineIndex >= 0) {
            firstLine = description.substring(0, newlineIndex);
        }

        String normalized = firstLine.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= DIGITAL_EMPLOYEE_TOOL_DESC_MAX_LEN) {
            return normalized;
        }
        return normalized.substring(0, DIGITAL_EMPLOYEE_TOOL_DESC_MAX_LEN) + "...";
    }

}
