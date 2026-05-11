package org.wwz.ai.domain.agent.model.valobj.enums;

import org.wwz.ai.domain.agent.model.valobj.AiClientAdvisorVO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.wwz.ai.domain.agent.reactor.service.VectorService;
import org.wwz.ai.domain.agent.service.armory.node.factory.element.RagAnswerAdvisor;

import java.util.HashMap;
import java.util.Map;

/**
 * 顾问类型枚举
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum AiClientAdvisorTypeEnumVO {

    CHAT_MEMORY("ChatMemory", "上下文记忆（内存模式）") {
        @Override
        public Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO, VectorService vectorService) {
            AiClientAdvisorVO.ChatMemory chatMemory = aiClientAdvisorVO.getChatMemory();
            return PromptChatMemoryAdvisor.builder(
                    MessageWindowChatMemory.builder()
                            .maxMessages(chatMemory.getMaxMessages())
                            .build()
            ).build();
        }
    },

    RAG_ANSWER("RagAnswer", "知识库") {
        @Override
        public Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO, VectorService vectorService) {
            AiClientAdvisorVO.RagAnswer ragAnswer = aiClientAdvisorVO.getRagAnswer();
            return new RagAnswerAdvisor(vectorService, ragAnswer);
        }
    }

    ;

    private String code;
    private String info;

    // 静态Map缓存，用于快速查找
    private static final Map<String, AiClientAdvisorTypeEnumVO> CODE_MAP = new HashMap<>();

    // 静态初始化块，在类加载时初始化Map
    static {
        for (AiClientAdvisorTypeEnumVO enumVO : values()) {
            CODE_MAP.put(enumVO.getCode(), enumVO);
        }
    }

    /**
     * 策略方法：创建顾问对象
     * @param aiClientAdvisorVO 顾问配置对象
     * @param vectorService Qdrant 向量检索服务
     * @return 顾问对象
     */
    public abstract Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO, VectorService vectorService);

    /**
     * 根据code获取枚举
     * @param code 编码
     * @return 枚举对象
     */
    public static AiClientAdvisorTypeEnumVO getByCode(String code) {
        AiClientAdvisorTypeEnumVO enumVO = CODE_MAP.get(code);
        if (enumVO == null) {
            throw new RuntimeException("err! advisorType " + code + " not exist!");
        }
        return enumVO;
    }

}
