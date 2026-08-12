package org.wwz.ai.infrastructure.memory;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.LtmExtractionApplier;
import org.wwz.ai.domain.agent.memory.ltm.LtmExtractionOp;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.printer.LogPrinter;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Flush / Review 共用的单次 LLM 结构化抽取。
 */
@Slf4j
public final class LtmLlmExtractionSupport {

    private LtmLlmExtractionSupport() {
    }

    public static int extractAndApply(ReactorRuntimeDependencies deps,
                                      CuratedMemoryStore store,
                                      LtmOwner owner,
                                      String sessionId,
                                      String requestId,
                                      String systemPrompt,
                                      String material,
                                      String writeOrigin,
                                      long timeoutSeconds) {
        if (deps == null || store == null || owner == null || StringUtils.isBlank(material)) {
            return 0;
        }
        try {
            String modelName = resolveModel(deps);
            LLM llm = new LLM(modelName, "", deps);
            AgentRequest fake = new AgentRequest();
            fake.setRequestId(StringUtils.defaultIfBlank(requestId, "ltm") + "-ltm-extract");
            fake.setSessionId(sessionId);
            AgentContext ctx = AgentContext.builder()
                    .requestId(fake.getRequestId())
                    .sessionId(sessionId)
                    .query("ltm-extraction")
                    .isStream(false)
                    .skipMemory(true) // 禁止再触发 LTM 副作用循环
                    .ltmOwner(owner)
                    .runtimeDependencies(deps)
                    .printer(new LogPrinter(fake))
                    .build();
            ctx.markExecutionPosition("ltm-extract", null);

            Message system = Message.systemMessage(systemPrompt, null);
            List<Message> ask = List.of(Message.userMessage(
                    "Conversation material:\n" + material + "\n\nReturn JSON array only.", null));

            long timeout = Math.max(5L, timeoutSeconds);
            // Prefer stream aggregation: some OpenAI-compatible gateways return empty/truncated
            // non-stream JSON bodies that Spring AI cannot deserialize as ChatCompletion.
            String raw = llm.ask(
                    ctx,
                    ask,
                    List.of(system),
                    true,
                    false,
                    0.2,
                    ExecutionLedgerConstants.CALL_KIND_INTERNAL_COMPACT
            ).get(timeout, TimeUnit.SECONDS);

            List<LtmExtractionOp> ops = LtmExtractionApplier.parseOps(raw);
            int applied = LtmExtractionApplier.apply(store, owner, ops, sessionId, requestId, writeOrigin);
            log.info("LTM extract applied={} origin={} sessionId={} owner={}",
                    applied, writeOrigin, sessionId, owner);
            return applied;
        } catch (Exception e) {
            log.warn("LTM extract failed origin={} sessionId={}: {}", writeOrigin, sessionId, e.toString());
            return 0;
        }
    }

    private static String resolveModel(ReactorRuntimeDependencies deps) {
        try {
            if (deps.getReactorConfig() != null
                    && StringUtils.isNotBlank(deps.getReactorConfig().getPlannerModelName())) {
                return deps.getReactorConfig().getPlannerModelName();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "gpt-4o-mini";
    }
}
