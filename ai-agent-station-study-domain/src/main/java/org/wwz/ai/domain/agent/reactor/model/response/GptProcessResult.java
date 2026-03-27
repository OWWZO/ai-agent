package org.wwz.ai.domain.agent.reactor.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GptProcessResult {
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
    private boolean encrypted;//是否加密
    private String query;
    private List<String> messages;
    /**
     * 回复包数据类型，用于区分问答结果还是心跳
     * result: 问答结果类型
     * heartbeat: 心跳
     */
    private String packageType = "result";
    /**
     * 失败信息
     */
    private String errorMsg;
}
