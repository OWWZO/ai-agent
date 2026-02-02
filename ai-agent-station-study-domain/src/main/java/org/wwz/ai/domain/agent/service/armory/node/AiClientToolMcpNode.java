package org.wwz.ai.domain.agent.service.armory.node;

import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import org.wwz.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP客户端配置节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/5 12:48
 */
@Slf4j
@Service
public class AiClientToolMcpNode extends AbstractArmorySupport {

    @Resource
    private AiClientModelNode aiClientModelNode;

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 构建节点，Tool MCP 工具配置{}", JSON.toJSONString(requestParameter));

        List<AiClientToolMcpVO> aiClientToolMcpList = dynamicContext.getValue(dataName());

        if (aiClientToolMcpList == null || aiClientToolMcpList.isEmpty()) {
            log.warn("没有需要被初始化的 ai client tool mcp");
            return router(requestParameter, dynamicContext);
        }

        for (AiClientToolMcpVO mcpVO : aiClientToolMcpList) {
            // 创建 MCP 服务
            McpSyncClient mcpSyncClient = createMcpSyncClient(mcpVO);

            // 注册 MCP 对象
            registerBean(beanName(mcpVO.getMcpId()), McpSyncClient.class, mcpSyncClient);
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return aiClientModelNode;
    }

    @Override
    protected String beanName(String beanId) {
        return AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getBeanName(beanId);
    }

    @Override
    protected String dataName() {
        return AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getDataName();
    }

