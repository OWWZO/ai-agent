/**
 * 预览状态的tab选项
 */
import csvIcon from "@/assets/icon/CSV.png";
import docxIcon from "@/assets/icon/docx.png";
import excleIcon from "@/assets/icon/excle.png";
import pdfIcon from "@/assets/icon/pdf.png";
import txtIcon from "@/assets/icon/txt.png";
import htmlIcon from "@/assets/icon/HTML.png";
import demo1 from "@/assets/icon/demo1.png";
import demo2 from "@/assets/icon/demo2.png";
import demo3 from "@/assets/icon/demo3.png";
import demo4 from "@/assets/icon/demo4.png";

import { ActionViewItemEnum } from "./enums";

export const iconType: Record<string, string> = {
  doc: docxIcon,
  docx: docxIcon,
  xlsx: excleIcon,
  csv: csvIcon,
  pdf: pdfIcon,
  txt: txtIcon,
  html: htmlIcon,
};

export const actionViewOptions = [
  {
    label: "动态",
    value: ActionViewItemEnum.follow,
    split: false,
  },
  {
    label: "文件",
    value: ActionViewItemEnum.file,
  },
];

export const defaultActiveActionView = actionViewOptions[0].value;

export type SuggestedQuestionIcon =
  | "youtube"
  | "instagram"
  | "creator"
  | "tiktok"
  | "x"
  | "reddit"
  | "shopping"
  | "megaphone"
  | "search"
  | "research"
  | "chart"
  | "amazon"
  | "google"
  | "meta"
  | "openai"
  | "seo"
  | "link"
  | "mention"
  | "company"
  | "linkedin"
  | "news"
  | "map"
  | "person"
  | "industry"
  | "briefcase"
  | "app";

export type SuggestedQuestion = {
  id: string;
  category: string;
  label: string;
  template: string;
  icon: SuggestedQuestionIcon;
  placeholders?: string[];
  deepThink?: boolean;
};

const socialMediaQuestions: SuggestedQuestion[] = [
  {
    id: "youtube-channels",
    category: "社交媒体",
    label: "找一个领域的 YouTube 频道",
    template:
      "帮我找 5 个关于【主题或细分领域】的 YouTube 频道，来源包括频道搜索和热门视频背后的频道。逐个查看并整理成表格。最后生成一份全面的报告，说明哪些最符合要求以及原因。",
    icon: "youtube",
    placeholders: ["【主题或细分领域】"],
  },
  {
    id: "youtube-comments",
    category: "社交媒体",
    label: "从 YouTube 评论里提取观众洞察",
    template:
      "从 YouTube 上关于【主题或视频链接】的视频评论里提取观众洞察，整理高频需求、抱怨、疑问和可行动结论，输出一份结构化分析。",
    icon: "youtube",
    placeholders: ["【主题或视频链接】"],
  },
  {
    id: "instagram-benchmark",
    category: "社交媒体",
    label: "找值得对标的 Instagram 账号",
    template:
      "找 5 个值得对标的 Instagram 账号，主题是【主题或细分领域】。分析账号定位、内容栏目、更新频率、互动表现和可复用的选题方向。",
    icon: "instagram",
    placeholders: ["【主题或细分领域】"],
  },
  {
    id: "creator-research",
    category: "社交媒体",
    label: "全平台调研一位达人",
    template:
      "全平台调研达人【达人名称或主页链接】，整理其主要平台、受众画像、内容风格、代表作品、商业合作和近期动态，最后给出合作建议。",
    icon: "creator",
    placeholders: ["【达人名称或主页链接】"],
  },
  {
    id: "tiktok-trends",
    category: "社交媒体",
    label: "看 TikTok 上这周什么在火",
    template:
      "调研 TikTok 本周关于【主题或行业】的热门内容，整理高频话题、视频结构、常见钩子、音乐和评论区反馈，并总结可借鉴的内容机会。",
    icon: "tiktok",
    placeholders: ["【主题或行业】"],
  },
  {
    id: "tiktok-breakdown",
    category: "社交媒体",
    label: "拆解一条 TikTok 爆款视频",
    template:
      "拆解这条 TikTok 爆款视频【视频链接】，从开头钩子、叙事结构、画面节奏、字幕、评论反馈和传播原因几个角度进行分析，并给出复刻建议。",
    icon: "tiktok",
    placeholders: ["【视频链接】"],
  },
  {
    id: "x-topic-discussion",
    category: "社交媒体",
    label: "总结一个话题在 X 上的讨论",
    template:
      "总结 X 上关于【话题】的近期讨论，区分主要观点、争议点、代表性用户和情绪变化，并输出一份带来源的舆情摘要。",
    icon: "x",
    placeholders: ["【话题】"],
  },
  {
    id: "reddit-complaints",
    category: "社交媒体",
    label: "看 Reddit 上大家在抱怨什么",
    template:
      "调研 Reddit 上关于【产品、行业或问题】的讨论，重点整理用户抱怨、未被满足的需求、替代方案和高频原话，最后归纳出产品机会。",
    icon: "reddit",
    placeholders: ["【产品、行业或问题】"],
  },
];

