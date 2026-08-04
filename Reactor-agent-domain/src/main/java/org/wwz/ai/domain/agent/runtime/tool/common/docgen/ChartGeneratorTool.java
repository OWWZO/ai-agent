package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * chart_generator 工具契约。
 *
 * 当前类只声明图表类型、数据和输出文件参数；图表布局、主题渲染和 PNG/SVG/PDF
 * 文件生成由 reactor-tool 的文档生成服务负责。
 */
public class ChartGeneratorTool extends AbstractDocGenTool {

    public static final String TOOL_NAME = "chart_generator";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/chart_generator";
    }

    @Override
    protected String defaultDescription() {
        return "Generate professional charts (bar, line, pie, scatter, heatmap, radar, area, histogram, "
                + "horizontal_bar) with themes presentation|report|dashboard|minimal. "
                + "Saves PNG/SVG/PDF image file.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        // chart_type 与 data 是生成图表不可缺少的最小输入，其余字段用于视觉和文件覆盖。
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("chart_type", stringProp("bar|line|pie|scatter|heatmap|radar|area|histogram|horizontal_bar"));
        properties.put("data", objectProp("Chart data: categories, series, values, labels, x/y, matrix, row_labels"));
        properties.put("title", stringProp("Chart title"));
        properties.put("x_label", stringProp("X-axis label"));
        properties.put("y_label", stringProp("Y-axis label"));
        properties.put("theme", stringProp("presentation | report | dashboard | minimal"));
        properties.put("output_format", stringProp("png | svg | pdf"));
        properties.put("output_path", stringProp("Output file name, e.g. sales.png"));
        properties.put("show_legend", boolProp("Show legend"));
        properties.put("stacked", boolProp("Stack bars/areas"));
        properties.put("fileName", stringProp("Alias of output_path"));
        return objectSchema(properties, List.of("chart_type", "data"));
    }
}
