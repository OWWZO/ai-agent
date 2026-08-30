package org.wwz.ai.config.reactor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.replay.ConversationHistoryReplayService;
import org.wwz.ai.domain.agent.ledger.replay.HistoryReplayPrinter;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.ToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.ToolInvocationProjectorRegistry;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.CanvasPublishToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.AskUserQuestionToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.GenUiPatchToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.GenUiTreeToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.CodeInterpreterToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.DataAnalysisToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.DefaultToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.DeepSearchToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.ImageGenerationToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.MultiModalToolInvocationProjector;

import java.util.List;

/**
 * 历史回放相关 Bean 装配。
 */
@Configuration
public class ReplayProjectorAutoConfiguration {

    @Bean
    public AskUserQuestionToolInvocationProjector askUserQuestionToolInvocationProjector(
            IUserQuestionRepository userQuestionRepository) {
        return new AskUserQuestionToolInvocationProjector(userQuestionRepository);
    }

    @Bean
    public DeepSearchToolInvocationProjector deepSearchToolInvocationProjector() {
        return new DeepSearchToolInvocationProjector();
    }

    @Bean
    public CodeInterpreterToolInvocationProjector codeInterpreterToolInvocationProjector() {
        return new CodeInterpreterToolInvocationProjector();
    }

    @Bean
    public CanvasPublishToolInvocationProjector canvasPublishToolInvocationProjector() {
        return new CanvasPublishToolInvocationProjector();
    }

    @Bean
    public GenUiTreeToolInvocationProjector genUiTreeToolInvocationProjector() {
        return new GenUiTreeToolInvocationProjector();
    }

    @Bean
    public GenUiPatchToolInvocationProjector genUiPatchToolInvocationProjector() {
        return new GenUiPatchToolInvocationProjector();
    }

    @Bean
    public DataAnalysisToolInvocationProjector dataAnalysisToolInvocationProjector() {
        return new DataAnalysisToolInvocationProjector();
    }

    @Bean
    public MultiModalToolInvocationProjector multiModalToolInvocationProjector() {
        return new MultiModalToolInvocationProjector();
    }

    @Bean
    public ImageGenerationToolInvocationProjector imageGenerationToolInvocationProjector() {
        return new ImageGenerationToolInvocationProjector();
    }

    @Bean
    public DefaultToolInvocationProjector defaultToolInvocationProjector() {
        return new DefaultToolInvocationProjector();
    }

    @Bean
    public ToolInvocationProjectorRegistry toolInvocationProjectorRegistry(
            List<ToolInvocationProjector> projectors,
            DefaultToolInvocationProjector defaultProjector) {
        return new ToolInvocationProjectorRegistry(projectors, defaultProjector);
    }

    @Bean
    public ReplayProjector replayProjector(ToolInvocationProjectorRegistry registry) {
        return new ReplayProjector(registry);
    }

    @Bean
    public HistoryReplayPrinter historyReplayPrinter() {
        return new HistoryReplayPrinter();
    }

    @Bean
    public ConversationHistoryReplayService conversationHistoryReplayService(
            ExecutionLedgerQueryService executionLedgerQueryService,
            ReplayProjector replayProjector,
            HistoryReplayPrinter historyReplayPrinter,
            LlmModelCatalog llmModelCatalog) {
        return new ConversationHistoryReplayService(
                executionLedgerQueryService,
                replayProjector,
                historyReplayPrinter,
                llmModelCatalog
        );
    }
}
