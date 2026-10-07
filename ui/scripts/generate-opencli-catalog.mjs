import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const sitePresentation = {
  aibase: ["AIBase", "AI 知识库与 AI 工具聚合站点。", "research"],
  "apple-podcasts": ["Apple Podcasts", "苹果官方播客平台，可搜索节目并查看单集。", "media"],
  archive: ["Internet Archive", "互联网档案馆，可检索网页、书籍、影像等历史存档。", "document"],
  arxiv: ["arXiv", "理工科预印本论文平台，可检索学术论文。", "research"],
  "baidu-scholar": ["百度学术", "中文文献检索平台，可查找论文和参考文献。", "research"],
  bbc: ["BBC", "英国广播公司新闻与国际资讯。", "news"],
  dblp: ["DBLP", "计算机领域文献索引库，可检索计算机顶会论文。", "research"],
  duckduckgo: ["DuckDuckGo", "注重隐私的搜索引擎。", "research"],
  geogebra: ["GeoGebra", "免费数学绘图工具，可视化几何与代数。", "creation"],
  "github-trending": ["GitHub Trending", "查看 GitHub 热门开源项目和趋势。", "research"],
  google: ["Google", "Google 网页搜索。", "research"],
  "google-scholar": ["Google Scholar", "全球学术文献检索平台。", "research"],
  "gov-law": ["法律法规", "公开法律法规站点，可查询法律条文。", "research"],
  hackernews: ["Hacker News", "Y Combinator 旗下的技术与创业资讯社区。", "social"],
  juejin: ["掘金", "国内程序员技术社区与技术文章平台。", "social"],
  steam: ["Steam", "Steam 游戏商店与游戏资讯平台。", "commerce"],
  wikidata: ["Wikidata", "维基数据结构化知识图谱。", "research"],
  wikipedia: ["Wikipedia", "多语言在线百科全书。", "research"],
  hupu: ["虎扑", "体育与生活社区论坛。", "social"],
  nowcoder: ["牛客网", "面向求职与技术交流的社区平台。", "social"],
  producthunt: ["Product Hunt", "海外互联网新产品发布与投票社区。", "social"],
  sinablog: ["新浪博客", "新浪博客内容平台。", "social"],
  sinafinance: ["新浪财经", "股票与财经资讯平台。", "finance"],
  tieba: ["百度贴吧", "兴趣主题论坛，公开帖子可浏览。", "social"],
  toutiao: ["今日头条", "资讯内容与信息流平台。", "news"],
  weixin: ["微信", "微信网页版公开页面与内容。", "social"],
  weread: ["微信读书", "可查询书籍信息；书架与笔记需要登录会话。", "document"],
  xiaoyuzhou: ["小宇宙", "中文播客平台，可查找节目与单集。", "media"],
  yollomi: ["Yollomi", "海外内容社区与创作站点。", "social"],
  amazon: ["Amazon", "海外电商平台，可搜索商品并查看商品信息。", "commerce"],
  bilibili: ["B 站", "视频与弹幕社区。", "media"],
  facebook: ["Facebook", "海外社交平台。", "social"],
  douyin: ["抖音", "短视频内容与创作者平台。", "social"],
  instagram: ["Instagram", "图片与短视频社交平台。", "social"],
  linkedin: ["LinkedIn", "职场社交与招聘平台。", "social"],
  reddit: ["Reddit", "海外论坛与社区平台。", "social"],
  "wechat-channels": ["微信视频号", "微信视频号网页端内容平台。", "media"],
  youtube: ["YouTube", "海外视频平台。", "media"],
  zhihu: ["知乎", "中文问答与知识社区。", "social"],
  autohome: ["汽车之家", "国内汽车资讯、车型参数与车友论坛。", "research"],
  binance: ["币安", "加密货币行情与交易平台。", "finance"],
  bluesky: ["Bluesky", "去中心化微博类社交网络。", "social"],
  booking: ["Booking", "全球酒店与民宿查询预订平台。", "commerce"],
  brave: ["Brave", "注重隐私的浏览器项目站点。", "browser"],
  coingecko: ["CoinGecko", "加密货币币价、市值与项目数据查询。", "finance"],
  confluence: ["Confluence", "Atlassian 团队知识库与企业文档协作平台。", "document"],
  crates: ["crates.io", "Rust 语言包仓库与开源包托管平台。", "research"],
  defillama: ["DeFiLlama", "DeFi 链上 TVL 与项目数据分析平台。", "finance"],
  devto: ["DEV", "程序员技术博客与技术交流社区。", "social"],
  dictionary: ["Dictionary", "英文单词释义与词源查询网站。", "research"],
  dockerhub: ["Docker Hub", "Docker 容器镜像托管仓库。", "research"],
  dongchedi: ["懂车帝", "汽车资讯、车型评测与汽车社区。", "research"],
  endoflife: ["EndOfLife", "软件与系统支持终止时间查询。", "research"],
  flathub: ["Flathub", "Linux Flatpak 桌面应用分发平台。", "commerce"],
  goproxy: ["Go Proxy", "Go 语言模块代理服务。", "research"],
  "gov-policy": ["政务政策", "国家与地方政策文件公开查询站点。", "research"],
  guazi: ["瓜子二手车", "二手车交易平台。", "commerce"],
  homebrew: ["Homebrew", "Mac 与 Linux 包管理器官网。", "research"],
  huodongxing: ["活动行", "线下活动、讲座与报名平台。", "commerce"],
  imdb: ["IMDb", "影视评分、演员与影片资料数据库。", "media"],
  jira: ["Jira", "Atlassian 项目任务、研发缺陷与需求跟踪工具。", "document"],
  lesswrong: ["LessWrong", "认知科学与理性思考文章社区。", "social"],
  lichess: ["Lichess", "免费国际象棋在线对弈平台。", "social"],
  lobsters: ["Lobsters", "程序员技术资讯社区。", "social"],
  maven: ["Maven Central", "Java 项目依赖包中央仓库。", "research"],
  mdn: ["MDN Web Docs", "Mozilla HTML、CSS 与 JavaScript 开发文档。", "document"],
  npm: ["npm", "JavaScript 软件包仓库。", "research"],
  nuget: ["NuGet", ".NET 程序包仓库。", "research"],
  nvd: ["NVD", "美国国家漏洞数据库与 CVE 信息查询平台。", "research"],
  oeis: ["OEIS", "整数序列百科与数学序列检索网站。", "research"],
  openalex: ["OpenAlex", "论文、学者与机构开放文献索引数据库。", "research"],
  openfda: ["openFDA", "美国 FDA 药品与医疗器械开放数据平台。", "research"],
  openreview: ["OpenReview", "学术会议论文评审与讨论平台。", "research"],
  osv: ["OSV", "开源软件漏洞查询数据库。", "research"],
  packagist: ["Packagist", "PHP Composer 开源包仓库。", "research"],
  paperreview: ["PaperReview", "论文评审辅助站点。", "research"],
  pubmed: ["PubMed", "生物医学文献数据库与医学论文检索平台。", "research"],
  pypi: ["PyPI", "Python 软件包仓库。", "research"],
  "rest-countries": ["REST Countries", "世界各国基础信息公开 API 站点。", "research"],
  rfc: ["IETF RFC", "互联网标准与协议文档库。", "document"],
  rubygems: ["RubyGems", "Ruby 语言软件包仓库。", "research"],
  semanticscholar: ["Semantic Scholar", "AI 驱动的学术论文检索平台。", "research"],
  spotify: ["Spotify", "流媒体音乐平台。", "media"],
  stackoverflow: ["Stack Overflow", "程序员问答与编程问题检索社区。", "social"],
  ths: ["同花顺", "金融与股票行情网站。", "finance"],
  tvmaze: ["TVmaze", "美剧、英剧更新与剧集资料网站。", "media"],
  uisdc: ["优设网", "UI 与设计教程、设计资讯社区。", "creation"],
  uiverse: ["Uiverse", "前端 UI 开源组件素材库。", "creation"],
  wanfang: ["万方数据", "中文期刊与学位论文数据库。", "research"],
  "weread-official": ["微信读书公开页", "微信读书公开书籍信息页面，不包含个人书架。", "document"],
  wttr: ["wttr.in", "极简命令行风格天气查询网站。", "research"],
  yahoo: ["Yahoo", "新闻、搜索与综合资讯门户。", "news"],
  youdao: ["有道翻译", "在线翻译与词典网站。", "research"],
};

