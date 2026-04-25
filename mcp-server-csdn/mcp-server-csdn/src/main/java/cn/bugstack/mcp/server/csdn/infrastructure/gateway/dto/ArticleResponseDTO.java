package cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto;

import lombok.Data;

/**
 *csdn后端的请求结果的载体 后续封装成ArticleFunctionResponse
 */
@Data
public class ArticleResponseDTO {
    private Integer code;
    private String traceId;
    private ArticleData data;
    private String msg;

    @Data
    public static class ArticleData {
        private String url;
        private Long id;
        private String qrcode;
        private String title;
        private String description;
    }
}