package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Skill 元数据目录。调用方只搜索和解析，不感知扫描与缓存。
 */
public interface SkillCatalog {

    List<SkillDescriptor> list();

    List<SkillSearchHit> search(SkillQuery query);

    SkillRef resolve(String identifier);

    Optional<SkillDescriptor> find(String identifier);

    SkillDescriptor getRequired(String identifier);

    String version();

    void refresh();

    boolean isEnabled();

    Path assertPathAllowed(Path candidatePath);
}
