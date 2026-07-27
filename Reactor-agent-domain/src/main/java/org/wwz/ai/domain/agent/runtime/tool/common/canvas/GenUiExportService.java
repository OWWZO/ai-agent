package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * GenUI export facade: validate tree then render PDF/DOCX.
 */
public final class GenUiExportService {

    private GenUiExportService() {
    }

    public static Map<String, Object> validateTree(Object tree) {
        return GenUiSchema.validateUiTree(tree);
    }

    public static byte[] exportPdf(Object tree, String mode) {
        Map<String, Object> normalized = validateTree(tree);
        return GenUiPdfExporter.export(normalized, StringUtils.defaultIfBlank(mode, "document"));
    }

    public static byte[] exportDocx(Object tree, String mode) {
        Map<String, Object> normalized = validateTree(tree);
        return GenUiDocxExporter.export(normalized, StringUtils.defaultIfBlank(mode, "document"));
    }
}
