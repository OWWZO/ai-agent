import { OPENCLI_SITE_DEFINITIONS } from "./openCliCatalog.generated";
import { buildToolPrompt } from "./toolCatalogModel";
import type {
  ToolCatalogItem,
  ToolField,
  ToolFunction,
} from "./toolCatalog";
import type {
  OpenCliArgumentDefinition,
  OpenCliCommandDefinition,
  OpenCliSiteDefinition,
} from "./openCliManifestTypes";

const argumentLabels: Record<string, string> = {
  action: "操作",
  author: "作者",
  category: "分类",
  comment: "评论内容",
  country: "国家或地区",
  date: "日期",
  file: "文件",
  image: "图片",
  id: "ID",
  input: "输入内容",
  keyword: "关键词",
  language: "语言",
  limit: "返回数量",
  media: "媒体文件",
  name: "名称",
  order: "排序",
  page: "页码",
  path: "路径",
  person: "人物照片",
  query: "搜索内容",
  search: "搜索词",
  sort: "排序方式",
  source: "源图片",
  target: "目标图片",
  text: "文本",
  title: "标题",
  type: "类型",
  url: "链接",
  user: "用户",
  username: "用户名",
  video: "视频文件",
  cover: "封面图片",
  "cover-image": "封面图片",
  cloth: "服装图片",
};

const commandTitles: Record<string, string> = {
  answer: "发布回答",
  "answer-comments": "查看回答评论",
  "answer-detail": "查看回答详情",
  comment: "查看或发布评论",
  comments: "查看评论",
  episodes: "查看播客单集",
  feed: "浏览动态",
  hot: "查看热门内容",
  item: "查看档案项目",
  login: "登录",
  news: "查看资讯",
  post: "查看或发布内容",
  profile: "查看账号资料",
  publish: "发布内容",
  question: "查看问题",
  read: "读取内容",
  reply: "回复内容",
  search: "搜索内容",
  top: "查看排行榜",
  trending: "查看趋势",
  user: "查看用户资料",
  video: "查看视频",
};

function argumentLabel(name: string): string {
  return argumentLabels[name] ?? name.replace(/[-_]+/g, " ");
}

function argumentType(argument: OpenCliArgumentDefinition): ToolField["type"] {
  if (argument.choices?.length) {
    return "select";
  }
  if (argument.type === "bool" || argument.type === "boolean") {
    return "boolean";
  }
  if (argument.type === "int" || argument.type === "number") {
    return "number";
  }
  if (/download directory|directory path/i.test(argument.help)) {
    return "local-path";
  }
  if (/comma-separated .*paths/i.test(argument.help)) {
    return "files";
  }
  if (
    /(?:local file path|file path|path to .*file|文件路径|图片路径|视频文件路径)/i.test(
      argument.help,
    )
  ) {
    return "file";
  }
  if (
    /(?:^|[-_])(url|link)$/i.test(argument.name) &&
    !/\b(or|\/).*\b(id|identifier)\b/i.test(argument.help)
  ) {
    return "url";
  }
  if (/\b(?:image|photo|cover|source|target|person|cloth)\b.*\bURL\b/i.test(argument.help)) {
    return "url";
  }
  if (["content", "description", "prompt", "text", "body"].includes(argument.name)) {
    return "textarea";
  }
  return "text";
}

function fileAccept(argument: OpenCliArgumentDefinition, type: ToolField["type"]): string | undefined {
  if (type !== "file" && type !== "files") return undefined;
  if (/image.*video|video.*image|images\/videos/i.test(argument.help)) {
    return "image/*,video/*";
  }
  if (/video/i.test(argument.name + argument.help)) return "video/*";
  if (/image|photo|cover|person|cloth/i.test(argument.name + argument.help)) {
    return "image/*";
  }
  const extensions = argument.help.match(/\.(?:mp4|mov|avi|webm|png|jpe?g|gif|webp)\b/gi);
  return extensions?.join(",");
}

function maxFileCount(argument: OpenCliArgumentDefinition): number | undefined {
  const match = argument.help.match(/\bup to (\d+)\b/i);
  return match ? Number(match[1]) : undefined;
}

function mapArgument(
  argument: OpenCliArgumentDefinition,
  legacyField?: ToolField,
): ToolField {
  const type = legacyField?.type ?? argumentType(argument);
  const defaultValue = argument.default ?? legacyField?.defaultValue;

  return {
    name: argument.name,
    label: legacyField?.label ?? argumentLabel(argument.name),
    type,
    required: argument.required,
    placeholder: legacyField?.placeholder,
    description: legacyField?.description ?? argument.help,
    defaultValue:
      type === "select" && defaultValue !== undefined
        ? String(defaultValue)
        : defaultValue,
    options:
      argument.choices?.map((value) => ({
        value,
        label: value,
      })) ??
      legacyField?.options,
    accept: legacyField?.accept ?? fileAccept(argument, type),
    min: legacyField?.min,
    max: legacyField?.max,
    maxLength: legacyField?.maxLength,
    minFiles: legacyField?.minFiles,
    maxFiles: legacyField?.maxFiles ?? maxFileCount(argument),
    positional: argument.positional,
  };
}

function mapCommand(
  site: OpenCliSiteDefinition,
  command: OpenCliCommandDefinition,
  legacyFunction?: ToolFunction,
): ToolFunction {
  const commandName = `${site.site}/${command.name}`;
  const legacyFields = new Map(
    (legacyFunction?.fields ?? []).map((field) => [field.name, field]),
  );
  const fields = command.args.map((argument) =>
    mapArgument(argument, legacyFields.get(argument.name)),
  );
  const access = command.access;

  return {
    id: `opencli-${commandName.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`,
    title: legacyFunction?.title ?? commandTitles[command.name] ?? `命令：${command.name}`,
    description: legacyFunction?.description ?? command.description,
    access,
    browser: command.browser,
    command: commandName,
    fields,
    buildPrompt: (values) =>
      buildToolPrompt({
        kind: "opencli-site",
        command: commandName,
        access,
        browser: command.browser,
        fields,
        values,
      }),
  };
}

function mapSite(
  site: OpenCliSiteDefinition,
  legacySite?: ToolCatalogItem,
): ToolCatalogItem {
  const legacyFunctions = new Map(
    (legacySite?.functions ?? []).map((toolFunction) => [toolFunction.command, toolFunction]),
  );

  return {
    id: `opencli-${site.site}`,
    kind: "opencli-site",
    title: site.title,
    description: site.description,
    category: site.category,
    iconKey: site.iconKey,
    requiresLogin: site.requiresLogin,
    functions: site.commands.map((command) =>
      mapCommand(site, command, legacyFunctions.get(`${site.site}/${command.name}`)),
    ),
  };
}

export function createOpenCliToolCatalog(
  legacySites: readonly ToolCatalogItem[] = [],
): ToolCatalogItem[] {
  const legacyBySite = new Map(
    legacySites.map((site) => [site.id.replace(/^opencli-/, ""), site]),
  );

  return OPENCLI_SITE_DEFINITIONS.map((site) =>
    mapSite(site, legacyBySite.get(site.site)),
  );
}