const ecommerceQuestions: SuggestedQuestion[] = [
  {
    id: "amazon-product-page",
    category: "电商",
    label: "诊断一个 Amazon 商品页",
    template:
      "诊断 Amazon 商品页【商品链接】，从标题、五点描述、主图、A+ 内容、关键词覆盖、评价和转化风险几个角度分析，并给出优化优先级。",
    icon: "amazon",
    placeholders: ["【商品链接】"],
  },
  {
    id: "amazon-competitor-comparison",
    category: "电商",
    label: "对比我的商品和 Amazon 上的竞品",
    template:
      "对比我的商品【商品链接】和 Amazon 上的竞品【竞品链接】，比较价格、卖点、功能、评价、差评和页面表达，最后给出竞争策略。",
    icon: "amazon",
    placeholders: ["【商品链接】", "【竞品链接】"],
  },
  {
    id: "amazon-bestseller-opportunity",
    category: "电商",
    label: "从畅销榜里找到切入机会",
    template:
      "研究 Amazon【类目名称】畅销榜，找出需求增长、竞争尚未饱和且有切入机会的细分方向，整理代表商品、用户痛点和进入建议。",
    icon: "amazon",
    placeholders: ["【类目名称】"],
  },
  {
    id: "amazon-keyword-research",
    category: "电商",
    label: "做一次 Amazon 关键词调研",
    template:
      "围绕商品【商品名称或链接】做一次 Amazon 关键词调研，整理核心词、长尾词、竞品词、搜索意图和页面布局建议。",
    icon: "amazon",
    placeholders: ["【商品名称或链接】"],
  },
  {
    id: "tiktok-shop-category",
    category: "电商",
    label: "摸清 TikTok Shop 上的一个品类",
    template:
      "摸清 TikTok Shop 上的品类【品类名称】，调研热销商品、价格带、内容卖点、达人带货方式、评论反馈和潜在机会。",
    icon: "tiktok",
    placeholders: ["【品类名称】"],
  },
  {
    id: "tiktok-shop-product",
    category: "电商",
    label: "找出一个 TikTok Shop 商品为什么好卖",
    template:
      "分析 TikTok Shop 商品【商品链接】为什么好卖，从商品本身、视频钩子、达人、评论、价格和转化路径几个角度拆解，并总结可复用打法。",
    icon: "tiktok",
    placeholders: ["【商品链接】"],
  },
  {
    id: "tiktok-commerce-creators",
    category: "电商",
    label: "找能帮我带货的 TikTok 达人",
    template:
      "为商品【商品名称或链接】寻找适合带货的 TikTok 达人，比较受众、内容风格、互动质量、历史带货表现和合作建议。",
    icon: "creator",
    placeholders: ["【商品名称或链接】"],
  },
  {
    id: "product-direction-validation",
    category: "电商",
    label: "验证几个产品方向",
    template:
      "验证以下产品方向是否值得做：【产品方向 1】、【产品方向 2】、【产品方向 3】。从需求、竞争、用户评价、渠道和商业化角度比较，并给出优先级。",
    icon: "google",
    placeholders: ["【产品方向 1】", "【产品方向 2】", "【产品方向 3】"],
  },
];

