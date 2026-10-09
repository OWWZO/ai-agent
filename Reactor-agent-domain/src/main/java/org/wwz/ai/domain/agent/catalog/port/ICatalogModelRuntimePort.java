package org.wwz.ai.domain.agent.catalog.port;

import org.wwz.ai.domain.agent.catalog.model.CatalogModel;

import java.util.List;

/**
 * 模型目录运行时端口，承接目录读取和管理写入后的缓存失效。
 */
public interface ICatalogModelRuntimePort {

    List<CatalogModel> listUserSelectableModels();

    void invalidateAll();
}
