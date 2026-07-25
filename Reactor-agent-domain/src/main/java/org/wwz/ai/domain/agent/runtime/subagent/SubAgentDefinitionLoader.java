package org.wwz.ai.domain.agent.runtime.subagent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.repository.ISubAgentDefinitionRepository;

import java.util.Collections;
import java.util.List;

/**
 * 从仓储加载启用中的子 Agent 定义并写入 {@link SubAgentRegistry}。
 * 仓储缺失或查询失败时保留内置类型，不影响主链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubAgentDefinitionLoader {

    private final SubAgentRegistry registry;
    private final ISubAgentDefinitionRepository repository;

    /**
     * 重新加载可配置子 Agent（内置不变）。
     *
     * @return 成功写入的配置条数
     */
    public int reload() {
        if (repository == null) {
            log.warn("ISubAgentDefinitionRepository 未注入，跳过可配置 SubAgent 加载");
            registry.replaceConfigured(Collections.emptyList());
            return 0;
        }
        try {
            List<SubAgentDefinition> enabled = repository.listEnabled();
            if (enabled == null) {
                enabled = Collections.emptyList();
            }
            registry.replaceConfigured(enabled);
            log.info("SubAgent 可配置定义已加载 count={} types={}",
                    registry.configuredCount(), registry.listTypeNames());
            return registry.configuredCount();
        } catch (Exception e) {
            log.error("加载可配置 SubAgent 失败，保留当前内置类型", e);
            return -1;
        }
    }
}
