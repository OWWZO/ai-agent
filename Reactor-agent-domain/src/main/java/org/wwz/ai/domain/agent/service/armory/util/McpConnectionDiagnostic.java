package org.wwz.ai.domain.agent.service.armory.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * MCP 连接诊断工具类
 *
 * <p>
 * 提供 MCP 服务连接诊断功能，帮助排查连接问题。
 * </p>
 */
@Slf4j
@Component
public class McpConnectionDiagnostic {

    /**
     * 检查 SSE MCP 服务是否可访问
     *
     * @param baseUri    基础地址，例如：http://localhost:8080
     * @param sseEndpoint SSE端点，例如：/sse 或 /api/sse
     * @param timeoutMs  超时时间（毫秒），默认 5000ms
     * @return 诊断结果，包含是否可访问、响应码、错误信息等
     */
    public DiagnosticResult checkSseConnection(String baseUri, String sseEndpoint, int timeoutMs) {
        DiagnosticResult result = new DiagnosticResult();
        result.setServiceType("SSE");
        result.setBaseUri(baseUri);
        result.setEndpoint(sseEndpoint);
        result.setTimeoutMs(timeoutMs);

        // 在方法级别声明 fullUrl，确保在 catch 块中可访问
        String fullUrl = null;
        
        try {
            // 构建完整URL
            fullUrl = buildFullUrl(baseUri, sseEndpoint);
            result.setFullUrl(fullUrl);

            log.info("检查 SSE MCP 服务连接: {}", fullUrl);

            // 尝试连接
            URL url = new URL(fullUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);

            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                httpConnection.setRequestMethod("GET");
                httpConnection.setInstanceFollowRedirects(false);

                try {
                    int responseCode = httpConnection.getResponseCode();
                    result.setResponseCode(responseCode);
                    result.setAccessible(true);

                    if (responseCode >= 200 && responseCode < 300) {
                        result.setStatus("✅ 服务可访问");
                        log.info("SSE MCP 服务连接检查成功: {}, 响应码: {}", fullUrl, responseCode);
                    } else {
                        result.setStatus("⚠️ 服务响应异常");
                        result.setErrorMessage("HTTP响应码: " + responseCode);
                        log.warn("SSE MCP 服务响应异常: {}, 响应码: {}", fullUrl, responseCode);
                    }
                } finally {
                    httpConnection.disconnect();
                }
            } else {
                // 非HTTP连接，尝试基本连接
                connection.connect();
                result.setAccessible(true);
                result.setStatus("✅ 服务可访问（非HTTP协议）");
                log.info("SSE MCP 服务连接检查成功: {}", fullUrl);
            }

        } catch (java.net.ConnectException e) {
            result.setAccessible(false);
            result.setStatus("❌ 连接失败");
            result.setErrorMessage("无法连接到服务: " + e.getMessage());
            result.setSuggestion("请检查：1) 服务是否已启动 2) 地址和端口是否正确 3) 防火墙设置");
            log.error("SSE MCP 服务连接失败: baseUri={}, sseEndpoint={}, fullUrl={}", 
                    baseUri, sseEndpoint, fullUrl != null ? fullUrl : "N/A", e);

        } catch (java.net.SocketTimeoutException e) {
            result.setAccessible(false);
            result.setStatus("❌ 连接超时");
            result.setErrorMessage("连接超时: " + e.getMessage());
            result.setSuggestion("请检查：1) 服务是否正常运行 2) 网络是否稳定 3) 超时时间是否足够");
            log.error("SSE MCP 服务连接超时: baseUri={}, sseEndpoint={}, fullUrl={}", 
                    baseUri, sseEndpoint, fullUrl != null ? fullUrl : "N/A", e);

        } catch (java.net.UnknownHostException e) {
            result.setAccessible(false);
            result.setStatus("❌ 主机不可达");
            result.setErrorMessage("无法解析主机名: " + e.getMessage());
            result.setSuggestion("请检查：1) 主机名是否正确 2) DNS配置 3) 网络连接");
            log.error("SSE MCP 服务主机不可达: baseUri={}, sseEndpoint={}, fullUrl={}", 
                    baseUri, sseEndpoint, fullUrl != null ? fullUrl : "N/A", e);

        } catch (IOException e) {
            result.setAccessible(false);
            result.setStatus("❌ IO异常");
            result.setErrorMessage("IO错误: " + e.getMessage());
            result.setSuggestion("请检查：1) 服务状态 2) 网络连接 3) 服务配置");
            log.error("SSE MCP 服务IO异常: baseUri={}, sseEndpoint={}, fullUrl={}", 
                    baseUri, sseEndpoint, fullUrl != null ? fullUrl : "N/A", e);

        } catch (Exception e) {
            result.setAccessible(false);
            result.setStatus("❌ 未知错误");
            result.setErrorMessage("错误: " + e.getMessage());
            log.error("SSE MCP 服务连接检查异常: baseUri={}, sseEndpoint={}, fullUrl={}", 
                    baseUri, sseEndpoint, fullUrl != null ? fullUrl : "N/A", e);
        }
        
