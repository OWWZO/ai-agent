package org.wwz.ai.test.domain.openui;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmCompletionPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequest;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiAutofixService;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiDiagnosis;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixError;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiIntentFixResult;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiRepairIntent;

import java.util.List;
import java.util.Optional;

/**
 * 意图修复链路（plans/038 v4）：开关、模型装配、块解析透传、prompt 组装。
 */
public class OpenUiIntentRepairTest {

    private static final String BROKEN_PROGRAM = "$display = 0\nt = Text($display + \"\", \"3xl\")\nroot = Col([t])";

    private RemoteHttpPort http;
    private LlmCompletionPort completionPort;
    private LlmModelCatalog modelCatalog;

    private OpenUiAutofixService newService(boolean intentEnabled) {
        return new OpenUiAutofixService(http, completionPort, modelCatalog,
                false, intentEnabled, "", "https://unused", 30L);
    }

    private static OpenUiDiagnosis diagnosis() {
        return new OpenUiDiagnosis(false, List.of(
                new OpenUiFixError("type-mismatch", "field \"/size\" expects one of [\"xs\",\"sm\",\"md\",\"lg\"] but got \"3xl\"", "t")));
    }

    @Before
    public void setUp() {
        http = Mockito.mock(RemoteHttpPort.class);
        completionPort = Mockito.mock(LlmCompletionPort.class);
        modelCatalog = Mockito.mock(LlmModelCatalog.class);
    }

    private static LLMSettings settings() {
        return LLMSettings.builder()
                .model("test-model")
                .baseUrl("https://llm.example")
                .apiKey("sk-test")
                .interfaceUrl("/chat/completions")
                .maxTokens(16384)
                .temperature(0.0)
                .functionCallType("function_call")
                .build();
    }

    @Test
    public void shouldBeDisabledWhenFlagOff() {
        OpenUiIntentFixResult result = newService(false).fixWithIntent(BROKEN_PROGRAM, diagnosis(), null);
        Assert.assertEquals(OpenUiIntentFixResult.DISABLED, result.status());
        Mockito.verifyNoInteractions(completionPort, modelCatalog);
    }

    @Test
    public void shouldErrorWhenCatalogHasNoBinding() {
        Mockito.when(modelCatalog.resolve(Mockito.anyString())).thenReturn(Optional.empty());
        OpenUiIntentFixResult result = newService(true).fixWithIntent(BROKEN_PROGRAM, diagnosis(), null);
        Assert.assertEquals(OpenUiIntentFixResult.ERROR, result.status());
        Mockito.verifyNoInteractions(completionPort);
    }

    @Test
    public void shouldReturnParsedBlocksAndPromptCarriesProgramAndErrors() {
        Mockito.when(modelCatalog.resolve(Mockito.anyString())).thenReturn(Optional.of(settings()));
        Mockito.when(completionPort.complete(Mockito.any())).thenReturn(LlmResponse.builder()
                .content("""
                        <<<<<<< SEARCH
                        t = Text($display + "", "3xl")
                        =======
                        t = Text($display + "", "lg")
                        >>>>>>> REPLACE
                        """)
                .build());

        OpenUiIntentFixResult result = newService(true).fixWithIntent(BROKEN_PROGRAM, diagnosis(), null);

        Assert.assertEquals(OpenUiIntentFixResult.INTENT_OK, result.status());
        Assert.assertEquals(1, result.blocks().size());
        Assert.assertEquals(new OpenUiRepairIntent("t = Text($display + \"\", \"3xl\")",
                "t = Text($display + \"\", \"lg\")"), result.blocks().get(0));
        Assert.assertEquals("test-model", result.model());

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        Mockito.verify(completionPort).complete(captor.capture());
        LlmRequest request = captor.getValue();
        Assert.assertEquals(0.0, request.getTemperature(), 1e-9);
        Assert.assertEquals(2, request.getMessages().size());
        String userContent = request.getMessages().get(1).getContent();
        // 程序原文、诊断错误、格式要求、组件目录都必须在 prompt 里
        Assert.assertTrue(userContent.contains(BROKEN_PROGRAM));
        Assert.assertTrue(userContent.contains("type-mismatch"));
        Assert.assertTrue(userContent.contains("SEARCH"));
        String systemContent = request.getMessages().get(0).getContent();
        Assert.assertTrue(systemContent.contains("openui-lang") || systemContent.length() > 10_000);
    }

    @Test
    public void shouldReturnIntentEmptyWhenModelGivesNoBlocks() {
        Mockito.when(modelCatalog.resolve(Mockito.anyString())).thenReturn(Optional.of(settings()));
        Mockito.when(completionPort.complete(Mockito.any())).thenReturn(LlmResponse.builder()
                .content("抱歉，我无法修复这段程序。").build());

        OpenUiIntentFixResult result = newService(true).fixWithIntent(BROKEN_PROGRAM, diagnosis(), null);
        Assert.assertEquals(OpenUiIntentFixResult.INTENT_EMPTY, result.status());
        Assert.assertTrue(result.blocks().isEmpty());
    }

    @Test
    public void shouldCarryFeedbackNoteIntoPrompt() {
        Mockito.when(modelCatalog.resolve(Mockito.anyString())).thenReturn(Optional.of(settings()));
        Mockito.when(completionPort.complete(Mockito.any())).thenReturn(LlmResponse.builder()
                .content("""
                        <<<<<<< SEARCH
                        "3xl"
                        =======
                        "lg"
                        >>>>>>> REPLACE
                        """)
                .build());

        OpenUiIntentFixResult result = newService(true).fixWithIntent(
                BROKEN_PROGRAM, diagnosis(), "- 块 #0 被作废（not_unique），SEARCH 开头：\"3xl\"");

        Assert.assertEquals(OpenUiIntentFixResult.INTENT_OK, result.status());
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        Mockito.verify(completionPort).complete(captor.capture());
        String userContent = captor.getValue().getMessages().get(1).getContent();
        Assert.assertTrue(userContent.contains("上一轮失败反馈"));
        Assert.assertTrue(userContent.contains("not_unique"));
    }

    @Test
    public void shouldDegradeOnCompletionFailure() {
        Mockito.when(modelCatalog.resolve(Mockito.anyString())).thenReturn(Optional.of(settings()));
        Mockito.when(completionPort.complete(Mockito.any())).thenThrow(new RuntimeException("boom"));

        OpenUiIntentFixResult result = newService(true).fixWithIntent(BROKEN_PROGRAM, diagnosis(), null);
        Assert.assertEquals(OpenUiIntentFixResult.ERROR, result.status());
        Assert.assertTrue(result.message().contains("boom"));
    }
}
