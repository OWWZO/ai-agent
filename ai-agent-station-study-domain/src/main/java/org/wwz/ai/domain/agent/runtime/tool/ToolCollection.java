package org.wwz.ai.domain.agent.runtime.tool;


/**
 * 工具集合类 - 管理可用的工具
 */

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能体工具集合管理类
 * 核心作用：统一管理智能体可调用的所有工具，包含两类核心工具：
 * 1. 基础工具（BaseTool）：本地实现的工具（如文件解析、计划管理、检索工具）；
 * 2. MCP工具（McpTool）：远程MCP服务提供的工具（通过HTTP调用远程服务）；
 * 附加能力：管理「数字员工」配置，关联工具与数字员工的映射关系，支撑动态工具扩展。
 *
 * 设计特点：
 * - 采用Map存储工具，通过工具名快速索引（O(1)查询效率）；
 * - 封装工具的添加、获取、执行逻辑，对外提供统一的execute入口；
 * - 关联AgentContext上下文，支撑工具执行时的上下文依赖。
 *
 * @author （可补充作者信息）
 * @date （可补充日期）
 */
@Data
@Slf4j
public class ToolCollection {
    /**
     * 基础工具映射表（核心工具容器）
     * Key：工具名称（唯一标识，如"planningTool"、"fileParseTool"）；
     * Value：BaseTool子类实例（本地实现的具体工具）；
     * 用途：存储所有本地可执行的基础工具，支持快速查询和调用。
     */
    private Map<String, BaseTool> toolMap;

    /**
     * MCP工具信息映射表（远程工具容器）
     * Key：工具名称（唯一标识，如"remoteSearchTool"、"mcpAnalysisTool"）；
     * Value：McpToolInfo（远程MCP工具的元信息：名称、描述、参数、服务地址）；
     * 用途：存储远程MCP工具的配置信息，执行时通过该信息调用远程服务。
     */
    private Map<String, McpToolInfo> mcpToolMap;

    /**
     * 关联的智能体上下文
     * 用途：工具执行时需要依赖上下文数据（如requestId、文件列表、输出器），
     * 尤其MCP工具调用时需传递上下文完成全链路追踪。
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JSONField(serialize = false, deserialize = false)
    private AgentContext agentContext;

    /**
     * MCP 工具统一执行器。
     * ToolCollection 不是 Spring Bean，因此必须由外部显式注入。
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JSONField(serialize = false, deserialize = false)
    private McpToolExecutor mcpToolExecutor;

    /**
     * 当前执行的任务标识
     * 业务说明：
     * - 非并发场景下，每个task执行时会更新该字段，关联当前任务与数字员工；
     * - TODO：并发场景下需加锁/线程隔离处理，避免多任务覆盖该字段导致数据错乱。
     */
    private String currentTask;

    /**
     * 数字员工配置JSON对象
     * 结构示例：{"file_tool": "市场洞察专员", "search_tool": "数据分析师"}
     * 用途：存储工具名称与数字员工的映射关系，实现「工具-数字员工」的动态绑定。
     */
    private JSONObject digitalEmployees;

    /**
     * 无参构造方法
     * 初始化工具映射表：默认创建空的HashMap，避免空指针异常。
     */
    public ToolCollection() {
        this.toolMap = new LinkedHashMap<>();
        this.mcpToolMap = new LinkedHashMap<>();
    }

    /**
     * 添加基础工具到集合
     * @param tool 基础工具实例（BaseTool子类，如PlanningTool、FileParseTool）
     * 说明：工具名称作为Key，重复添加会覆盖原有同名称工具。
     */
    public void addTool(BaseTool tool) {
        toolMap.put(tool.getName(), tool);
    }

    /**
     * 根据工具名称获取基础工具
     * @param name 工具名称（唯一标识）
     * @return BaseTool 基础工具实例：存在则返回，不存在返回null。
     */
    public BaseTool getTool(String name) {
        return toolMap.get(name);
    }

    /**
     * 添加MCP远程工具到集合
     * @param toolInfo MCP 工具元信息
     * 说明：直接接收完整 McpToolInfo，重复添加时同名工具会被覆盖。
     */
    public void addMcpTool(McpToolInfo toolInfo) {
        if (toolInfo == null || StringUtils.isBlank(toolInfo.getName())) {
            log.warn("requestId:{} addMcpTool skipped, invalid toolInfo: {}",
                    agentContext != null ? agentContext.getRequestId() : "unknown", toolInfo);
            return;
        }
        mcpToolMap.put(toolInfo.getName(), toolInfo);
    }

    /**
     * 根据工具名称获取MCP工具信息
     * @param name 工具名称（唯一标识）
     * @return McpToolInfo MCP工具元信息：存在则返回，不存在返回null。
     */
    public McpToolInfo getMcpTool(String name) {
        return mcpToolMap.get(name);
    }