const advertisingQuestions: SuggestedQuestion[] = [
  {
    id: "competitor-ad-breakdown",
    category: "广告与素材",
    label: "拆解竞品的广告",
    template:
      "拆解竞品【品牌或产品】的广告素材，分析目标受众、核心卖点、开场钩子、画面结构、文案、CTA 和落地页，最后总结可复用的创意规律。",
    icon: "google",
    placeholders: ["【品牌或产品】"],
  },
  {
    id: "long-running-category-ads",
    category: "广告与素材",
    label: "找我所在品类里投放最久的广告",
    template:
      "找出【品类或市场】里持续投放时间最长的广告，分析它们的素材变化、卖点稳定性、受众、渠道和长期有效的原因。",
    icon: "meta",
    placeholders: ["【品类或市场】"],
  },
  {
    id: "competitor-ad-library",
    category: "广告与素材",
    label: "对比竞品的广告投放",
    template:
      "对比【品牌 A】、【品牌 B】和【品牌 C】的广告投放，整理渠道、素材形式、文案主题、投放节奏和主推产品，并给出差异化建议。",
    icon: "meta",
    placeholders: ["【品牌 A】", "【品牌 B】", "【品牌 C】"],
  },
  {
    id: "new-competitor-ads",
    category: "广告与素材",
    label: "发现竞品的新广告",
    template:
      "追踪竞品【品牌或产品】最近发布的新广告，整理新增素材、主推卖点、投放平台、受众变化和可能的营销意图。",
    icon: "google",
    placeholders: ["【品牌或产品】"],
  },
  {
    id: "tiktok-hot-ads",
    category: "广告与素材",
    label: "找一个产品的 TikTok 热门广告",
    template:
      "寻找产品【产品或品类】在 TikTok 上表现好的广告，分析开头三秒、脚本结构、镜头、达人表达、评论反馈和可复刻元素。",
    icon: "tiktok",
    placeholders: ["【产品或品类】"],
  },
  {
    id: "viral-ad-remix",
    category: "广告与素材",
    label: "为我的产品改编一条爆款广告",
    template:
      "参考爆款广告【广告链接或描述】，为我的产品【产品名称】改编一条广告，保留有效结构但替换成适合我的卖点、受众和场景。",
    icon: "meta",
    placeholders: ["【广告链接或描述】", "【产品名称】"],
  },
  {
    id: "customer-language-hooks",
    category: "广告与素材",
    label: "用客户的原话写广告钩子",
    template:
      "从关于【产品或问题】的用户评论和讨论中提取真实原话，改写成 15 个广告开场钩子，并说明每个钩子对应的痛点和适合的素材形式。",
    icon: "reddit",
    placeholders: ["【产品或问题】"],
  },
  {
    id: "search-ad-keywords",
    category: "广告与素材",
    label: "规划搜索广告关键词",
    template:
      "为【产品或服务】规划搜索广告关键词，按高意向词、比较词、问题词和竞品词分组，并给出匹配的广告标题、描述和否定关键词。",
    icon: "google",
    placeholders: ["【产品或服务】"],
  },
];

const seoQuestions: SuggestedQuestion[] = [
  {
    id: "brand-ai-visibility",
    category: "SEO 与 AI 搜索",
    label: "查看我的品牌在 AI 搜索中的可见度",
    template:
      "查看品牌【品牌名称或网站】在 AI 搜索中的可见度，测试用户可能提出的问题，整理品牌是否被提及、引用了哪些来源，以及提升可见度的建议。",
    icon: "openai",
    placeholders: ["【品牌名称或网站】"],
  },
  {
    id: "ai-topic-questions",
    category: "SEO 与 AI 搜索",
    label: "找出人们就我的主题问 AI 的问题",
    template:
      "围绕主题【主题或产品】找出用户可能向 AI 提出的问题，按入门、比较、购买和使用阶段分类，并给出内容覆盖优先级。",
    icon: "openai",
    placeholders: ["【主题或产品】"],
  },
  {
    id: "website-seo-diagnosis",
    category: "SEO 与 AI 搜索",
    label: "诊断我的网站 SEO",
    template:
      "诊断网站【网站链接】的 SEO，检查技术结构、页面质量、关键词、内部链接、外链和可抓取性，并列出最值得优先修复的问题。",
    icon: "seo",
    placeholders: ["【网站链接】"],
  },
  {
    id: "competitor-ranking-gaps",
    category: "SEO 与 AI 搜索",
    label: "找出竞品有排名而我没有的关键词",
    template:
      "比较我的网站【我的网站】和竞品【竞品网站】，找出竞品有排名而我没有覆盖的关键词，并按搜索意图、难度和商业价值排序。",
    icon: "google",
    placeholders: ["【我的网站】", "【竞品网站】"],
  },
  {
    id: "competitor-backlink-gaps",
    category: "SEO 与 AI 搜索",
    label: "找出竞品有而我没有的外链",
    template:
      "比较网站【我的网站】和竞品【竞品网站】的外链来源，找出竞品获得但我还没有的高价值链接，并给出获取路径。",
    icon: "link",
    placeholders: ["【我的网站】", "【竞品网站】"],
  },
  {
    id: "topic-keyword-map",
    category: "SEO 与 AI 搜索",
    label: "挖掘一个主题的关键词",
    template:
      "围绕主题【主题】挖掘关键词，覆盖核心词、问题词、长尾词、比较词和交易词，并整理成可用于内容规划的主题集群。",
    icon: "google",
    placeholders: ["【主题】"],
  },
  {
    id: "keyword-ranking-threshold",
    category: "SEO 与 AI 搜索",
    label: "分析一个关键词的排名门槛",
    template:
      "分析关键词【关键词】的排名门槛，研究当前结果页、头部页面、内容深度、域名竞争力、外链和用户意图，并判断我是否值得进入。",
    icon: "google",
    placeholders: ["【关键词】"],
  },
  {
    id: "brand-mentions",
    category: "SEO 与 AI 搜索",
    label: "监测我的品牌在网上的提及",
    template:
      "监测品牌【品牌名称】近期在网上的提及，整理出现渠道、讨论主题、正负面情绪、代表性原话和需要跟进的机会。",
    icon: "mention",
    placeholders: ["【品牌名称】"],
  },
];

