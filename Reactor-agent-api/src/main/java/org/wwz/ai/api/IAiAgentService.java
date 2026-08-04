package org.wwz.ai.api;

import org.wwz.ai.api.dto.AiAgentResponseDTO;
import org.wwz.ai.api.dto.ArmoryAgentRequestDTO;
import org.wwz.ai.api.dto.ArmoryApiRequestDTO;
import org.wwz.ai.api.dto.AutoAgentRequestDTO;
import org.wwz.ai.api.response.Response;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;


/**
 * 智能体与外部模型 API 的装配及发现契约。
 *
 * <p>armory 方法只表达“将配置装配进运行时”的应用动作；真正的注册、校验和运行时对象创建由实现层完成，
 * queryAvailableAgents 则只返回当前可供调用的智能体摘要。</p>
 */
public interface IAiAgentService {

//    ResponseBodyEmitter autoAgent(AutoAgentRequestDTO request, HttpServletResponse response);

    /**
     * 装配智能体
     */
    Response<Boolean> armoryAgent(ArmoryAgentRequestDTO request);

    /**
     * 查询可用的智能体列表
     */
    Response<List<AiAgentResponseDTO>> queryAvailableAgents();

    /**
     * 装配API
     */
    Response<Boolean> armoryApi(ArmoryApiRequestDTO request);

}
