package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloud OCR for images (MRAG OCR_TYPE: vlm-ocr / deepseek-ocr / paddleocr-vl).
 */
public class ImageOcrTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "image_ocr";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/image_ocr";
    }

    @Override
    protected long timeoutSeconds() {
        return 320L;
    }

    @Override
    protected String defaultDescription() {
        return "Extract text from an image (PNG/JPG/WEBP/...) via cloud OCR. "
                + "Pass workspace file_path. Optional lang=ch|en|ch_en or custom prompt. "
                + "Backend selected by reactor-tool OCR_TYPE (vlm-ocr / deepseek-ocr / paddleocr-vl).";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("file_path", stringProp("Image path under workspace (png/jpg/jpeg/bmp/webp/tiff/gif)"));
        properties.put("lang", stringProp("Optional language hint: ch | en | ch_en"));
        properties.put("prompt", stringProp("Optional custom OCR instruction (VLM backends)"));
        return objectSchema(properties, List.of("file_path"));
    }
}
