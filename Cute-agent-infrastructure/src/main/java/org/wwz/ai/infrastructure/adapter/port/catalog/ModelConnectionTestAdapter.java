package org.wwz.ai.infrastructure.adapter.port.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.catalog.model.ModelConnectionTestResult;
import org.wwz.ai.domain.agent.catalog.port.IModelConnectionTestPort;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.LlmCompletionPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmMessage;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequest;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.List;

/** 模型连接探测技术适配器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConnectionTestAdapter implements IModelConnectionTestPort {
    private final ObjectProvider<LlmModelCatalog> catalogProvider;
    private final ObjectProvider<LlmCompletionPort> completionPortProvider;

    public ModelConnectionTestResult test(String modelReference) {
        LlmModelCatalog catalog = catalogProvider.getIfAvailable();
        LlmCompletionPort completionPort = completionPortProvider.getIfAvailable();
        if (catalog == null || completionPort == null) return new ModelConnectionTestResult(false, 0, "模型目录未装配");
        try {
            LLMSettings settings = catalog.resolve(modelReference).orElseThrow(() -> new IllegalStateException(
                    "找不到可用模型配置：" + modelReference + "（需启用且配齐 API Key）"));
            long start = System.currentTimeMillis();
            completionPort.complete(LlmRequest.builder()
                    .model(settings.getModel())
                    .settings(settings)
                    .temperature(settings.getTemperature())
                    .messages(List.of(LlmMessage.builder().role(RoleType.USER).content("ping").build()))
                    .build());
            return new ModelConnectionTestResult(true, System.currentTimeMillis() - start, "连接成功");
        } catch (Exception e) {
            log.warn("模型连接测试失败 modelReference={}", modelReference, e);
            return new ModelConnectionTestResult(false, 0, "连接失败: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    public ModelConnectionTestResult testByRecordId(Long id) {
        return test(LlmModelCatalog.BINDING_REF_PREFIX + id);
    }
}
