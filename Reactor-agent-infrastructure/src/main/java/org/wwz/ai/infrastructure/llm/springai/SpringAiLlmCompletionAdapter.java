package org.wwz.ai.infrastructure.llm.springai;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatModelResolver;
import org.wwz.ai.domain.agent.runtime.llm.LlmCompletionPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequest;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.domain.agent.runtime.llm.LlmStreamEvent;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring AI completion adapter. Domain only sees LlmCompletionPort.
 */
@Slf4j
@Component
public class SpringAiLlmCompletionAdapter implements LlmCompletionPort, LlmChatModelResolver {

    private final SpringAiMessageMapper messageMapper;
    private final SpringAiOptionsMapper optionsMapper;
    private final SpringAiResponseMapper responseMapper;
    private final Map<String, OpenAiChatModel> modelCache = new ConcurrentHashMap<>();

    public SpringAiLlmCompletionAdapter(SpringAiMessageMapper messageMapper,
                                        SpringAiOptionsMapper optionsMapper,
                                        SpringAiResponseMapper responseMapper) {
        this.messageMapper = messageMapper;
        this.optionsMapper = optionsMapper;
        this.responseMapper = responseMapper;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        ChatResponse response = resolve(request.getSettings()).call(buildPrompt(request));
        return responseMapper.toResponse(response);
    }

    @Override
    public StreamCall stream(LlmRequest request) {
        Flux<ChatResponse> stream = resolve(request.getSettings()).stream(buildPrompt(request));
        return new SpringAiStreamCall(stream, responseMapper);
    }

    @Override
    public void invalidateAll() {
        modelCache.clear();
    }

    private Prompt buildPrompt(LlmRequest request) {
        return new Prompt(messageMapper.convert(request.getMessages()), optionsMapper.map(request));
    }

    private OpenAiChatModel resolve(LLMSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("LLMSettings must not be null");
        }
        return modelCache.computeIfAbsent(buildCacheKey(settings), ignored -> createModel(settings));
    }

    private String buildCacheKey(LLMSettings settings) {
        return String.join("::",
                StringUtils.defaultString(settings.getModel()),
                StringUtils.defaultString(settings.getBaseUrl()),
                StringUtils.defaultString(settings.getInterfaceUrl()),
                StringUtils.defaultString(settings.getApiKey()),
                JSON.toJSONString(settings.getExtParams()));
    }

    private OpenAiChatModel createModel(LLMSettings settings) {
        String baseUrl = StringUtils.trimToEmpty(settings.getBaseUrl());
        if (StringUtils.isBlank(baseUrl)) {
            throw new IllegalArgumentException("Base URL is not configured for model: " + settings.getModel());
        }
        String completionsPath = StringUtils.isNotBlank(settings.getInterfaceUrl())
                ? settings.getInterfaceUrl().trim()
                : "/v1/chat/completions";
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(StringUtils.defaultString(settings.getApiKey()))
                .completionsPath(completionsPath)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(settings.getModel()).build())
                .build();
        log.info("初始化 Spring AI ChatModel: model={}, baseUrl={}, completionsPath={}",
                settings.getModel(), baseUrl, completionsPath);
        return chatModel;
    }

    private static final class SpringAiStreamCall implements StreamCall {
        private final Flux<ChatResponse> stream;
        private final SpringAiResponseMapper responseMapper;
        private final AtomicReference<Disposable> subscription = new AtomicReference<>();

        private SpringAiStreamCall(Flux<ChatResponse> stream, SpringAiResponseMapper responseMapper) {
            this.stream = stream;
            this.responseMapper = responseMapper;
        }

        @Override
        public void subscribe(java.util.function.Consumer<LlmStreamEvent> onEvent,
                              java.util.function.Consumer<Throwable> onError,
                              Runnable onComplete) {
            Disposable disposable = stream.subscribe(
                    response -> onEvent.accept(responseMapper.toStreamEvent(response)),
                    onError,
                    onComplete);
            subscription.set(disposable);
            if (disposable.isDisposed()) {
                subscription.set(null);
            }
        }

        @Override
        public void cancel() {
            Disposable disposable = subscription.getAndSet(null);
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        }
    }
}
