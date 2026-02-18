package org.wwz.ai.domain.agent.service.armory.node;

import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import org.wwz.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import org.wwz.ai.domain.agent.service.armory.util.McpConnectionDiagnostic;
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
 */
@Slf4j
@Service
public class AiClientToolMcpNode extends AbstractArmorySupport {

    @Resource
    private AiClientModelNode aiClientModelNode;

    @Resource
    private McpConnectionDiagnostic mcpConnectionDiagnostic;

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 构建节点，Tool MCP 工具配置{}", JSON.toJSONString(requestParameter));

        List<AiClientToolMcpVO> aiClientToolMcpList = dynamicContext.getValue(dataName());

        if (aiClientToolMcpList == null || aiClientToolMcpList.isEmpty()) {
            log.warn("没有需要被初始化的 ai client tool mcp");
            return router(requestParameter, dynamicContext);
        }

        int successCount = 0;
        int failureCount = 0;
        
        for (AiClientToolMcpVO mcpVO : aiClientToolMcpList) {
            try {
                log.info("正在初始化 MCP 工具: mcpId={}, mcpName={}, transportType={}", 
                        mcpVO.getMcpId(), mcpVO.getMcpName(), mcpVO.getTransportType());
                
                // 创建 MCP 服务
                McpSyncClient mcpSyncClient = createMcpSyncClient(mcpVO);

                // 注册 MCP 对象
                registerBean(beanName(mcpVO.getMcpId()), McpSyncClient.class, mcpSyncClient);
                
                successCount++;
                log.info("✅ MCP 工具初始化成功: mcpId={}, mcpName={}", 
                        mcpVO.getMcpId(), mcpVO.getMcpName());
                
            } catch (Exception e) {
                failureCount++;
                String errorMsg = String.format(
                        "❌ MCP 工具初始化失败: mcpId=%s, mcpName=%s, transportType=%s, error=%s",
                        mcpVO.getMcpId(), mcpVO.getMcpName(), mcpVO.getTransportType(), e.getMessage()
                );
                log.error(errorMsg, e);
                
                // 提供诊断建议
                provideDiagnosticInfo(mcpVO, e);
                
                // 根据配置决定是否继续（如果配置了允许部分失败，则继续）
                // 这里默认继续，允许部分 MCP 工具失败不影响整体流程
                log.warn("⚠️ 跳过失败的 MCP 工具，继续初始化其他工具");
            }
        }
        
        log.info("MCP 工具初始化完成: 成功 {} 个, 失败 {} 个, 总计 {} 个", 
                successCount, failureCount, aiClientToolMcpList.size());
        