    /**
     * 统一执行工具（核心方法）
     * 逻辑分支：
     * 1. 优先执行基础工具（本地）；
     * 2. 基础工具不存在时执行MCP远程工具；
     * 3. 工具不存在时记录错误日志并返回null。
     * @param name 工具名称（唯一标识）
     * @param toolInput 工具输入参数（Object类型，适配不同工具的参数格式，如String/JSONObject）
     * @return Object 工具执行结果：
     *         - 基础工具：返回 tool.execute() 的原始结果对象；
     *         - MCP工具：返回远程调用的响应结果字符串；
     *         - 工具不存在：返回null。
     */
    public Object execute(String name, Object toolInput) {
        // 分支1：执行本地基础工具
        if (toolMap.containsKey(name)) {
            BaseTool tool = getTool(name);
            return tool.execute(toolInput);
        }
        // 分支2：执行远程MCP工具
        else if (mcpToolMap.containsKey(name)) {
            McpToolInfo toolInfo = mcpToolMap.get(name);
            McpToolExecutor executor = mcpToolExecutor;
            if (executor == null) {
                log.error("requestId:{} execute mcp tool {} failed, McpToolExecutor not found",
                        agentContext != null ? agentContext.getRequestId() : "unknown", name);
                return "Tool" + name + " Error.";
            }
            return executor.executeTool(toolInfo, toolInput);
        }
        // 分支3：工具不存在，记录错误日志
        else {
            log.error("Error: Unknown tool {}", name);
        }
        return null;
    }

    /**
     * 更新数字员工配置
     * @param digitalEmployee 数字员工配置JSON对象（如{"file_tool": "市场洞察专员"}）
     * 说明：若传入null，记录错误日志但不抛出异常（容错设计，避免阻断流程）。
     */
    public void updateDigitalEmployee(JSONObject digitalEmployee) {
        if (digitalEmployee == null) {
            log.error("requestId:{} setDigitalEmployee: 数字员工配置为null", agentContext.getRequestId());
        }
        setDigitalEmployees(digitalEmployee);
    }

    /**
     * 根据工具名称获取绑定的数字员工名称
     * @param toolName 工具名称（唯一标识，如"file_tool"）
     * @return String 数字员工名称：
     *         - 工具名称为空/数字员工配置为空：返回null；
     *         - 存在映射关系：返回数字员工名称（如"市场洞察专员"）；
     *         - 无映射关系：返回null。
     */
    public String getDigitalEmployee(String toolName) {
        // 空值校验：工具名称为空直接返回null
        if (StringUtils.isEmpty(toolName)) {
            return null;
        }
        // 空值校验：数字员工配置未初始化直接返回null
        if (digitalEmployees == null) {
            return null;
        }
        // 从JSON对象中获取工具对应的数字员工名称
        return (String) digitalEmployees.get(toolName);
    }

    /**
     * 复制当前任务态快照。
     * 并发 child task 只允许继承父 task 的初始视图，后续修改必须彼此隔离。
     */
    public TaskScopedStateSnapshot snapshotTaskScopedState() {
        return new TaskScopedStateSnapshot(
                currentTask,
                digitalEmployees == null ? null : JSONFieldCopySupport.copyJsonObject(digitalEmployees)
        );
    }

    /**
     * 恢复一份任务态快照。
     */
    public void restoreTaskScopedState(TaskScopedStateSnapshot snapshot) {
        if (snapshot == null) {
            currentTask = null;
            digitalEmployees = null;
            return;
        }
        currentTask = snapshot.currentTask();
        digitalEmployees = snapshot.digitalEmployees() == null
                ? null
                : JSONFieldCopySupport.copyJsonObject(snapshot.digitalEmployees());
    }

    @Override
    public String toString() {
        return "ToolCollection(" +
                "toolMap=" + (toolMap != null ? toolMap.keySet() : "null") +
                ", mcpToolMap=" + (mcpToolMap != null ? mcpToolMap.keySet() : "null") +
                ", currentTask='" + currentTask + '\'' +
                ')';
    }

    /**
     * task 级运行态快照，仅复制并发路径需要隔离的可变字段。
     */
    public record TaskScopedStateSnapshot(String currentTask, JSONObject digitalEmployees) {
    }

    /**
     * 统一封装 JSON 对象深拷贝，避免到处散落 FastJSON 细节。
     */
    private static final class JSONFieldCopySupport {

        private JSONFieldCopySupport() {
        }

        private static JSONObject copyJsonObject(JSONObject source) {
            if (source == null) {
                return null;
            }
            return JSONObject.parseObject(source.toJSONString());
        }
    }
}
