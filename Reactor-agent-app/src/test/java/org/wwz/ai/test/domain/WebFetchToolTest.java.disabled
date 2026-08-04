package org.wwz.ai.test.domain;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpResponse;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.WebFetchTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebFetch（cc-haha 风格）回归：url+prompt、同主机 redirect、跨域 redirect 提示。
 */
public class WebFetchToolTest {

    @Test
    public void shouldFetchHtmlAndRequirePrompt() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/page", exchange -> {
            byte[] body = "<html><body><h1>Hello</h1><p>World content about Spring AI.</p></body></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            String pageUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
            // 本地 http 会被升级为 https 导致失败；此处用 mock port 直接返回 HTML
            RemoteHttpPort httpPort = request -> {
                throw new UnsupportedOperationException("use detailed");
            };
            RemoteHttpPort detailedPort = new RemoteHttpPort() {
                @Override
                public String execute(RemoteHttpRequest request) {
                    return "<html><body><h1>Hello</h1><p>World content about Spring AI.</p></body></html>";
                }

                @Override
                public RemoteHttpResponse executeDetailed(RemoteHttpRequest request) {
                    Assert.assertEquals(Boolean.FALSE, request.getFollowRedirects());
                    Assert.assertTrue(request.getUrl().startsWith("https://") || request.getUrl().startsWith("http://"));
                    return RemoteHttpResponse.builder()
                            .statusCode(200)
                            .statusText("OK")
                            .headers(Map.of("Content-Type", "text/html"))
                            .body("<html><body><h1>Hello</h1><p>World content about Spring AI.</p></body></html>")
                            .finalUrl(request.getUrl())
                            .build();
                }
            };

            // LLM 路径会真实调用模型；本测只验证参数校验与无 prompt 失败，以及 redirect 路径
            WebFetchTool tool = new WebFetchTool();
            tool.setAgentContext(AgentContext.builder()
                    .requestId("req-wf-1")
                    .sessionId("session-wf-1")
                    .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig(), detailedPort))
                    .build());

            ToolResultPayload missingPrompt = (ToolResultPayload) tool.execute(Map.of("url", "https://example.com/a"));
            Assert.assertTrue(Boolean.TRUE.equals(missingPrompt.getFailed()));
            Assert.assertTrue(missingPrompt.getLlmObservation().contains("prompt"));

            ToolResultPayload missingUrl = (ToolResultPayload) tool.execute(Map.of("prompt", "summarize"));
            Assert.assertTrue(Boolean.TRUE.equals(missingUrl.getFailed()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldReportCrossHostRedirect() {
        AtomicInteger calls = new AtomicInteger();
        RemoteHttpPort httpPort = new RemoteHttpPort() {
            @Override
            public String execute(RemoteHttpRequest request) {
                return "";
            }

            @Override
            public RemoteHttpResponse executeDetailed(RemoteHttpRequest request) {
                calls.incrementAndGet();
                return RemoteHttpResponse.builder()
                        .statusCode(302)
                        .statusText("Found")
                        .headers(Map.of("Location", "https://other.example.com/target"))
                        .body("")
                        .finalUrl(request.getUrl())
                        .build();
            }
        };

        AgentContext context = AgentContext.builder()
                .requestId("req-wf-redirect")
                .sessionId("session-wf-redirect")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig(), httpPort))
                .build();
        WebFetchTool tool = new WebFetchTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "url", "https://example.com/start",
                "prompt", "extract title"
        ));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmObservation().contains("REDIRECT DETECTED"));
        Assert.assertTrue(payload.getLlmObservation().contains("https://other.example.com/target"));
        Assert.assertTrue(payload.getLlmObservation().contains("extract title"));
        Assert.assertEquals(1, calls.get());
    }

    @Test
    public void shouldFollowSameHostWwwRedirect() {
        AtomicInteger calls = new AtomicInteger();
        RemoteHttpPort httpPort = new RemoteHttpPort() {
            @Override
            public String execute(RemoteHttpRequest request) {
                return "";
            }

            @Override
            public RemoteHttpResponse executeDetailed(RemoteHttpRequest request) throws IOException {
                int n = calls.incrementAndGet();
                if (n == 1) {
                    return RemoteHttpResponse.builder()
                            .statusCode(301)
                            .statusText("Moved Permanently")
                            .headers(Map.of("Location", "https://www.example.com/page"))
                            .body("")
                            .finalUrl(request.getUrl())
                            .build();
                }
                return RemoteHttpResponse.builder()
                        .statusCode(200)
                        .statusText("OK")
                        .headers(Map.of("Content-Type", "text/plain"))
                        .body("Plain page body for extraction.")
                        .finalUrl(request.getUrl())
                        .build();
            }
        };

        // 同主机 redirect 后会走 LLM；无真实 key 时会失败，至少验证会二次请求
        AgentContext context = AgentContext.builder()
                .requestId("req-wf-www")
                .sessionId("session-wf-www")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig(), httpPort))
                .build();
        WebFetchTool tool = new WebFetchTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "url", "https://example.com/page",
                "prompt", "summarize"
        ));
        // 第二次请求拿到正文后 LLM 可能失败（无模型配置），但至少发生了 2 次 fetch
        Assert.assertTrue(calls.get() >= 2);
        Assert.assertNotNull(payload);
    }

    @Test
    public void permittedRedirectHelper() {
        Assert.assertTrue(WebFetchTool.isPermittedRedirect(
                "https://example.com/a", "https://www.example.com/b"));
        Assert.assertTrue(WebFetchTool.isPermittedRedirect(
                "https://www.example.com/a", "https://example.com/b"));
        Assert.assertFalse(WebFetchTool.isPermittedRedirect(
                "https://example.com/a", "https://evil.com/b"));
    }
}
