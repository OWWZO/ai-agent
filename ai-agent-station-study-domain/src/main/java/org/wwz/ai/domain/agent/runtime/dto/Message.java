package org.wwz.ai.domain.agent.runtime.dto;




/**
 * 消息类 - 表示代理系统中的各种消息
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.List;

/**
 * 大模型交互的统一消息模型
 * 核心作用：封装与LLM（大语言模型）交互的所有消息类型，兼容「多角色对话、工具调用、多模态（图文）」场景，
 * 是智能体与大模型之间数据传输的标准化载体。
 *
 * 设计特点：
 * 1. 采用Builder模式（@Builder）：支持灵活构建不同类型的消息（如带图片的用户消息、带工具调用的助手消息）；
 * 2. 静态工厂方法：封装不同角色消息的创建逻辑，简化调用（无需手动设置role）；
 * 3. 多模态支持：内置base64Image字段，适配图文混合的消息交互；
 * 4. 工具调用适配：内置toolCallId/toolCalls字段，支撑ReAct范式的工具调用流程。
 *
 * @author （可补充作者信息）
 * @date （可补充日期）
 */
@Data // Lombok注解：自动生成getter/setter/toString/equals/hashCode等方法
@Builder // Lombok注解：支持链式构建消息对象（如Message.builder().role(RoleType.USER).content("你好").build()）
@NoArgsConstructor // Lombok注解：生成无参构造方法
@AllArgsConstructor // Lombok注解：生成全参构造方法
public class Message {
    /**
     * 消息角色（核心标识）
     * 枚举值（RoleType）：
     * - USER：用户消息（大模型的输入，如用户的问题、指令）；
     * - SYSTEM：系统消息（大模型的核心指令，如角色定义、执行规则）；
     * - ASSISTANT：助手消息（大模型的输出，如回答、思考过程、工具调用指令）；
     * - TOOL：工具消息（工具执行结果的反馈，供大模型后续思考参考）；
     * 用途：大模型根据角色区分消息类型，执行不同的处理逻辑。
     */
    private RoleType role;

    /**
     * 消息文本内容
     * 用途：
     * - USER/SYSTEM：存储用户问题/系统指令文本；
     * - ASSISTANT：存储大模型的思考过程/回答文本；
     * - TOOL：存储工具执行的结果文本；
     * 示例：
     * - USER："帮我分析这款产品的市场竞争力"；
     * - ASSISTANT："需要调用市场检索工具获取竞品数据，工具调用参数如下：..."。
     */
    private String content;

    /**
     * 图片数据（Base64编码）
     * 用途：支撑多模态交互，存储图片的Base64编码字符串（无需额外文件传输）；
     * 场景：用户上传商品图片提问、大模型返回图文回答等；
     * 格式示例："data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."。
     */
    private String base64Image;

    /**
     * 工具调用ID（唯一标识）
     * 用途：
     * 1. 关联「助手消息的工具调用请求」与「工具消息的执行结果」；
     * 2. 多工具并行调用时，通过该ID区分不同工具的执行结果；
     * 仅在RoleType.TOOL类型消息中有效。
     */
    private String toolCallId;

    /**
     * 工具调用列表
     * 用途：存储大模型返回的待执行工具指令（包含工具名称、参数等）；
     * 仅在RoleType.ASSISTANT类型消息中有效（思考阶段返回的工具调用决策）；
     * 场景：ReAct范式中，助手消息携带该列表，智能体根据列表执行对应工具。
     */
    private List<ToolCall> toolCalls;

    /**
     * 静态工厂方法：创建用户消息（最常用）
     * @param content  用户输入的文本内容（必填）
     * @param base64Image  图片Base64编码（可选，无图片传null）
     * @return Message  用户角色消息对象（role=USER）
     * 示例：Message.userMessage("帮我分析这张商品图片", base64Str);
     */
    public static Message userMessage(String content, String base64Image) {
        return Message.builder()
                .role(RoleType.USER)
                .content(content)
                .base64Image(base64Image)
                .build();
    }

    /**
     * 静态工厂方法：创建系统消息（核心指令）
     * @param content  系统指令文本（必填，如角色定义、执行规则）
     * @param base64Image  图片Base64编码（可选，极少使用）
     * @return Message  系统角色消息对象（role=SYSTEM）
     * 示例：Message.systemMessage("你是一名电商市场分析师，严格按照SOP生成报告", null);
     */
    public static Message systemMessage(String content, String base64Image) {
        return Message.builder()
                .role(RoleType.SYSTEM)
                .content(content)
                .base64Image(base64Image)
                .build();
    }

    /**
     * 静态工厂方法：创建助手消息（大模型输出）
     * @param content  大模型返回的文本内容（必填，如回答、思考过程）
     * @param base64Image  图片Base64编码（可选，大模型返回图文回答时使用）
     * @return Message  助手角色消息对象（role=ASSISTANT）
     * 示例：Message.assistantMessage("以下是产品市场竞争力分析...", null);
     */
    public static Message assistantMessage(String content, String base64Image) {
        return Message.builder()
                .role(RoleType.ASSISTANT)
                .content(content)
                .base64Image(base64Image)
                .build();
    }

    /**
     * 静态工厂方法：创建工具消息（工具执行结果反馈）
     * @param content  工具执行结果文本（必填，如检索到的竞品数据）
     * @param toolCallId  工具调用ID（必填，关联对应的助手消息工具调用）
     * @param base64Image  图片Base64编码（可选，工具返回图片结果时使用）
     * @return Message  工具角色消息对象（role=TOOL）
     * 示例：Message.toolMessage("竞品A的市场份额为20%", "call_123", null);
     */
    public static Message toolMessage(String content, String toolCallId, String base64Image) {
        return Message.builder()
                .role(RoleType.TOOL)
                .content(content)
                .toolCallId(toolCallId)
                .base64Image(base64Image)
                .build();
    }

    /**
     * 静态工厂方法：从工具调用列表创建助手消息（ReAct范式核心）
     * @param content  助手思考过程文本（必填，如"需要调用市场检索工具获取数据"）
     * @param toolCalls  待执行的工具调用列表（必填，包含工具名称、参数）
     * @return Message  携带工具调用的助手消息（role=ASSISTANT，toolCalls非空）
     * 场景：大模型思考后决定调用工具，通过该方法创建包含工具调用指令的助手消息。
     */
    public static Message fromToolCalls(String content, List<ToolCall> toolCalls) {
        return Message.builder()
                .role(RoleType.ASSISTANT)
                .content(content)
                .toolCalls(toolCalls)
                .build();
    }
}