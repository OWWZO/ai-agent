package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.model.valobj.AiClientAdvisorVO;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConstants;
import org.wwz.ai.domain.agent.reactor.data.dto.VectorRecallReq;
import org.wwz.ai.domain.agent.reactor.service.VectorService;
import org.wwz.ai.domain.agent.service.armory.node.factory.element.RagAnswerAdvisor;

import java.util.Map;

/**
 * RagAnswerAdvisor 行为测试。
 * 锁定知识库过滤表达式到 Qdrant 请求的转换规则，避免回退到已废弃的 pgvector 语义。
 */
public class RagAnswerAdvisorTest {

    @Test
    public void shouldTranslateKnowledgeFilterIntoQdrantKeywordFilter() {
        TestableRagAnswerAdvisor advisor = new TestableRagAnswerAdvisor(new VectorService(), AiClientAdvisorVO.RagAnswer.builder()
                .topK(6)
                .filterExpression("knowledge == 'article-prompt-words'")
                .build());

        VectorRecallReq req = advisor.buildReq("什么是 JMM", Map.of());

        Assert.assertEquals(DataAgentConstants.SCHEMA_COLLECTION_NAME, req.getCollectionName());
        Assert.assertEquals("什么是 JMM", req.getQuery());
        Assert.assertEquals(Integer.valueOf(6), req.getLimit());
        Assert.assertEquals(Map.of("knowledge", "article-prompt-words"), req.getKeywordFilterMap());
    }

    @Test
    public void shouldPreferContextFilterExpressionWhenPresent() {
        TestableRagAnswerAdvisor advisor = new TestableRagAnswerAdvisor(new VectorService(), AiClientAdvisorVO.RagAnswer.builder()
                .topK(4)
                .filterExpression("knowledge == 'default-knowledge'")
                .build());

        VectorRecallReq req = advisor.buildReq("什么是 Agent", Map.of(
                "qa_filter_expression", "knowledge == 'override-knowledge'"
        ));

        Assert.assertEquals(Map.of("knowledge", "override-knowledge"), req.getKeywordFilterMap());
    }

    @Test
    public void shouldRejectUnsupportedFilterExpression() {
        TestableRagAnswerAdvisor advisor = new TestableRagAnswerAdvisor(new VectorService(), AiClientAdvisorVO.RagAnswer.builder()
                .filterExpression("source == 'book'")
                .build());

        IllegalArgumentException error = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> advisor.buildReq("什么是 RAG", Map.of())
        );

        Assert.assertTrue(error.getMessage().contains("knowledge == 'xxx'"));
    }

    private static class TestableRagAnswerAdvisor extends RagAnswerAdvisor {

        private TestableRagAnswerAdvisor(VectorService vectorService, AiClientAdvisorVO.RagAnswer ragAnswer) {
            super(vectorService, ragAnswer);
        }

        private VectorRecallReq buildReq(String userText, Map<String, Object> context) {
            return super.buildVectorRecallReq(userText, context);
        }
    }
}