const deepResearchQuestions: SuggestedQuestion[] = [
  {
    id: "company-research",
    category: "深度调研",
    label: "调研一家公司的业务与动态",
    template:
      "调研公司【公司名称】，梳理业务、产品、商业模式、财务、组织、客户、竞争格局和最近一年动态，并标注关键信息来源。",
    icon: "company",
    placeholders: ["【公司名称】"],
    deepThink: true,
  },
  {
    id: "competitor-side-by-side",
    category: "深度调研",
    label: "并排对比竞争对手",
    template:
      "并排对比【公司 A】、【公司 B】和【公司 C】，从产品、客户、定价、渠道、商业模式、团队和发展方向进行研究，最后给出判断。",
    icon: "linkedin",
    placeholders: ["【公司 A】", "【公司 B】", "【公司 C】"],
    deepThink: true,
  },
  {
    id: "company-industry-news",
    category: "深度调研",
    label: "追踪一家公司的或一个行业的新闻",
    template:
      "追踪【公司或行业】最近【时间范围】的新闻和重要事件，按时间线整理，并分析这些事件对业务、竞争和市场的影响。",
    icon: "news",
    placeholders: ["【公司或行业】", "【时间范围】"],
    deepThink: true,
  },
  {
    id: "local-competitor-reviews",
    category: "深度调研",
    label: "分析本地竞争对手的评价",
    template:
      "分析【城市或地区】的竞争对手【行业或商家类型】评价，整理用户满意点、抱怨、服务差距和可以切入的机会。",
    icon: "map",
    placeholders: ["【城市或地区】", "【行业或商家类型】"],
    deepThink: true,
  },
  {
    id: "person-career-background",
    category: "深度调研",
    label: "调研一个人的职业背景",
    template:
      "调研人物【人物姓名或主页链接】的职业背景，梳理教育、任职经历、代表项目、公开观点、影响力和近期动态，并标注信息来源。",
    icon: "person",
    placeholders: ["【人物姓名或主页链接】"],
    deepThink: true,
  },
  {
    id: "technology-research",
    category: "深度调研",
    label: "调研一项技术",
    template:
      "调研技术【技术名称】，解释它的原理、发展历史、主要实现、应用场景、生态、限制和与替代方案的差异，输出一份技术研究报告。",
    icon: "industry",
    placeholders: ["【技术名称】"],
    deepThink: true,
  },
  {
    id: "hiring-strategy",
    category: "深度调研",
    label: "从招聘看一家公司的战略",
    template:
      "分析公司【公司名称】近期招聘职位，从岗位类型、技能要求、地点、团队结构和职位变化推断公司的业务重点与战略方向。",
    icon: "briefcase",
    placeholders: ["【公司名称】"],
    deepThink: true,
  },
  {
    id: "app-review-research",
    category: "深度调研",
    label: "分析一款应用的评论",
    template:
      "分析应用【应用名称或商店链接】的用户评论，提取高频好评、差评、功能需求、使用场景和竞品替代原因，最后给出产品改进建议。",
    icon: "app",
    placeholders: ["【应用名称或商店链接】"],
    deepThink: true,
  },
];

const otherSuggestedQuestions: SuggestedQuestion[] = [
  ...ecommerceQuestions,
  ...advertisingQuestions,
  ...seoQuestions,
  ...deepResearchQuestions,
];

const generalSuggestedQuestions: SuggestedQuestion[] = [
  ...socialMediaQuestions,
  ...otherSuggestedQuestions,
];

