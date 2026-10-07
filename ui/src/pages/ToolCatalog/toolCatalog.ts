import { buildToolPrompt } from "./toolCatalogModel";
import { createOpenCliToolCatalog } from "./openCliCatalogModel";

export type ToolCatalogKind = "base-tool" | "opencli-site";
export type ToolAccess = "read" | "write";
export type ToolCategory =
  | "social"
  | "research"
  | "data"
  | "document"
  | "browser"
  | "creation"
  | "media"
  | "news"
  | "commerce"
  | "finance";

export type ToolField = {
  name: string;
  label: string;
  type:
    | "text"
    | "textarea"
    | "number"
    | "select"
    | "multi-select"
    | "boolean"
    | "url"
    | "file"
    | "files"
    | "datetime"
    | "local-path";
  required?: boolean;
  positional?: boolean;
  placeholder?: string;
  description?: string;
  defaultValue?: string | number | boolean | string[];
  options?: Array<{ label: string; value: string }>;
  accept?: string;
  min?: number;
  max?: number;
  minFiles?: number;
  maxFiles?: number;
  maxLength?: number;
};

export type ToolFunction = {
  id: string;
  title: string;
  description: string;
  access: ToolAccess;
  browser?: boolean;
  command?: string;
  fields: ToolField[];
  buildPrompt: (values: Record<string, unknown>) => string;
};

export type ToolCatalogItem = {
  id: string;
  kind: ToolCatalogKind;
  title: string;
  description: string;
  category: ToolCategory;
  iconKey: string;
  requiresLogin?: boolean;
  functions: ToolFunction[];
};

export type ToolTaskDraft = {
  message: string;
  files: File[];
};

const field = (
  name: string,
  label: string,
  type: ToolField["type"],
  config: Omit<Partial<ToolField>, "name" | "label" | "type"> = {}
): ToolField => ({
  name,
  label,
  type,
  ...config
});

const options = (
  ...entries: Array<[value: string, label: string]>
): Array<{ value: string; label: string }> =>
  entries.map(([value, label]) => ({
    value,
    label
  }));

