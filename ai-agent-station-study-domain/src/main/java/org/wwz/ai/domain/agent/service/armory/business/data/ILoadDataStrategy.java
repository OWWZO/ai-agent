package org.wwz.ai.domain.agent.service.armory.business.data;

import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;

/**
 * 数据加载策略
 */
public interface ILoadDataStrategy {

    void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext);

}