export const suggestedQuestionsByProductType: Record<string, SuggestedQuestion[]> = {
  task: generalSuggestedQuestions,
  dataAgent: [
    {
      id: "monthly-sales-trend",
      category: "数据分析",
      label: "2024年各月销量变化趋势如何？",
      template: "分析 2024 年各月销量变化趋势，并指出明显的增长、下降和异常月份。",
      icon: "chart",
    },
    {
      id: "top-purchase-costs",
      category: "数据分析",
      label: "采购成本最高的前十名商品是什么？",
      template: "找出采购成本最高的前十名商品，并整理商品、成本、占比和可能原因。",
      icon: "chart",
    },
    {
      id: "sales-comprehensive-analysis",
      category: "数据分析",
      label: "对销售数据进行综合分析",
      template: "对销售数据进行综合分析，覆盖趋势、区域、商品、客户和异常情况，并给出可执行的结论。",
      icon: "chart",
    },
    {
      id: "product-sales-performance",
      category: "数据分析",
      label: "分析产品的销售表现",
      template: "分析产品的销售表现，比较不同产品的销量、收入、利润和增长情况。",
      icon: "chart",
    },
  ],
};

export const GENERIC_TASK_PRODUCT: CHAT.Product = {
  name: "通用任务",
  img: "icon-aichat",
  type: "task",
  placeholder: "Reactor 会先完成你的任务，再根据内容给出合适的回答",
  color: "text-[#4040FF]",
};

export const productList = [
  {
    name: "数据分析",
    img: "icon-xinjianduihua",
    type: "dataAgent",
    placeholder: "Reactor 会完成你的数据分析任务",
    color: "text-[#4040FF]",
  },
];

export const defaultProduct = GENERIC_TASK_PRODUCT;

export const getProductByType = (type?: string): CHAT.Product => {
  if (!type || type === GENERIC_TASK_PRODUCT.type || type === "chat") {
    return GENERIC_TASK_PRODUCT;
  }
  return productList.find((item) => item.type === type) ?? defaultProduct;
};

export const RESULT_TYPES = ["task_summary", "result"];

export const InputSize: Record<string, string> = {
  big: "106",
  medium: "72",
  small: "32",
};

