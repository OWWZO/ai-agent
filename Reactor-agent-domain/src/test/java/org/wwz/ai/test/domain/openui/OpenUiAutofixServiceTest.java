package org.wwz.ai.test.domain.openui;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpResponse;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiAutofixService;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixResult;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixTurn;

import java.util.ArrayList;
import java.util.List;

/**
 * Autofix 服务：请求组装、状态映射、上下文裁剪、失败降级。
 */
public class OpenUiAutofixServiceTest {

    private static final String URL = "https://api.thesys.dev/v1/autofix";

    private RemoteHttpPort http;
    private OpenUiAutofixService service;

    @Before
    public void setUp() {
        http = Mockito.mock(RemoteHttpPort.class);
        service = new OpenUiAutofixService(http, true, "test-key", URL, 30L);
    }

    @Test
    public void shouldReportDisabledWithoutApiKey() {
        OpenUiAutofixService withoutKey = new OpenUiAutofixService(http, true, "", URL, 30L);
        Assert.assertFalse(withoutKey.isAvailable());
        Assert.assertEquals(OpenUiFixResult.DISABLED, withoutKey.fix("root = Col([])", List.of()).status());
        Mockito.verifyNoInteractions(http);
    }

    @Test
    public void shouldSendConfigBlockFirstAndProgramLast() throws Exception {
        Mockito.when(http.executeDetailed(Mockito.any())).thenReturn(response(200, FIXED_BODY));

        service.fix("root = Col([a])\na = FancyThing(\"x\")", List.of(new OpenUiFixTurn("user", "做个卡片")));

        ArgumentCaptor<RemoteHttpRequest> captor = ArgumentCaptor.forClass(RemoteHttpRequest.class);
        Mockito.verify(http).executeDetailed(captor.capture());
        RemoteHttpRequest request = captor.getValue();

        Assert.assertEquals("POST", request.getMethod());
        Assert.assertEquals(URL, request.getUrl());
        Assert.assertEquals("Bearer test-key", request.getHeaders().get("Authorization"));

        String body = request.getBody();
        // 首条必须是带 config block 的 system turn，否则 API 不认识组件库
        Assert.assertTrue(body.contains("]]>openui:config"));
        Assert.assertTrue(body.indexOf("openui:config") < body.indexOf("做个卡片"));
        Assert.assertTrue(body.indexOf("做个卡片") < body.indexOf("FancyThing"));
    }

    @Test
    public void shouldReturnFixedContentAndErrors() throws Exception {
        Mockito.when(http.executeDetailed(Mockito.any())).thenReturn(response(200, FIXED_BODY));

        OpenUiFixResult result = service.fix("root = Col([a])\na = FancyThing(\"x\")", List.of());

        Assert.assertEquals(OpenUiFixResult.FIXED, result.status());
        Assert.assertTrue(result.hasRepairedContent());
        Assert.assertEquals("root = Col([head, a])\nhead = Title(\"Q3\", \"xl\")\na = Text(\"x\")", result.content());
        Assert.assertEquals(1, result.errors().size());
        Assert.assertEquals("unknown-component", result.errors().get(0).code());
    }

    @Test
    public void shouldMapFixFailedToErrors() throws Exception {
        Mockito.when(http.executeDetailed(Mockito.any())).thenReturn(response(200, """
                {"choices":[{"message":{"role":"assistant","content":null}}],
                 "fix_summary":{"status":"fix_failed","fixed_errors":[],
                   "unfixed_errors":[{"code":"missing-root","message":"no valid root statement"}]}}
                """));

        OpenUiFixResult result = service.fix("a = Title(\"x\")", List.of());

        Assert.assertEquals(OpenUiFixResult.FIX_FAILED, result.status());
        Assert.assertNull(result.content());
        Assert.assertFalse(result.hasRepairedContent());
        Assert.assertEquals("missing-root", result.errors().get(0).code());
    }

    @Test
    public void shouldDegradeOnHttpError() throws Exception {
        Mockito.when(http.executeDetailed(Mockito.any())).thenReturn(response(401, "{\"error\":\"invalid key\"}"));

        OpenUiFixResult result = service.fix("root = Col([])", List.of());

        Assert.assertEquals(OpenUiFixResult.ERROR, result.status());
        Assert.assertFalse(result.hasRepairedContent());
        Assert.assertTrue(result.message().contains("401"));
    }

    @Test
    public void shouldDegradeOnNetworkFailure() throws Exception {
        Mockito.when(http.executeDetailed(Mockito.any())).thenThrow(new java.io.IOException("boom"));

        OpenUiFixResult result = service.fix("root = Col([])", List.of());

        Assert.assertEquals(OpenUiFixResult.ERROR, result.status());
        Assert.assertTrue(result.message().contains("boom"));
    }

    @Test
    public void shouldRejectOversizedProgram() {
        OpenUiFixResult result = service.fix("x".repeat(OpenUiAutofixService.MAX_PROGRAM_CHARS + 1), List.of());

        Assert.assertEquals(OpenUiFixResult.ERROR, result.status());
        Mockito.verifyNoInteractions(http);
    }

    @Test
    public void shouldKeepOnlyRecentContextWithinLimits() throws Exception {
        Mockito.when(http.executeDetailed(Mockito.any())).thenReturn(response(200, FIXED_BODY));

        List<OpenUiFixTurn> turns = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            turns.add(new OpenUiFixTurn("user", "turn-" + i));
        }
        turns.add(new OpenUiFixTurn("tool", "ignored-role"));

        service.fix("root = Col([])", turns);

        ArgumentCaptor<RemoteHttpRequest> captor = ArgumentCaptor.forClass(RemoteHttpRequest.class);
        Mockito.verify(http).executeDetailed(captor.capture());
        String body = captor.getValue().getBody();

        // 保留最近 20 轮：turn-20 在，turn-19 不在；非白名单角色被丢弃
        Assert.assertTrue(body.contains("turn-20"));
        Assert.assertTrue(body.contains("turn-39"));
        Assert.assertFalse(body.contains("turn-19\""));
        Assert.assertFalse(body.contains("ignored-role"));
        Assert.assertEquals(OpenUiAutofixService.MAX_CONTEXT_TURNS,
                body.split("\"role\":\"user\"", -1).length - 1);
    }

    private static RemoteHttpResponse response(int status, String body) {
        return RemoteHttpResponse.builder()
                .statusCode(status)
                .statusText(status == 200 ? "OK" : "ERROR")
                .headers(java.util.Map.of())
                .body(body)
                .finalUrl(URL)
                .build();
    }

    private static final String FIXED_BODY = """
            {"id":"fix_x","object":"chat.completion","model":"openui/autofix",
             "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant",
               "content":"root = Col([head, a])\\nhead = Title(\\"Q3\\", \\"xl\\")\\na = Text(\\"x\\")"}}],
             "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15},
             "fix_summary":{"status":"fixed","fixed_errors":[
               {"code":"unknown-component","message":"Unknown component \\"FancyThing\\"","statementId":"a"}],
               "unfixed_errors":[]}}
            """;
}
