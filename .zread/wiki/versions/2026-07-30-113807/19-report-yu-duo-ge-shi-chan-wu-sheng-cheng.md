**

# Report 与多格式产物生成

Report 与多格式产物生成模块是 Reactor-agent 的核心能力之一，它通过统一的 `report_tool` 和一整套 DocGen 工具，为用户提供从 Markdown/HTML 基础报告到 PPTX 幻灯片、PDF 文档、DOCX 文档、Excel 表格及自定义模板的全链路、多格式产物生成能力。核心设计目标是让 Agent 在 ReAct/Plan-Execute 循环中能无缝生成结构化、高质量、可下载的交付物（如代码审计报告、研究分析报告、PPT 演示材料），并通过文件注册机制与 SSE 流式渲染实时展示。

## 工具架构与执行流转

整个 Report 体系由两层构成：Java 侧的 `ReportTool` 与一组抽象 DocGen 工具，以及 Python 侧的 `reactor_tool/tool/report.py`（专为 report_tool 提供流式 Markdown/HTML/PPT 渲染）与 `reactor_tool/tool/docgen/service.py`（通用文档/幻灯片/表格/检查表生成引擎）。

**Mermaid 执行流转图**：

```mermaid
graph TD
    A[Agent 在 ReAct/Plan-Execute 循环中调用 report_tool] --> B[Java ReportTool 接收 task + fileNames]
    B --> C[CodeInterpreterRequest 流式请求 /v1/tool/report]
    C --> D[Python report.py 分发到 markdown_report / html_report / ppt_report]
    D --> E[下载引用文件 → 截断上下文 → 构建 Jinja2 prompt → 流式调用 LLM]
    E --> F[输出流式 Markdown/HTML/PPT 内容]
    F --> G[CodeInterpreter 后处理注册文件 + 发送 SSE]
    G --> H[Java DocGen 工具（document_generate/slides_generate/...）调用 /v1/tool/document_generate 等端点]
    H --> I[service.py 解析 markdown content/blocks → 渲染为 PDF/DOCX/HTML/PPTX]
    I --> J[文件注册到 ToolArtifactSource 并通过 Printer SSE 实时推送]
```

## 多格式产物生成能力详解

### 1. report_tool（核心报告生成器）

```java
// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/common/ReportTool.java#L35-L67
public class ReportTool implements BaseTool {
    // ...
    public String getDescription() { /* 支持 ReactorConfig 动态描述 */ }
    public Map<String, Object> toParams() { /* task + fileDescription + fileName + fileType */ }
    public Object execute(...) { /* 构建 CodeInterpreterRequest → 流式调用 CodeAgent */ }
}
```

- **支持格式**：markdown, html, ppt（最终输出 HTML 格式 PPT）
- **输入**：task（必填）、fileDescription、fileName、fileType（可选，默认 markdown）
- **输出**：通过 CodeInterpreter 流式返回 HTML/Markdown 片段，最终注册为文件

### 2. DocGen 工具集

```java
// Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/common/docgen/...
public class DocumentGenerateTool extends AbstractDocGenTool {
    protected String endpointPath() { return "/v1/tool/document_generate"; }
    // 支持 PDF/DOCX/HTML/Markdown
}
public class SlidesGenerateTool extends AbstractDocGenTool {
    protected String endpointPath() { return "/v1/tool/slides_generate"; }
    // 支持 PPTX
}
```

其他工具包括：
- ExcelGeneratorTool、ChecklistGenerateTool、ChartGeneratorTool、TemplateFillerTool、ThemeDesignerTool

**参数对比表**：

| 工具名称              | 主要输出格式 | 核心输入项               | 适用场景                     |
|-----------------------|-------------|--------------------------|------------------------------|
| document_generate     | PDF/DOCX/HTML/MD | content/blocks + title  | 审计报告、分析报告、模板文档 |
| slides_generate       | PPTX        | slides list + title      | 演示文稿、汇报材料            |
| excel_generate        | Excel       | sheet spec               | 数据报表、财务报表            |
| checklist_generate    | PDF/DOCX/HTML/JSON | items + groups        | 任务清单、验收标准            |
| chart_generator       | PDF/DOCX/HTML | chart JSON fence         | 技术图表、流程图              |
| template_filler       | 任意         | template + variables     | 自定义报告模板填充            |
| theme_designer        | 主题文件    | theme name + config      | 统一报告风格                  |

**Sources: [Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/common/ReportTool.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/common/ReportTool.java#L35-L67)**

**Sources: [reactor-tool/reactor_tool/tool/report.py](reactor-tool/reactor_tool/tool/report.py#L24-L44)**

**Sources: [Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/common/docgen/DocumentGenerateTool.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/common/docgen/DocumentGenerateTool.java#L11-L37)**

**Sources: [reactor-tool/reactor_tool/tool/docgen/service.py](reactor-tool/reactor_tool/tool/docgen/service.py#L63-L140)**

## prompt 与模板体系

- `reactor-tool/reactor_tool/prompt/report.yaml` 包含 `ppt_prompt`、`markdown_prompt`、`html_prompt`、`fix_html_prompt` 等专有模板
- DocGen 引擎支持 Markdown front-matter（title, theme, cover, toc 等）与 ```chart/```metrics/```checklist fence
- 模板系统通过 `reactor_tool/docgen/templates.py` 动态加载与保存自定义模板

**Sources: [reactor-tool/reactor_tool/prompt/report.yaml](reactor-tool/reactor_tool/prompt/report.yaml#L2-L115)**

## 产物登记与文件注册机制

成功生成后，Python 端通过 `upload_file_by_path` + `resolve_output_path` 写入 `skilloutput/docgen/{session-id}/`，Java 端通过 `AbstractDocGenTool.registerFiles` 与 `agentContext.registerGeneratedArtifact` 完成文件到 ToolArtifactSource 的注册，随后通过 SSE 实时推送给前端 UI。

**Sources: [reactor-tool/reactor_tool/tool/docgen/service.py](reactor-tool/reactor_tool/tool/docgen/service.py#L42-L60)**

**Sources: [Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/common/docgen/AbstractDocGenTool.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/common/docgen/AbstractDocGenTool.java#L100-L136)**

## 配置与最佳实践

- 在 `reactor-tool/.env_template` 中配置 `REPORT_MODEL`、`REACTOR_DOCGEN_OUTPUT_DIR`
- Java 侧通过 `ReactorConfig` 动态调整 `report_tool` 的描述与参数
- 推荐阅读顺序：先从 `report_tool` 开始，再探索 DocGen 工具集以获得更专业的 PDF/PPTX 产出

[Report 与多格式产物生成](19-report-yu-duo-ge-shi-chan-wu-sheng-cheng)
