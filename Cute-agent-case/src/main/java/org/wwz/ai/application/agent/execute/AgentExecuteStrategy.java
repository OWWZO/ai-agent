package org.wwz.ai.application.agent.execute;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.memory.SessionContextMemoryService;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.IDesktopControlRepository;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 应用层执行策略：ReAct 与 PlanSolve 共用同一套 run 生命周期。
 * <p>
 * 两条链路只在两处不同：执行树工厂、失败错误码。原先由两个
 * 逐字重复的策略类分别持有，改为按 {@code agentType} 在运行时选树，生命周期
 * （记忆 hydrate → 注册活动 run → 执行 → 双终态收口）只写一遍。
 * <p>
 * 节点树本身仍按链路分开（wrench 要求节点绑定各自的 DynamicContext 泛型），
 * 本类只消除应用层重复。
 */
@Slf4j
@Service("agentExecuteStrategy")
public class AgentExecuteStrategy implements IExecuteStrategy {

    /** 一条链路的可变量：选哪棵树、失败时用哪个错误码。 */
    private record Link(DefaultReactAgentExecuteStrategyFactory reactFactory,
                        DefaultPlanSolveAgentExecuteStrategyFactory planSolveFactory,
                        String linkName,
                        String failureCode) {
    }

    @Resource
    private DefaultReactAgentExecuteStrategyFactory defaultReactAgentExecuteStrategyFactory;

    @Resource
    private DefaultPlanSolveAgentExecuteStrategyFactory defaultPlanSolveAgentExecuteStrategyFactory;

    @Resource
    private SessionContextMemoryService sessionContextMemoryService;

    @Resource
    private SessionWorkingMemoryService sessionWorkingMemoryService;

    @Resource
    private ActiveAgentRunRegistry activeAgentRunRegistry;

    @Resource
    private IUserQuestionRepository userQuestionRepository;

    @Resource
    private IDesktopControlRepository desktopControlRepository;

    @Resource
    private IPlanApprovalRepository planApprovalRepository;

    @Override
    public void execute(AgentExecutionCommand request, AgentSessionStream stream) throws Exception {
        Link link = resolveLink(request);
        // 先 hydrate 跨轮工作记忆，再进入 Agent 内核；记忆加载失败不应改变 case 的执行边界。
        enrichWorkingMemory(request);
        // 注册活动 run 后，stop 请求才能通过 requestId 找到同一条执行流。
        // begin 对已注册的 requestId 是幂等空操作，因此 GptQuery 侧已注册时这里不会重复建。
        activeAgentRunRegistry.begin(
                request.getRequestId(),
                request.getSessionId(),
                request.getUserId());
        activeAgentRunRegistry.bindStream(request.getRequestId(), stream);

        // 执行树在 apply 过程中才把 AgentContext 挂到 DynamicContext 上；用 Supplier
        // 持有已构建的 DynamicContext，异常路径也能取到已经装配好的上下文写终态。
        AtomicReference<Supplier<AgentContext>> contextRef = new AtomicReference<>();
        try {
            String result = applyHandler(link, request, stream, contextRef);
            log.info("{} execute result: {}", link.linkName(), result);
            finishTerminal(link, contextRef, null);
        } catch (Exception e) {
            // 用户取消与系统失败使用不同账本终态，前端和历史回放据此区分 STOPPED 与 FAILED。
            finishTerminal(link, contextRef, e);
            throw e;
        }
    }

    /** 按 agentType 选链路；未识别类型统一回退 ReAct。 */
    private Link resolveLink(AgentExecutionCommand request) {
        if (request.getAgentType() != null
                && AgentType.PLAN_SOLVE.getValue().equals(request.getAgentType())) {
            return new Link(null, defaultPlanSolveAgentExecuteStrategyFactory,
                    "PlanSolve", "PLAN_SOLVE_EXECUTE_ERROR");
        }
        return new Link(defaultReactAgentExecuteStrategyFactory, null,
                "React", "REACT_EXECUTE_ERROR");
    }

    /**
     * 两种 DynamicContext 字段相同但无共同父类型，且 wrench 的节点树绑定各自泛型，
     * 因此这里按链路分别构造并执行；生命周期差异仅在于用哪棵树。
     */
    private String applyHandler(Link link, AgentExecutionCommand request, AgentSessionStream stream,
                                AtomicReference<Supplier<AgentContext>> contextRef) throws Exception {
        AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, request.getAgentType());
        if (link.planSolveFactory() != null) {
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                    DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext.builder()
                            .printer(printer)
                            .build();
            contextRef.set(dynamicContext::getAgentContext);
            StrategyHandler<AgentExecutionCommand,
                    DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> handler =
                    link.planSolveFactory().armoryStrategyHandler();
            return handler.apply(request, dynamicContext);
        }
        DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                        .printer(printer)
                        .build();
        contextRef.set(dynamicContext::getAgentContext);
        StrategyHandler<AgentExecutionCommand,
                DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> handler =
                link.reactFactory().armoryStrategyHandler();
        return handler.apply(request, dynamicContext);
    }

    /**
     * 取消与失败的终态收口。
     * 取消优先于失败判定：用户 stop 引发的异常必须落 STOPPED 而不是 FAILED。
     *
     * @param failure 非空表示异常路径；为空表示正常返回路径
     */
    private void finishTerminal(Link link, AtomicReference<Supplier<AgentContext>> contextRef, Exception failure) {
        Supplier<AgentContext> supplier = contextRef.get();
        AgentContext agentContext = supplier == null ? null : supplier.get();
        if (agentContext == null) {
            // 执行树尚未产出上下文，无法写 ledger；异常路径补一条日志后继续向上抛。
            if (failure != null) {
                log.warn("{} execute failed before AgentContext was assembled", link.linkName(), failure);
            }
            return;
        }
        if (agentContext.isRunCancelled()) {
            ExecutionLedgerRunSupport.finishRun(
                    agentContext,
                    ExecutionLedgerConstants.STATUS_STOPPED,
                    null,
                    "USER_STOP",
                    failure == null
                            ? "用户停止本轮对话"
                            : (failure.getMessage() == null ? "用户停止本轮对话" : failure.getMessage()));
            return;
        }
        if (failure != null) {
            ExecutionLedgerRunSupport.finishRun(
                    agentContext,
                    ExecutionLedgerConstants.STATUS_FAILED,
                    null,
                    link.failureCode(),
                    failure.getMessage());
        }
    }

    private void enrichWorkingMemory(AgentExecutionCommand request) {
        WorkingMemoryHydration.enrich(
                request,
                sessionWorkingMemoryService,
                sessionContextMemoryService,
                userQuestionRepository,
                desktopControlRepository,
                planApprovalRepository);
    }
}