function slug(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

function makeToolFunction(input: {
  id: string;
  kind: ToolCatalogKind;
  command: string;
  title: string;
  description: string;
  fields: ToolField[];
  access?: ToolAccess;
  browser?: boolean;
}): ToolFunction {
  const access = input.access ?? "read";
  return {
    id: input.id,
    title: input.title,
    description: input.description,
    access,
    browser: input.browser,
    command: input.command,
    fields: input.fields,
    buildPrompt: (values) =>
      buildToolPrompt({
        kind: input.kind,
        command: input.command,
        access,
        fields: input.fields,
        values,
      }),
  };
}

function baseToolFunction(
  command: string,
  title: string,
  description: string,
  fields: ToolField[],
  access: ToolAccess = "read"
): ToolFunction {
  return makeToolFunction({
    id: `base-${slug(command)}`,
    kind: "base-tool",
    command,
    title,
    description,
    fields,
    access,
  });
}

function openCliFunction(
  site: string,
  commandName: string,
  title: string,
  description: string,
  fields: ToolField[],
  access: ToolAccess = "read"
): ToolFunction {
  const command = `${site}/${commandName}`;
  return makeToolFunction({
    id: `opencli-${slug(command)}`,
    kind: "opencli-site",
    command,
    title,
    description,
    fields,
    access,
  });
}

function baseToolItem(input: {
  id: string;
  title: string;
  description: string;
  category: ToolCategory;
  iconKey: string;
  functions: ToolFunction[];
}): ToolCatalogItem {
  return {
    kind: "base-tool",
    ...input
  };
}

function openCliSite(input: {
  site: string;
  title: string;
  description: string;
  iconKey: string;
  functions: ToolFunction[];
}): ToolCatalogItem {
  return {
    id: `opencli-${input.site}`,
    kind: "opencli-site",
    title: input.title,
    description: input.description,
    category: "social",
    iconKey: input.iconKey,
    functions: input.functions,
  };
}

const MANUAL_TOOL_CATALOG: ToolCatalogItem[] = [
  baseToolItem({
    id: "base-research",
    title: "搜索与研究",
    description: "搜索公开网页、读取页面内容，并开展多来源深度研究。",
    category: "research",
    iconKey: "search",
    functions: [
      baseToolFunction("WebSearch", "网页搜索", "按关键词检索公开网页并整理来源。", [
        field("query", "搜索主题", "text", {
          required: true,
          placeholder: "输入关键词或问题"
        }),
        field("max_results", "结果数量", "number", {
          defaultValue: 5,
          min: 1,
          max: 20
        }),
        field("recency", "时间范围", "select", {
          options: options(["any", "不限"], ["day", "最近一天"], ["week", "最近一周"], ["month", "最近一月"]),
          defaultValue: "any",
        }),
      ]),
      baseToolFunction("WebFetch", "读取网页", "读取指定网页并提取正文或结构化内容。", [
        field("url", "网页链接", "url", {
          required: true,
          placeholder: "https://example.com"
        }),
        field("format", "提取格式", "select", {
          options: options(["markdown", "Markdown"], ["text", "纯文本"], ["html", "HTML"]),
          defaultValue: "markdown",
        }),
        field("max_length", "内容长度上限", "number", {
          min: 500,
          max: 50000,
          defaultValue: 12000
        }),
      ]),
      baseToolFunction("deep_search", "深度研究", "围绕主题检索多类资料并归纳证据与结论。", [
        field("topic", "研究主题", "textarea", {
          required: true,
          placeholder: "描述需要回答的问题"
        }),
        field("depth", "研究深度", "select", {
          options: options(["balanced", "标准"], ["deep", "深入"], ["quick", "快速" ]),
          defaultValue: "balanced",
        }),
        field("source_types", "重点来源", "multi-select", {
          options: options(["web", "网页"], ["academic", "学术"], ["news", "新闻"], ["forums", "社区讨论"]),
          defaultValue: ["web"],
        }),
      ]),
    ],
  }),
  baseToolItem({
    id: "base-data-analysis",
    title: "数据分析",
    description: "分析表格数据、执行清洗转换、聚合校验与只读 SQL 查询。",
    category: "data",
    iconKey: "chart",
    functions: [
      baseToolFunction("data_analysis", "分析数据", "根据自然语言问题分析数据文件并给出结论。", [
        field("question", "分析问题", "textarea", {
          required: true,
          placeholder: "描述希望从数据中了解什么"
        }),
        field("data_files", "数据文件", "files", {
          accept: ".csv,.tsv,.xlsx,.xls,.json",
          maxFiles: 10
        }),
        field("period_start", "分析起始时间", "datetime"),
        field("include_chart", "生成图表", "boolean", { defaultValue: true }),
      ]),
      baseToolFunction("data_aggregate", "聚合数据", "按分组、透视或统计操作汇总表格数据。", [
        field("file_path", "数据文件路径", "local-path", {
          required: true,
          placeholder: "工作区中的文件路径"
        }),
        field("operation", "聚合方式", "select", {
          required: true,
          options: options(["groupby", "分组汇总"], ["pivot", "透视表"], ["describe", "描述统计"], ["value_counts", "值计数"], ["rolling", "滚动统计"]),
          defaultValue: "groupby",
        }),
        field("group_columns", "分组列", "multi-select", { description: "留空时由 Agent 根据数据列判断。" }),
        field("metric_column", "统计列", "text"),
      ]),
      baseToolFunction("data_clean", "清洗数据", "处理重复行、缺失值、空格和常见类型问题。", [
        field("file_path", "数据文件路径", "local-path", { required: true }),
        field("operations", "清洗操作", "multi-select", {
          required: true,
          options: options(["deduplicate", "删除重复行"], ["trim", "清理首尾空格"], ["fill_missing", "填补缺失值"], ["drop_missing", "删除缺失行"], ["normalize_types", "规范数据类型"]),
          defaultValue: ["deduplicate", "trim"],
        }),
        field("notes", "补充规则", "textarea"),
      ]),
      baseToolFunction("data_merge", "合并数据", "按指定键连接多个表格，或纵向拼接数据集。", [
        field("left_file", "主数据文件", "local-path", { required: true }),
        field("right_file", "待合并文件", "local-path", { required: true }),
        field("join_type", "合并方式", "select", {
          options: options(["inner", "内连接"], ["left", "左连接"], ["right", "右连接"], ["outer", "全连接"], ["concat", "纵向拼接"]),
          defaultValue: "left",
        }),
        field("key_columns", "匹配列", "multi-select"),
      ]),
      baseToolFunction("data_transform", "转换数据", "按规则重命名列、转换类型或映射字段值。", [
        field("file_path", "数据文件路径", "local-path", { required: true }),
        field("transformations", "转换规则", "textarea", {
          required: true,
          placeholder: "描述列名、类型或值的转换方式"
        }),
        field("output_path", "输出路径", "local-path"),
      ]),
      baseToolFunction("data_validate", "校验数据", "依据类型、范围、必填项等规则检查数据质量。", [
        field("file_path", "数据文件路径", "local-path", { required: true }),
        field("rules", "校验规则", "textarea", {
          required: true,
          placeholder: "列出必填列、类型或取值范围"
        }),
        field("strict", "严格模式", "boolean", { defaultValue: false }),
      ]),
      baseToolFunction("sql_query", "只读 SQL 查询", "对当前数据集执行 SELECT 查询并解释结果。", [
        field("query", "查询问题或 SQL", "textarea", {
          required: true,
          placeholder: "描述查询目标，也可直接提供 SELECT 语句"
        }),
        field("result_limit", "结果行数上限", "number", {
          min: 1,
          max: 1000,
          defaultValue: 100
        }),
      ]),
    ],
  }),
  baseToolItem({
    id: "base-document-read",
    title: "文档读取",
    description: "读取常见文档、表格、PDF 与图片，并提取需要的信息。",
    category: "document",
    iconKey: "file-input",
    functions: [
      baseToolFunction("csv_processor", "读取 CSV 表格", "读取、统计或查询 CSV/TSV 表格内容。", [
        field("file", "CSV/TSV 文件", "file", {
          required: true,
          accept: ".csv,.tsv"
        }),
        field("operation", "读取方式", "select", {
          options: options(["read", "读取行"], ["stats", "统计摘要"], ["query", "筛选查询"], ["convert", "格式转换"]),
          defaultValue: "read"
        }),
        field("row_limit", "读取行数", "number", {
          min: 1,
          max: 5000,
          defaultValue: 200
        }),
      ]),
      baseToolFunction("excel_reader", "读取 Excel", "读取 Excel 工作表、指定区域或表格摘要。", [
        field("file", "Excel 文件", "file", {
          required: true,
          accept: ".xlsx,.xls,.xlsm"
        }),
        field("sheet", "工作表名称", "text"),
        field("cell_range", "单元格范围", "text", { placeholder: "例如 A1:F30" }),
      ]),
      baseToolFunction("html_processor", "读取 HTML", "读取 HTML 文件并提取链接、表格或页面元数据。", [
        field("file", "HTML 文件", "file", {
          required: true,
          accept: ".html,.htm"
        }),
        field("operation", "提取内容", "select", {
          options: options(["read", "正文"], ["extract_links", "链接"], ["extract_tables", "表格"], ["extract_metadata", "页面元数据"]),
          defaultValue: "read"
        }),
        field("selector", "CSS 选择器", "text"),
      ]),
      baseToolFunction("markdown_processor", "读取 Markdown", "读取 Markdown 文档并提取标题、内容或关键词上下文。", [
        field("file", "Markdown 文件", "file", {
          required: true,
          accept: ".md,.markdown"
        }),
        field("operation", "读取方式", "select", {
          options: options(["read", "读取全文"], ["outline", "提取标题大纲"], ["search", "搜索关键词"]),
          defaultValue: "read"
        }),
        field("keyword", "搜索关键词", "text"),
      ]),
      baseToolFunction("text_processor", "读取文本文件", "读取文本并按需查找内容或分析差异。", [
        field("file", "文本文件", "file", {
          required: true,
          accept: ".txt,.log,.csv,.json,.xml"
        }),
        field("operation", "读取方式", "select", {
          options: options(["read", "读取全文"], ["search", "查找内容"], ["stats", "文本统计"], ["diff", "比较差异"]),
          defaultValue: "read"
        }),
        field("keyword", "查找内容", "text"),
      ]),
      baseToolFunction("word_reader", "读取 Word 文档", "提取 Word 文档的段落、标题和表格内容。", [
        field("file", "Word 文档", "file", {
          required: true,
          accept: ".doc,.docx,.docm"
        }),
        field("section", "目标章节", "text"),
        field("include_tables", "包含表格", "boolean", { defaultValue: true }),
      ]),
      baseToolFunction("pdf_reader", "读取 PDF", "读取 PDF 页面、检索文本或提取表格。", [
        field("file", "PDF 文件", "file", {
          required: true,
          accept: ".pdf"
        }),
        field("operation", "读取方式", "select", {
          options: options(["read", "读取正文"], ["extract_tables", "提取表格"], ["search", "搜索文本"], ["extract_links", "提取链接"]),
          defaultValue: "read"
        }),
        field("pages", "页码范围", "text", { placeholder: "例如 1-5" }),
        field("keyword", "搜索词", "text"),
      ]),
      baseToolFunction("pdf_structure", "分析 PDF 结构", "提取 PDF 页数、目录书签和章节结构。", [
        field("file", "PDF 文件", "file", {
          required: true,
          accept: ".pdf"
        }),
        field("include_headings", "识别章节标题", "boolean", { defaultValue: true }),
      ]),
      baseToolFunction("citation_extractor", "提取文献引用", "从学术 PDF 中提取参考文献列表。", [
        field("file", "学术 PDF", "file", {
          required: true,
          accept: ".pdf"
        }),
        field("citation_style", "引用格式", "select", {
          options: options(["auto", "自动识别"], ["apa", "APA"], ["gbt7714", "GB/T 7714"]),
          defaultValue: "auto"
        }),
      ]),
      baseToolFunction("image_ocr", "图片文字识别", "识别图片中的文字并保留主要段落与字段结构。", [
        field("files", "图片文件", "files", {
          required: true,
          accept: "image/*",
          minFiles: 1,
          maxFiles: 10
        }),
        field("languages", "识别语言", "multi-select", {
          options: options(["zh", "中文"], ["en", "英文"]),
          defaultValue: ["zh", "en"]
        }),
      ]),
    ],
  }),
  baseToolItem({
    id: "base-document-create",
    title: "文档生成",
    description: "生成文档、演示文稿、表格、清单、模板、主题与图表文件。",
    category: "creation",
    iconKey: "file-output",
    functions: [
      baseToolFunction("document_generate", "生成文档", "根据内容生成 PDF、DOCX、HTML 或 Markdown 文档。", [
        field("title", "文档标题", "text", { required: true }),
        field("content", "文档内容", "textarea", {
          required: true,
          placeholder: "输入或描述需要整理的内容"
        }),
        field("format", "文件格式", "select", {
          options: options(["docx", "Word"], ["pdf", "PDF"], ["html", "HTML"], ["markdown", "Markdown"]),
          defaultValue: "docx"
        }),
        field("output_path", "输出路径", "local-path", { required: true }),
      ], "write"),
      baseToolFunction("slides_generate", "生成演示文稿", "根据大纲生成分页面的 PowerPoint 演示文稿。", [
        field("title", "演示标题", "text", { required: true }),
        field("outline", "演示大纲", "textarea", {
          required: true,
          placeholder: "描述每页主题或粘贴分点大纲"
        }),
        field("slide_count", "页数", "number", {
          min: 1,
          max: 40,
          defaultValue: 10
        }),
        field("theme", "视觉主题", "text"),
      ], "write"),
      baseToolFunction("excel_generator", "生成 Excel", "创建包含多个工作表、表头和公式的 Excel 文件。", [
        field("workbook_name", "工作簿名称", "text", { required: true }),
        field("sheets", "工作表内容", "textarea", {
          required: true,
          placeholder: "逐项描述工作表、列和数据"
        }),
        field("output_path", "输出路径", "local-path"),
      ], "write"),
      baseToolFunction("checklist_generate", "生成清单", "生成可跟踪状态的任务清单并导出文件。", [
        field("items", "清单事项", "textarea", {
          required: true,
          placeholder: "每行一项，可补充负责人或验收标准"
        }),
        field("format", "导出格式", "select", {
          options: options(["markdown", "Markdown"], ["json", "JSON"], ["html", "HTML"], ["pdf", "PDF"], ["docx", "Word"]),
          defaultValue: "markdown"
        }),
        field("due_at", "截止时间", "datetime"),
      ], "write"),
      baseToolFunction("template_filler", "填充文档模板", "使用变量填充现有模板并生成结果文件。", [
        field("template", "模板文件", "file", {
          required: true,
          accept: ".docx,.html,.md,.txt"
        }),
        field("variables", "变量内容", "textarea", {
          required: true,
          placeholder: "填写变量名和值，或描述替换规则"
        }),
        field("output_path", "输出路径", "local-path"),
      ], "write"),
      baseToolFunction("document_template", "管理文档模板", "查看、保存或按已配置模板生成文档。", [
        field("action", "模板操作", "select", {
          required: true,
          options: options(["list", "查看模板"], ["get", "读取模板"], ["save", "保存模板"], ["preview", "预览模板"], ["generate", "生成文档"]),
          defaultValue: "list"
        }),
        field("template_name", "模板名称", "text"),
        field("content", "模板内容或生成说明", "textarea"),
      ], "write"),
      baseToolFunction("theme_designer", "设计文档主题", "创建或查看文档与演示文稿使用的视觉主题。", [
        field("action", "主题操作", "select", {
          required: true,
          options: options(["list", "查看主题"], ["get", "读取主题"], ["create", "创建主题"]),
          defaultValue: "create"
        }),
        field("theme_name", "主题名称", "text"),
        field("style", "风格描述", "textarea", { placeholder: "描述色彩、字体和整体气质" }),
      ], "write"),
      baseToolFunction("chart_generator", "生成图表", "根据结构化数据生成专业图表及配套文件。", [
        field("chart_type", "图表类型", "select", {
          required: true,
          options: options(["bar", "柱状图"], ["line", "折线图"], ["pie", "饼图"], ["scatter", "散点图"], ["area", "面积图"], ["histogram", "直方图"], ["heatmap", "热力图"]),
          defaultValue: "bar"
        }),
        field("data", "图表数据", "textarea", {
          required: true,
          placeholder: "粘贴数据或说明数据所在文件"
        }),
        field("title", "图表标题", "text"),
        field("output_path", "输出路径", "local-path"),
      ], "write"),
    ],
  }),
  baseToolItem({
    id: "base-workspace-browse",
    title: "工作区浏览",
    description: "查看工作区文件、按模式查找文件并搜索文件内容。",
    category: "document",
    iconKey: "folder-search",
    functions: [
      baseToolFunction("workspace_read", "读取工作区文件", "读取工作区中的指定文件或片段。", [
        field("path", "文件路径", "local-path", { required: true }),
        field("line_range", "行范围", "text", { placeholder: "例如 1-120" }),
      ]),
      baseToolFunction("workspace_list", "浏览目录", "列出工作区目录中的文件和子目录。", [
        field("path", "目录路径", "local-path", { defaultValue: "." }),
        field("include_hidden", "包含隐藏文件", "boolean", { defaultValue: false }),
      ]),
      baseToolFunction("workspace_glob", "按模式查找文件", "使用 glob 模式在工作区中查找文件。", [
        field("pattern", "文件匹配模式", "text", {
          required: true,
          placeholder: "例如 **/*.md"
        }),
        field("path", "搜索目录", "local-path", { defaultValue: "." }),
      ]),
      baseToolFunction("workspace_grep", "搜索文件内容", "在工作区文件中搜索文本或正则表达式。", [
        field("query", "搜索内容", "text", { required: true }),
        field("path", "搜索目录", "local-path", { defaultValue: "." }),
        field("case_sensitive", "区分大小写", "boolean", { defaultValue: false }),
      ]),
    ],
  }),
  baseToolItem({
    id: "base-browser",
    title: "浏览器能力",
    description: "通过本机浏览器或 Agent 浏览器访问网页、读取页面并执行交互任务。",
    category: "browser",
    iconKey: "browser",
    functions: [
      baseToolFunction("browser", "操作本机浏览器", "通过已连接的本机浏览器访问页面并执行指定交互。", [
        field("url", "目标网页", "url", { required: true }),
        field("task", "浏览器任务", "textarea", {
          required: true,
          placeholder: "描述要查看或操作的页面内容"
        }),
        field("wait_seconds", "页面等待时间（秒）", "number", {
          min: 0,
          max: 60,
          defaultValue: 5
        }),
      ], "write"),
      baseToolFunction("agent_browser", "操作 Agent 浏览器", "在已配置的 Agent 浏览器会话中访问页面并执行交互。", [
        field("url", "目标网页", "url", { required: true }),
        field("task", "浏览器任务", "textarea", { required: true }),
        field("timeout_seconds", "超时时间（秒）", "number", {
          min: 1,
          max: 300,
          defaultValue: 60
        }),
      ], "write"),
    ],
  }),
  baseToolItem({
    id: "base-image-generation",
    title: "图片生成",
    description: "根据文字提示生成图片，可结合参考图片、画幅比例和视觉风格。",
    category: "creation",
    iconKey: "image",
    functions: [
      baseToolFunction("image_generation_tool", "生成图片", "根据描述生成图片并返回可查看的产物。", [
        field("prompt", "图片描述", "textarea", {
          required: true,
          placeholder: "描述主体、场景、构图和细节"
        }),
        field("aspect_ratio", "画幅比例", "select", {
          options: options(["1:1", "正方形"], ["3:2", "横向"], ["2:3", "纵向"], ["16:9", "宽屏"], ["9:16", "竖屏"]),
          defaultValue: "1:1"
        }),
        field("style", "视觉风格", "text"),
        field("reference_images", "参考图片", "files", {
          accept: "image/*",
          maxFiles: 4
        }),
      ], "write"),
    ],
  }),
  openCliSite({
    site: "douyin",
    title: "抖音",
    description: "搜索抖音内容、查看创作者与数据，并发布视频。",
    iconKey: "douyin",
    functions: [
      openCliFunction("douyin", "search", "搜索视频", "按关键词搜索抖音视频并汇总互动信息。", [
        field("keyword", "搜索关键词", "text", { required: true }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 30,
          defaultValue: 10
        }),
      ]),
      openCliFunction("douyin", "profile", "查看当前账号资料", "查看当前登录抖音账号的公开资料与账号信息。", []),
      openCliFunction("douyin", "user-videos", "查看创作者视频", "列出指定抖音创作者发布的视频。", [
        field("sec_uid", "创作者 sec_uid 或主页链接", "text", {
          required: true,
          description: "sec_uid 可从抖音个人主页链接末尾获取。"
        }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 20,
          defaultValue: 20
        }),
        field("with_comments", "附带热门评论", "boolean", { defaultValue: true }),
        field("comment_limit", "每条视频评论数", "number", {
          min: 1,
          max: 10,
          defaultValue: 10
        }),
      ]),
      openCliFunction("douyin", "stats", "查看作品数据", "查看指定抖音作品在创作者中心的深层指标。", [
        field("aweme_id", "作品 ID", "text", {
          required: true,
          description: "填写作品链接末尾的 16-20 位数字 ID。"
        }),
      ]),
      openCliFunction("douyin", "hashtag", "查看热门话题", "读取抖音热点词；可选关键词用于缩小热点范围。", [
        field("action", "话题操作", "select", {
          required: true,
          options: options(["hot", "查看热点词"]),
          defaultValue: "hot"
        }),
        field("keyword", "筛选关键词", "text"),
        field("limit", "返回数量", "number", {
          min: 1,
          defaultValue: 10
        }),
      ]),
      openCliFunction("douyin", "publish", "发布视频", "发布视频并返回发布结果。", [
        field("video", "视频文件", "file", {
          required: true,
          accept: "video/*"
        }),
        field("title", "视频标题", "text", {
          required: true,
          maxLength: 30
        }),
        field("schedule", "计划发布时间", "datetime", {
          required: true,
          description: "发布时间需在 2 小时至 14 天后。"
        }),
        field("caption", "视频文案", "textarea", {maxLength: 1000}),
        field("cover", "封面图片", "file", { accept: "image/*" }),
        field("visibility", "可见范围", "select", {
          options: options(["public", "公开"], ["friends", "朋友"], ["private", "仅自己"]),
          defaultValue: "public"
        }),
        field("allow_download", "允许下载", "boolean", { defaultValue: false }),
        field("hotspot", "关联热点词", "text"),
      ], "write"),
    ],
  }),
  openCliSite({
    site: "xiaohongshu",
    title: "小红书",
    description: "搜索笔记、查看创作者与评论、浏览 Feed 并发布笔记。",
    iconKey: "xiaohongshu",
    functions: [
      openCliFunction("xiaohongshu", "search", "搜索笔记", "按关键词搜索小红书笔记并整理作者和互动数据。", [
        field("keyword", "搜索关键词", "text", { required: true }),
        field("note_type", "笔记类型", "select", {
          options: options(["all", "全部"], ["image", "图文"], ["video", "视频"]),
          defaultValue: "all"
        }),
        field("sort", "排序方式", "select", {
          options: options(["general", "综合"], ["latest", "最新"], ["most_liked", "最多点赞"]),
          defaultValue: "general"
        }),
        field("published_within", "发布时间", "select", {
          options: options(["any", "不限"], ["week", "最近一周"], ["month", "最近一月"]),
          defaultValue: "any"
        }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 50,
          defaultValue: 20
        }),
      ]),
      openCliFunction("xiaohongshu", "creator-profile", "查看创作者主页", "查看创作者公开资料与主页信息。", [
        field("creator", "创作者主页链接或 ID", "text", { required: true }),
      ]),
      openCliFunction("xiaohongshu", "note", "查看笔记详情", "读取指定笔记内容、作者和互动信息。", [
        field("note", "笔记链接或 ID", "text", { required: true }),
      ]),
      openCliFunction("xiaohongshu", "comments", "查看笔记评论", "读取笔记评论并概括讨论重点。", [
        field("note", "笔记链接或 ID", "text", { required: true }),
        field("limit", "评论数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 30
        }),
        field("sort", "排序方式", "select", {
          options: options(["hot", "热门"], ["latest", "最新"]),
          defaultValue: "hot"
        }),
      ]),
      openCliFunction("xiaohongshu", "feed", "浏览推荐 Feed", "读取小红书推荐 Feed 并整理笔记摘要。", [
        field("feed_type", "Feed 类型", "select", {
          options: options(["home", "首页推荐"], ["follow", "关注"], ["explore", "发现"]),
          defaultValue: "home"
        }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 50,
          defaultValue: 20
        }),
      ]),
      openCliFunction("xiaohongshu", "publish", "发布笔记", "发布图文或视频笔记并返回发布结果。", [
        field("title", "笔记标题", "text", {
          required: true,
          maxLength: 100
        }),
        field("content", "笔记正文", "textarea", {
          required: true,
          maxLength: 5000
        }),
        field("media", "图片或视频", "files", {
          accept: "image/*,video/*",
          minFiles: 1,
          maxFiles: 18
        }),
        field("publish_at", "计划发布时间", "datetime"),
      ], "write"),
    ],
  }),
  openCliSite({
    site: "twitter",
    title: "Twitter / X",
    description: "搜索帖子、查看账号与话题串，并发布帖子或回复。",
    iconKey: "twitter-x",
    functions: [
      openCliFunction("twitter", "search", "搜索帖子", "按关键词搜索公开帖子并整理作者与互动情况。", [
        field("query", "搜索关键词", "text", { required: true }),
        field("sort", "排序方式", "select", {
          options: options(["relevance", "相关度"], ["latest", "最新"], ["popular", "热门"]),
          defaultValue: "relevance"
        }),
        field("since", "开始时间", "datetime"),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 20
        }),
      ]),
      openCliFunction("twitter", "profile", "查看账号资料", "查看 Twitter/X 用户公开资料。", [
        field("username", "用户名或主页链接", "text", {
          required: true,
          placeholder: "例如 username"
        }),
      ]),
      openCliFunction("twitter", "thread", "查看帖子串", "读取指定帖子及其上下文串联内容。", [
        field("tweet", "帖子链接或 ID", "url", { required: true }),
      ]),
      openCliFunction("twitter", "trending", "查看热门趋势", "查看指定地区的热门趋势话题。", [
        field("region", "地区", "select", {
          options: options(["worldwide", "全球"], ["us", "美国"], ["jp", "日本"], ["gb", "英国"]),
          defaultValue: "worldwide"
        }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 50,
          defaultValue: 20
        }),
      ]),
      openCliFunction("twitter", "tweets", "查看用户帖子", "读取指定用户近期发布的帖子。", [
        field("username", "用户名或主页链接", "text", { required: true }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 20
        }),
        field("since", "开始时间", "datetime"),
      ]),
      openCliFunction("twitter", "post", "发布帖子", "发布一条帖子并返回发布结果。", [
        field("text", "帖子内容", "textarea", {
          required: true,
          maxLength: 280
        }),
        field("media", "媒体文件", "files", {
          accept: "image/*,video/*",
          maxFiles: 4
        }),
      ], "write"),
      openCliFunction("twitter", "reply", "回复帖子", "向指定帖子发布回复并返回结果。", [
        field("tweet", "目标帖子链接", "url", { required: true }),
        field("text", "回复内容", "textarea", {
          required: true,
          maxLength: 280
        }),
      ], "write"),
    ],
  }),
  openCliSite({
    site: "reddit",
    title: "Reddit",
    description: "搜索社区内容、查看帖子与用户，并参与评论讨论。",
    iconKey: "reddit",
    functions: [
      openCliFunction("reddit", "search", "搜索帖子", "按关键词检索 Reddit 帖子。", [
        field("query", "搜索关键词", "text", { required: true }),
        field("subreddit", "限定社区", "text", { placeholder: "例如 technology" }),
        field("sort", "排序方式", "select", {
          options: options(["relevance", "相关度"], ["new", "最新"], ["top", "高分"]),
          defaultValue: "relevance"
        }),
        field("time", "时间范围", "select", {
          options: options(["all", "不限"], ["day", "一天"], ["week", "一周"], ["month", "一月"]),
          defaultValue: "all"
        }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 25
        }),
      ]),
      openCliFunction("reddit", "hot", "查看热门帖子", "读取热门帖子并整理标题、社区和互动数据。", [
        field("subreddit", "社区名称", "text", { placeholder: "留空查看站点热门内容" }),
        field("time", "时间范围", "select", {
          options: options(["day", "一天"], ["week", "一周"], ["month", "一月"]),
          defaultValue: "day"
        }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 25
        }),
      ]),
      openCliFunction("reddit", "subreddit", "查看社区", "查看指定 subreddit 的简介和基本信息。", [
        field("name", "社区名称", "text", {
          required: true,
          placeholder: "例如 technology"
        }),
      ]),
      openCliFunction("reddit", "user", "查看用户资料", "查看 Reddit 用户的公开资料。", [
        field("username", "用户名", "text", { required: true }),
      ]),
      openCliFunction("reddit", "read", "查看帖子详情", "读取 Reddit 帖子正文及相关上下文。", [
        field("post", "帖子链接", "url", { required: true }),
      ]),
      openCliFunction("reddit", "comment", "发表评论", "在指定 Reddit 帖子下发布评论。", [
        field("post", "帖子链接", "url", { required: true }),
        field("content", "评论内容", "textarea", { required: true }),
      ], "write"),
      openCliFunction("reddit", "reply", "回复评论", "回复指定 Reddit 评论。", [
        field("comment", "评论链接或 ID", "text", { required: true }),
        field("content", "回复内容", "textarea", { required: true }),
      ], "write"),
    ],
  }),
  openCliSite({
    site: "bilibili",
    title: "B 站",
    description: "搜索视频、查看热门与排行榜、读取视频评论并发表评论。",
    iconKey: "bilibili",
    functions: [
      openCliFunction("bilibili", "search", "搜索视频", "搜索 B 站视频并整理播放与互动信息。", [
        field("keyword", "搜索关键词", "text", { required: true }),
        field("category", "分区", "select", {
          options: options(["all", "全部"], ["video", "视频"], ["live", "直播"]),
          defaultValue: "all"
        }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 20
        }),
      ]),
      openCliFunction("bilibili", "hot", "查看热门视频", "读取 B 站热门视频列表。", [
        field("category", "分区", "text", { placeholder: "留空查看全站热门" }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 20
        }),
      ]),
      openCliFunction("bilibili", "ranking", "查看视频排行榜", "按分区和周期查看视频排行榜。", [
        field("category", "分区", "select", {
          options: options(["all", "全站"], ["bangumi", "番剧"], ["music", "音乐"], ["knowledge", "知识"], ["technology", "科技"]),
          defaultValue: "all"
        }),
        field("period", "统计周期", "select", {
          options: options(["day", "日榜"], ["week", "周榜"], ["month", "月榜"]),
          defaultValue: "day"
        }),
      ]),
      openCliFunction("bilibili", "video", "查看视频详情", "读取 B 站视频信息、简介和公开统计。", [
        field("video", "视频链接或 BV 号", "text", { required: true }),
      ]),
      openCliFunction("bilibili", "comments", "查看视频评论", "读取视频评论并整理讨论重点。", [
        field("video", "视频链接或 BV 号", "text", { required: true }),
        field("limit", "评论数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 30
        }),
        field("sort", "排序方式", "select", {
          options: options(["hot", "热门"], ["latest", "最新"]),
          defaultValue: "hot"
        }),
      ]),
      openCliFunction("bilibili", "user-videos", "查看创作者视频", "列出指定 B 站创作者发布的视频。", [
        field("user", "用户空间链接或 UID", "text", { required: true }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 20
        }),
      ]),
      openCliFunction("bilibili", "comment", "发表评论", "在指定 B 站视频下发布评论。", [
        field("video", "视频链接或 BV 号", "text", { required: true }),
        field("content", "评论内容", "textarea", {
          required: true,
          maxLength: 1000
        }),
      ], "write"),
    ],
  }),
  openCliSite({
    site: "weibo",
    title: "微博",
    description: "搜索微博、查看热榜与用户内容，并发布微博。",
    iconKey: "weibo",
    functions: [
      openCliFunction("weibo", "search", "搜索微博", "按关键词搜索微博内容并整理发布时间和互动信息。", [
        field("keyword", "搜索关键词", "text", { required: true }),
        field("scope", "搜索范围", "select", {
          options: options(["all", "全部"], ["hot", "热门"], ["user", "用户"]),
          defaultValue: "all"
        }),
        field("since", "开始时间", "datetime"),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 20
        }),
      ]),
      openCliFunction("weibo", "hot", "查看微博热榜", "读取微博热搜榜单并概括热门话题。", [
        field("category", "榜单分类", "text", { placeholder: "留空查看综合热榜" }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 50,
          defaultValue: 20
        }),
      ]),
      openCliFunction("weibo", "user", "查看用户主页", "查看微博用户公开资料与近期内容。", [
        field("user", "用户昵称、UID 或主页链接", "text", { required: true }),
      ]),
      openCliFunction("weibo", "post", "查看微博详情", "读取指定微博正文和公开互动信息。", [
        field("post", "微博链接或 ID", "text", { required: true }),
      ]),
      openCliFunction("weibo", "comments", "查看微博评论", "读取指定微博评论并整理讨论内容。", [
        field("post", "微博链接或 ID", "text", { required: true }),
        field("limit", "评论数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 30
        }),
      ]),
      openCliFunction("weibo", "publish", "发布微博", "发布微博文字或附带图片并返回操作结果。", [
        field("content", "微博内容", "textarea", {
          required: true,
          maxLength: 2000
        }),
        field("images", "图片", "files", {
          accept: "image/*",
          maxFiles: 9
        }),
        field("publish_at", "计划发布时间", "datetime"),
      ], "write"),
    ],
  }),
  openCliSite({
    site: "zhihu",
    title: "知乎",
    description: "搜索问题与回答、查看热榜和用户资料，并发布回答或评论。",
    iconKey: "zhihu",
    functions: [
      openCliFunction("zhihu", "search", "搜索知乎内容", "搜索知乎问题、回答或文章。", [
        field("query", "搜索关键词", "text", { required: true }),
        field("content_type", "内容类型", "select", {
          options: options(["all", "全部"], ["question", "问题"], ["answer", "回答"], ["article", "文章"]),
          defaultValue: "all"
        }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 20
        }),
      ]),
      openCliFunction("zhihu", "hot", "查看知乎热榜", "读取知乎热门问题并整理热度与回答信息。", [
        field("category", "话题分类", "text", { placeholder: "留空查看综合热榜" }),
        field("limit", "返回数量", "number", {
          min: 1,
          max: 50,
          defaultValue: 20
        }),
      ]),
      openCliFunction("zhihu", "user", "查看用户资料", "查看知乎用户公开资料与内容概况。", [
        field("user", "用户主页链接或 ID", "text", { required: true }),
      ]),
      openCliFunction("zhihu", "question", "查看问题详情", "读取指定知乎问题及其回答概况。", [
        field("question", "问题链接或 ID", "url", { required: true }),
      ]),
      openCliFunction("zhihu", "answer-detail", "查看回答详情", "读取指定回答正文、作者与互动信息。", [
        field("answer", "回答链接或 ID", "url", { required: true }),
      ]),
      openCliFunction("zhihu", "answer-comments", "查看回答评论", "读取指定回答下的评论。", [
        field("answer", "回答链接或 ID", "url", { required: true }),
        field("limit", "评论数量", "number", {
          min: 1,
          max: 100,
          defaultValue: 30
        }),
      ]),
      openCliFunction("zhihu", "answer", "发布回答", "在指定知乎问题下发布回答。", [
        field("question", "问题链接或 ID", "url", { required: true }),
        field("content", "回答内容", "textarea", {
          required: true,
          maxLength: 20000
        }),
        field("publish_at", "计划发布时间", "datetime"),
      ], "write"),
      openCliFunction("zhihu", "comment", "发表评论", "在指定知乎回答下发布评论。", [
        field("answer", "回答链接或 ID", "url", { required: true }),
        field("content", "评论内容", "textarea", {
          required: true,
          maxLength: 1000
        }),
      ], "write"),
    ],
  }),
];

const legacyOpenCliSites = MANUAL_TOOL_CATALOG.filter(
  (item) => item.kind === "opencli-site",
);
const generatedOpenCliSites = createOpenCliToolCatalog(legacyOpenCliSites);
const generatedSiteIds = new Set(generatedOpenCliSites.map((item) => item.id));

export const TOOL_CATALOG: ToolCatalogItem[] = [
  ...MANUAL_TOOL_CATALOG.filter((item) => item.kind === "base-tool"),
  ...generatedOpenCliSites,
  ...legacyOpenCliSites.filter((item) => !generatedSiteIds.has(item.id)),
];
