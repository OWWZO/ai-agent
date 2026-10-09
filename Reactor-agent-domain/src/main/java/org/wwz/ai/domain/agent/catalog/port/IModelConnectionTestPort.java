package org.wwz.ai.domain.agent.catalog.port;

import org.wwz.ai.domain.agent.catalog.model.ModelConnectionTestResult;

/**
 * 模型连接探测端口。
 */
public interface IModelConnectionTestPort {

    ModelConnectionTestResult test(String modelReference);

    ModelConnectionTestResult testByRecordId(Long id);
}
