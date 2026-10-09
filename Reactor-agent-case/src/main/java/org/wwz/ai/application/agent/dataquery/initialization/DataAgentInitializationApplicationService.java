package org.wwz.ai.application.agent.dataquery.initialization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.rag.model.config.ColumnValueRecallSettings;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.model.config.VectorRecallSettings;
import org.wwz.ai.domain.agent.rag.port.DataAgentInitializerPort;
import org.wwz.ai.domain.agent.rag.service.ChatModelInfoService;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;

import java.util.Optional;

/**
 * Coordinates startup preparation for optional DataAgent capabilities and metadata.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAgentInitializationApplicationService {

    private final DataQuerySettings dataQuerySettings;
    private final DataAgentInitializerPort dataAgentInitializerPort;
    private final ChatModelInfoService chatModelInfoService;
    private final Optional<SkillRegistry> skillRegistry;

    public void initialize(int embeddingDimension) throws Exception {
        boolean forceRefresh = Boolean.TRUE.equals(dataQuerySettings.getForceRefresh());
        prepareVectorCapability(forceRefresh, embeddingDimension);
        prepareColumnValueCapability(forceRefresh);
        refreshModelMetadata(forceRefresh);
        refreshSkills();
    }

    private void prepareVectorCapability(boolean forceRefresh, int embeddingDimension) throws Exception {
        VectorRecallSettings config = dataQuerySettings.getQdrantConfig();
        if (!Boolean.TRUE.equals(config.getEnable())) {
            return;
        }
        try {
            dataAgentInitializerPort.initializeVectorIndex(forceRefresh, embeddingDimension);
            log.info("qdrant collection init success");
        } catch (Exception e) {
            handleCapabilityFailure("qdrant", forceRefresh, e);
            config.setEnable(false);
            if (forceRefresh) {
                throw e;
            }
        }
    }

    private void prepareColumnValueCapability(boolean forceRefresh) throws Exception {
        ColumnValueRecallSettings config = dataQuerySettings.getEsConfig();
        if (!Boolean.TRUE.equals(config.getEnable())) {
            return;
        }
        try {
            dataAgentInitializerPort.initializeColumnValueIndex(forceRefresh);
            log.info("column value es index init success");
        } catch (Exception e) {
            handleCapabilityFailure("es", forceRefresh, e);
            config.setEnable(false);
            if (forceRefresh) {
                throw e;
            }
        }
    }

    private void refreshModelMetadata(boolean forceRefresh) throws Exception {
        try {
            if (forceRefresh) {
                chatModelInfoService.refreshModelInfo(dataQuerySettings);
            } else {
                chatModelInfoService.initModelInfo(dataQuerySettings);
            }
        } catch (Exception e) {
            if (forceRefresh) {
                log.error("强制刷新失败，终止启动流程", e);
                throw e;
            }
            log.error("Failed to init model info", e);
        }
    }

    private void refreshSkills() {
        skillRegistry.ifPresent(registry -> {
            try {
                registry.refresh();
                log.info("skill registry init success, loaded skills={}", registry.listSkills().size());
            } catch (Exception e) {
                log.error("Failed to init skill registry", e);
            }
        });
    }

    private void handleCapabilityFailure(String capability, boolean forceRefresh, Exception e) {
        if (forceRefresh) {
            log.error("{} capability force-refresh failed", capability, e);
        } else {
            log.warn("{} capability degraded and disabled: {}", capability, e.getMessage(), e);
        }
    }
}
