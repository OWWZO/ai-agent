package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * GenUI 导出门面。
 *
 * 所有导出入口先复用同一套树校验，再交给对应格式 exporter；这样 PDF 和 DOCX
 * 不会各自接受一套不一致的节点协议，mode 为空时统一采用 document。
 */
public final class GenUiExportService {

    private GenUiExportService() {
    }

    public static Map<String, Object> validateTree(Object tree) {
        // 将输入规范化并返回校验后的树，后续 exporter 不再重复解析原始对象。
        return GenUiSchema.validateUiTree(tree);
    }

    public static byte[] exportPdf(Object tree, String mode) {
        // 先校验再渲染，避免非法 GenUI 节点进入文件生成器。
        Map<String, Object> normalized = validateTree(tree);
        return GenUiPdfExporter.export(normalized, StringUtils.defaultIfBlank(mode, "document"));
    }

    public static byte[] exportDocx(Object tree, String mode) {
        // PDF/DOCX 共享 normalized tree，只在最终格式 exporter 处分流。
        Map<String, Object> normalized = validateTree(tree);
        return GenUiDocxExporter.export(normalized, StringUtils.defaultIfBlank(mode, "document"));
    }
}