export const demoList = [
  {
    title: "Browser代码架构分析",
    description: "帮我分析github中开源的browser-use的代码，并进行分析",
    tag: "专业研究",
    videoUrl:
      "https://private-user-images.githubusercontent.com/49786633/469170308-065b8d1a-92e4-470a-bbe3-426fafeca5c4.mp4?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTM2OTE1NDIsIm5iZiI6MTc1MzY5MTI0MiwicGF0aCI6Ii80OTc4NjYzMy80NjkxNzAzMDgtMDY1YjhkMWEtOTJlNC00NzBhLWJiZTMtNDI2ZmFmZWNhNWM0Lm1wND9YLUFtei1BbGdvcml0aG09QVdTNC1ITUFDLVNIQTI1NiZYLUFtei1DcmVkZW50aWFsPUFLSUFWQ09EWUxTQTUzUFFLNFpBJTJGMjAyNTA3MjglMkZ1cy1lYXN0LTElMkZzMyUyRmF3czRfcmVxdWVzdCZYLUFtei1EYXRlPTIwMjUwNzI4VDA4MjcyMlomWC1BbXotRXhwaXJlcz0zMDAmWC1BbXotU2lnbmF0dXJlPWNlOWNiZmZkMzdjNDUxODc4YjMyNDE1ZmU4ZjlmZjgwZjYxMzRlNWMwNmFlZjM1M2Q3ZDNlNDYzOTUzNmZlMTAmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0In0.6OwtEGxcMnWlSCMgl0AaNy8NRl9lLuLx-nXrXdHLETg",
    url: "//storage.360buyimg.com/pubfree-bucket/ei-data-resource/89ab083/static/demoPage.html",
    image: demo1,
  },
  {
    title: "京东财报分析",
    description: "分析一下京东的最新财务报告，总结出核心数据以及公司发展情况",
    tag: "数据分析",
    videoUrl:
      "https://private-user-images.githubusercontent.com/49786633/469171050-15dcf089-5659-489e-849d-39c651ca7e5a.mp4?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTM2OTE5ODgsIm5iZiI6MTc1MzY5MTY4OCwicGF0aCI6Ii80OTc4NjYzMy80NjkxNzEwNTAtMTVkY2YwODktNTY1OS00ODllLTg0OWQtMzljNjUxY2E3ZTVhLm1wND9YLUFtei1BbGdvcml0aG09QVdTNC1ITUFDLVNIQTI1NiZYLUFtei1DcmVkZW50aWFsPUFLSUFWQ09EWUxTQTUzUFFLNFpBJTJGMjAyNTA3MjglMkZ1cy1lYXN0LTElMkZzMyUyRmF3czRfcmVxdWVzdCZYLUFtei1EYXRlPTIwMjUwNzI4VDA4MzQ0OFomWC1BbXotRXhwaXJlcz0zMDAmWC1BbXotU2lnbmF0dXJlPTY0MDE1OWQ1NjNiNTcwZGY1ZTBhNzllNDhhMjM3M2E3YjQ3Mzc4ZjYwN2ExMWUxMTZjYzIwZWIzOGFhYjEzYjkmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0In0.QqNCtSyGy20QbeNPPib6zVLpzPrcKmDMHJFphAwzx6E",
    url: "//storage.360buyimg.com/pubfree-bucket/ei-data-resource/89ab083/static/demoPage2.html",
    image: demo2,
  },
  {
    title: "HR智能招聘产品竞品分析",
    description: "分析一下HR智能招聘领域的优秀产品，形成一个竞品对比报告",
    tag: "竞品调研",
    videoUrl:
      "https://private-user-images.githubusercontent.com/49786633/469171112-cd99e2f8-9887-459f-ae51-00e7883fa050.mp4?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTM2OTE5ODgsIm5iZiI6MTc1MzY5MTY4OCwicGF0aCI6Ii80OTc4NjYzMy80NjkxNzExMTItY2Q5OWUyZjgtOTg4Ny00NTlmLWFlNTEtMDBlNzg4M2ZhMDUwLm1wND9YLUFtei1BbGdvcml0aG09QVdTNC1ITUFDLVNIQTI1NiZYLUFtei1DcmVkZW50aWFsPUFLSUFWQ09EWUxTQTUzUFFLNFpBJTJGMjAyNTA3MjglMkZ1cy1lYXN0LTElMkZzMyUyRmF3czRfcmVxdWVzdCZYLUFtei1EYXRlPTIwMjUwNzI4VDA4MzQ0OFomWC1BbXotRXhwaXJlcz0zMDAmWC1BbXotU2lnbmF0dXJlPTA2MDNiNDk5MThlZTRhMTY0YTM0YWQ1MGU2NDRlYzg1NWIxNDM4ZmYyMmE1MTY2YzgwZmUyOTI1MjY3NjFiNTQmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0In0.-r9MhEJ9RgbYPi-cTCmG0wMxNmFC0rjXNMti4LRvspc",
    url: "//storage.360buyimg.com/pubfree-bucket/ei-data-resource/89ab083/static/demoPage3.html",
    image: demo3,
  },
  {
    title: "超市销售数据分析",
    description: "帮我分析一下国内销售数据",
    tag: "数据分析",
    videoUrl:
      "https://private-user-images.githubusercontent.com/49786633/469171151-657bbe61-5516-4ab9-84c2-c6ca75cc4a6f.mp4?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTM2OTE5ODgsIm5iZiI6MTc1MzY5MTY4OCwicGF0aCI6Ii80OTc4NjYzMy80NjkxNzExNTEtNjU3YmJlNjEtNTUxNi00YWI5LTg0YzItYzZjYTc1Y2M0YTZmLm1wND9YLUFtei1BbGdvcml0aG09QVdTNC1ITUFDLVNIQTI1NiZYLUFtei1DcmVkZW50aWFsPUFLSUFWQ09EWUxTQTUzUFFLNFpBJTJGMjAyNTA3MjglMkZ1cy1lYXN0LTElMkZzMyUyRmF3czRfcmVxdWVzdCZYLUFtei1EYXRlPTIwMjUwNzI4VDA4MzQ0OFomWC1BbXotRXhwaXJlcz0zMDAmWC1BbXotU2lnbmF0dXJlPTJkMDNlNTkxNzFkNjFlYTI1MTAzNTIyZWM0YzA1MzE5MTY4NDYyYTg5MjUxZWY0Mjg0OWU1ODUxNGZkNTU3ZTEmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0In0.BRatyWFZm91TAvRn1iss7DMPWLXIoRm9geqaN6af7cI",
    url: "//storage.360buyimg.com/pubfree-bucket/ei-data-resource/89ab083/static/demoPage4.html",
    image: demo4,
  },
];
