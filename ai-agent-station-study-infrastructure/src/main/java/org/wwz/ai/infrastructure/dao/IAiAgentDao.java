package org.wwz.ai.infrastructure.dao;

import org.wwz.ai.infrastructure.dao.po.AiAgent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI智能体配置表 DAO
 * @description AI智能体配置表数据访问对象
 */
@Mapper
public interface IAiAgentDao {

    /**
     * 插入AI智能体配置
     * @param aiAgent AI智能体配置对象
     * @return 影响行数
     */
    int insert(AiAgent aiAgent);

    /**
     * 根据ID更新AI智能体配置
     * @param aiAgent AI智能体配置对象
     * @return 影响行数
     */
    int updateById(AiAgent aiAgent);

    /**
     * 根据智能体ID更新AI智能体配置
     * @param aiAgent AI智能体配置对象
     * @return 影响行数
     */
    int updateByAgentId(AiAgent aiAgent);

    /**
     * 根据ID删除AI智能体配置
     * @param id 主键ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 根据智能体ID删除AI智能体配置
     * @param agentId 智能体ID
     * @return 影响行数
     */
    int deleteByAgentId(@Param("agentId") String agentId);

    /**
     * 根据ID查询AI智能体配置
     * @param id 主键ID
     * @return AI智能体配置对象
     */
    AiAgent queryById(Long id);

    /**
     * 根据智能体ID查询AI智能体配置
     * @param agentId 智能体ID
     * @return AI智能体配置对象
     */
    AiAgent queryByAgentId(@Param("agentId") String agentId);

    /**
     * 查询所有启用的AI智能体配置
     * @return AI智能体配置列表
     */
    List<AiAgent> queryEnabledAgents();

    /**
     * 根据渠道类型查询AI智能体配置
     * @param channel 渠道类型
     * @return AI智能体配置列表
     */
    List<AiAgent> queryByChannel(@Param("channel") String channel);

    /**
     * 查询所有AI智能体配置
     * @return AI智能体配置列表
     */
    List<AiAgent> queryAll();

    /**
     * 查询可用的 Fix 角色列表
     * @return Fix 角色列表
     */
    List<AiAgent> queryAvailableFixRoles();

    /**
     * 按角色ID查询可用的 Fix 角色
     * @param agentId 角色ID
     * @return Fix 角色
     */
    AiAgent queryAvailableFixRoleByAgentId(@Param("agentId") String agentId);

}