const noLoginSites = new Set([
  "aibase",
  "apple-podcasts",
  "archive",
  "arxiv",
  "autohome",
  "baidu-scholar",
  "bbc",
  "binance",
  "bluesky",
  "booking",
  "brave",
  "coingecko",
  "confluence",
  "crates",
  "dblp",
  "defillama",
  "devto",
  "dictionary",
  "dockerhub",
  "dongchedi",
  "duckduckgo",
  "endoflife",
  "flathub",
  "geogebra",
  "github-trending",
  "google",
  "google-scholar",
  "goproxy",
  "gov-law",
  "gov-policy",
  "guazi",
  "hackernews",
  "homebrew",
  "huodongxing",
  "imdb",
  "jira",
  "juejin",
  "lesswrong",
  "lichess",
  "lobsters",
  "maven",
  "mdn",
  "npm",
  "nuget",
  "nvd",
  "oeis",
  "openalex",
  "openfda",
  "openreview",
  "osv",
  "packagist",
  "paperreview",
  "pubmed",
  "pypi",
  "rest-countries",
  "rfc",
  "rubygems",
  "semanticscholar",
  "spotify",
  "stackoverflow",
  "steam",
  "ths",
  "tvmaze",
  "uisdc",
  "uiverse",
  "wanfang",
  "weread-official",
  "wikidata",
  "wikipedia",
  "wttr",
  "yahoo",
  "youdao",
]);

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const uiDirectory = resolve(scriptDirectory, "..");
const repositoryDirectory = resolve(uiDirectory, "..");
const manifestPath = resolve(repositoryDirectory, "adapter-host/cli-manifest.json");
const outputPath = resolve(
  uiDirectory,
  "src/pages/ToolCatalog/openCliCatalog.generated.ts",
);
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
const siteNames = new Set(manifest.map((entry) => entry.site));
const missingSites = Object.keys(sitePresentation).filter((site) => !siteNames.has(site));
const missingNoLoginSites = [...noLoginSites].filter(
  (site) => !Object.hasOwn(sitePresentation, site),
);

