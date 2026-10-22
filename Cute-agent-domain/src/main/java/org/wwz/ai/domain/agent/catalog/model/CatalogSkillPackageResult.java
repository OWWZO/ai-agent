package org.wwz.ai.domain.agent.catalog.model;

import java.util.Map;

/**
 * 技能包操作结果。Map 只承载既有技能接口的动态响应数据，不暴露基础设施类型。
 */
public record CatalogSkillPackageResult(Map<String, Object> data) {
}
