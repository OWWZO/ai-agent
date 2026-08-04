package org.wwz.ai.domain.agent.service.armory.node;

import org.wwz.ai.domain.agent.model.entity.ArmoryCommandEntity;
import org.wwz.ai.domain.agent.service.armory.business.data.ILoadDataStrategy;
import org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 装配逻辑树根节点。
 * <p>
 * 根据命令选择数据加载策略，把配置快照放入 DynamicContext，再交给后续运行时对象节点处理。
 */
@Slf4j
@Service
public class RootNode extends AbstractArmorySupport {

    private final Map<String, ILoadDataStrategy> loadDataStrategyMap;

    @Resource
    private AiClientApiNode aiClientApiNode;

    @Autowired
    public RootNode(Map<String, ILoadDataStrategy> loadDataStrategyMap) {
        this.loadDataStrategyMap = loadDataStrategyMap;
    }

    @Override
    protected void multiThread(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // 数据查询可以并行执行，但结果必须在策略返回前写入同一个动态上下文。
        ILoadDataStrategy loadDataStrategy = loadDataStrategyMap.get(requestParameter.getLoadDataStrategy());
        loadDataStrategy.loadData(requestParameter, dynamicContext);
    }

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 构建，数据加载节点{}", JSON.toJSONString(requestParameter));
        // 当前节点只负责路由，实际加载由 multiThread 中选中的 ILoadDataStrategy 完成。
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return aiClientApiNode;
    }

}
