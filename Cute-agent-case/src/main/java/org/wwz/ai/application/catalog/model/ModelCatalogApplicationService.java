package org.wwz.ai.application.catalog.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.CatalogModel;
import org.wwz.ai.domain.agent.catalog.port.ICatalogModelRuntimePort;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.List;

/**
 * 普通目录中的模型查询与创建用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCatalogApplicationService {

    private final ICatalogModelRuntimePort modelRuntimePort;
    private final CatalogConfigurationApplicationService configurationService;

    public ApplicationResult<List<CatalogModel>> listModels() {
        try {
            List<CatalogModel> models = modelRuntimePort.listUserSelectableModels();
            return ApplicationResult.success(models == null ? List.of() : models);
        } catch (Exception e) {
            log.error("查询模型目录失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<Boolean> createModel(CatalogModelCreateCommand command) {
        try {
            return ApplicationResult.success(configurationService.createModel(command));
        } catch (IllegalArgumentException e) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage(), false);
        } catch (Exception e) {
            log.error("新增模型失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }
}