        // 如果 fullUrl 未设置，尝试设置一个默认值
        if (result.getFullUrl() == null && fullUrl != null) {
            result.setFullUrl(fullUrl);
        } else if (result.getFullUrl() == null) {
            // 如果构建 URL 失败，至少设置一个基础信息
            result.setFullUrl(baseUri + (sseEndpoint != null ? sseEndpoint : "/sse"));
        }

        return result;
    }

    /**
     * 构建完整的URL
     */
    private String buildFullUrl(String baseUri, String sseEndpoint) {
        if (baseUri == null || baseUri.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUri 不能为空");
        }

        // 确保 baseUri 不以 / 结尾
        String cleanBaseUri = baseUri.trim();
        if (cleanBaseUri.endsWith("/")) {
            cleanBaseUri = cleanBaseUri.substring(0, cleanBaseUri.length() - 1);
        }

        // 确保 sseEndpoint 以 / 开头
        String cleanEndpoint = sseEndpoint != null ? sseEndpoint.trim() : "/sse";
        if (!cleanEndpoint.startsWith("/")) {
            cleanEndpoint = "/" + cleanEndpoint;
        }

        return cleanBaseUri + cleanEndpoint;
    }

    /**
     * 诊断结果类
     */
    public static class DiagnosticResult {
        private String serviceType;
        private String baseUri;
        private String endpoint;
        private String fullUrl;
        private boolean accessible;
        private String status;
        private String errorMessage;
        private String suggestion;
        private Integer responseCode;
        private int timeoutMs;

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(String serviceType) {
            this.serviceType = serviceType;
        }

        public String getBaseUri() {
            return baseUri;
        }

        public void setBaseUri(String baseUri) {
            this.baseUri = baseUri;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getFullUrl() {
            return fullUrl;
        }

        public void setFullUrl(String fullUrl) {
            this.fullUrl = fullUrl;
        }

        public boolean isAccessible() {
            return accessible;
        }

        public void setAccessible(boolean accessible) {
            this.accessible = accessible;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }

        public Integer getResponseCode() {
            return responseCode;
        }

        public void setResponseCode(Integer responseCode) {
            this.responseCode = responseCode;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("诊断结果:\n");
            sb.append("  服务类型: ").append(serviceType).append("\n");
            sb.append("  基础地址: ").append(baseUri).append("\n");
            sb.append("  端点: ").append(endpoint).append("\n");
            sb.append("  完整URL: ").append(fullUrl).append("\n");
            sb.append("  可访问: ").append(accessible ? "是" : "否").append("\n");
            sb.append("  状态: ").append(status).append("\n");
            if (responseCode != null) {
                sb.append("  响应码: ").append(responseCode).append("\n");
            }
            if (errorMessage != null) {
                sb.append("  错误信息: ").append(errorMessage).append("\n");
            }
            if (suggestion != null) {
                sb.append("  建议: ").append(suggestion).append("\n");
            }
            return sb.toString();
        }
    }
}
