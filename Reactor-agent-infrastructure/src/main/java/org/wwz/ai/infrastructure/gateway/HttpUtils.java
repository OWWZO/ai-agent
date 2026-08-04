package org.wwz.ai.infrastructure.gateway;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 历史 HTTP 调用工具。
 *
 * <p>这里同时保留三类兼容契约：失败返回 {@code null} 的旧同步调用、异常传播的
 * 同步调用，以及按行转发 LLM 流的回调调用。新业务应优先使用明确的远程端口，调用
 * 本类时必须根据具体方法区分空响应、网络异常和流式终态。</p>
 */
@Slf4j
public class HttpUtils {

    private HttpUtils() {

    }

    public static String postReq(String url, String params) {
        return postReq(url, null, params);
    }

    public static String postReq(String url, String params, int timeout) {
        return postReq(url, null, params, timeout);
    }

    public static String postReq(String url, Map<String, String> headers, String params) {
        return postReq(url, headers, params, 30000);
    }

    public static String getReq(String url) {
        return httpReq(url, "get", null, null, 30000);
    }

    public static String postReq(String url, Map<String, String> headers, String params, int timeout) {

        // log.info("发送http请求：url:{}, timeout:{}, headers:{}, body:{}", url, timeout, JSON.toJSONString(headers), params);
        // 这是兼容旧调用方的“失败返回 null”接口；新链路应优先使用带明确异常/超时语义的
        // RemoteHttpPort，避免把网络失败和合法空响应混为一谈。
        HttpPost httpPost = new HttpPost(url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).setConnectionRequestTimeout(timeout).setSocketTimeout(timeout).build();
        httpPost.setConfig(requestConfig);
        httpPost.setHeader("Content-Type", "application/json");
        // 传入header信息
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpPost.setHeader(entry.getKey(), entry.getValue());
            }
        }
        CloseableHttpResponse response = null;
        try {
            if (params != null) {
                StringEntity entity = new StringEntity(params, "UTF-8");
                httpPost.setEntity(entity);
            }
            response = HttpClient.INS.getHttpClient().execute(httpPost);
            HttpEntity entity2 = response.getEntity();
            return EntityUtils.toString(entity2, "UTF-8");
        } catch (Exception var16) {
            log.error(var16.getMessage(), var16);
        } finally {
            close(response);
        }
        return null;
    }

    public static String httpReq(String url, String type, Map<String, String> headers, String body, int timeout) {
        // 通用请求适配器只支持当前调用方需要的 HTTP 动词；body 仅对可承载实体的请求设置，
        // 响应统一按字符串交还给上层解析。
        HttpRequestBase httpReq = getHttpRequest(type, url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).setConnectionRequestTimeout(timeout).setSocketTimeout(timeout).build();
        httpReq.setConfig(requestConfig);
        // 传入header信息
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpReq.setHeader(entry.getKey(), entry.getValue());
            }
        }
        // 传入body信息
        if (StringUtils.isNotEmpty(body)) {
            ((HttpPost) httpReq).setEntity(new StringEntity(body, ContentType.create("application/json", "utf-8")));
        }
        CloseableHttpResponse response = null;
        try {
            response = HttpClient.INS.getHttpClient().execute(httpReq);
            return EntityUtils.toString(response.getEntity());
        } catch (Exception e) {
            log.error("http请求失败！", e);
        } finally {
            close(response);
        }
        return null;
    }

    public static String httpReqThrowException(String url, String type, Map<String, String> headers, String body, int timeout) {
        // 与 httpReq 的兼容返回值不同，本方法把空响应和请求异常转成 RuntimeException，供
        // 必须区分失败的同步调用方使用；响应仍在 finally 中关闭。
        log.debug("发送http请求：url:{}, type:{}, headers:{}, body:{}", url, type, JSON.toJSONString(headers), body);
        HttpRequestBase httpReq = getHttpRequest(type, url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).setConnectionRequestTimeout(timeout).setSocketTimeout(timeout).build();
        httpReq.setConfig(requestConfig);
        // 传入header信息
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpReq.setHeader(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        // 传入body信息
        if (StringUtils.isNotEmpty(body)) {
            ((HttpEntityEnclosingRequestBase) httpReq).setEntity(new StringEntity(body, ContentType.create("application/json", "utf-8")));
        }
        CloseableHttpResponse response = null;
        try {
            response = HttpClient.INS.getHttpClient().execute(httpReq);
            String responseEntityStr = EntityUtils.toString(response.getEntity());
            return Objects.requireNonNull(responseEntityStr);
        } catch (NullPointerException e) {
            log.error("httpReq请求失败：url:{}", url, e);
            throw new RuntimeException("返回结果为空！", e);
        } catch (Exception e) {
            log.error("httpReq请求失败：url:{}", url, e);
            throw new RuntimeException(e);
        } finally {
            close(response);
        }
    }

    private static HttpRequestBase getHttpRequest(String type, String url) {
        HttpRequestBase httpReq = null;
        switch (type) {
            case "post":
                httpReq = new HttpPost(url);
                break;
            case "put":
                httpReq = new HttpPut(url);
                break;
            case "delete":
                httpReq = new HttpDelete(url);
                break;
            case "get":
            default:
                httpReq = new HttpGet(url);
        }
        return httpReq;
    }

    @Getter
    public enum HttpClient {
        INS;

        private final CloseableHttpClient httpClient;

        HttpClient() {
            PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
            cm.setMaxTotal(300);
            cm.setDefaultMaxPerRoute(50);
            this.httpClient = HttpClients.custom()
                    // .disableRedirectHandling() 禁止重定向
                    .setConnectionManager(cm)
                    .disableCookieManagement()
                    .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy() {
                        @Override
                        public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                            return 30 * 1000; // 设置空闲连接存活时间为30秒
                        }
                    })
                    .build();
        }

    }

    private static void close(CloseableHttpResponse response) {
        try {
            if (response != null) {
                response.close();
            }
        } catch (IOException e) {
            log.error("关闭CloseableHttpResponse失败！", e);
        }
    }

    public static boolean jdSsrfCheck(URL urlObj) {
        try {
            // 定义请求协议白名单列表
            String[] allowProtocols = new String[]{"http", "https"};
            // 定义请求域名白名单列表，根据业务需求进行配置
            // 定义请求端口白名单列表
            int[] allowPorts = new int[]{80, 443};
            boolean protocolCheck = false;

            // 首先进行协议校验，若协议校验不通过，SSRF校验不通过；当前方法只做协议/端口
            // 粗筛，域名/IP 解析和内网地址策略仍需由更上层的 URL 校验链补齐。
            String protocol = urlObj.getProtocol();
            for (String item : allowProtocols) {
                if (protocol.equals(item)) {
                    protocolCheck = true;
                    break;
                }
            }

            if (!protocolCheck) {
                return false;
            }

            int port = urlObj.getPort();
            if (port == -1) {
                port = 80;
            }
            for (Integer item : allowPorts) {
                if (item == port) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log.error("http链接校验失败，{}", JSON.toJSONString(urlObj), e);
            return true;
        }
    }

    public static void llmPost(String url, Map<String, String> header, String body, String bodyCharset, int timeOut, Consumer<String> consumer) {
        // LLM 流式响应按行转发给 consumer，并用 completed:/error: 前缀保留终态；它不负责
        // 解析 SSE JSON，调用方仍需处理事件边界和 [DONE] 语义。
        HttpPost httpPost = new HttpPost(url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeOut).setConnectionRequestTimeout(timeOut).setSocketTimeout(timeOut).build();
        httpPost.setConfig(requestConfig);
        httpPost.setHeader("Content-Type", "application/json;charset=utf-8");
        if (Objects.nonNull(header)) {
            header.forEach((k, v) -> httpPost.setHeader(k, String.valueOf(v)));
        }
        if (StringUtils.isNotEmpty(body)) {
            if (bodyCharset == null) {
                bodyCharset = Consts.UTF_8.name();
            }
            StringEntity entity = new StringEntity(body, bodyCharset);
            httpPost.setEntity(entity);
        }
        CloseableHttpResponse response = null;
        try {
            response = HttpClient.INS.getHttpClient().execute(httpPost);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (StringUtils.isNotEmpty(line)) {
                    consumer.accept("completed:" + line);
                }
            }
        } catch (Exception e) {
            consumer.accept("error:" + e.getMessage());
        } finally {
            close(response);
        }
    }
}
