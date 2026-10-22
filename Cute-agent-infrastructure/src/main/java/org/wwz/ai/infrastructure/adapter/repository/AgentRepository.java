package org.wwz.ai.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientToolMcpConfigRepository;
import org.wwz.ai.domain.agent.model.valobj.AiClientToolMcpVO;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP 配置仓储适配器：把 catalog 配置模型翻译成运行时描述。
 * <p>
 * 读路径复用 {@link IAiClientToolMcpConfigRepository}，与管理端写入同源，
 * 避免同一张表出现两套 PO/Mapper/端口。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentRepository {

    private final IAiClientToolMcpConfigRepository mcpConfigRepository;

    public List<AiClientToolMcpVO> queryEnabledAiClientToolMcpVOList() {
        return toAiClientToolMcpVOList(mcpConfigRepository.findEnabled());
    }

    private List<AiClientToolMcpVO> toAiClientToolMcpVOList(List<AiClientToolMcpConfig> mcps) {
        if (mcps == null || mcps.isEmpty()) {
            return List.of();
        }
        return mcps.stream()
                .map(this::toAiClientToolMcpVO)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 将 MCP 持久化配置转换为运行时描述，并按 transportType 解析多态 JSON。
     */
    private AiClientToolMcpVO toAiClientToolMcpVO(AiClientToolMcpConfig toolMcp) {
        if (toolMcp == null) {
            return null;
        }

        AiClientToolMcpVO mcpVO = AiClientToolMcpVO.builder()
                .mcpId(toolMcp.getMcpId())
                .mcpName(toolMcp.getMcpName())
                .transportType(toolMcp.getTransportType())
                .transportConfig(toolMcp.getTransportConfig())
                .requestTimeout(toolMcp.getRequestTimeout())
                .status(toolMcp.getStatus())
                .build();

        String transportConfig = toolMcp.getTransportConfig();
        String transportType = toolMcp.getTransportType();

        try {
            if ("sse".equals(transportType)) {
                ObjectMapper objectMapper = new ObjectMapper();
                AiClientToolMcpVO.TransportConfigSse configSse =
                        objectMapper.readValue(transportConfig, AiClientToolMcpVO.TransportConfigSse.class);
                mcpVO.setTransportConfigSse(configSse);
            } else if ("stdio".equals(transportType)) {
                Map<String, AiClientToolMcpVO.TransportConfigStdio.Stdio> stdio = JSON.parseObject(
                        transportConfig,
                        new TypeReference<>() {
                        });
                AiClientToolMcpVO.TransportConfigStdio configStdio = new AiClientToolMcpVO.TransportConfigStdio();
                configStdio.setStdio(stdio);
                mcpVO.setTransportConfigStdio(configStdio);
            } else if ("streamable_http".equals(transportType)) {
                ObjectMapper objectMapper = new ObjectMapper();
                AiClientToolMcpVO.TransportConfigStreamableHttp configHttp =
                        objectMapper.readValue(transportConfig, AiClientToolMcpVO.TransportConfigStreamableHttp.class);
                if (configHttp.getEndpoint() == null || configHttp.getEndpoint().isBlank()) {
                    configHttp.setEndpoint("/mcp");
                }
                if (configHttp.getHeaders() == null) {
                    configHttp.setHeaders(Map.of());
                }
                if (configHttp.getResumableStreams() == null) {
                    configHttp.setResumableStreams(false);
                }
                if (configHttp.getOpenConnectionOnStartup() == null) {
                    configHttp.setOpenConnectionOnStartup(true);
                }
                mcpVO.setTransportConfigStreamableHttp(configHttp);
            }
        } catch (Exception e) {
            log.error("解析传输配置失败: mcpId={}, reason={}", toolMcp.getMcpId(), e.getMessage(), e);
        }

        return mcpVO;
    }
}
