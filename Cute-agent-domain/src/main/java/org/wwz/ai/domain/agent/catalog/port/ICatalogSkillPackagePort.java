package org.wwz.ai.domain.agent.catalog.port;

import org.wwz.ai.domain.agent.catalog.model.CatalogSkillPackageResult;

/**
 * 技能包读写端口。
 */
public interface ICatalogSkillPackagePort {

    CatalogSkillPackageResult previewZip(byte[] zipBytes);

    CatalogSkillPackageResult installZip(byte[] zipBytes, String originalFilename, boolean replace);

    CatalogSkillPackageResult installFromMarkdown(String name, String description, String content, boolean replace);

    CatalogSkillPackageResult installFromUrl(String url, boolean replace);
}
