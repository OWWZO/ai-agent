package org.wwz.ai.domain.agent.service.armory.business.data;

import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;

/**
 * 装配配置数据加载策略。
 * <p>
 * 不同装配命令拥有不同的 ID 类型和关联查询范围，策略负责把查询结果统一放入 DynamicContext。
 */
public interface ILoadDataStrategy {

    void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext);

}