if (missingSites.length > 0) {
  throw new Error(`OpenCLI manifest is missing requested sites: ${missingSites.join(", ")}`);
}
if (missingNoLoginSites.length > 0) {
  throw new Error(`No-login sites are missing presentation metadata: ${missingNoLoginSites.join(", ")}`);
}

const iconByCategory = {
  social: "social",
  research: "search",
  data: "chart",
  document: "document",
  browser: "browser",
  creation: "creation",
  media: "media",
  news: "news",
  commerce: "commerce",
  finance: "finance",
};
const iconOverrides = {
  bilibili: "bilibili",
  douyin: "douyin",
  reddit: "reddit",
  zhihu: "zhihu",
};

const sites = Object.entries(sitePresentation).map(([site, [title, description, category]]) => ({
  site,
  title,
  description,
  category,
  iconKey: iconOverrides[site] ?? iconByCategory[category],
  ...(noLoginSites.has(site) ? { requiresLogin: false } : {}),
  commands: manifest
    .filter((entry) => entry.site === site)
    .map(({ site: commandSite, name, description: commandDescription, access, browser, args = [] }) => ({
      site: commandSite,
      name,
      description: commandDescription,
      access,
      browser: Boolean(browser),
      args,
    })),
}));

const duplicateCommands = sites.flatMap((entry) => entry.commands.map((command) => `${entry.site}/${command.name}`));
if (new Set(duplicateCommands).size !== duplicateCommands.length) {
  throw new Error("OpenCLI manifest contains duplicate command names for requested sites.");
}

const generatedContent = [
  "// Generated from adapter-host/cli-manifest.json. Run pnpm sync:opencli-catalog to refresh.",
  'import type { OpenCliSiteDefinition } from "./openCliManifestTypes";',
  "",
  `export const OPENCLI_SITE_DEFINITIONS: readonly OpenCliSiteDefinition[] = ${JSON.stringify(sites, null, 2)};`,
  "",
].join("\n");

writeFileSync(outputPath, generatedContent, "utf8");
console.log(`Generated ${sites.length} OpenCLI sites and ${duplicateCommands.length} commands.`);
