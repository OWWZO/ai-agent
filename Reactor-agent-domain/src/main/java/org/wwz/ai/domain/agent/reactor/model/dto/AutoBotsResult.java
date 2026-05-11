package org.wwz.ai.domain.agent.reactor.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoBotsResult {
    private String status;//状态
    private String response = "";//增量内容回复
    private String responseAll = "";//全量内容回复
    private boolean finished;//是否结束
    private long useTimes;
    private long useTokens;
    private Map<String, Object> resultMap;//结构化输出结果
    private String responseType = "markdown";//大模型响应内容类型
    private String traceId;//会话ID
    private String reqId;//请求ID
}