    /**
     * 根据MCP工具配置创建并初始化对应的McpSyncClient同步客户端
     * 核心支持两种传输协议：SSE（服务器发送事件）、STDIO（标准输入输出）
     * @param aiClientToolMcpVO MCP工具的核心配置对象，包含传输类型、协议专属配置、超时时间、MCP名称等关键信息
     * @return 初始化完成的McpSyncClient同步客户端，可直接用于MCP工具的同步调用
     * @throws RuntimeException 当配置中的传输类型不是sse/stdio时，抛出该异常
     */
    private McpSyncClient createMcpSyncClient(AiClientToolMcpVO aiClientToolMcpVO) {
        // 从配置中提取传输协议类型，核心分支判断依据（目前支持sse/stdio两种）
        String transportType = aiClientToolMcpVO.getTransportType();

        // 根据传输类型分支，构建对应协议的MCP客户端
        switch (transportType) {
            // 分支1：SSE（Server-Sent Events）服务器发送事件协议，基于HTTP的单向流式通信
            case "sse" -> {
                // 获取SSE协议的专属配置对象，包含基础地址、默认端点等SSE通信所需参数
                AiClientToolMcpVO.TransportConfigSse transportConfigSse = aiClientToolMcpVO.getTransportConfigSse();
                // 原始基础地址（可能包含SSE端点路径，需做分离处理）
                String originalBaseUri = transportConfigSse.getBaseUri();
                // 处理后的纯基础地址（用于构建HTTP客户端）
                String baseUri;
                // SSE通信的具体端点路径（如/sse、/api/sse等）
                String sseEndpoint;

                // 检查原始基础地址中是否包含"SSE关键字段"，用于分离基础地址和SSE端点
                int queryParamStartIndex = originalBaseUri.indexOf("sse");
                if (queryParamStartIndex != -1) {
                    // 找到sse关键字：截取到关键字前一位作为纯基础地址（去除分隔的/、&等字符，保证地址格式正确）
                    baseUri = originalBaseUri.substring(0, queryParamStartIndex - 1);
                    // 截取从关键字前一位到末尾作为SSE完整端点（包含sse路径）
                    sseEndpoint = originalBaseUri.substring(queryParamStartIndex - 1);
                } else {
                    // 未找到sse关键字：基础地址直接使用原始值，SSE端点使用配置中指定的默认值
                    baseUri = originalBaseUri;
                    sseEndpoint = transportConfigSse.getSseEndpoint();
                }

                // 兜底处理：若SSE端点为空（null/空字符串/全空格），设置默认端点"/sse"，避免空端点导致通信失败
                sseEndpoint = StringUtils.isBlank(sseEndpoint) ? "/sse" : sseEndpoint;

                // 构建SSE协议的客户端传输层（基于HTTP客户端实现），传入处理后的基础地址和SSE端点
                HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                        .builder(baseUri) // 传入分离后的纯HTTP基础地址
                        .sseEndpoint(sseEndpoint) // 传入最终的SSE通信端点
                        .build();

                // 构建SSE类型的MCP同步客户端核心实例
                // 1. 基于SSE传输层创建客户端
                // 2. 设置请求超时时间：从配置中获取值，单位为【分钟】
                // 3. build()完成客户端基础构建
                McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport)
                        .requestTimeout(Duration.ofMinutes(aiClientToolMcpVO.getRequestTimeout()+1))
                        .build();
                // 初始化MCP客户端：执行协议握手/建立底层连接，保证客户端可直接用于后续调用
                var init_sse = mcpSyncClient.initialize();

                // 打印初始化日志，记录SSE客户端初始化结果，方便问题排查
                log.info("Tool SSE MCP Initialized {}", init_sse);
                // 返回初始化完成的SSE类型MCP同步客户端
                return mcpSyncClient;
            }
            // 分支2：STDIO（Standard Input/Output）标准输入输出协议，基于子进程的双向通信
            case "stdio" -> {
                // 获取STDIO协议的专属配置对象，包含子进程执行的命令、参数、环境变量等映射配置
                AiClientToolMcpVO.TransportConfigStdio transportConfigStdio = aiClientToolMcpVO.getTransportConfigStdio();
                // 获取STDIO配置中的子进程映射：key为MCP名称，value为对应子进程的执行配置（命令、参数、环境变量）
                Map<String, AiClientToolMcpVO.TransportConfigStdio.Stdio> stdioMap = transportConfigStdio.getStdio();
                // 根据当前MCP工具的名称，从映射中获取专属的子进程执行配置（一个STDIO配置可对应多个MCP工具）
                AiClientToolMcpVO.TransportConfigStdio.Stdio stdio = stdioMap.get(aiClientToolMcpVO.getMcpName());

                // 构建STDIO子进程的执行参数对象
                // 1. 核心参数：子进程的执行命令（如python、java -jar等）
                // 2. 附加参数：命令行执行参数（如脚本路径、启动参数）
                // 3. 环境变量：子进程运行的自定义环境变量
                var stdioParams = ServerParameters.builder(stdio.getCommand())
                        .args(stdio.getArgs())
                        .env(stdio.getEnv())
                        .build();

                // 构建STDIO类型的MCP同步客户端核心实例
                // 1. 基于STDIO传输层（传入子进程执行参数）创建客户端
                // 2. 设置请求超时时间：从配置中获取值，单位为【秒】（注意与SSE的分钟区分，配置单位不同）
                // 3. build()完成客户端基础构建
                var mcpClient = McpClient.sync(new StdioClientTransport(stdioParams))
                        .requestTimeout(Duration.ofSeconds(aiClientToolMcpVO.getRequestTimeout())).build();
                // 初始化MCP客户端：启动子进程、建立标准输入输出的通信通道
                var init_stdio = mcpClient.initialize();

                // 打印初始化日志，记录STDIO客户端初始化结果，方便问题排查
                log.info("Tool Stdio MCP Initialized {}", init_stdio);
                // 返回初始化完成的STDIO类型MCP同步客户端
                return mcpClient;
            }
        }

        // 所有支持的传输类型分支均未匹配：抛出运行时异常，提示不支持的传输类型
        // 快速失败原则，避免后续出现未知的通信异常
        throw new RuntimeException("err! transportType " + transportType + " not exist!");
    }

}