        if (failureCount > 0 && successCount == 0) {
            log.warn("⚠️ 所有 MCP 工具初始化失败，但系统将继续运行（部分功能可能受限）");
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

                // 可选：在连接前进行诊断检查（仅在调试模式下）
                if (log.isDebugEnabled() && mcpConnectionDiagnostic != null) {
                    try {
                        var diagnosticResult = mcpConnectionDiagnostic.checkSseConnection(baseUri, sseEndpoint, 3000);
                        if (!diagnosticResult.isAccessible()) {
                            log.warn("MCP 连接诊断检查失败: {}", diagnosticResult.toString());
                        } else {
                            log.debug("MCP 连接诊断检查通过: {}", diagnosticResult.getFullUrl());
                        }
                    } catch (Exception e) {
                        log.debug("连接诊断检查异常（不影响后续连接）: {}", e.getMessage());
                    }
                }

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
                        .requestTimeout(Duration.ofMinutes(aiClientToolMcpVO.getRequestTimeout()))
                        .build();
                // 初始化MCP客户端：执行协议握手/建立底层连接，保证客户端可直接用于后续调用
                try {
                    log.debug("正在连接 SSE MCP 服务: baseUri={}, sseEndpoint={}", baseUri, sseEndpoint);
                    var init_sse = mcpSyncClient.initialize();

                    // 打印初始化日志，记录SSE客户端初始化结果，方便问题排查
                    log.info("Tool SSE MCP Initialized successfully: mcpName={}, initResult={}", 
                            aiClientToolMcpVO.getMcpName(), init_sse);
                    // 返回初始化完成的SSE类型MCP同步客户端
                    return mcpSyncClient;
                } catch (Exception e) {
                    String errorDetail = String.format(
                            "SSE MCP 连接失败: baseUri=%s, sseEndpoint=%s, mcpName=%s",
                            baseUri, sseEndpoint, aiClientToolMcpVO.getMcpName()
                    );
                    log.error(errorDetail, e);
                    throw new RuntimeException("SSE MCP 连接失败: " + errorDetail, e);
                }
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
                try {
                    log.debug("正在启动 STDIO MCP 进程: command={}, args={}", 
                            stdio.getCommand(), stdio.getArgs());
                    var init_stdio = mcpClient.initialize();

                    // 打印初始化日志，记录STDIO客户端初始化结果，方便问题排查
                    log.info("Tool Stdio MCP Initialized successfully: mcpName={}, initResult={}", 
                            aiClientToolMcpVO.getMcpName(), init_stdio);
                    // 返回初始化完成的STDIO类型MCP同步客户端
                    return mcpClient;
                } catch (Exception e) {
                    String errorDetail = String.format(
                            "STDIO MCP 启动失败: command=%s, args=%s, mcpName=%s",
                            stdio.getCommand(), stdio.getArgs(), aiClientToolMcpVO.getMcpName()
                    );
                    log.error(errorDetail, e);
                    throw new RuntimeException("STDIO MCP 启动失败: " + errorDetail, e);
                }
            }
        }

        // 所有支持的传输类型分支均未匹配：抛出运行时异常，提示不支持的传输类型
        // 快速失败原则，避免后续出现未知的通信异常
        throw new RuntimeException("err! transportType " + transportType + " not exist!");
    }

    /**
     * 提供 MCP 连接失败的诊断信息和建议。
     *
     * @param mcpVO MCP 配置对象
     * @param e     连接异常
     */
    private void provideDiagnosticInfo(AiClientToolMcpVO mcpVO, Exception e) {
        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("\n").append("=".repeat(60)).append("\n");
        diagnostic.append("MCP 工具连接失败诊断信息\n");
        diagnostic.append("=".repeat(60)).append("\n");
        diagnostic.append("工具ID: ").append(mcpVO.getMcpId()).append("\n");
        diagnostic.append("工具名称: ").append(mcpVO.getMcpName()).append("\n");
        diagnostic.append("传输类型: ").append(mcpVO.getTransportType()).append("\n");
        
        if ("sse".equals(mcpVO.getTransportType())) {
            AiClientToolMcpVO.TransportConfigSse sseConfig = mcpVO.getTransportConfigSse();
            if (sseConfig != null) {
                diagnostic.append("基础地址: ").append(sseConfig.getBaseUri()).append("\n");
                diagnostic.append("SSE端点: ").append(sseConfig.getSseEndpoint()).append("\n");
            }
        } else if ("stdio".equals(mcpVO.getTransportType())) {
            AiClientToolMcpVO.TransportConfigStdio stdioConfig = mcpVO.getTransportConfigStdio();
            if (stdioConfig != null) {
                Map<String, AiClientToolMcpVO.TransportConfigStdio.Stdio> stdioMap = stdioConfig.getStdio();
                AiClientToolMcpVO.TransportConfigStdio.Stdio stdio = stdioMap != null 
                        ? stdioMap.get(mcpVO.getMcpName()) : null;
                if (stdio != null) {
                    diagnostic.append("执行命令: ").append(stdio.getCommand()).append("\n");
                    diagnostic.append("命令参数: ").append(stdio.getArgs()).append("\n");
                }
            }
        }
        
        diagnostic.append("\n错误信息: ").append(e.getMessage()).append("\n");
        diagnostic.append("\n可能的原因:\n");
        
        if (e instanceof java.net.ConnectException) {
            diagnostic.append("1. MCP 服务未启动或服务地址配置错误\n");
            diagnostic.append("2. 网络连接问题（防火墙、代理等）\n");
            diagnostic.append("3. 服务端口被占用或不可访问\n");
            diagnostic.append("4. 服务地址格式错误（检查 baseUri 和 sseEndpoint）\n");
        } else if (e instanceof java.nio.channels.ClosedChannelException) {
            diagnostic.append("1. 连接通道被意外关闭\n");
            diagnostic.append("2. 服务端主动断开连接\n");
            diagnostic.append("3. 网络不稳定导致连接中断\n");
        } else {
            diagnostic.append("1. 检查 MCP 服务是否正常运行\n");
            diagnostic.append("2. 检查配置参数是否正确\n");
            diagnostic.append("3. 查看服务端日志获取更多信息\n");
        }
        
        diagnostic.append("\n建议的排查步骤:\n");
        diagnostic.append("1. 检查 MCP 服务是否已启动（查看服务进程或日志）\n");
        diagnostic.append("2. 验证服务地址和端口是否正确（使用 curl 或浏览器测试）\n");
        diagnostic.append("3. 检查网络连接（ping、telnet 等）\n");
        diagnostic.append("4. 查看 MCP 服务端的错误日志\n");
        diagnostic.append("5. 确认服务配置与客户端配置一致\n");
        
        diagnostic.append("\n").append("=".repeat(60)).append("\n");
        
        log.warn(diagnostic.toString());
    }

}
