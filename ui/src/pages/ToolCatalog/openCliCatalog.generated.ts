// Generated from adapter-host/cli-manifest.json. Run pnpm sync:opencli-catalog to refresh.
import type { OpenCliSiteDefinition } from "./openCliManifestTypes";

export const OPENCLI_SITE_DEFINITIONS: readonly OpenCliSiteDefinition[] = [
  {
    "site": "aibase",
    "title": "AIBase",
    "description": "AI 知识库与 AI 工具聚合站点。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "aibase",
        "name": "news",
        "description": "AIbase 日报 - 每天三分钟关注AI行业趋势",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of news items to return (max 50)"
          }
        ]
      }
    ]
  },
  {
    "site": "apple-podcasts",
    "title": "Apple Podcasts",
    "description": "苹果官方播客平台，可搜索节目并查看单集。",
    "category": "media",
    "iconKey": "media",
    "requiresLogin": false,
    "commands": [
      {
        "site": "apple-podcasts",
        "name": "episodes",
        "description": "List recent episodes of an Apple Podcast (use ID from search)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Podcast ID (collectionId from search output)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": "Max episodes to show"
          }
        ]
      },
      {
        "site": "apple-podcasts",
        "name": "search",
        "description": "Search Apple Podcasts",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max results"
          }
        ]
      },
      {
        "site": "apple-podcasts",
        "name": "top",
        "description": "Top podcasts chart on Apple Podcasts",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of podcasts (max 100)"
          },
          {
            "name": "country",
            "type": "str",
            "default": "us",
            "required": false,
            "help": "Country code (e.g. us, cn, gb, jp)"
          }
        ]
      }
    ]
  },
  {
    "site": "archive",
    "title": "Internet Archive",
    "description": "互联网档案馆，可检索网页、书籍、影像等历史存档。",
    "category": "document",
    "iconKey": "document",
    "requiresLogin": false,
    "commands": [
      {
        "site": "archive",
        "name": "item",
        "description": "Fetch metadata for a single Internet Archive item by identifier.",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "identifier",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Archive item identifier (e.g. \"open-syllabus\", \"FinalFantasy2_356\")."
          }
        ]
      },
      {
        "site": "archive",
        "name": "search",
        "description": "Search Internet Archive items across books, movies, audio, software, and web.",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Full-text query (matches title, description, creator, subject)."
          },
          {
            "name": "mediatype",
            "type": "string",
            "required": false,
            "help": "Restrict to mediatype: texts, movies, audio, software, image, web, data, collection"
          },
          {
            "name": "sort",
            "type": "string",
            "default": "downloads",
            "required": false,
            "help": "Sort key: downloads, date, addeddate, week, title"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max items (max 100; one API page)."
          }
        ]
      },
      {
        "site": "archive",
        "name": "snapshots",
        "description": "List Wayback Machine snapshots over time for a URL via the CDX API.",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "URL to look up (with or without scheme)."
          },
          {
            "name": "from",
            "type": "string",
            "required": false,
            "help": "Earliest year/timestamp (YYYY[MM[DD[hh[mm[ss]]]]])"
          },
          {
            "name": "to",
            "type": "string",
            "required": false,
            "help": "Latest year/timestamp (YYYY[MM[DD[hh[mm[ss]]]]])"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max snapshots to return (max 1000)."
          }
        ]
      },
      {
        "site": "archive",
        "name": "wayback",
        "description": "Look up the closest Wayback Machine snapshot for a URL.",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "URL to look up (with or without scheme)."
          },
          {
            "name": "timestamp",
            "type": "string",
            "required": false,
            "help": "Target timestamp (YYYY[MM[DD[hh[mm[ss]]]]] or ISO date). Defaults to most recent snapshot."
          }
        ]
      }
    ]
  },
  {
    "site": "arxiv",
    "title": "arXiv",
    "description": "理工科预印本论文平台，可检索学术论文。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "arxiv",
        "name": "author",
        "description": "List arXiv papers by a given author (newest first)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "author",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Author name (e.g. \"Yoshua Bengio\" or \"Y Bengio\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max papers to return (max 50)"
          }
        ]
      },
      {
        "site": "arxiv",
        "name": "paper",
        "description": "Get arXiv paper details by ID",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "arXiv paper ID (e.g. 1706.03762)"
          }
        ]
      },
      {
        "site": "arxiv",
        "name": "recent",
        "description": "List recent arXiv submissions in a category",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "category",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "arXiv category (e.g. cs.CL, cs.LG, math.PR, q-bio.NC)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max results (max 50)"
          }
        ]
      },
      {
        "site": "arxiv",
        "name": "search",
        "description": "Search arXiv papers",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"attention is all you need\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max results (max 25)"
          }
        ]
      }
    ]
  },
  {
    "site": "baidu-scholar",
    "title": "百度学术",
    "description": "中文文献检索平台，可查找论文和参考文献。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "baidu-scholar",
        "name": "search",
        "description": "百度学术搜索",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回结果数量 (max 20)"
          }
        ]
      }
    ]
  },
  {
    "site": "bbc",
    "title": "BBC",
    "description": "英国广播公司新闻与国际资讯。",
    "category": "news",
    "iconKey": "news",
    "requiresLogin": false,
    "commands": [
      {
        "site": "bbc",
        "name": "news",
        "description": "BBC News headlines (RSS)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of headlines (max 50)"
          }
        ]
      },
      {
        "site": "bbc",
        "name": "topic",
        "description": "BBC News headlines for a specific section (RSS feed)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "topic",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Section name (world / business / politics / health / education / science_and_environment / technology / entertainment_and_arts)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max headlines (1-50)"
          }
        ]
      }
    ]
  },
  {
    "site": "dblp",
    "title": "DBLP",
    "description": "计算机领域文献索引库，可检索计算机顶会论文。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "dblp",
        "name": "author",
        "description": "List dblp publications by a given author (newest first; resolves to top PID match)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "author",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Author name (e.g. \"Yoshua Bengio\"). Optional when --pid is given."
          },
          {
            "name": "pid",
            "type": "str",
            "required": false,
            "help": "Canonical dblp PID (e.g. \"56/953\"). Bypasses author search."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max publications (1-200)"
          }
        ]
      },
      {
        "site": "dblp",
        "name": "paper",
        "description": "Fetch a dblp record by canonical key (e.g. conf/nips/VaswaniSPUJGKP17)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "key",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "dblp record key (round-tripped from the `key` column of `dblp search`)"
          }
        ]
      },
      {
        "site": "dblp",
        "name": "search",
        "description": "Search dblp computer-science bibliography by free-text query",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (title / author / venue, e.g. \"attention is all you need\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100, single dblp page)"
          }
        ]
      },
      {
        "site": "dblp",
        "name": "venue",
        "description": "Search dblp venue registry (conferences / journals) by name or acronym",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Venue name or acronym (e.g. \"ICLR\", \"neural networks\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max venues (1-100, single dblp page)"
          }
        ]
      }
    ]
  },
  {
    "site": "duckduckgo",
    "title": "DuckDuckGo",
    "description": "注重隐私的搜索引擎。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "duckduckgo",
        "name": "search",
        "description": "Search DuckDuckGo",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results per page (1-10). For multi-page, use --offset"
          },
          {
            "name": "offset",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Result offset for pagination (0, 10, 20...). Uses XHR POST internally"
          },
          {
            "name": "region",
            "type": "str",
            "required": false,
            "help": "Region code (e.g. jp-jp, us-en, cn-zh). Default: all regions"
          },
          {
            "name": "time",
            "type": "str",
            "required": false,
            "help": "Time range: d (day), w (week), m (month), y (year)"
          }
        ]
      },
      {
        "site": "duckduckgo",
        "name": "suggest",
        "description": "DuckDuckGo search suggestions",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query prefix"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 8,
            "required": false,
            "help": "Max number of suggestions"
          }
        ]
      }
    ]
  },
  {
    "site": "geogebra",
    "title": "GeoGebra",
    "description": "免费数学绘图工具，可视化几何与代数。",
    "category": "creation",
    "iconKey": "creation",
    "requiresLogin": false,
    "commands": [
      {
        "site": "geogebra",
        "name": "add-circle",
        "description": "Create a circle by center+radius or center+point",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "center",
            "type": "str",
            "required": true,
            "help": "Center point label (e.g. A)"
          },
          {
            "name": "radius",
            "type": "str",
            "required": false,
            "help": "Radius value (number) or a point label on the circle"
          },
          {
            "name": "point",
            "type": "str",
            "required": false,
            "help": "Alternative: a point label on the circle (use instead of --radius for Circle(center,point))"
          }
        ]
      },
      {
        "site": "geogebra",
        "name": "add-line",
        "description": "Create a line through two points or a segment between two points",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "points",
            "type": "str",
            "required": true,
            "help": "Two point labels separated by comma (e.g. \"A,B\")"
          },
          {
            "name": "type",
            "type": "str",
            "default": "line",
            "required": false,
            "help": "Type: line, segment, or ray (default: line)",
            "choices": [
              "line",
              "segment",
              "ray"
            ]
          }
        ]
      },
      {
        "site": "geogebra",
        "name": "add-point",
        "description": "Create a point with given label and coordinates",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "help": "Point label (e.g. A, B, P1)"
          },
          {
            "name": "coords",
            "type": "str",
            "required": true,
            "help": "Coordinates as x,y (e.g. \"1,2\")"
          }
        ]
      },
      {
        "site": "geogebra",
        "name": "add-polygon",
        "description": "Create a polygon from a list of point labels",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "points",
            "type": "str",
            "required": true,
            "help": "Comma-separated point labels (e.g. \"A,B,C\" or \"A,B,C,D\")"
          }
        ]
      },
      {
        "site": "geogebra",
        "name": "eval",
        "description": "Execute one or more GeoGebra command strings (semicolon-separated)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "command",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "GeoGebra command string (use ; to chain multiple commands)"
          }
        ]
      },
      {
        "site": "geogebra",
        "name": "hexagon",
        "description": "Draw a regular hexagon centered at the origin",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "size",
            "type": "str",
            "default": "2",
            "required": false,
            "help": "Radius of the hexagon (default: 2)"
          }
        ]
      },
      {
        "site": "geogebra",
        "name": "info",
        "description": "Get detailed properties of a GeoGebra object",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "help": "Object label (e.g. A, c1, poly1)"
          }
        ]
      },
      {
        "site": "geogebra",
        "name": "list",
        "description": "List all geometric objects on the GeoGebra canvas",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "type",
            "type": "str",
            "required": false,
            "help": "Filter by object type (e.g. \"point\", \"line\", \"circle\")"
          }
        ]
      },
      {
        "site": "geogebra",
        "name": "triangle",
        "description": "Draw an equilateral triangle from a horizontal base segment",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "size",
            "type": "str",
            "default": "2",
            "required": false,
            "help": "Side length of the triangle (default: 2)"
          }
        ]
      }
    ]
  },
  {
    "site": "github-trending",
    "title": "GitHub Trending",
    "description": "查看 GitHub 热门开源项目和趋势。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "github-trending",
        "name": "repos",
        "description": "GitHub Trending repositories (public, no login). Filter by --language and --since.",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "since",
            "type": "string",
            "default": "daily",
            "required": false,
            "help": "Time range: daily / weekly / monthly"
          },
          {
            "name": "language",
            "type": "string",
            "default": "",
            "required": false,
            "help": "Filter by programming language slug, e.g. python, rust, \"c++\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Number of repositories to return (max 25)"
          }
        ]
      }
    ]
  },
  {
    "site": "google",
    "title": "Google",
    "description": "Google 网页搜索。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "google",
        "name": "images",
        "description": "Search Google Images for photos and image results",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Image search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of image results (1-100)"
          },
          {
            "name": "lang",
            "type": "str",
            "default": "en",
            "required": false,
            "help": "Language short code (e.g. en, zh)"
          },
          {
            "name": "resolve",
            "type": "bool",
            "default": true,
            "required": false,
            "help": "Click image previews to resolve original imgurl values"
          }
        ]
      },
      {
        "site": "google",
        "name": "news",
        "description": "Get Google News headlines",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Search query (omit for top stories)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          },
          {
            "name": "lang",
            "type": "str",
            "default": "en",
            "required": false,
            "help": "Language short code (e.g. en, zh)"
          },
          {
            "name": "region",
            "type": "str",
            "default": "US",
            "required": false,
            "help": "Region code (e.g. US, CN)"
          }
        ]
      },
      {
        "site": "google",
        "name": "search",
        "description": "Search Google",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results (1-100)"
          },
          {
            "name": "lang",
            "type": "str",
            "default": "en",
            "required": false,
            "help": "Language short code (e.g. en, zh)"
          }
        ]
      },
      {
        "site": "google",
        "name": "suggest",
        "description": "Get Google search suggestions",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "lang",
            "type": "str",
            "default": "zh-CN",
            "required": false,
            "help": "Language code"
          }
        ]
      },
      {
        "site": "google",
        "name": "trends",
        "description": "Get Google Trends daily trending searches",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "region",
            "type": "str",
            "default": "US",
            "required": false,
            "help": "Region code (e.g. US, CN, JP)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          }
        ]
      }
    ]
  },
  {
    "site": "google-scholar",
    "title": "Google Scholar",
    "description": "全球学术文献检索平台。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "google-scholar",
        "name": "cite",
        "description": "Get citation for a Google Scholar paper",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Paper title to search for"
          },
          {
            "name": "style",
            "type": "str",
            "default": "bibtex",
            "required": false,
            "help": "Citation format",
            "choices": [
              "bibtex",
              "endnote",
              "refman",
              "refworks"
            ]
          },
          {
            "name": "index",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Which search result to cite (1-based)"
          }
        ]
      },
      {
        "site": "google-scholar",
        "name": "profile",
        "description": "View a Google Scholar author profile",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "author",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Author name or Scholar user ID (e.g. JicYPdAAAAAJ)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max papers to show (max 20)"
          }
        ]
      },
      {
        "site": "google-scholar",
        "name": "search",
        "description": "Google Scholar 学术搜索",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回结果数量 (max 20)"
          }
        ]
      }
    ]
  },
  {
    "site": "gov-law",
    "title": "法律法规",
    "description": "公开法律法规站点，可查询法律条文。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "gov-law",
        "name": "recent",
        "description": "最新法律法规",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回结果数量 (max 20)"
          }
        ]
      },
      {
        "site": "gov-law",
        "name": "search",
        "description": "国家法律法规数据库搜索",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回结果数量 (max 20)"
          }
        ]
      }
    ]
  },
  {
    "site": "hackernews",
    "title": "Hacker News",
    "description": "Y Combinator 旗下的技术与创业资讯社区。",
    "category": "social",
    "iconKey": "social",
    "requiresLogin": false,
    "commands": [
      {
        "site": "hackernews",
        "name": "ask",
        "description": "Hacker News Ask HN posts",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories"
          }
        ]
      },
      {
        "site": "hackernews",
        "name": "best",
        "description": "Hacker News best stories",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories"
          }
        ]
      },
      {
        "site": "hackernews",
        "name": "jobs",
        "description": "Hacker News job postings",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of job postings"
          }
        ]
      },
      {
        "site": "hackernews",
        "name": "new",
        "description": "Hacker News newest stories",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories"
          }
        ]
      },
      {
        "site": "hackernews",
        "name": "read",
        "description": "Read a Hacker News story and its comment tree",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "HN item ID (e.g. 39847301)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Max top-level comments"
          },
          {
            "name": "depth",
            "type": "int",
            "default": 2,
            "required": false,
            "help": "Max reply depth (1=no replies, 2=one level of replies, etc.)"
          },
          {
            "name": "replies",
            "type": "int",
            "default": 5,
            "required": false,
            "help": "Max replies shown per comment at each level"
          },
          {
            "name": "max-length",
            "type": "int",
            "default": 2000,
            "required": false,
            "help": "Max characters per comment body (min 100)"
          }
        ]
      },
      {
        "site": "hackernews",
        "name": "search",
        "description": "Search Hacker News stories",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "relevance",
            "required": false,
            "help": "Sort by relevance or date",
            "choices": [
              "relevance",
              "date"
            ]
          }
        ]
      },
      {
        "site": "hackernews",
        "name": "show",
        "description": "Hacker News Show HN posts",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories"
          }
        ]
      },
      {
        "site": "hackernews",
        "name": "top",
        "description": "Hacker News top stories",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories"
          }
        ]
      },
      {
        "site": "hackernews",
        "name": "user",
        "description": "Hacker News user profile",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "HN username"
          }
        ]
      }
    ]
  },
  {
    "site": "juejin",
    "title": "掘金",
    "description": "国内程序员技术社区与技术文章平台。",
    "category": "social",
    "iconKey": "social",
    "requiresLogin": false,
    "commands": [
      {
        "site": "juejin",
        "name": "hot",
        "description": "Juejin (掘金) hot article ranking, optionally scoped to a category",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "category",
            "type": "string",
            "required": false,
            "help": "Category slug or numeric id. Slugs: backend, frontend, android, ios, ai. Defaults to \"backend\"."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max articles (1-50)."
          }
        ]
      },
      {
        "site": "juejin",
        "name": "recommend",
        "description": "Juejin (掘金) homepage recommended article feed",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max articles (1-100, single page)."
          },
          {
            "name": "cursor",
            "type": "string",
            "default": "0",
            "required": false,
            "help": "Pagination cursor; pass back the previous response's next-page cursor to keep scrolling."
          }
        ]
      }
    ]
  },
  {
    "site": "steam",
    "title": "Steam",
    "description": "Steam 游戏商店与游戏资讯平台。",
    "category": "commerce",
    "iconKey": "commerce",
    "requiresLogin": false,
    "commands": [
      {
        "site": "steam",
        "name": "app",
        "description": "Steam storefront detail for a single app id",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Numeric Steam app id (e.g. \"620\" for Portal 2)"
          },
          {
            "name": "currency",
            "type": "str",
            "default": "us",
            "required": false,
            "help": "Storefront country code (e.g. us / cn / jp / de)"
          }
        ]
      },
      {
        "site": "steam",
        "name": "search",
        "description": "Search the Steam storefront by name keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"portal\", \"stardew\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-50)"
          },
          {
            "name": "currency",
            "type": "str",
            "default": "us",
            "required": false,
            "help": "Storefront country code (e.g. us / cn / jp / de)"
          }
        ]
      },
      {
        "site": "steam",
        "name": "top-sellers",
        "description": "Steam top selling games",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of games"
          }
        ]
      }
    ]
  },
  {
    "site": "wikidata",
    "title": "Wikidata",
    "description": "维基数据结构化知识图谱。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "wikidata",
        "name": "entity",
        "description": "Fetch a Wikidata entity by Q/P/L id (label, description, aliases, claim summary)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Entity id (e.g. Q937 = Albert Einstein, P31 = instance of)"
          },
          {
            "name": "language",
            "type": "str",
            "default": "en",
            "required": false,
            "help": "Display language (ISO 639, falls back to English when missing)"
          }
        ]
      },
      {
        "site": "wikidata",
        "name": "search",
        "description": "Search Wikidata items by keyword (returns Q-IDs)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (label / alias)"
          },
          {
            "name": "language",
            "type": "str",
            "default": "en",
            "required": false,
            "help": "Search & display language (ISO 639, e.g. en, fr, zh)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max items (1-50)"
          }
        ]
      }
    ]
  },
  {
    "site": "wikipedia",
    "title": "Wikipedia",
    "description": "多语言在线百科全书。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "wikipedia",
        "name": "page",
        "description": "Full plain-text extract of a Wikipedia article (optional paragraph cap).",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "title",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Article title (e.g. \"Transformer (machine learning model)\")"
          },
          {
            "name": "lang",
            "type": "string",
            "default": "en",
            "required": false,
            "help": "Language code (en, zh, ja, de, ...)."
          },
          {
            "name": "paragraphs",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Cap to first N paragraphs (0 = full article)."
          }
        ]
      },
      {
        "site": "wikipedia",
        "name": "random",
        "description": "Get a random Wikipedia article",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "lang",
            "type": "str",
            "default": "en",
            "required": false,
            "help": "Language code (e.g. en, zh, ja)"
          }
        ]
      },
      {
        "site": "wikipedia",
        "name": "search",
        "description": "Search Wikipedia articles",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max results"
          },
          {
            "name": "lang",
            "type": "str",
            "default": "en",
            "required": false,
            "help": "Language code (e.g. en, zh, ja)"
          }
        ]
      },
      {
        "site": "wikipedia",
        "name": "summary",
        "description": "Get Wikipedia article summary",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "title",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Article title (e.g. \"Transformer (machine learning model)\")"
          },
          {
            "name": "lang",
            "type": "str",
            "default": "en",
            "required": false,
            "help": "Language code (e.g. en, zh, ja)"
          }
        ]
      },
      {
        "site": "wikipedia",
        "name": "trending",
        "description": "Most-read Wikipedia articles (yesterday)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max results"
          },
          {
            "name": "lang",
            "type": "str",
            "default": "en",
            "required": false,
            "help": "Language code (e.g. en, zh, ja)"
          }
        ]
      }
    ]
  },
  {
    "site": "hupu",
    "title": "虎扑",
    "description": "体育与生活社区论坛。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "hupu",
        "name": "detail",
        "description": "获取虎扑帖子详情 (使用Next.js JSON数据)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "tid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "帖子ID（9位数字）"
          },
          {
            "name": "replies",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "是否包含热门回复"
          }
        ]
      },
      {
        "site": "hupu",
        "name": "hot",
        "description": "虎扑首页热门帖子（含 lights / replies / forum / is_hot 列）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of threads (1-100)"
          }
        ]
      },
      {
        "site": "hupu",
        "name": "like",
        "description": "点赞虎扑回复 (需要登录)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "tid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "帖子ID（9位数字）"
          },
          {
            "name": "pid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "回复ID"
          },
          {
            "name": "fid",
            "type": "str",
            "required": true,
            "help": "板块ID（如278汽车区）"
          }
        ]
      },
      {
        "site": "hupu",
        "name": "login",
        "description": "Open hupu login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "hupu",
        "name": "mentions",
        "description": "查看虎扑提到我的回复 (需要登录)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "最多返回多少条消息"
          },
          {
            "name": "max_pages",
            "type": "int",
            "default": 3,
            "required": false,
            "help": "最多抓取多少页"
          },
          {
            "name": "page_str",
            "type": "str",
            "required": false,
            "help": "分页游标；不传时从第一页开始"
          }
        ]
      },
      {
        "site": "hupu",
        "name": "reply",
        "description": "回复虎扑帖子 (需要登录)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "tid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "帖子ID（9位数字）"
          },
          {
            "name": "topic_id",
            "type": "str",
            "required": true,
            "help": "板块ID，即接口中的 topicId（如 502 篮球资讯）"
          },
          {
            "name": "text",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "回复内容"
          },
          {
            "name": "quote_id",
            "type": "str",
            "required": false,
            "help": "被引用回复的 pid；填写后会以“回复某条热门回复”的方式发言"
          }
        ]
      },
      {
        "site": "hupu",
        "name": "search",
        "description": "搜索虎扑帖子 (使用官方API)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "结果页码"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "返回结果数量"
          },
          {
            "name": "forum",
            "type": "str",
            "required": false,
            "help": "板块ID过滤 (可选)"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "general",
            "required": false,
            "help": "排序方式: general/createtime/replytime/light/reply"
          }
        ]
      },
      {
        "site": "hupu",
        "name": "unlike",
        "description": "取消点赞虎扑回复 (需要登录)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "tid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "帖子ID（9位数字）"
          },
          {
            "name": "pid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "回复ID"
          },
          {
            "name": "fid",
            "type": "str",
            "required": true,
            "help": "板块ID（如278汽车区）"
          }
        ]
      },
      {
        "site": "hupu",
        "name": "whoami",
        "description": "Show the current logged-in hupu account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "nowcoder",
    "title": "牛客网",
    "description": "面向求职与技术交流的社区平台。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "nowcoder",
        "name": "companies",
        "description": "Hot companies for interview prep",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "job",
            "type": "str",
            "default": "11002",
            "required": false,
            "help": "Job ID (11002=Java, 11003=C++, 11200=Backend, 11203=QA, 11201=Frontend)"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "creators",
        "description": "Top content creators leaderboard",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of items"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "detail",
        "description": "Content or moment detail (use an ID or URL returned by search/experience)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Numeric content ID, moment UUID, or canonical URL"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "experience",
        "description": "Interview experience content and moment posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number (1-1000)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": "Number of posts (1-50)"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "hot",
        "description": "Hot search ranking",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of items"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "jobs",
        "description": "Career category listing",
        "access": "read",
        "browser": false,
        "args": []
      },
      {
        "site": "nowcoder",
        "name": "login",
        "description": "Open nowcoder login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "notifications",
        "description": "Unread message summary",
        "access": "read",
        "browser": true,
        "args": []
      },
      {
        "site": "nowcoder",
        "name": "papers",
        "description": "Interview question bank by company and job",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "job",
            "type": "str",
            "default": "11002",
            "required": false,
            "help": "Job ID (11002=Java, 11003=C++, 11200=Backend, 11203=QA, 11201=Frontend)"
          },
          {
            "name": "company",
            "type": "str",
            "default": "",
            "required": false,
            "help": "Company ID (e.g. 139=Baidu, 138=Tencent, 239=Huawei)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of items"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "practice",
        "description": "Categorized practice questions with progress",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "job",
            "type": "str",
            "default": "11226",
            "required": false,
            "help": "Career ID (11226=Software, 11227=Hardware, 11229=Product, 11230=Finance)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of items"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "recommend",
        "description": "Recommended feed",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": "Number of items"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "referral",
        "description": "Internal referral posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": "Number of items"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "salary",
        "description": "Salary disclosure posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": "Number of items"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "search",
        "description": "Search content and moment posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          },
          {
            "name": "type",
            "type": "str",
            "default": "post",
            "required": false,
            "help": "Post search scope (post/all)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of posts (1-50)"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "suggest",
        "description": "Search suggestions",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "topics",
        "description": "Hot discussion topics",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of items"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "trending",
        "description": "Trending posts",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of items"
          }
        ]
      },
      {
        "site": "nowcoder",
        "name": "whoami",
        "description": "Show the current logged-in nowcoder account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "producthunt",
    "title": "Product Hunt",
    "description": "海外互联网新产品发布与投票社区。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "producthunt",
        "name": "browse",
        "description": "Best products in a Product Hunt category",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "category",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Category slug, e.g. vibe-coding, ai-agents, developer-tools"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results (max 50)"
          }
        ]
      },
      {
        "site": "producthunt",
        "name": "hot",
        "description": "Today's top Product Hunt launches with vote counts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results (max 50)"
          }
        ]
      },
      {
        "site": "producthunt",
        "name": "posts",
        "description": "Latest Product Hunt launches (optional category filter)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results (max 50)"
          },
          {
            "name": "category",
            "type": "string",
            "default": "",
            "required": false,
            "help": "Category filter: ai-agents, ai-coding-agents, ai-code-editors, ai-chatbots, ai-workflow-automation, vibe-coding, developer-tools, productivity, design-creative, marketing-sales, no-code-platforms, llms, finance, social-community, engineering-development"
          }
        ]
      },
      {
        "site": "producthunt",
        "name": "today",
        "description": "Today's Product Hunt launches (most recent day in feed)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results"
          }
        ]
      }
    ]
  },
  {
    "site": "sinablog",
    "title": "新浪博客",
    "description": "新浪博客内容平台。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "sinablog",
        "name": "article",
        "description": "获取新浪博客单篇文章详情",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "文章URL（如 https://blog.sina.com.cn/s/blog_xxx.html）"
          }
        ]
      },
      {
        "site": "sinablog",
        "name": "hot",
        "description": "获取新浪博客热门文章/推荐",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "返回的文章数量"
          }
        ]
      },
      {
        "site": "sinablog",
        "name": "search",
        "description": "搜索新浪博客文章（通过新浪搜索）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "返回的文章数量"
          }
        ]
      },
      {
        "site": "sinablog",
        "name": "user",
        "description": "获取新浪博客用户的文章列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "uid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "新浪博客用户ID（如 1234567890）"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "返回的文章数量"
          }
        ]
      }
    ]
  },
  {
    "site": "sinafinance",
    "title": "新浪财经",
    "description": "股票与财经资讯平台。",
    "category": "finance",
    "iconKey": "finance",
    "commands": [
      {
        "site": "sinafinance",
        "name": "news",
        "description": "新浪财经 7x24 小时实时快讯",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (max 50)"
          },
          {
            "name": "type",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "News type: 0=全部 1=A股 2=宏观 3=公司 4=数据 5=市场 6=国际 7=观点 8=央行 9=其它"
          }
        ]
      },
      {
        "site": "sinafinance",
        "name": "rolling-news",
        "description": "新浪财经滚动新闻",
        "access": "read",
        "browser": false,
        "args": []
      },
      {
        "site": "sinafinance",
        "name": "stock",
        "description": "新浪财经行情（A股/港股/美股）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "key",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Stock name or code (e.g. 贵州茅台, 腾讯控股, AAPL)"
          },
          {
            "name": "market",
            "type": "string",
            "default": "auto",
            "required": false,
            "help": "Market: cn, hk, us, auto (default: auto searches cn → hk → us)"
          }
        ]
      },
      {
        "site": "sinafinance",
        "name": "stock-rank",
        "description": "新浪财经热搜榜",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "market",
            "type": "string",
            "default": "cn",
            "required": false,
            "help": "Market: cn (A股), hk (港股), us (美股), wh (外汇), ft (期货)",
            "choices": [
              "cn",
              "hk",
              "us",
              "wh",
              "ft"
            ]
          }
        ]
      }
    ]
  },
  {
    "site": "tieba",
    "title": "百度贴吧",
    "description": "兴趣主题论坛，公开帖子可浏览。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "tieba",
        "name": "hot",
        "description": "Tieba hot topics",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of items to return"
          }
        ]
      },
      {
        "site": "tieba",
        "name": "posts",
        "description": "Browse posts in a tieba forum",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "forum",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Forum name in Chinese"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of items to return"
          }
        ]
      },
      {
        "site": "tieba",
        "name": "read",
        "description": "Read a tieba thread",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Thread ID"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Number of replies to return"
          }
        ]
      },
      {
        "site": "tieba",
        "name": "search",
        "description": "Search posts across tieba",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "keyword",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number (currently only 1 is supported)",
            "choices": [
              "1"
            ]
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of items to return"
          }
        ]
      }
    ]
  },
  {
    "site": "toutiao",
    "title": "今日头条",
    "description": "资讯内容与信息流平台。",
    "category": "news",
    "iconKey": "news",
    "commands": [
      {
        "site": "toutiao",
        "name": "articles",
        "description": "获取头条号创作者后台文章列表及数据",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "页码 (1-4)"
          }
        ]
      },
      {
        "site": "toutiao",
        "name": "hot",
        "description": "今日头条首页热榜（公开 API，无需登录）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "返回条数 (1-50)"
          }
        ]
      },
      {
        "site": "toutiao",
        "name": "login",
        "description": "Open toutiao login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "toutiao",
        "name": "recommend",
        "description": "今日头条频道推荐流（公开 API，无需登录）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "category",
            "type": "string",
            "default": "__all__",
            "required": false,
            "help": "频道 (__all__, news_tech, news_finance, news_world, news_sports, news_entertainment, news_military)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "返回条数 (1-50)"
          }
        ]
      },
      {
        "site": "toutiao",
        "name": "whoami",
        "description": "Show the current logged-in toutiao account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "weixin",
    "title": "微信",
    "description": "微信网页版公开页面与内容。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "weixin",
        "name": "create-draft",
        "description": "创建微信公众号图文草稿",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "title",
            "type": "str",
            "required": true,
            "help": "文章标题 (最长64字)"
          },
          {
            "name": "content",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "文章正文"
          },
          {
            "name": "author",
            "type": "str",
            "required": false,
            "help": "作者名 (最长8字)"
          },
          {
            "name": "cover-image",
            "type": "str",
            "required": false,
            "help": "封面图片路径 (会先上传到正文再设为封面)"
          },
          {
            "name": "summary",
            "type": "str",
            "required": false,
            "help": "文章摘要"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 180,
            "required": false,
            "help": "Max seconds for the overall command (default: 180)"
          }
        ]
      },
      {
        "site": "weixin",
        "name": "download",
        "description": "下载微信公众号文章为 Markdown 格式",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "help": "WeChat article URL (mp.weixin.qq.com/s/xxx)"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./weixin-articles",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "download-images",
            "type": "boolean",
            "default": true,
            "required": false,
            "help": "Download images locally"
          }
        ]
      },
      {
        "site": "weixin",
        "name": "drafts",
        "description": "列出微信公众号草稿箱",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "最多显示条数"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 60,
            "required": false,
            "help": "Max seconds for the overall command (default: 60)"
          }
        ]
      },
      {
        "site": "weixin",
        "name": "search",
        "description": "使用搜狗微信搜索公众号文章；如需导出正文 Markdown，请使用 weixin download 处理公众号文章链接",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词；如需正文 Markdown，请使用 weixin download 处理公众号文章链接"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "结果页码，从 1 开始"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回条数，最大 10"
          }
        ]
      }
    ]
  },
  {
    "site": "weread",
    "title": "微信读书",
    "description": "可查询书籍信息；书架与笔记需要登录会话。",
    "category": "document",
    "iconKey": "document",
    "commands": [
      {
        "site": "weread",
        "name": "ai-outline",
        "description": "Get AI-generated outline for a book",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "book-id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Book ID (from shelf or search results)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 200,
            "required": false,
            "help": "Max outline items to return"
          },
          {
            "name": "depth",
            "type": "int",
            "default": 4,
            "required": false,
            "help": "Max outline depth (2=topics, 3=key points, 4=details)"
          },
          {
            "name": "raw",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Output structured rows (chapter/idx/level/text) for programmatic use"
          }
        ]
      },
      {
        "site": "weread",
        "name": "book",
        "description": "View book details on WeRead",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "book-id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Book ID from search or shelf results"
          }
        ]
      },
      {
        "site": "weread",
        "name": "book-search",
        "description": "Search within a WeRead book after resolving it by title",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "book",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Book title keyword, numeric bookId, or reader URL"
          },
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Keyword to search inside the selected book"
          },
          {
            "name": "book-rank",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Which book search result to use when book is a title keyword"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max in-book matches to return (1-100)"
          },
          {
            "name": "fragment-size",
            "type": "int",
            "default": 150,
            "required": false,
            "help": "Snippet length around each match (1-500)"
          },
          {
            "name": "raw",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Output structured rows instead of markdown text"
          }
        ]
      },
      {
        "site": "weread",
        "name": "highlights",
        "description": "List your highlights (underlines) in a book",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "book-id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Book ID (from shelf or search results)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results"
          }
        ]
      },
      {
        "site": "weread",
        "name": "login",
        "description": "Open weread login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "weread",
        "name": "notebooks",
        "description": "List books that have highlights or notes",
        "access": "read",
        "browser": true,
        "args": []
      },
      {
        "site": "weread",
        "name": "notes",
        "description": "List your notes (thoughts) on a book",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "book-id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Book ID (from shelf or search results)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results"
          }
        ]
      },
      {
        "site": "weread",
        "name": "ranking",
        "description": "WeRead book rankings by category",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "category",
            "type": "str",
            "default": "all",
            "required": false,
            "positional": true,
            "help": "Category: all (default), rising, or numeric category ID"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results"
          }
        ]
      },
      {
        "site": "weread",
        "name": "search",
        "description": "Search books on WeRead",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max results"
          }
        ]
      },
      {
        "site": "weread",
        "name": "shelf",
        "description": "List books on your WeRead bookshelf",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results"
          }
        ]
      },
      {
        "site": "weread",
        "name": "whoami",
        "description": "Show the current logged-in weread account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "xiaoyuzhou",
    "title": "小宇宙",
    "description": "中文播客平台，可查找节目与单集。",
    "category": "media",
    "iconKey": "media",
    "commands": [
      {
        "site": "xiaoyuzhou",
        "name": "download",
        "description": "Download Xiaoyuzhou episode audio",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Episode ID (eid from podcast-episodes output)"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./xiaoyuzhou-downloads",
            "required": false,
            "help": "Output directory"
          }
        ]
      },
      {
        "site": "xiaoyuzhou",
        "name": "episode",
        "description": "View details of a Xiaoyuzhou podcast episode",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Episode ID (eid from podcast-episodes output)"
          }
        ]
      },
      {
        "site": "xiaoyuzhou",
        "name": "history",
        "description": "List playback history for the logged-in Xiaoyuzhou account",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Maximum rows to return (default 20, max 5000). Ignored with --all."
          },
          {
            "name": "all",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Fetch every history page until the API cursor is exhausted."
          },
          {
            "name": "max-pages",
            "type": "int",
            "default": 500,
            "required": false,
            "help": "Pagination safety limit (default 500, max 1000)."
          }
        ]
      },
      {
        "site": "xiaoyuzhou",
        "name": "podcast",
        "description": "View a Xiaoyuzhou podcast profile",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Podcast ID (from xiaoyuzhoufm.com URL)"
          }
        ]
      },
      {
        "site": "xiaoyuzhou",
        "name": "podcast-episodes",
        "description": "List episodes of a Xiaoyuzhou podcast",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Podcast ID (from xiaoyuzhoufm.com URL)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max episodes to show"
          }
        ]
      },
      {
        "site": "xiaoyuzhou",
        "name": "transcript",
        "description": "Download Xiaoyuzhou transcript as JSON and text (requires local credentials)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Episode ID (eid from podcast-episodes output)"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./xiaoyuzhou-transcripts",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "json",
            "type": "boolean",
            "default": true,
            "required": false,
            "help": "Save transcript JSON file"
          },
          {
            "name": "text",
            "type": "boolean",
            "default": true,
            "required": false,
            "help": "Save extracted transcript text file"
          }
        ]
      }
    ]
  },
  {
    "site": "yollomi",
    "title": "Yollomi",
    "description": "海外内容社区与创作站点。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "yollomi",
        "name": "background",
        "description": "Generate AI background for a product/object image (5 credits)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "image",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Image URL (upload via \"opencli yollomi upload\" first)"
          },
          {
            "name": "prompt",
            "type": "str",
            "default": "",
            "required": false,
            "help": "Background description (optional)"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URL"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "edit",
        "description": "Edit images with AI text prompts (Qwen image edit)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "image",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Input image URL (upload via \"opencli yollomi upload\" first)"
          },
          {
            "name": "prompt",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Editing instruction (e.g. \"Make it look vintage\")"
          },
          {
            "name": "model",
            "type": "str",
            "default": "qwen-image-edit",
            "required": false,
            "help": "Edit model",
            "choices": [
              "qwen-image-edit",
              "qwen-image-edit-plus"
            ]
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URL"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "face-swap",
        "description": "Swap faces between two photos (3 credits)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "source",
            "type": "str",
            "required": true,
            "help": "Source face image URL"
          },
          {
            "name": "target",
            "type": "str",
            "required": true,
            "help": "Target photo URL"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URL"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "generate",
        "description": "Generate images with AI (text-to-image or image-to-image)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "prompt",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Text prompt describing the image"
          },
          {
            "name": "model",
            "type": "str",
            "default": "z-image-turbo",
            "required": false,
            "help": "Model ID (z-image-turbo, flux-schnell, nano-banana, flux-2-pro, ...)"
          },
          {
            "name": "ratio",
            "type": "str",
            "default": "1:1",
            "required": false,
            "help": "Aspect ratio",
            "choices": [
              "1:1",
              "16:9",
              "9:16",
              "4:3",
              "3:4"
            ]
          },
          {
            "name": "image",
            "type": "str",
            "required": false,
            "help": "Input image URL for image-to-image (upload via \"opencli yollomi upload\" first)"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URLs, skip download"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "models",
        "description": "List available Yollomi AI models (image, video, tools)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "type",
            "type": "str",
            "default": "all",
            "required": false,
            "help": "Filter by model type",
            "choices": [
              "all",
              "image",
              "video",
              "tool"
            ]
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "object-remover",
        "description": "Remove unwanted objects from images (3 credits)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "image",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Image URL"
          },
          {
            "name": "mask",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Mask image URL (white = area to remove)"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URL"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "remove-bg",
        "description": "Remove image background with AI (free)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "image",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Image URL to remove background from"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URL"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "restore",
        "description": "Restore old or damaged photos with AI (4 credits)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "image",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Image URL to restore"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URL"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "try-on",
        "description": "Virtual try-on — see how clothes look on a person (3 credits)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "person",
            "type": "str",
            "required": true,
            "help": "Person photo URL (upload via \"opencli yollomi upload\" first)"
          },
          {
            "name": "cloth",
            "type": "str",
            "required": true,
            "help": "Clothing image URL"
          },
          {
            "name": "cloth-type",
            "type": "str",
            "default": "upper",
            "required": false,
            "help": "Clothing type",
            "choices": [
              "upper",
              "lower",
              "overall"
            ]
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URL"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "upload",
        "description": "Upload an image or video to Yollomi (returns URL for other commands)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "file",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Local file path to upload"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "upscale",
        "description": "Upscale image resolution with AI (1 credit)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "image",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Image URL to upscale"
          },
          {
            "name": "scale",
            "type": "str",
            "default": "2",
            "required": false,
            "help": "Upscale factor (2 or 4)",
            "choices": [
              "2",
              "4"
            ]
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URL"
          }
        ]
      },
      {
        "site": "yollomi",
        "name": "video",
        "description": "Generate videos with AI (text-to-video or image-to-video)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "prompt",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Text prompt describing the video"
          },
          {
            "name": "model",
            "type": "str",
            "default": "kling-2-1",
            "required": false,
            "help": "Model (kling-2-1, openai-sora-2, google-veo-3-1, wan-2-5-t2v, ...)"
          },
          {
            "name": "image",
            "type": "str",
            "required": false,
            "help": "Input image URL for image-to-video"
          },
          {
            "name": "ratio",
            "type": "str",
            "default": "16:9",
            "required": false,
            "help": "Aspect ratio",
            "choices": [
              "1:1",
              "16:9",
              "9:16",
              "4:3",
              "3:4"
            ]
          },
          {
            "name": "output",
            "type": "str",
            "default": "./yollomi-output",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "no-download",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only show URL, skip download"
          }
        ]
      }
    ]
  },
  {
    "site": "amazon",
    "title": "Amazon",
    "description": "海外电商平台，可搜索商品并查看商品信息。",
    "category": "commerce",
    "iconKey": "commerce",
    "commands": [
      {
        "site": "amazon",
        "name": "bestsellers",
        "description": "Amazon Best Sellers pages for category candidate discovery",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "input",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Ranking URL or supported Amazon path. Omit to use the list root."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 100,
            "required": false,
            "help": "Maximum number of ranked items to return (default 100)"
          }
        ]
      },
      {
        "site": "amazon",
        "name": "discussion",
        "description": "Amazon review summary and sample customer discussion from product review pages",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "input",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "ASIN or product URL, for example B0FJS72893"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Maximum number of review samples to return (default 10)"
          }
        ]
      },
      {
        "site": "amazon",
        "name": "login",
        "description": "Open amazon login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "amazon",
        "name": "movers-shakers",
        "description": "Amazon Movers & Shakers pages for short-term growth signals",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "input",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Ranking URL or supported Amazon path. Omit to use the list root."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 100,
            "required": false,
            "help": "Maximum number of ranked items to return (default 100)"
          }
        ]
      },
      {
        "site": "amazon",
        "name": "new-releases",
        "description": "Amazon New Releases pages for early momentum discovery",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "input",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Ranking URL or supported Amazon path. Omit to use the list root."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 100,
            "required": false,
            "help": "Maximum number of ranked items to return (default 100)"
          }
        ]
      },
      {
        "site": "amazon",
        "name": "offer",
        "description": "Amazon seller, buy box, and fulfillment facts from the product page",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "input",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "ASIN or product URL, for example B0FJS72893"
          }
        ]
      },
      {
        "site": "amazon",
        "name": "product",
        "description": "Amazon product page facts for candidate validation",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "input",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "ASIN or product URL, for example B0FJS72893"
          }
        ]
      },
      {
        "site": "amazon",
        "name": "search",
        "description": "Amazon search results for product discovery and coarse filtering",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query, for example \"desk shelf organizer\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Maximum number of results to return (default 20)"
          }
        ]
      },
      {
        "site": "amazon",
        "name": "whoami",
        "description": "Show the current logged-in amazon account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "bilibili",
    "title": "B 站",
    "description": "视频与弹幕社区。",
    "category": "media",
    "iconKey": "bilibili",
    "commands": [
      {
        "site": "bilibili",
        "name": "comment",
        "description": "在 B站视频下发表评论或回复（官方 API，需登录；消息里的 @用户 会被解析为真实提及）",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "bvid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Video BV ID / URL / b23.tv short link"
          },
          {
            "name": "message",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Comment text. Any @username in it is resolved to a real mention"
          },
          {
            "name": "parent",
            "type": "int",
            "required": false,
            "help": "top-level/root rpid to reply under (omit for a top-level comment)"
          },
          {
            "name": "execute",
            "type": "boolean",
            "required": false,
            "help": "Actually post the comment. Without it the command refuses to write."
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "comments",
        "description": "获取 B站视频评论（官方 API；用 --parent <rpid> 读取某条评论下的「楼中楼」回复；用 --top 只看置顶评论）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "bvid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Video BV ID (e.g. BV1WtAGzYEBm)"
          },
          {
            "name": "parent",
            "type": "int",
            "required": false,
            "help": "rpid of a comment — fetch the replies under it instead of top-level comments"
          },
          {
            "name": "top",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "只返回置顶评论（与 --parent 互斥）"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of comments (max 50)"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "creator-stats",
        "description": "读取当前账号最近稿件的核心创作指标（需登录创作中心）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "bvid",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Exact case-sensitive BV ID or bilibili.com video URL"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "download",
        "description": "下载B站视频（需要 yt-dlp）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "bvid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Video BV ID (e.g., BV1xxx)"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./bilibili-downloads",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "quality",
            "type": "str",
            "default": "best",
            "required": false,
            "help": "Video quality (best, 1080p, 720p, 480p)"
          },
          {
            "name": "force",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "跳过付费内容预检直接下载（已购买/已充电/已开通会员时用）"
          },
          {
            "name": "page",
            "type": "str",
            "required": false,
            "help": "分P 选集序号（从 1 开始）。多 P 视频下载该集；缺省下载默认 P1"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "dynamic",
        "description": "Get Bilibili user dynamic feed",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "favorite",
        "description": "我的收藏夹",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "fid",
            "type": "int",
            "required": false,
            "help": "Favorite folder ID (defaults to first folder)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "feed",
        "description": "动态时间线（不传 uid 查关注时间线，传 uid 查指定用户动态）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "uid",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "用户 UID 或用户名（不传则显示关注时间线）"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results to return"
          },
          {
            "name": "type",
            "type": "str",
            "default": "all",
            "required": false,
            "help": "Filter: all, video, article, draw, text"
          },
          {
            "name": "pages",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Number of pages to fetch (each ~20 items)"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "feed-detail",
        "description": "查看 Bilibili 动态详情（支持充电专属内容）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "动态 ID（从 feed 命令的 url 中获取）"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "follow",
        "description": "关注 B站用户（官方 API，需登录）",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "target",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "目标 UID / 用户名 / space.bilibili.com 链接"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "following",
        "description": "获取 Bilibili 用户的关注列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "uid",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "目标用户 ID（默认为当前登录用户）"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "页码"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 50,
            "required": false,
            "help": "每页数量 (最大 50)"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "history",
        "description": "我的观看历史",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "hot",
        "description": "B站热门视频",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of videos"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "login",
        "description": "Open bilibili login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "me",
        "description": "My Bilibili profile info",
        "access": "read",
        "browser": true,
        "args": []
      },
      {
        "site": "bilibili",
        "name": "ranking",
        "description": "Get Bilibili video ranking board",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "search",
        "description": "Search Bilibili videos or users",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          },
          {
            "name": "type",
            "type": "str",
            "default": "video",
            "required": false,
            "help": "video or user"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Result page"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "subtitle",
        "description": "获取 Bilibili 视频的字幕",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "bvid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Bilibili 视频 BV ID（如 BV1xx411c7mD），或视频 URL / b23.tv 短链"
          },
          {
            "name": "lang",
            "type": "str",
            "required": false,
            "help": "字幕语言代码 (如 zh-CN, en-US, ai-zh)，默认取第一个"
          },
          {
            "name": "page",
            "type": "str",
            "required": false,
            "help": "分P 选集序号（从 1 开始）。多 P 视频取该集字幕；缺省取默认 P1"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "summary",
        "description": "获取 B站视频的官方 AI 总结（视频页「AI总结」同款，含分段大纲与时间戳）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "bvid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Video BV ID / URL / b23.tv short link"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "unfollow",
        "description": "取消关注 B站用户（官方 API，需登录）",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "target",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "目标 UID / 用户名 / space.bilibili.com 链接"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "user-videos",
        "description": "查看指定用户的投稿视频",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "uid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "User UID or username"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          },
          {
            "name": "order",
            "type": "str",
            "default": "pubdate",
            "required": false,
            "help": "Sort: pubdate, click, stow"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "video",
        "description": "Get Bilibili video metadata (title, author, duration, stats, etc.)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "bvid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "BV ID, video URL, or b23.tv short link"
          },
          {
            "name": "page",
            "type": "str",
            "required": false,
            "help": "分P 选集序号（从 1 开始）。多 P 视频指定某一集，title/cid 返回该集；缺省取整集默认（P1）"
          }
        ]
      },
      {
        "site": "bilibili",
        "name": "whoami",
        "description": "Show the current logged-in bilibili account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "facebook",
    "title": "Facebook",
    "description": "海外社交平台。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "facebook",
        "name": "add-friend",
        "description": "Send a friend request on Facebook",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Facebook username or profile URL"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "events",
        "description": "Browse Facebook event categories",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": "Number of categories"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "feed",
        "description": "Get your Facebook news feed",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of posts"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "friends",
        "description": "Get Facebook friend suggestions",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of friend suggestions"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "groups",
        "description": "List your Facebook groups",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of groups"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "join-group",
        "description": "Join a Facebook group",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "group",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Group ID or URL path (e.g. '1876150192925481' or group name)"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "login",
        "description": "Open facebook login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "marketplace-inbox",
        "description": "List recent Facebook Marketplace buyer/seller conversations",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of conversations to return"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "marketplace-listings",
        "description": "List your Facebook Marketplace seller listings",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of listings to return"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "memories",
        "description": "Get your Facebook memories (On This Day)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of memories"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "notifications",
        "description": "Get recent Facebook notifications (含 unread / time / url / notif_id / notif_type 列)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": "Number of notifications (1-100)"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "profile",
        "description": "Get Facebook user/page profile info",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Facebook username or page name"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "search",
        "description": "Search Facebook for people, pages, or posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "facebook",
        "name": "whoami",
        "description": "Show the current logged-in facebook account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "douyin",
    "title": "抖音",
    "description": "短视频内容与创作者平台。",
    "category": "social",
    "iconKey": "douyin",
    "commands": [
      {
        "site": "douyin",
        "name": "activities",
        "description": "官方活动列表",
        "access": "read",
        "browser": true,
        "args": []
      },
      {
        "site": "douyin",
        "name": "collections",
        "description": "合集列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "douyin",
        "name": "delete",
        "description": "删除作品（优先使用创作者后台作品管理；找不到时回退到旧删除接口）",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "aweme_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "作品 ID / item_id"
          }
        ]
      },
      {
        "site": "douyin",
        "name": "draft",
        "description": "上传视频并保存为草稿",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "video",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "视频文件路径"
          },
          {
            "name": "title",
            "type": "str",
            "required": true,
            "help": "视频标题（≤30字）"
          },
          {
            "name": "caption",
            "type": "str",
            "default": "",
            "required": false,
            "help": "正文内容（≤1000字，支持 #话题）"
          },
          {
            "name": "cover",
            "type": "str",
            "default": "",
            "required": false,
            "help": "封面图片路径"
          },
          {
            "name": "visibility",
            "type": "str",
            "default": "public",
            "required": false,
            "help": "",
            "choices": [
              "public",
              "friends",
              "private"
            ]
          }
        ]
      },
      {
        "site": "douyin",
        "name": "drafts",
        "description": "获取草稿列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "douyin",
        "name": "hashtag",
        "description": "话题搜索 / AI推荐 / 热点词",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "action",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "search=关键词搜索 (--keyword 必填), suggest=AI推荐 (--cover 必填), hot=热点词 (--keyword 可选)",
            "choices": [
              "search",
              "suggest",
              "hot"
            ]
          },
          {
            "name": "keyword",
            "type": "str",
            "default": "",
            "required": false,
            "help": "搜索关键词. search 必填; hot 可选; suggest 不使用 (传 --cover)"
          },
          {
            "name": "cover",
            "type": "str",
            "default": "",
            "required": false,
            "help": "封面 URI (cover_uri). suggest 必填; 其它 action 不使用"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "douyin",
        "name": "location",
        "description": "地理位置 POI 搜索",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "地名关键词"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "douyin",
        "name": "login",
        "description": "Open douyin login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "douyin",
        "name": "profile",
        "description": "获取账号信息",
        "access": "read",
        "browser": true,
        "args": []
      },
      {
        "site": "douyin",
        "name": "publish",
        "description": "定时发布视频到抖音（必须设置 2h ~ 14天后的发布时间）",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "video",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "视频文件路径"
          },
          {
            "name": "title",
            "type": "str",
            "required": true,
            "help": "视频标题（≤30字）"
          },
          {
            "name": "schedule",
            "type": "str",
            "required": true,
            "help": "定时发布时间（ISO8601 或 Unix 秒，2h ~ 14天后）"
          },
          {
            "name": "caption",
            "type": "str",
            "default": "",
            "required": false,
            "help": "正文内容（≤1000字，支持 #话题）"
          },
          {
            "name": "cover",
            "type": "str",
            "default": "",
            "required": false,
            "help": "封面图片路径（不提供时使用视频截帧）"
          },
          {
            "name": "visibility",
            "type": "str",
            "default": "public",
            "required": false,
            "help": "",
            "choices": [
              "public",
              "friends",
              "private"
            ]
          },
          {
            "name": "allow_download",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "允许下载"
          },
          {
            "name": "collection",
            "type": "str",
            "default": "",
            "required": false,
            "help": "合集 ID"
          },
          {
            "name": "activity",
            "type": "str",
            "default": "",
            "required": false,
            "help": "活动 ID"
          },
          {
            "name": "poi_id",
            "type": "str",
            "default": "",
            "required": false,
            "help": "地理位置 ID"
          },
          {
            "name": "poi_name",
            "type": "str",
            "default": "",
            "required": false,
            "help": "地理位置名称"
          },
          {
            "name": "hotspot",
            "type": "str",
            "default": "",
            "required": false,
            "help": "关联热点词"
          },
          {
            "name": "no_safety_check",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "跳过内容安全检测"
          },
          {
            "name": "sync_toutiao",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "同步发布到头条"
          }
        ]
      },
      {
        "site": "douyin",
        "name": "search",
        "description": "关键词搜索抖音视频",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "结果数量 (1-30)"
          }
        ]
      },
      {
        "site": "douyin",
        "name": "stats",
        "description": "获取单个作品的创作者中心深层指标",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "aweme_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "抖音作品 ID（aweme_id，可从作品 URL 末尾获取）"
          }
        ]
      },
      {
        "site": "douyin",
        "name": "update",
        "description": "更新视频信息",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "aweme_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "抖音作品 ID（aweme_id，可从作品 URL 末尾获取）"
          },
          {
            "name": "reschedule",
            "type": "str",
            "default": "",
            "required": false,
            "help": "新的发布时间（ISO8601 或 Unix 秒）"
          },
          {
            "name": "caption",
            "type": "str",
            "default": "",
            "required": false,
            "help": "新的正文内容"
          }
        ]
      },
      {
        "site": "douyin",
        "name": "user-videos",
        "description": "获取指定用户的视频列表（含下载地址和热门评论）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "sec_uid",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "用户 sec_uid（个人主页 URL 末尾部分，也可直接传整条主页 URL）"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "获取数量（最大 20）"
          },
          {
            "name": "with_comments",
            "type": "bool",
            "default": true,
            "required": false,
            "help": "包含热门评论（默认: true）"
          },
          {
            "name": "comment_limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "每个视频获取多少条评论（最大 10）"
          }
        ]
      },
      {
        "site": "douyin",
        "name": "videos",
        "description": "获取作品列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "最多返回多少个作品（跨页自动收集）"
          },
          {
            "name": "status",
            "type": "str",
            "default": "all",
            "required": false,
            "help": "",
            "choices": [
              "all",
              "published",
              "reviewing",
              "scheduled"
            ]
          }
        ]
      },
      {
        "site": "douyin",
        "name": "whoami",
        "description": "Show the current logged-in douyin account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "instagram",
    "title": "Instagram",
    "description": "图片与短视频社交平台。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "instagram",
        "name": "collection-create",
        "description": "Create a new Instagram saved-posts collection (folder)",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Name of the collection to create"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "collection-delete",
        "description": "Delete an Instagram saved-posts collection (folder) by name or id",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "target",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Collection name (case-insensitive) or numeric collection_id"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "comment",
        "description": "Comment on an Instagram post",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Username of the post author"
          },
          {
            "name": "text",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Comment text"
          },
          {
            "name": "index",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Post index (1 = most recent)"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "download",
        "description": "Download images and videos from Instagram posts and reels",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Instagram post / reel / tv URL"
          },
          {
            "name": "path",
            "type": "str",
            "default": "~/Downloads/Instagram",
            "required": false,
            "help": "Download directory"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "explore",
        "description": "Instagram explore/discover trending posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of posts"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "follow",
        "description": "Follow an Instagram user",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Instagram username to follow"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "followers",
        "description": "List followers of an Instagram user",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Instagram username"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of followers"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "following",
        "description": "List accounts an Instagram user is following",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Instagram username"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of accounts"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "like",
        "description": "Like an Instagram post",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Username of the post author"
          },
          {
            "name": "index",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Post index (1 = most recent)"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "login",
        "description": "Open instagram login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "note",
        "description": "Publish a text Instagram note",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "content",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Note text (max 60 characters)"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 120,
            "required": false,
            "help": "Max seconds for the overall command (default: 120)"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "post",
        "description": "Post an Instagram feed image or mixed-media carousel",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "media",
            "type": "str",
            "required": false,
            "valueRequired": true,
            "help": "Comma-separated media paths (images/videos, up to 10)"
          },
          {
            "name": "content",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Caption text"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Max seconds for the overall command (default: 300)"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "profile",
        "description": "Get Instagram user profile info",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Instagram username"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "reel",
        "description": "Post an Instagram reel video",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "video",
            "type": "str",
            "required": false,
            "valueRequired": true,
            "help": "Path to a single .mp4 video file"
          },
          {
            "name": "content",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Caption text"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 600,
            "required": false,
            "help": "Max seconds for the overall command (default: 600)"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "save",
        "description": "Save (bookmark) an Instagram post",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Username of the post author"
          },
          {
            "name": "index",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Post index (1 = most recent)"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "saved",
        "description": "Get your saved Instagram posts (optionally from a specific collection)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of saved posts"
          },
          {
            "name": "collection",
            "type": "str",
            "required": false,
            "help": "Collection name (case-insensitive). Omit for the default \"All posts\" feed."
          }
        ]
      },
      {
        "site": "instagram",
        "name": "search",
        "description": "Search Instagram users",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "story",
        "description": "Post a single Instagram story image or video",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "media",
            "type": "str",
            "required": false,
            "valueRequired": true,
            "help": "Path to a single story image or video file"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Max seconds for the overall command (default: 300)"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "unfollow",
        "description": "Unfollow an Instagram user",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Instagram username to unfollow"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "unlike",
        "description": "Unlike an Instagram post",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Username of the post author"
          },
          {
            "name": "index",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Post index (1 = most recent)"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "unsave",
        "description": "Unsave (remove bookmark) an Instagram post",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Username of the post author"
          },
          {
            "name": "index",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Post index (1 = most recent)"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "user",
        "description": "Get recent posts from an Instagram user",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Instagram username"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 12,
            "required": false,
            "help": "Number of posts"
          }
        ]
      },
      {
        "site": "instagram",
        "name": "whoami",
        "description": "Show the current logged-in instagram account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "linkedin",
    "title": "LinkedIn",
    "description": "职场社交与招聘平台。",
    "category": "social",
    "iconKey": "social",
    "commands": [
      {
        "site": "linkedin",
        "name": "company",
        "description": "Read a LinkedIn company page: industry, size, HQ, founded, website, followers, about",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "company",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Company universal name (nvidia), /company/<name> path, or full URL"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "connect",
        "description": "Fail-closed LinkedIn connection request sender that verifies the exact profile before optionally sending a note",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "profile-url",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Exact LinkedIn profile URL to open and verify"
          },
          {
            "name": "expected-name",
            "type": "string",
            "required": true,
            "help": "Expected visible profile name"
          },
          {
            "name": "note",
            "type": "string",
            "default": "",
            "required": false,
            "help": "Optional connection note, max 300 chars"
          },
          {
            "name": "send",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Actually click Send. Default is dry-run verification only."
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "connections",
        "description": "List your LinkedIn first-degree connections (name, headline, profile URL)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of connections to return (max 500)"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "inbox",
        "description": "List LinkedIn messaging inbox conversations and unread messages",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 40,
            "required": false,
            "help": "Maximum conversations to return (1-100)"
          },
          {
            "name": "unread-only",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Return only conversations with unread messages"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "job-detail",
        "description": "Read one LinkedIn job page with description, apply URL, workplace type, applicants, and company metadata",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "job-url",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Exact LinkedIn job URL, e.g. https://www.linkedin.com/jobs/view/123/"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "jobs-preferences",
        "description": "Read visible LinkedIn Jobs preferences and alert settings without changing them",
        "access": "read",
        "browser": true,
        "args": []
      },
      {
        "site": "linkedin",
        "name": "login",
        "description": "Open linkedin login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "people-search",
        "description": "Search standard LinkedIn (not Sales Navigator) for people by keyword. Each invocation consumes against LinkedIn's monthly Commercial Use Limit on people search; throttle accordingly.",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "keywords",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "People search keywords, e.g. \"site reliability engineer berlin\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 5,
            "required": false,
            "help": "Maximum people to return (1-10); each query counts toward LinkedIn's monthly CUL"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "post-analytics",
        "description": "Summarize raw visible LinkedIn post counters without custom scoring or classification",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "profile-url",
            "type": "string",
            "required": false,
            "help": "LinkedIn /in/<handle>/ profile URL. Defaults to /in/me/."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Maximum posts to summarize (1-100)"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "posts",
        "description": "Export visible posts from a LinkedIn profile activity page with engagement metrics",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "profile-url",
            "type": "string",
            "required": false,
            "help": "LinkedIn /in/<handle>/ profile URL. Defaults to /in/me/."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Maximum posts to return (1-100)"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "profile-analytics",
        "description": "Read visible LinkedIn profile dashboard metrics such as profile views, post impressions, and search appearances",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "profile-url",
            "type": "string",
            "required": false,
            "help": "LinkedIn /in/<handle>/ profile URL. Defaults to /in/me/."
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "profile-experience",
        "description": "Read visible LinkedIn profile experience entries with titles, dates, locations, skills, media, and URLs",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "profile-url",
            "type": "string",
            "required": false,
            "help": "LinkedIn /in/<handle>/ profile URL. Defaults to /in/me/."
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "profile-projects",
        "description": "Read visible LinkedIn profile projects with descriptions, dates, skills, media, and URLs",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "profile-url",
            "type": "string",
            "required": false,
            "help": "LinkedIn /in/<handle>/ profile URL. Defaults to /in/me/."
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "profile-read",
        "description": "Read visible LinkedIn profile sections: headline, About, experience, education, services, and featured sections",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "profile-url",
            "type": "string",
            "required": false,
            "help": "LinkedIn /in/<handle>/ profile URL. Defaults to /in/me/."
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "safe-send",
        "description": "Fail-closed LinkedIn message sender that verifies exact thread, recipient, and latest message before filling/sending",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "thread-url",
            "type": "str",
            "required": true,
            "help": "Exact LinkedIn messaging thread URL to open and verify"
          },
          {
            "name": "expected-name",
            "type": "str",
            "required": true,
            "help": "Expected visible recipient name in the active thread header"
          },
          {
            "name": "message",
            "type": "str",
            "required": true,
            "help": "Message body to send or dry-run"
          },
          {
            "name": "expected-last-text",
            "type": "str",
            "required": false,
            "help": "Substring expected in the currently visible latest conversation context"
          },
          {
            "name": "expected-last-hash",
            "type": "str",
            "required": false,
            "help": "SHA-256 hash of expected latest visible message text"
          },
          {
            "name": "send",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Actually click Send. Default is dry-run verification only."
          },
          {
            "name": "screenshot",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Capture a screenshot during verification"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "salesnav-inbox",
        "description": "List LinkedIn Sales Navigator message conversations with API pagination",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "number",
            "default": 40,
            "required": false,
            "help": "Maximum conversations to return (1-500)"
          },
          {
            "name": "max-pages",
            "type": "number",
            "default": 30,
            "required": false,
            "help": "Maximum Sales Navigator API pages to fetch"
          },
          {
            "name": "unread-only",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Return only unread conversations"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "salesnav-message",
        "description": "Send or dry-run a LinkedIn Sales Navigator InMail to a lead using the Sales Navigator messaging API",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "recipient",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Sales Navigator lead URL, LinkedIn /in/ URL from salesnav-search, or urn:li:fs_salesProfile:(...)"
          },
          {
            "name": "subject",
            "type": "string",
            "required": true,
            "help": "InMail subject"
          },
          {
            "name": "body",
            "type": "string",
            "required": true,
            "help": "InMail body"
          },
          {
            "name": "send",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Actually send the InMail. Default is dry-run validation only."
          },
          {
            "name": "copy-to-crm",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Set Sales Navigator copyToCrm on the message request"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "salesnav-search",
        "description": "Search LinkedIn Sales Navigator for people leads by keyword",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "keywords",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "People search keywords, e.g. \"quality manager food manufacturing\""
          },
          {
            "name": "limit",
            "type": "number",
            "default": 25,
            "required": false,
            "help": "Maximum leads to return (1-500, fetched 25 per request)"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "salesnav-thread",
        "description": "Return full Sales Navigator message history for a thread id, Sales Navigator inbox URL, lead URL, recipient urn, or exact recipient name",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "thread-or-recipient",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Sales Navigator inbox URL/thread id, Sales Navigator lead URL, recipient urn, or exact participant name"
          },
          {
            "name": "limit",
            "type": "number",
            "default": 200,
            "required": false,
            "help": "Maximum messages to return (1-500)"
          },
          {
            "name": "max-pages",
            "type": "number",
            "default": 30,
            "required": false,
            "help": "Maximum inbox pages to scan when resolving a recipient"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "search",
        "description": "Search LinkedIn jobs",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Job search keywords"
          },
          {
            "name": "location",
            "type": "string",
            "required": false,
            "help": "Location text such as San Francisco Bay Area"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of jobs to return (max 100)"
          },
          {
            "name": "start",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Result offset for pagination"
          },
          {
            "name": "details",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Include full job description and apply URL (slower)"
          },
          {
            "name": "company",
            "type": "string",
            "required": false,
            "help": "Comma-separated company names or LinkedIn company IDs"
          },
          {
            "name": "experience-level",
            "type": "string",
            "required": false,
            "help": "Comma-separated: internship, entry, associate, mid-senior, director, executive"
          },
          {
            "name": "job-type",
            "type": "string",
            "required": false,
            "help": "Comma-separated: full-time, part-time, contract, temporary, volunteer, internship, other"
          },
          {
            "name": "date-posted",
            "type": "string",
            "required": false,
            "help": "One of: any, month, week, 24h"
          },
          {
            "name": "remote",
            "type": "string",
            "required": false,
            "help": "Comma-separated: on-site, hybrid, remote"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "sent-invitations",
        "description": "List pending LinkedIn sent invitations for CRM reconciliation",
        "access": "read",
        "browser": true,
        "args": []
      },
      {
        "site": "linkedin",
        "name": "services-read",
        "description": "Read LinkedIn Services page details including services, overview, availability, pricing, and media titles/descriptions",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "profile-url",
            "type": "string",
            "required": false,
            "help": "LinkedIn /in/<handle>/ profile URL. Defaults to /in/me/."
          },
          {
            "name": "services-url",
            "type": "string",
            "required": false,
            "help": "LinkedIn /services/page/<id>/ URL. If omitted, it is discovered from the profile."
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "thread-snapshot",
        "description": "Load a LinkedIn messaging thread and return a structured conversation snapshot",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "thread-url",
            "type": "str",
            "required": true,
            "help": "Exact LinkedIn messaging thread URL to open and snapshot"
          },
          {
            "name": "max-scrolls",
            "type": "number",
            "default": 30,
            "required": false,
            "help": "Maximum upward scroll attempts used to request older message pages"
          },
          {
            "name": "json",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Return only JSON snapshot string in the snapshot_json field"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "timeline",
        "description": "Read LinkedIn home timeline posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of posts to return (max 100)"
          }
        ]
      },
      {
        "site": "linkedin",
        "name": "whoami",
        "description": "Show the current logged-in linkedin account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "reddit",
    "title": "Reddit",
    "description": "海外论坛与社区平台。",
    "category": "social",
    "iconKey": "reddit",
    "commands": [
      {
        "site": "reddit",
        "name": "comment",
        "description": "Post a comment on a Reddit post",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "post-id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Post ID (e.g. 1abc123) or fullname (t3_xxx)"
          },
          {
            "name": "text",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Comment text"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "frontpage",
        "description": "Reddit Frontpage / r/all",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "reddit",
        "name": "home",
        "description": "Reddit personalized home feed (Best, requires login)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Number of posts (1–100)"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "hot",
        "description": "Reddit 热门帖子",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "subreddit",
            "type": "str",
            "default": "",
            "required": false,
            "help": "Subreddit name (e.g. programming). Empty for frontpage"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of posts"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "login",
        "description": "Open reddit login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "popular",
        "description": "Reddit Popular posts (/r/popular)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "reddit",
        "name": "read",
        "description": "Read a Reddit post and its comments",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "post-id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Post ID (e.g. 1abc123) or full URL"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "best",
            "required": false,
            "help": "Comment sort: best, top, new, controversial, old, qa"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Number of top-level comments"
          },
          {
            "name": "depth",
            "type": "int",
            "default": 2,
            "required": false,
            "help": "Max reply depth (1=no replies, 2=one level of replies, etc.)"
          },
          {
            "name": "replies",
            "type": "int",
            "default": 5,
            "required": false,
            "help": "Max replies shown per comment at each level (sorted by score)"
          },
          {
            "name": "max-length",
            "type": "int",
            "default": 2000,
            "required": false,
            "help": "Max characters per comment body (min 100)"
          },
          {
            "name": "expand-more",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Follow Reddit \"more comments\" stubs by calling /api/morechildren.json"
          },
          {
            "name": "expand-rounds",
            "type": "int",
            "default": 2,
            "required": false,
            "help": "Max expansion passes when --expand-more is on (1–5; each round can fan out new \"more\" stubs)"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "reply",
        "description": "Reply to a Reddit comment",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "comment-id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Comment ID (e.g. okf3s7u) or fullname (t1_xxx)"
          },
          {
            "name": "text",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Reply text"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "save",
        "description": "Save or unsave a Reddit post",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "post-id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Post ID (e.g. 1abc123) or fullname (t3_xxx)"
          },
          {
            "name": "undo",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Unsave instead of save"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "saved",
        "description": "Browse your saved Reddit posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "reddit",
        "name": "search",
        "description": "Search Reddit Posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Reddit search query"
          },
          {
            "name": "subreddit",
            "type": "string",
            "default": "",
            "required": false,
            "help": "Search within a specific subreddit"
          },
          {
            "name": "sort",
            "type": "string",
            "default": "relevance",
            "required": false,
            "help": "Sort order: relevance, hot, top, new, comments"
          },
          {
            "name": "time",
            "type": "string",
            "default": "all",
            "required": false,
            "help": "Time filter: hour, day, week, month, year, all"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "reddit",
        "name": "subreddit",
        "description": "Get posts from a specific Subreddit",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "name",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Subreddit name (no `r/` prefix; e.g. `python`)"
          },
          {
            "name": "sort",
            "type": "string",
            "default": "hot",
            "required": false,
            "help": "Sorting method: hot, new, top, rising, controversial"
          },
          {
            "name": "time",
            "type": "string",
            "default": "all",
            "required": false,
            "help": "Time filter for top/controversial: hour, day, week, month, year, all"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "reddit",
        "name": "subreddit-info",
        "description": "Show metadata for a Reddit subreddit (subscribers, description, created date, NSFW)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "name",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Subreddit name (no `r/` prefix needed)"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "subscribe",
        "description": "Subscribe or unsubscribe to a subreddit",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "subreddit",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Subreddit name (e.g. python)"
          },
          {
            "name": "undo",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Unsubscribe instead of subscribe"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "subscribed",
        "description": "List subreddits you are subscribed to",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 100,
            "required": false,
            "help": "Max subreddits to return (1-1000, auto-paginates)"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "upvote",
        "description": "Upvote or downvote a Reddit post",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "post-id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Post ID (e.g. 1abc123) or fullname (t3_xxx)"
          },
          {
            "name": "direction",
            "type": "string",
            "default": "up",
            "required": false,
            "help": "Vote direction: up, down, none"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "upvoted",
        "description": "Browse your upvoted Reddit posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "reddit",
        "name": "user",
        "description": "View a Reddit user profile",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Reddit username (no `u/` prefix needed)"
          }
        ]
      },
      {
        "site": "reddit",
        "name": "user-comments",
        "description": "View a Reddit user's comment history",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Reddit username (no `u/` prefix needed)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "reddit",
        "name": "user-posts",
        "description": "View a Reddit user's submitted posts",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "username",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Reddit username (no `u/` prefix needed)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": ""
          }
        ]
      },
      {
        "site": "reddit",
        "name": "whoami",
        "description": "Show the currently logged-in Reddit user",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "wechat-channels",
    "title": "微信视频号",
    "description": "微信视频号网页端内容平台。",
    "category": "media",
    "iconKey": "media",
    "commands": [
      {
        "site": "wechat-channels",
        "name": "login",
        "description": "Open wechat-channels login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "wechat-channels",
        "name": "publish",
        "description": "发布视频到视频号",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "video",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "视频文件路径 (.mp4/.mov/.avi/.webm)"
          },
          {
            "name": "title",
            "type": "str",
            "required": false,
            "help": "短标题（建议 6-16 字）"
          },
          {
            "name": "caption",
            "type": "str",
            "required": false,
            "help": "描述内容，支持直接写 #话题（如：日常生活 #搞笑 #生活）"
          },
          {
            "name": "schedule",
            "type": "str",
            "required": false,
            "help": "定时发布时间（ISO8601 或 Unix 秒，如 \"2026-05-20 10:00\"）"
          },
          {
            "name": "draft",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "保存为草稿"
          },
          {
            "name": "manual",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "填完所有字段后不自动发布，由用户手动点击发表（务必同时传 --site-session persistent，否则表单页约 30 秒后会被重置为空白页）"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 600,
            "required": false,
            "help": "命令整体超时秒数（含登录等待 + 上传转码，默认 600）"
          }
        ]
      },
      {
        "site": "wechat-channels",
        "name": "whoami",
        "description": "Show the current logged-in wechat-channels account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "youtube",
    "title": "YouTube",
    "description": "海外视频平台。",
    "category": "media",
    "iconKey": "media",
    "commands": [
      {
        "site": "youtube",
        "name": "channel",
        "description": "Get YouTube channel info and recent videos",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Channel ID (UCxxxx) or handle (@name)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max recent videos (max 30)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "comments",
        "description": "Get YouTube video comments",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "YouTube video URL or video ID"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max comments (max 100)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "feed",
        "description": "Get YouTube homepage recommended videos",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max videos to return (default 20, max 100)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "history",
        "description": "Get YouTube watch history",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Max videos to return (default 30, max 200)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "like",
        "description": "Like a YouTube video",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "YouTube video URL or video ID"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "login",
        "description": "Open youtube login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "playlist",
        "description": "Get YouTube playlist info and video list",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Playlist URL or playlist ID (PLxxxxxx)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 50,
            "required": false,
            "help": "Max videos to return (default 50, max 200)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "search",
        "description": "Search YouTube videos, Shorts, channels, and playlists",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (max 50)"
          },
          {
            "name": "type",
            "type": "str",
            "default": "",
            "required": false,
            "help": "Filter type: shorts, video, channel, playlist"
          },
          {
            "name": "upload",
            "type": "str",
            "default": "",
            "required": false,
            "help": "Upload date: hour, today, week, month, year"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "",
            "required": false,
            "help": "Sort by: relevance, date, views, rating"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "subscribe",
        "description": "Subscribe to a YouTube channel",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "channel",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Channel ID (UCxxxx) or handle (@name)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "subscriptions",
        "description": "List subscribed YouTube channels",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 50,
            "required": false,
            "help": "Max channels to return (default 50)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "transcript",
        "description": "Get YouTube video transcript/subtitles",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "YouTube video URL or video ID"
          },
          {
            "name": "lang",
            "type": "str",
            "required": false,
            "help": "Language code (e.g. en, zh-Hans). Omit to auto-select"
          },
          {
            "name": "mode",
            "type": "str",
            "default": "grouped",
            "required": false,
            "help": "Output mode: grouped (readable paragraphs) or raw (every segment)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "unlike",
        "description": "Remove like from a YouTube video",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "YouTube video URL or video ID"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "unsubscribe",
        "description": "Unsubscribe from a YouTube channel",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "channel",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Channel ID (UCxxxx) or handle (@name)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "video",
        "description": "Get YouTube video metadata (title, views, description, etc.)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "YouTube video URL or video ID"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "watch-later",
        "description": "Get your YouTube Watch Later queue",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 50,
            "required": false,
            "help": "Max videos to return (default 50, max 200)"
          }
        ]
      },
      {
        "site": "youtube",
        "name": "whoami",
        "description": "Show the current logged-in youtube account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "zhihu",
    "title": "知乎",
    "description": "中文问答与知识社区。",
    "category": "social",
    "iconKey": "zhihu",
    "commands": [
      {
        "site": "zhihu",
        "name": "answer",
        "description": "Answer a Zhihu question",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "target",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Zhihu question URL or typed target"
          },
          {
            "name": "text",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Answer text"
          },
          {
            "name": "file",
            "type": "str",
            "required": false,
            "help": "Answer text file path"
          },
          {
            "name": "execute",
            "type": "boolean",
            "required": false,
            "help": "Actually perform the write action"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "answer-comments",
        "description": "知乎回答评论列表（保留回复层级）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Answer ID, full Zhihu answer URL, or typed target (answer:<qid>:<aid>)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of top-level comments (max 1000)"
          },
          {
            "name": "replies-limit",
            "type": "int",
            "default": 3,
            "required": false,
            "help": "Number of replies to include per top-level comment (max 100)"
          },
          {
            "name": "order",
            "type": "str",
            "default": "score",
            "required": false,
            "help": "Root comment order",
            "choices": [
              "score",
              "latest"
            ]
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "answer-detail",
        "description": "知乎单个回答完整内容（按 answer ID 获取）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Answer ID, full Zhihu answer URL, or typed target (answer:<qid>:<aid>)"
          },
          {
            "name": "max-content",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Optional cap on stripped content length in characters (0 = no truncation, return the full answer)"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "collection",
        "description": "知乎收藏夹内容列表（需要登录）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "收藏夹 ID (数字，可从收藏夹 URL 中获取)"
          },
          {
            "name": "offset",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "起始偏移量（用于分页）"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "每页数量（最大 20）"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "collections",
        "description": "知乎收藏夹列表（需要登录）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "每页数量（最大 20）"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "comment",
        "description": "Create a top-level comment on a Zhihu answer or article",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "target",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Zhihu target URL or typed target"
          },
          {
            "name": "text",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Comment text"
          },
          {
            "name": "file",
            "type": "str",
            "required": false,
            "help": "Comment text file path"
          },
          {
            "name": "execute",
            "type": "boolean",
            "required": false,
            "help": "Actually perform the write action"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "download",
        "description": "导出知乎专栏文章或回答为 Markdown 格式",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "help": "Column article URL, answer ID, typed target, or answer URL"
          },
          {
            "name": "output",
            "type": "str",
            "default": "./zhihu-articles",
            "required": false,
            "help": "Output directory"
          },
          {
            "name": "download-images",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Download images locally"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "favorite",
        "description": "Favorite a Zhihu answer or article into a specific collection",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "target",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Zhihu target URL or typed target"
          },
          {
            "name": "collection",
            "type": "str",
            "required": false,
            "help": "Collection name"
          },
          {
            "name": "collection-id",
            "type": "str",
            "required": false,
            "help": "Stable collection id"
          },
          {
            "name": "execute",
            "type": "boolean",
            "required": false,
            "help": "Actually perform the write action"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "follow",
        "description": "Follow a Zhihu user or question",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "target",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Zhihu target URL or typed target"
          },
          {
            "name": "execute",
            "type": "boolean",
            "required": false,
            "help": "Actually perform the write action"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "followers",
        "description": "知乎某用户的粉丝列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "user",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "User url_token or people URL"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of followers to return (max 1000)"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "following",
        "description": "知乎某用户关注的人列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "user",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "User url_token or people URL"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of followees to return (max 1000)"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "hot",
        "description": "知乎热榜",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of items to return"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "like",
        "description": "Like a Zhihu answer or article",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "target",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Zhihu target URL or typed target"
          },
          {
            "name": "execute",
            "type": "boolean",
            "required": false,
            "help": "Actually perform the write action"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "login",
        "description": "Open zhihu login and wait until the browser session is authenticated",
        "access": "write",
        "browser": true,
        "args": [
          {
            "name": "timeout",
            "type": "int",
            "default": 300,
            "required": false,
            "help": "Maximum seconds to wait for the user to finish login"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "pins",
        "description": "知乎某用户的想法（短内容）列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "user",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "User url_token or people URL"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of pins to return (max 1000)"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "question",
        "description": "知乎问题详情和回答",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Question ID (numeric)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 5,
            "required": false,
            "help": "Number of answers (max 1000; use normal-sized requests)"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "default",
            "required": false,
            "help": "Answer order: default or created",
            "choices": [
              "default",
              "created"
            ]
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "recommend",
        "description": "知乎首页推荐",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of items to return (max 1000; use normal-sized requests)"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "search",
        "description": "知乎搜索",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results (max 1000; use normal-sized requests)"
          },
          {
            "name": "type",
            "type": "str",
            "default": "all",
            "required": false,
            "help": "Result type: all, answer, article, or question",
            "choices": [
              "all",
              "answer",
              "article",
              "question"
            ]
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "user",
        "description": "知乎用户主页资料（粉丝/关注/回答/文章/获赞数）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "user",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "User url_token or people URL, e.g. wen-jie-16-47"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "user-answers",
        "description": "知乎某用户的回答列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "user",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "User url_token or people URL"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of answers to return (max 1000)"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "user-articles",
        "description": "知乎某用户的文章/专栏列表",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "user",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "User url_token or people URL"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of articles to return (max 1000)"
          }
        ]
      },
      {
        "site": "zhihu",
        "name": "whoami",
        "description": "Show the current logged-in zhihu account",
        "access": "read",
        "browser": true,
        "args": []
      }
    ]
  },
  {
    "site": "autohome",
    "title": "汽车之家",
    "description": "国内汽车资讯、车型参数与车友论坛。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "autohome",
        "name": "brand",
        "description": "汽车之家按品牌列出全部车系 + 厂商指导价（免登录）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "brand",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "品牌名（宝马 / 比亚迪 / 理想 / 丰田 …）或车系目录首字母 A-Z"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 60,
            "required": false,
            "help": "返回的车系数量（最多 120）"
          }
        ]
      },
      {
        "site": "autohome",
        "name": "score",
        "description": "汽车之家车系口碑评分（总分 + 各维度 + 故障率PPH + 竞品对比，免登录）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "series_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "车系 ID（来自 brand 的 series_id，或 k.autohome.com.cn/<id> URL）"
          }
        ]
      }
    ]
  },
  {
    "site": "binance",
    "title": "币安",
    "description": "加密货币行情与交易平台。",
    "category": "finance",
    "iconKey": "finance",
    "requiresLogin": false,
    "commands": [
      {
        "site": "binance",
        "name": "asks",
        "description": "Order book ask prices for a trading pair",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "symbol",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Trading pair symbol (e.g. BTCUSDT, ETHUSDT)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of price levels (5, 10, 20, 50, 100)"
          }
        ]
      },
      {
        "site": "binance",
        "name": "depth",
        "description": "Order book bid and ask prices for a trading pair",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "symbol",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Trading pair symbol (e.g. BTCUSDT, ETHUSDT)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of price levels (5, 10, 20, 50, 100)"
          }
        ]
      },
      {
        "site": "binance",
        "name": "gainers",
        "description": "Top gaining trading pairs by 24h price change",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of trading pairs"
          }
        ]
      },
      {
        "site": "binance",
        "name": "klines",
        "description": "Candlestick/kline data for a trading pair",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "symbol",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Trading pair symbol (e.g. BTCUSDT, ETHUSDT)"
          },
          {
            "name": "interval",
            "type": "str",
            "default": "1d",
            "required": false,
            "help": "Kline interval (1m, 5m, 15m, 1h, 4h, 1d, 1w, 1M)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of klines (max 1000)"
          }
        ]
      },
      {
        "site": "binance",
        "name": "losers",
        "description": "Top losing trading pairs by 24h price change",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of trading pairs"
          }
        ]
      },
      {
        "site": "binance",
        "name": "pairs",
        "description": "List active trading pairs on Binance",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of trading pairs"
          }
        ]
      },
      {
        "site": "binance",
        "name": "price",
        "description": "Quick price check for a trading pair",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "symbol",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Trading pair symbol (e.g. BTCUSDT, ETHUSDT)"
          }
        ]
      },
      {
        "site": "binance",
        "name": "prices",
        "description": "Latest prices for all trading pairs",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of prices"
          }
        ]
      },
      {
        "site": "binance",
        "name": "ticker",
        "description": "24h ticker statistics for top trading pairs by volume",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of tickers"
          }
        ]
      },
      {
        "site": "binance",
        "name": "top",
        "description": "Top trading pairs by 24h volume on Binance",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of trading pairs"
          }
        ]
      },
      {
        "site": "binance",
        "name": "trades",
        "description": "Recent trades for a trading pair",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "symbol",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Trading pair symbol (e.g. BTCUSDT, ETHUSDT)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of trades (max 1000)"
          }
        ]
      }
    ]
  },
  {
    "site": "bluesky",
    "title": "Bluesky",
    "description": "去中心化微博类社交网络。",
    "category": "social",
    "iconKey": "social",
    "requiresLogin": false,
    "commands": [
      {
        "site": "bluesky",
        "name": "feeds",
        "description": "Popular Bluesky feed generators",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of feeds"
          }
        ]
      },
      {
        "site": "bluesky",
        "name": "followers",
        "description": "List followers of a Bluesky user",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "handle",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Bluesky handle"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of followers"
          }
        ]
      },
      {
        "site": "bluesky",
        "name": "following",
        "description": "List accounts a Bluesky user is following",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "handle",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Bluesky handle"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of accounts"
          }
        ]
      },
      {
        "site": "bluesky",
        "name": "profile",
        "description": "Get Bluesky user profile info",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "handle",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Bluesky handle (e.g. bsky.app, jay.bsky.team)"
          }
        ]
      },
      {
        "site": "bluesky",
        "name": "search",
        "description": "Search Bluesky users",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "bluesky",
        "name": "starter-packs",
        "description": "Get starter packs created by a Bluesky user",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "handle",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Bluesky handle"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of starter packs"
          }
        ]
      },
      {
        "site": "bluesky",
        "name": "thread",
        "description": "Get a Bluesky post thread with replies",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "uri",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Post AT URI (at://did:.../app.bsky.feed.post/...) or bsky.app URL"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of replies"
          }
        ]
      },
      {
        "site": "bluesky",
        "name": "trending",
        "description": "Trending topics on Bluesky",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of topics"
          }
        ]
      },
      {
        "site": "bluesky",
        "name": "user",
        "description": "Get recent posts from a Bluesky user",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "handle",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Bluesky handle (e.g. bsky.app)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of posts"
          }
        ]
      }
    ]
  },
  {
    "site": "booking",
    "title": "Booking",
    "description": "全球酒店与民宿查询预订平台。",
    "category": "commerce",
    "iconKey": "commerce",
    "requiresLogin": false,
    "commands": [
      {
        "site": "booking",
        "name": "search",
        "description": "Search Booking.com hotels by destination and dates (server-rendered card scrape).",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "destination",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Destination keyword (city, district, or hotel name)"
          },
          {
            "name": "checkin",
            "type": "str",
            "required": true,
            "help": "Check-in date YYYY-MM-DD"
          },
          {
            "name": "checkout",
            "type": "str",
            "required": true,
            "help": "Check-out date YYYY-MM-DD"
          },
          {
            "name": "adults",
            "type": "int",
            "default": 2,
            "required": false,
            "help": "Number of adults (1-30)"
          },
          {
            "name": "rooms",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Number of rooms (1-30)"
          },
          {
            "name": "children",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Number of children (0-10)"
          },
          {
            "name": "currency",
            "type": "str",
            "required": false,
            "help": "Force result currency (e.g. USD, JPY, CNY)"
          },
          {
            "name": "lang",
            "type": "str",
            "required": false,
            "help": "Force result language (e.g. en-us, zh-cn, ja)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Max rows to return (1-100; Booking pages 25 per request)"
          },
          {
            "name": "offset",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Result offset for pagination (multiple of 25)"
          }
        ]
      }
    ]
  },
  {
    "site": "brave",
    "title": "Brave",
    "description": "注重隐私的浏览器项目站点。",
    "category": "browser",
    "iconKey": "browser",
    "requiresLogin": false,
    "commands": [
      {
        "site": "brave",
        "name": "search",
        "description": "Search Brave Search",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results per page (max 18)"
          },
          {
            "name": "offset",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Page offset (0, 1, 2...). Brave returns ~18 results per page"
          }
        ]
      }
    ]
  },
  {
    "site": "coingecko",
    "title": "CoinGecko",
    "description": "加密货币币价、市值与项目数据查询。",
    "category": "finance",
    "iconKey": "finance",
    "requiresLogin": false,
    "commands": [
      {
        "site": "coingecko",
        "name": "categories",
        "description": "Crypto categories ranked by aggregated market cap",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "sort",
            "type": "str",
            "default": "market_cap_desc",
            "required": false,
            "help": "Sort order (market_cap_desc / market_cap_asc / name_desc / name_asc / market_cap_change_24h_desc / market_cap_change_24h_asc)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of categories (1-100; CoinGecko returns ~120 max)"
          }
        ]
      },
      {
        "site": "coingecko",
        "name": "coin",
        "description": "Fetch a single cryptocurrency's market data by CoinGecko id (e.g. bitcoin, ethereum).",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "CoinGecko coin id (lowercase, e.g. bitcoin / ethereum / solana)."
          },
          {
            "name": "currency",
            "type": "string",
            "default": "usd",
            "required": false,
            "help": "Quote currency (usd, cny, eur, jpy, ...)."
          }
        ]
      },
      {
        "site": "coingecko",
        "name": "derivatives",
        "description": "Top crypto derivative (perpetual / futures) markets by 24h volume",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max rows to return (1-500; CoinGecko returns one large page)."
          },
          {
            "name": "symbol",
            "type": "string",
            "required": false,
            "help": "Optional symbol substring filter (e.g. \"BTC\", \"ETHUSDT\")."
          }
        ]
      },
      {
        "site": "coingecko",
        "name": "exchanges",
        "description": "Top crypto exchanges by 24h BTC trading volume",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of exchanges (1-250, CoinGecko per_page upper bound)"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number (1-based)"
          }
        ]
      },
      {
        "site": "coingecko",
        "name": "global",
        "description": "Aggregate crypto market stats: total market cap, volume, dominance",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "currency",
            "type": "string",
            "default": "usd",
            "required": false,
            "help": "Quote currency for total market cap / volume (usd, cny, eur, jpy, ...)"
          }
        ]
      },
      {
        "site": "coingecko",
        "name": "top",
        "description": "按市值排序的加密货币行情（默认 USD）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "currency",
            "type": "string",
            "default": "usd",
            "required": false,
            "help": "计价币种 (usd / cny / eur / jpy ...)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回数量（默认 10，最多 250）"
          }
        ]
      },
      {
        "site": "coingecko",
        "name": "trending",
        "description": "Top trending cryptocurrencies on CoinGecko in the last 24h (search-volume based).",
        "access": "read",
        "browser": false,
        "args": []
      }
    ]
  },
  {
    "site": "confluence",
    "title": "Confluence",
    "description": "Atlassian 团队知识库与企业文档协作平台。",
    "category": "document",
    "iconKey": "document",
    "requiresLogin": false,
    "commands": [
      {
        "site": "confluence",
        "name": "create",
        "description": "Create a Confluence page from Markdown or storage XHTML",
        "access": "write",
        "browser": false,
        "args": [
          {
            "name": "space",
            "type": "string",
            "required": true,
            "help": "Cloud space id, or Data Center space key"
          },
          {
            "name": "title",
            "type": "string",
            "required": true,
            "help": "Page title"
          },
          {
            "name": "file",
            "type": "string",
            "required": true,
            "help": "Markdown file path"
          },
          {
            "name": "parent",
            "type": "string",
            "required": false,
            "help": "Optional parent page id"
          },
          {
            "name": "representation",
            "type": "string",
            "default": "markdown",
            "required": false,
            "help": "Input file format",
            "choices": [
              "markdown",
              "storage"
            ]
          },
          {
            "name": "execute",
            "type": "boolean",
            "required": false,
            "help": "Actually create the remote page"
          }
        ]
      },
      {
        "site": "confluence",
        "name": "page",
        "description": "Confluence page by id with storage and Markdown body",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Confluence page id"
          }
        ]
      },
      {
        "site": "confluence",
        "name": "search",
        "description": "Search Confluence content with CQL",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "cql",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "CQL query, e.g. \"type = page and title ~ \\\"RCA\\\"\""
          },
          {
            "name": "space",
            "type": "string",
            "required": false,
            "help": "Limit search to a Confluence space key"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results to return (1-100)"
          }
        ]
      },
      {
        "site": "confluence",
        "name": "update",
        "description": "Update a Confluence page body from Markdown or storage XHTML",
        "access": "write",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Confluence page id"
          },
          {
            "name": "file",
            "type": "string",
            "required": true,
            "help": "Markdown file path"
          },
          {
            "name": "title",
            "type": "string",
            "required": false,
            "help": "Optional replacement title; defaults to current title"
          },
          {
            "name": "version-message",
            "type": "string",
            "required": false,
            "help": "Confluence version message"
          },
          {
            "name": "representation",
            "type": "string",
            "default": "markdown",
            "required": false,
            "help": "Input file format",
            "choices": [
              "markdown",
              "storage"
            ]
          },
          {
            "name": "execute",
            "type": "boolean",
            "required": false,
            "help": "Actually update the remote page"
          }
        ]
      }
    ]
  },
  {
    "site": "crates",
    "title": "crates.io",
    "description": "Rust 语言包仓库与开源包托管平台。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "crates",
        "name": "crate",
        "description": "Single crates.io crate metadata (latest version, downloads, license, repo)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "crates.io crate name (e.g. \"serde\", \"tokio\")"
          }
        ]
      },
      {
        "site": "crates",
        "name": "search",
        "description": "Search the public crates.io registry by keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"serde\", \"async runtime\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100)"
          }
        ]
      }
    ]
  },
  {
    "site": "defillama",
    "title": "DeFiLlama",
    "description": "DeFi 链上 TVL 与项目数据分析平台。",
    "category": "finance",
    "iconKey": "finance",
    "requiresLogin": false,
    "commands": [
      {
        "site": "defillama",
        "name": "protocol",
        "description": "Single DefiLlama protocol details (current TVL, mcap, chains, twitter, github, description)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "slug",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "DefiLlama protocol slug (e.g. \"aave\", \"lido\")"
          }
        ]
      },
      {
        "site": "defillama",
        "name": "protocols",
        "description": "Top DeFi protocols on DefiLlama by current TVL (slug, name, category, TVL, mcap, change_1d/7d, chains)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Number of rows to return (1-500)"
          }
        ]
      }
    ]
  },
  {
    "site": "devto",
    "title": "DEV",
    "description": "程序员技术博客与技术交流社区。",
    "category": "social",
    "iconKey": "social",
    "requiresLogin": false,
    "commands": [
      {
        "site": "devto",
        "name": "latest",
        "description": "Newest dev.to articles (firehose, all tags)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Articles per page (1-100)"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number (1-based)"
          }
        ]
      },
      {
        "site": "devto",
        "name": "read",
        "description": "Read a DEV.to article body by id",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "DEV.to article id (numeric, e.g. 3605688)"
          },
          {
            "name": "max-length",
            "type": "int",
            "default": 20000,
            "required": false,
            "help": "Max characters of body to return (min 100)"
          }
        ]
      },
      {
        "site": "devto",
        "name": "tag",
        "description": "Latest DEV.to articles for a specific tag",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "tag",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Tag name (e.g. javascript, python, webdev)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of articles"
          }
        ]
      },
      {
        "site": "devto",
        "name": "top",
        "description": "Top DEV.to articles of the day",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of articles"
          }
        ]
      },
      {
        "site": "devto",
        "name": "user",
        "description": "Recent DEV.to articles from a specific user",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "DEV.to username (e.g. ben, thepracticaldev)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of articles"
          }
        ]
      }
    ]
  },
  {
    "site": "dictionary",
    "title": "Dictionary",
    "description": "英文单词释义与词源查询网站。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "dictionary",
        "name": "examples",
        "description": "Read real-world example sentences utilizing the word",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "word",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Word to get example sentences for"
          }
        ]
      },
      {
        "site": "dictionary",
        "name": "search",
        "description": "Search the Free Dictionary API for definitions, parts of speech, and pronunciations.",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "word",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Word to define (e.g., serendipity)"
          }
        ]
      },
      {
        "site": "dictionary",
        "name": "synonyms",
        "description": "Find synonyms for a specific word",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "word",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Word to find synonyms for (e.g., serendipity)"
          }
        ]
      }
    ]
  },
  {
    "site": "dockerhub",
    "title": "Docker Hub",
    "description": "Docker 容器镜像托管仓库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "dockerhub",
        "name": "image",
        "description": "Fetch a Docker Hub repository's public metadata (stars, pulls, last updated, status)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "image",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Image name (e.g. \"nginx\", \"library/nginx\", \"bitnami/redis\")"
          }
        ]
      },
      {
        "site": "dockerhub",
        "name": "search",
        "description": "Search Docker Hub repositories by keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"nginx\", \"bitnami redis\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Max repositories (1-100, single Docker Hub page)"
          }
        ]
      }
    ]
  },
  {
    "site": "dongchedi",
    "title": "懂车帝",
    "description": "汽车资讯、车型评测与汽车社区。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "dongchedi",
        "name": "koubei",
        "description": "懂车帝车系口碑/车主评价（评分 / 购车款型 / 点赞 / 评论 / 正文摘要）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "series_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "车系 ID（来自 search 的 series_id，或 /auto/series/<id> URL）"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回的口碑条数（最多 15，单页 SSR 上限）"
          }
        ]
      },
      {
        "site": "dongchedi",
        "name": "models",
        "description": "懂车帝车系款型列表（car_id / 名称 / 年款 / 指导价 / 经销商价 / 车主成交价）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "series_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "车系 ID（来自 search 的 series_id，或 /auto/series/<id> URL）"
          },
          {
            "name": "status",
            "type": "str",
            "default": "online",
            "required": false,
            "help": "在售 online（默认）或停售 offline"
          }
        ]
      },
      {
        "site": "dongchedi",
        "name": "score",
        "description": "懂车帝车系评分（懂车分 8 维度 + 同级车均值对比）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "series_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "车系 ID（来自 search 的 series_id，或 /auto/series/<id> URL）"
          }
        ]
      },
      {
        "site": "dongchedi",
        "name": "search",
        "description": "懂车帝车系搜索（按关键词，返回车系 + 指导价/经销商价）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词，例如 \"宝马X5\" 或 \"汉兰达\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 15,
            "required": false,
            "help": "返回的车系数量（最多 30）"
          }
        ]
      },
      {
        "site": "dongchedi",
        "name": "series",
        "description": "懂车帝车系概览（品牌 / 指导价 / 二手价 / 懂车分 / 销量排名 / 在售款型数）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "series_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "车系 ID（来自 search 的 series_id，或 /auto/series/<id> URL）"
          }
        ]
      },
      {
        "site": "dongchedi",
        "name": "specs",
        "description": "懂车帝车系配置概览（尺寸 / 动力 / 发动机 / 变速箱 / 四驱 / 悬挂 / 气囊）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "series_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "车系 ID（来自 search 的 series_id，或 /auto/series/<id> URL）"
          }
        ]
      }
    ]
  },
  {
    "site": "endoflife",
    "title": "EndOfLife",
    "description": "软件与系统支持终止时间查询。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "endoflife",
        "name": "product",
        "description": "Release cycles + EOL / LTS / support dates for one product on endoflife.date",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "product",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "endoflife.date product slug (e.g. \"nodejs\", \"python\", \"ubuntu\")"
          }
        ]
      }
    ]
  },
  {
    "site": "flathub",
    "title": "Flathub",
    "description": "Linux Flatpak 桌面应用分发平台。",
    "category": "commerce",
    "iconKey": "commerce",
    "requiresLogin": false,
    "commands": [
      {
        "site": "flathub",
        "name": "app",
        "description": "Full Flathub appstream metadata for an app id (license, categories, latest release)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "appId",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "AppStream id (e.g. \"org.mozilla.firefox\", \"org.gnome.Calculator\")"
          }
        ]
      },
      {
        "site": "flathub",
        "name": "search",
        "description": "Search Flathub apps by keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Max apps (1-100)"
          }
        ]
      }
    ]
  },
  {
    "site": "goproxy",
    "title": "Go Proxy",
    "description": "Go 语言模块代理服务。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "goproxy",
        "name": "module",
        "description": "Latest version + VCS origin metadata for a Go module on proxy.golang.org",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "module",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Go module path (e.g. \"github.com/gin-gonic/gin\", \"golang.org/x/net\")"
          }
        ]
      },
      {
        "site": "goproxy",
        "name": "versions",
        "description": "Published version tags for a Go module (newest first), optionally with publish times",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "module",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Go module path (e.g. \"github.com/gin-gonic/gin\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Max rows to return (1-200)"
          },
          {
            "name": "with-time",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Fetch each version's publish time (one extra request per row)"
          }
        ]
      }
    ]
  },
  {
    "site": "gov-policy",
    "title": "政务政策",
    "description": "国家与地方政策文件公开查询站点。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "gov-policy",
        "name": "recent",
        "description": "国务院最新政策文件",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回结果数量 (max 20)"
          }
        ]
      },
      {
        "site": "gov-policy",
        "name": "search",
        "description": "中国政府网政策文件搜索",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回结果数量 (max 20)"
          }
        ]
      }
    ]
  },
  {
    "site": "guazi",
    "title": "瓜子二手车",
    "description": "二手车交易平台。",
    "category": "commerce",
    "iconKey": "commerce",
    "requiresLogin": false,
    "commands": [
      {
        "site": "guazi",
        "name": "browse",
        "description": "瓜子二手车在售车源列表（按城市，含售价/首付/里程/年份）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "city",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "城市名（北京/上海/...）或瓜子城市码（bj/sh/...）。默认 bj 北京"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "返回的车源数量（最多 40，单页 SSR 上限）"
          }
        ]
      },
      {
        "site": "guazi",
        "name": "car",
        "description": "瓜子二手车车源详情（售价 / 上牌 / 里程 / 过户 / 配置 / 车况）",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "clue_id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "车源 ID（来自 browse 的 clue_id，或 /car-detail/c<id>.html URL）"
          }
        ]
      }
    ]
  },
  {
    "site": "homebrew",
    "title": "Homebrew",
    "description": "Mac 与 Linux 包管理器官网。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "homebrew",
        "name": "cask",
        "description": "Fetch a Homebrew cask's metadata (version, homepage, deprecation, download URL)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "token",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Cask token (e.g. \"firefox\", \"visual-studio-code\", \"google-chrome\")"
          }
        ]
      },
      {
        "site": "homebrew",
        "name": "formula",
        "description": "Fetch a Homebrew formula's metadata (version, license, deps, deprecation, source)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Formula name (e.g. \"wget\", \"gcc@13\", \"imagemagick\")"
          }
        ]
      },
      {
        "site": "homebrew",
        "name": "popular",
        "description": "List most-installed Homebrew formulae or casks (Homebrew's analytics ranking)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "type",
            "type": "str",
            "default": "formula",
            "required": false,
            "help": "Package type (formula / cask)"
          },
          {
            "name": "window",
            "type": "str",
            "default": "30d",
            "required": false,
            "help": "Time window (30d / 90d / 365d)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Max rows (1-500)"
          }
        ]
      }
    ]
  },
  {
    "site": "huodongxing",
    "title": "活动行",
    "description": "线下活动、讲座与报名平台。",
    "category": "commerce",
    "iconKey": "commerce",
    "requiresLogin": false,
    "commands": [
      {
        "site": "huodongxing",
        "name": "events",
        "description": "活动行活动搜索（按标签、城市、日期、线上/线下、名称过滤）",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "tag",
            "type": "string",
            "default": "",
            "required": false,
            "help": "活动标签，例如 AI"
          },
          {
            "name": "city",
            "type": "string",
            "default": "全部",
            "required": false,
            "help": "城市名，例如 北京 / 上海 / 全部"
          },
          {
            "name": "date",
            "type": "string",
            "default": "",
            "required": false,
            "help": "开始日期，格式 YYYY-MM-DD"
          },
          {
            "name": "dateTo",
            "type": "string",
            "default": "",
            "required": false,
            "help": "结束日期，格式 YYYY-MM-DD"
          },
          {
            "name": "eventType",
            "type": "int",
            "required": false,
            "help": "活动类型：1 线下，2 线上"
          },
          {
            "name": "qs",
            "type": "string",
            "default": "",
            "required": false,
            "help": "按活动名称关键词过滤"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "返回条数（1-50）"
          }
        ]
      }
    ]
  },
  {
    "site": "imdb",
    "title": "IMDb",
    "description": "影视评分、演员与影片资料数据库。",
    "category": "media",
    "iconKey": "media",
    "requiresLogin": false,
    "commands": [
      {
        "site": "imdb",
        "name": "person",
        "description": "Get actor or director info",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "IMDb person ID (nm0634240) or URL"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max filmography entries"
          }
        ]
      },
      {
        "site": "imdb",
        "name": "reviews",
        "description": "Get user reviews for a movie or TV show",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "IMDb title ID (tt1375666) or URL"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of reviews"
          }
        ]
      },
      {
        "site": "imdb",
        "name": "search",
        "description": "Search IMDb for movies, TV shows, and people",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "imdb",
        "name": "title",
        "description": "Get movie or TV show details",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "IMDb title ID (tt1375666) or URL"
          }
        ]
      },
      {
        "site": "imdb",
        "name": "top",
        "description": "IMDb Top 250 Movies",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "imdb",
        "name": "trending",
        "description": "IMDb Most Popular Movies",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          }
        ]
      }
    ]
  },
  {
    "site": "jira",
    "title": "Jira",
    "description": "Atlassian 项目任务、研发缺陷与需求跟踪工具。",
    "category": "document",
    "iconKey": "document",
    "requiresLogin": false,
    "commands": [
      {
        "site": "jira",
        "name": "attachments",
        "description": "Jira issue attachment metadata",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "key",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Jira issue key, e.g. PROJ-123"
          }
        ]
      },
      {
        "site": "jira",
        "name": "comments",
        "description": "Jira issue comments as Markdown",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "key",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Jira issue key, e.g. PROJ-123"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 50,
            "required": false,
            "help": "Max comments to return (1-100)"
          }
        ]
      },
      {
        "site": "jira",
        "name": "issue",
        "description": "Jira issue detail normalized for agents (description, comments, attachments, links)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "key",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Jira issue key, e.g. PROJ-123"
          },
          {
            "name": "comments-limit",
            "type": "int",
            "default": 100,
            "required": false,
            "help": "Max comments to include (1-100)"
          },
          {
            "name": "fields",
            "type": "string",
            "required": false,
            "help": "Fields to request, comma-separated, or auto for all fields"
          }
        ]
      },
      {
        "site": "jira",
        "name": "links",
        "description": "Jira issue links",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "key",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Jira issue key, e.g. PROJ-123"
          }
        ]
      },
      {
        "site": "jira",
        "name": "search",
        "description": "Search Jira issues with JQL",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "jql",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "JQL query, e.g. \"project = PROJ order by updated desc\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max issues to return (1-100)"
          }
        ]
      }
    ]
  },
  {
    "site": "lesswrong",
    "title": "LessWrong",
    "description": "认知科学与理性思考文章社区。",
    "category": "social",
    "iconKey": "social",
    "requiresLogin": false,
    "commands": [
      {
        "site": "lesswrong",
        "name": "comments",
        "description": "Top comments on a post",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "url-or-id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Post URL or LessWrong post ID"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 5,
            "required": false,
            "help": "Number of comments"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "curated",
        "description": "Curated editor's picks",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "frontpage",
        "description": "Algorithmic frontpage",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "new",
        "description": "Latest posts",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "read",
        "description": "Read full post by URL or ID",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "url-or-id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Post URL or LessWrong post ID"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "sequences",
        "description": "List post collections",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "shortform",
        "description": "Quick takes / shortform posts",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "tag",
        "description": "Posts by tag",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "tag",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Tag slug or name"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "tags",
        "description": "List popular tags",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "top",
        "description": "Top all-time",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "top-month",
        "description": "Top this month",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "top-week",
        "description": "Top this week",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "top-year",
        "description": "Top this year",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "user",
        "description": "User profile",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "username",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "LessWrong username or slug"
          }
        ]
      },
      {
        "site": "lesswrong",
        "name": "user-posts",
        "description": "List a user's posts",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "username",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "LessWrong username or slug"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results"
          }
        ]
      }
    ]
  },
  {
    "site": "lichess",
    "title": "Lichess",
    "description": "免费国际象棋在线对弈平台。",
    "category": "social",
    "iconKey": "social",
    "requiresLogin": false,
    "commands": [
      {
        "site": "lichess",
        "name": "top",
        "description": "Top-N Lichess leaderboard for a perf type (bullet/blitz/rapid/classical/...)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "perf",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Perf type (bullet, blitz, rapid, classical, ultraBullet, chess960, ...)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Top-N rows (1-200)"
          }
        ]
      },
      {
        "site": "lichess",
        "name": "user",
        "description": "Fetch a Lichess player profile by username (rating, perfs, counts)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "username",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Lichess username (case-insensitive)"
          }
        ]
      }
    ]
  },
  {
    "site": "lobsters",
    "title": "Lobsters",
    "description": "程序员技术资讯社区。",
    "category": "social",
    "iconKey": "social",
    "requiresLogin": false,
    "commands": [
      {
        "site": "lobsters",
        "name": "active",
        "description": "Lobste.rs most active discussions",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories"
          }
        ]
      },
      {
        "site": "lobsters",
        "name": "domain",
        "description": "Lobste.rs stories submitted from a specific domain",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "domain",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Source domain (e.g. github.com, arxiv.org, blog.cloudflare.com)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories (1-25 — single page)"
          }
        ]
      },
      {
        "site": "lobsters",
        "name": "hot",
        "description": "Lobste.rs hottest stories",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories"
          }
        ]
      },
      {
        "site": "lobsters",
        "name": "newest",
        "description": "Lobste.rs newest stories",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories"
          }
        ]
      },
      {
        "site": "lobsters",
        "name": "read",
        "description": "Read a Lobste.rs story and its comment tree",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Lobste.rs short_id (e.g. 6cmh6h)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Max top-level comments"
          },
          {
            "name": "depth",
            "type": "int",
            "default": 2,
            "required": false,
            "help": "Max reply depth (1=no replies, 2=one level of replies, etc.)"
          },
          {
            "name": "replies",
            "type": "int",
            "default": 5,
            "required": false,
            "help": "Max replies shown per comment at each level"
          },
          {
            "name": "max-length",
            "type": "int",
            "default": 2000,
            "required": false,
            "help": "Max characters per comment body (min 100)"
          }
        ]
      },
      {
        "site": "lobsters",
        "name": "tag",
        "description": "Lobste.rs stories by tag",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "tag",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Tag name (e.g. programming, rust, security, ai)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of stories"
          }
        ]
      }
    ]
  },
  {
    "site": "maven",
    "title": "Maven Central",
    "description": "Java 项目依赖包中央仓库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "maven",
        "name": "artifact",
        "description": "Fetch a Maven Central artifact's version history (groupId:artifactId[:version])",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "coordinate",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Maven coord \"groupId:artifactId\" or \"groupId:artifactId:version\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max versions (1-200, ignored when version is pinned)"
          }
        ]
      },
      {
        "site": "maven",
        "name": "search",
        "description": "Search Maven Central by keyword (artifact name, groupId, tag)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"jackson\", \"guava\", \"ai.koog\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Max artifacts (1-200)"
          }
        ]
      }
    ]
  },
  {
    "site": "mdn",
    "title": "MDN Web Docs",
    "description": "Mozilla HTML、CSS 与 JavaScript 开发文档。",
    "category": "document",
    "iconKey": "document",
    "requiresLogin": false,
    "commands": [
      {
        "site": "mdn",
        "name": "search",
        "description": "Search MDN Web Docs by keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"fetch\", \"flexbox\", \"Array.prototype.map\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max results (1-50)"
          },
          {
            "name": "locale",
            "type": "str",
            "default": "en-US",
            "required": false,
            "help": "Doc locale (en-US default; de / es / fr / ja / ko / pt-BR / ru / zh-CN / zh-TW)"
          }
        ]
      }
    ]
  },
  {
    "site": "npm",
    "title": "npm",
    "description": "JavaScript 软件包仓库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "npm",
        "name": "downloads",
        "description": "Daily download counts for an npm package over a window",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "npm package name (e.g. \"react\", \"@vercel/og\")"
          },
          {
            "name": "period",
            "type": "str",
            "default": "last-week",
            "required": false,
            "help": "last-day / last-week / last-month / last-year, or YYYY-MM-DD:YYYY-MM-DD"
          }
        ]
      },
      {
        "site": "npm",
        "name": "package",
        "description": "Single npm package metadata (latest version, license, homepage, repository). Use `npm downloads` for stats.",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "npm package name (e.g. \"react\", \"@vercel/og\")"
          }
        ]
      },
      {
        "site": "npm",
        "name": "search",
        "description": "Search the public npm registry by keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"react\", \"graphql client\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-250)"
          }
        ]
      }
    ]
  },
  {
    "site": "nuget",
    "title": "NuGet",
    "description": ".NET 程序包仓库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "nuget",
        "name": "package",
        "description": "Full NuGet package version history (catalogEntry per release)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "NuGet package id (e.g. \"Newtonsoft.Json\", case-insensitive)"
          }
        ]
      },
      {
        "site": "nuget",
        "name": "search",
        "description": "Search NuGet packages by keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max packages (1-1000)"
          },
          {
            "name": "prerelease",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Include prerelease versions"
          }
        ]
      }
    ]
  },
  {
    "site": "nvd",
    "title": "NVD",
    "description": "美国国家漏洞数据库与 CVE 信息查询平台。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "nvd",
        "name": "cve",
        "description": "NIST NVD CVE detail (description, CVSS, CWE, KEV flag)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "CVE identifier (e.g. \"CVE-2021-44228\")"
          }
        ]
      }
    ]
  },
  {
    "site": "oeis",
    "title": "OEIS",
    "description": "整数序列百科与数学序列检索网站。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "oeis",
        "name": "search",
        "description": "Search OEIS sequences by keyword or numeric pattern",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword or comma-separated terms (e.g. \"fibonacci\", \"1,1,2,3,5,8\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max sequences (1-100)"
          }
        ]
      },
      {
        "site": "oeis",
        "name": "sequence",
        "description": "Full OEIS sequence detail by A-number (terms, name, keywords, formula counts)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "OEIS sequence id (e.g. \"A000045\" for Fibonacci)"
          }
        ]
      }
    ]
  },
  {
    "site": "openalex",
    "title": "OpenAlex",
    "description": "论文、学者与机构开放文献索引数据库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "openalex",
        "name": "search",
        "description": "Search OpenAlex Works (papers, books, preprints) by keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search text (e.g. \"transformers\", \"open access scholarly\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max works (1-200, single OpenAlex page)"
          }
        ]
      },
      {
        "site": "openalex",
        "name": "work",
        "description": "Fetch a single OpenAlex Work (paper / preprint / book) — metadata + abstract",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "OpenAlex Work id (\"W2741809807\"), DOI (\"10.7717/peerj.4375\"), or full URL"
          }
        ]
      }
    ]
  },
  {
    "site": "openfda",
    "title": "openFDA",
    "description": "美国 FDA 药品与医疗器械开放数据平台。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "openfda",
        "name": "drug-label",
        "description": "Search FDA-approved drug labels (brand or generic name)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Brand or generic drug name (e.g. \"aspirin\", \"lisinopril\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 5,
            "required": false,
            "help": "Max rows (1-25, default 5; openFDA caps anonymous tier at 25/page)"
          }
        ]
      },
      {
        "site": "openfda",
        "name": "food-recall",
        "description": "FDA food recall and enforcement actions (most recent first)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": false,
            "help": "Free-text Lucene query (e.g. \"salmonella\", \"listeria\"); default: all recent recalls"
          },
          {
            "name": "status",
            "type": "str",
            "required": false,
            "help": "Filter by status: \"Ongoing\", \"Completed\", \"Terminated\""
          },
          {
            "name": "classification",
            "type": "str",
            "required": false,
            "help": "Filter by class: \"Class I\" (most serious), \"Class II\", \"Class III\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max rows (1-100, default 10; openFDA caps anonymous tier at 100/page)"
          }
        ]
      }
    ]
  },
  {
    "site": "openreview",
    "title": "OpenReview",
    "description": "学术会议论文评审与讨论平台。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "openreview",
        "name": "author",
        "description": "List OpenReview submissions by an author profile id (newest first)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "profile",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "OpenReview profile id (e.g. \"~Yoshua_Bengio1\"). Find it on the author profile URL on openreview.net."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 50,
            "required": false,
            "help": "Max submissions (1-1000)"
          }
        ]
      },
      {
        "site": "openreview",
        "name": "paper",
        "description": "Show full metadata for a single OpenReview paper",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "OpenReview note id (e.g. \"5sRnsubyAK\")"
          }
        ]
      },
      {
        "site": "openreview",
        "name": "reviews",
        "description": "Show full review thread (paper + reviews + decisions) for an OpenReview forum",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "forum",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "OpenReview forum id (same as paper id)"
          },
          {
            "name": "max-length",
            "type": "int",
            "default": 4000,
            "required": false,
            "help": "Per-row text truncation (min 200)"
          }
        ]
      },
      {
        "site": "openreview",
        "name": "search",
        "description": "Search OpenReview papers by free-text query",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"diffusion model\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Max results (max 50)"
          }
        ]
      },
      {
        "site": "openreview",
        "name": "venue",
        "description": "List papers at an OpenReview venue (e.g. \"ICLR 2024 oral\" or full invitation id)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "venue",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Venue name (\"ICLR 2024 oral\") or invitation (\"ICLR.cc/2025/Conference/-/Submission\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Max results (max 200)"
          },
          {
            "name": "offset",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Pagination offset"
          }
        ]
      }
    ]
  },
  {
    "site": "osv",
    "title": "OSV",
    "description": "开源软件漏洞查询数据库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "osv",
        "name": "query",
        "description": "OSV.dev vulnerabilities affecting a package (optionally pinned to a version)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "package",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Package name (e.g. \"lodash\", \"django\")"
          },
          {
            "name": "ecosystem",
            "type": "string",
            "required": true,
            "help": "OSV ecosystem (npm / PyPI / Go / Maven / NuGet / RubyGems / crates.io / Packagist / ...)"
          },
          {
            "name": "version",
            "type": "string",
            "required": false,
            "help": "Pin to a specific version (e.g. \"4.17.20\"); omit for all known vulns"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Max rows to return (1-200)"
          }
        ]
      },
      {
        "site": "osv",
        "name": "vulnerability",
        "description": "Single OSV.dev vulnerability detail (severity, affected packages, CVE/GHSA aliases)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "OSV vulnerability id (e.g. \"GHSA-29mw-wpgm-hmr9\", \"CVE-2020-28500\")"
          }
        ]
      }
    ]
  },
  {
    "site": "packagist",
    "title": "Packagist",
    "description": "PHP Composer 开源包仓库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "packagist",
        "name": "package",
        "description": "Fetch a Packagist package's metadata (version, downloads, license, repo, GitHub stars)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Composer package \"<vendor>/<package>\" (e.g. \"symfony/console\", \"monolog/monolog\")"
          }
        ]
      },
      {
        "site": "packagist",
        "name": "search",
        "description": "Search Packagist (PHP / Composer) packages by keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"symfony\", \"laravel http\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Max packages (1-100, single Packagist page)"
          }
        ]
      }
    ]
  },
  {
    "site": "paperreview",
    "title": "PaperReview",
    "description": "论文评审辅助站点。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "paperreview",
        "name": "feedback",
        "description": "Submit feedback for a paperreview.ai review token",
        "access": "write",
        "browser": false,
        "args": [
          {
            "name": "token",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Review token returned by paperreview.ai"
          },
          {
            "name": "helpfulness",
            "type": "int",
            "required": true,
            "help": "Helpfulness score from 1 to 5"
          },
          {
            "name": "critical-error",
            "type": "str",
            "required": true,
            "help": "Whether the review contains a critical error",
            "choices": [
              "yes",
              "no"
            ]
          },
          {
            "name": "actionable-suggestions",
            "type": "str",
            "required": true,
            "help": "Whether the review contains actionable suggestions",
            "choices": [
              "yes",
              "no"
            ]
          },
          {
            "name": "additional-comments",
            "type": "str",
            "required": false,
            "help": "Optional free-text feedback"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Max seconds for the overall command (default: 30)"
          }
        ]
      },
      {
        "site": "paperreview",
        "name": "review",
        "description": "Fetch a paperreview.ai review by token",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "token",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Review token returned by paperreview.ai"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Max seconds for the overall command (default: 30)"
          }
        ]
      },
      {
        "site": "paperreview",
        "name": "submit",
        "description": "Submit a PDF to paperreview.ai for review",
        "access": "write",
        "browser": false,
        "args": [
          {
            "name": "pdf",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Path to the paper PDF"
          },
          {
            "name": "email",
            "type": "str",
            "required": true,
            "help": "Email address for the submission"
          },
          {
            "name": "venue",
            "type": "str",
            "required": false,
            "help": "Optional target venue such as ICLR or NeurIPS"
          },
          {
            "name": "dry-run",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Validate the input and stop before remote submission"
          },
          {
            "name": "prepare-only",
            "type": "bool",
            "default": false,
            "required": false,
            "help": "Request an upload slot but stop before uploading the PDF"
          },
          {
            "name": "timeout",
            "type": "int",
            "default": 120,
            "required": false,
            "help": "Max seconds for the overall command (default: 120)"
          }
        ]
      }
    ]
  },
  {
    "site": "pubmed",
    "title": "PubMed",
    "description": "生物医学文献数据库与医学论文检索平台。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "pubmed",
        "name": "article",
        "description": "Get detailed information for a PubMed article by PMID",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "pmid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "PubMed ID, e.g. 37780221"
          },
          {
            "name": "full-abstract",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Do not truncate the abstract in table output"
          }
        ]
      },
      {
        "site": "pubmed",
        "name": "author",
        "description": "Search PubMed articles by author name and optional affiliation",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Author name, e.g. \"Smith J\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100)"
          },
          {
            "name": "affiliation",
            "type": "str",
            "required": false,
            "help": "Filter by author affiliation"
          },
          {
            "name": "position",
            "type": "str",
            "default": "any",
            "required": false,
            "help": "Author position: any, first, or last",
            "choices": [
              "any",
              "first",
              "last"
            ]
          },
          {
            "name": "year-from",
            "type": "int",
            "required": false,
            "help": "Filter publication year from"
          },
          {
            "name": "year-to",
            "type": "int",
            "required": false,
            "help": "Filter publication year to"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "date",
            "required": false,
            "help": "Sort by date or relevance",
            "choices": [
              "date",
              "relevance"
            ]
          }
        ]
      },
      {
        "site": "pubmed",
        "name": "citations",
        "description": "Get PubMed citation relationships for an article",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "pmid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "PubMed ID, e.g. 37780221"
          },
          {
            "name": "direction",
            "type": "str",
            "default": "citedby",
            "required": false,
            "help": "citedby or references",
            "choices": [
              "citedby",
              "references"
            ]
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100)"
          }
        ]
      },
      {
        "site": "pubmed",
        "name": "clinical-trial",
        "description": "Search PubMed clinical trials with a trial-study preset",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Clinical topic query, e.g. \"breast cancer\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100)"
          },
          {
            "name": "year-from",
            "type": "int",
            "required": false,
            "help": "Filter publication year from"
          },
          {
            "name": "year-to",
            "type": "int",
            "required": false,
            "help": "Filter publication year to"
          },
          {
            "name": "free-full-text",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only include free full text articles"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "date",
            "required": false,
            "help": "Sort by date or relevance",
            "choices": [
              "date",
              "relevance"
            ]
          }
        ]
      },
      {
        "site": "pubmed",
        "name": "journal",
        "description": "Search PubMed articles by journal name",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "journal",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Journal name, e.g. \"Nature\" or \"The Lancet\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100)"
          },
          {
            "name": "year-from",
            "type": "int",
            "required": false,
            "help": "Filter publication year from"
          },
          {
            "name": "year-to",
            "type": "int",
            "required": false,
            "help": "Filter publication year to"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "relevance",
            "required": false,
            "help": "Sort by relevance or date",
            "choices": [
              "relevance",
              "date"
            ]
          }
        ]
      },
      {
        "site": "pubmed",
        "name": "mesh",
        "description": "Search PubMed articles by MeSH term",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "term",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "MeSH term, e.g. \"Neoplasms\" or \"Machine Learning\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100)"
          },
          {
            "name": "major",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only include articles where this is a major MeSH topic"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "relevance",
            "required": false,
            "help": "Sort by relevance or date",
            "choices": [
              "relevance",
              "date"
            ]
          }
        ]
      },
      {
        "site": "pubmed",
        "name": "related",
        "description": "Find articles related to a PubMed article",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "pmid",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "PubMed ID, e.g. 37780221"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100)"
          },
          {
            "name": "score",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Show similarity scores when available"
          }
        ]
      },
      {
        "site": "pubmed",
        "name": "review",
        "description": "Search PubMed review articles with a review preset",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Review topic query, e.g. \"immunotherapy\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100)"
          },
          {
            "name": "year-from",
            "type": "int",
            "required": false,
            "help": "Filter publication year from"
          },
          {
            "name": "year-to",
            "type": "int",
            "required": false,
            "help": "Filter publication year to"
          },
          {
            "name": "has-abstract",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only include articles with abstracts"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "date",
            "required": false,
            "help": "Sort by date or relevance",
            "choices": [
              "date",
              "relevance"
            ]
          }
        ]
      },
      {
        "site": "pubmed",
        "name": "search",
        "description": "Search PubMed articles with advanced filters",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query, e.g. \"machine learning cancer\""
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max results (1-100)"
          },
          {
            "name": "author",
            "type": "str",
            "required": false,
            "help": "Filter by author name"
          },
          {
            "name": "journal",
            "type": "str",
            "required": false,
            "help": "Filter by journal name"
          },
          {
            "name": "year-from",
            "type": "int",
            "required": false,
            "help": "Filter publication year from"
          },
          {
            "name": "year-to",
            "type": "int",
            "required": false,
            "help": "Filter publication year to"
          },
          {
            "name": "article-type",
            "type": "str",
            "required": false,
            "help": "Filter by publication type, e.g. Review or Clinical Trial"
          },
          {
            "name": "has-abstract",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only include articles with abstracts"
          },
          {
            "name": "free-full-text",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only include free full text articles"
          },
          {
            "name": "humans-only",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only include human studies"
          },
          {
            "name": "english-only",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Only include English articles"
          },
          {
            "name": "sort",
            "type": "str",
            "default": "relevance",
            "required": false,
            "help": "Sort by relevance, date, author, or journal",
            "choices": [
              "relevance",
              "date",
              "author",
              "journal"
            ]
          }
        ]
      }
    ]
  },
  {
    "site": "pypi",
    "title": "PyPI",
    "description": "Python 软件包仓库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "pypi",
        "name": "downloads",
        "description": "PyPI download stats for a package (recent totals or full daily history)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "PyPI package name (e.g. \"requests\", \"pandas\")"
          },
          {
            "name": "period",
            "type": "str",
            "default": "recent",
            "required": false,
            "help": "recent (default — 1 row, last day/week/month) or overall (1 row per day)"
          }
        ]
      },
      {
        "site": "pypi",
        "name": "package",
        "description": "Single PyPI package metadata (latest version, license, homepage, classifiers)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "PyPI package name (e.g. \"requests\", \"pandas\")"
          }
        ]
      }
    ]
  },
  {
    "site": "rest-countries",
    "title": "REST Countries",
    "description": "世界各国基础信息公开 API 站点。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "rest-countries",
        "name": "country",
        "description": "Look up countries by name (common / official, substring match)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Country name (e.g. \"japan\", \"united kingdom\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 25,
            "required": false,
            "help": "Max rows (1-250)"
          }
        ]
      },
      {
        "site": "rest-countries",
        "name": "region",
        "description": "List countries in a region (africa / americas / asia / europe / oceania / antarctic)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "region",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Region name (case-insensitive)"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 250,
            "required": false,
            "help": "Max rows (1-250)"
          }
        ]
      }
    ]
  },
  {
    "site": "rfc",
    "title": "IETF RFC",
    "description": "互联网标准与协议文档库。",
    "category": "document",
    "iconKey": "document",
    "requiresLogin": false,
    "commands": [
      {
        "site": "rfc",
        "name": "rfc",
        "description": "Single IETF RFC metadata (title, abstract, working group, authors, std level)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "number",
            "type": "int",
            "required": true,
            "positional": true,
            "help": "RFC number (e.g. 9000, 791, 2616)"
          }
        ]
      }
    ]
  },
  {
    "site": "rubygems",
    "title": "RubyGems",
    "description": "Ruby 语言软件包仓库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "rubygems",
        "name": "gem",
        "description": "Fetch a RubyGems.org gem's metadata (version, downloads, license, links)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Gem name (e.g. \"rails\", \"sidekiq\")"
          }
        ]
      },
      {
        "site": "rubygems",
        "name": "search",
        "description": "Search RubyGems.org gems by keyword",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword (e.g. \"rails\", \"redis\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 30,
            "required": false,
            "help": "Max gems (1-100, single RubyGems page)"
          }
        ]
      }
    ]
  },
  {
    "site": "semanticscholar",
    "title": "Semantic Scholar",
    "description": "AI 驱动的学术论文检索平台。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "semanticscholar",
        "name": "citations",
        "description": "List papers that cite a Semantic Scholar paper (paginated)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "paperId (40-char hex), DOI, arXiv id, or prefixed id"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max citing papers (1-1000, single Semantic Scholar page)"
          },
          {
            "name": "offset",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Page offset (0-based)"
          }
        ]
      },
      {
        "site": "semanticscholar",
        "name": "paper",
        "description": "Semantic Scholar paper detail (citation graph + AI tldr) by paperId, DOI, or arXiv id",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "paperId (40-char hex), DOI, arXiv id, or prefixed id (e.g. \"ARXIV:1706.03762\", \"PMID:12345\")"
          }
        ]
      },
      {
        "site": "semanticscholar",
        "name": "recommendations",
        "description": "Semantic Scholar AI-curated related papers for a paperId, DOI, or arXiv id",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "paperId (40-char hex), DOI, arXiv id, or prefixed id"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max recommendations (1-500)"
          }
        ]
      },
      {
        "site": "semanticscholar",
        "name": "search",
        "description": "Search Semantic Scholar papers by free text",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search text (e.g. \"attention is all you need\", \"diffusion model\")"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max papers (1-100, single Semantic Scholar page)"
          }
        ]
      }
    ]
  },
  {
    "site": "spotify",
    "title": "Spotify",
    "description": "流媒体音乐平台。",
    "category": "media",
    "iconKey": "media",
    "requiresLogin": false,
    "commands": [
      {
        "site": "spotify",
        "name": "auth",
        "description": "Authenticate with Spotify (OAuth — run once)",
        "access": "write",
        "browser": false,
        "args": []
      },
      {
        "site": "spotify",
        "name": "next",
        "description": "Skip to next track",
        "access": "write",
        "browser": false,
        "args": []
      },
      {
        "site": "spotify",
        "name": "pause",
        "description": "Pause playback",
        "access": "write",
        "browser": false,
        "args": []
      },
      {
        "site": "spotify",
        "name": "play",
        "description": "Resume playback or search and play a track/artist",
        "access": "write",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "default": "",
            "required": false,
            "positional": true,
            "help": "Track or artist to play (optional)"
          }
        ]
      },
      {
        "site": "spotify",
        "name": "prev",
        "description": "Skip to previous track",
        "access": "write",
        "browser": false,
        "args": []
      },
      {
        "site": "spotify",
        "name": "queue",
        "description": "Add a track to the playback queue",
        "access": "write",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Track to add to queue"
          }
        ]
      },
      {
        "site": "spotify",
        "name": "repeat",
        "description": "Set repeat mode (off / track / context)",
        "access": "write",
        "browser": false,
        "args": [
          {
            "name": "mode",
            "type": "str",
            "default": "context",
            "required": false,
            "positional": true,
            "help": "off / track / context",
            "choices": [
              "off",
              "track",
              "context"
            ]
          }
        ]
      },
      {
        "site": "spotify",
        "name": "search",
        "description": "Search for tracks",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Number of results (default: 10)"
          }
        ]
      },
      {
        "site": "spotify",
        "name": "shuffle",
        "description": "Toggle shuffle on/off",
        "access": "write",
        "browser": false,
        "args": [
          {
            "name": "state",
            "type": "str",
            "default": "on",
            "required": false,
            "positional": true,
            "help": "on or off",
            "choices": [
              "on",
              "off"
            ]
          }
        ]
      },
      {
        "site": "spotify",
        "name": "status",
        "description": "Show current playback status",
        "access": "read",
        "browser": false,
        "args": []
      },
      {
        "site": "spotify",
        "name": "volume",
        "description": "Set playback volume (0-100)",
        "access": "write",
        "browser": false,
        "args": [
          {
            "name": "level",
            "type": "int",
            "default": 50,
            "required": true,
            "positional": true,
            "help": "Volume 0–100"
          }
        ]
      }
    ]
  },
  {
    "site": "stackoverflow",
    "title": "Stack Overflow",
    "description": "程序员问答与编程问题检索社区。",
    "category": "social",
    "iconKey": "social",
    "requiresLogin": false,
    "commands": [
      {
        "site": "stackoverflow",
        "name": "bounties",
        "description": "Active bounties on Stack Overflow",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max number of results"
          }
        ]
      },
      {
        "site": "stackoverflow",
        "name": "hot",
        "description": "Hot Stack Overflow questions",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max number of results"
          }
        ]
      },
      {
        "site": "stackoverflow",
        "name": "read",
        "description": "Read a Stack Overflow question with answers and comments",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Stack Overflow question id (numeric, e.g. 79935770)"
          },
          {
            "name": "answers-limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max answers to include (1-100; accepted answer always included first)"
          },
          {
            "name": "comments-limit",
            "type": "int",
            "default": 5,
            "required": false,
            "help": "Max comments per question/answer (1-100)"
          },
          {
            "name": "max-length",
            "type": "int",
            "default": 4000,
            "required": false,
            "help": "Max characters per body / answer / comment (min 100)"
          }
        ]
      },
      {
        "site": "stackoverflow",
        "name": "related",
        "description": "List Stack Overflow questions related to a given question id.",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Stack Overflow question id (numeric, e.g. 79935770)."
          },
          {
            "name": "sort",
            "type": "string",
            "default": "rank",
            "required": false,
            "help": "Sort key: rank, activity, votes, creation (rank = SO relevance default)."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max related questions (1-100)."
          }
        ]
      },
      {
        "site": "stackoverflow",
        "name": "search",
        "description": "Search Stack Overflow questions",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max number of results"
          }
        ]
      },
      {
        "site": "stackoverflow",
        "name": "tag",
        "description": "List Stack Overflow questions tagged with a given tag (most active first).",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "tag",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Tag slug (e.g. python, rust, typescript)."
          },
          {
            "name": "sort",
            "type": "string",
            "default": "activity",
            "required": false,
            "help": "Sort key: activity, votes, creation, hot, week, month"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max questions to return (max 100)."
          }
        ]
      },
      {
        "site": "stackoverflow",
        "name": "unanswered",
        "description": "Top voted unanswered questions on Stack Overflow",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max number of results"
          }
        ]
      },
      {
        "site": "stackoverflow",
        "name": "user",
        "description": "Find Stack Overflow users by display name (highest reputation first).",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "name",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "Display name (or substring) to search."
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "Max users to return (max 100)."
          }
        ]
      }
    ]
  },
  {
    "site": "ths",
    "title": "同花顺",
    "description": "金融与股票行情网站。",
    "category": "finance",
    "iconKey": "finance",
    "requiresLogin": false,
    "commands": [
      {
        "site": "ths",
        "name": "hot-rank",
        "description": "同花顺热股榜",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "返回数量"
          }
        ]
      }
    ]
  },
  {
    "site": "tvmaze",
    "title": "TVmaze",
    "description": "美剧、英剧更新与剧集资料网站。",
    "category": "media",
    "iconKey": "media",
    "requiresLogin": false,
    "commands": [
      {
        "site": "tvmaze",
        "name": "search",
        "description": "TVmaze TV show search by title (returns id, name, network, premiered/ended, rating)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "query",
            "type": "string",
            "required": true,
            "positional": true,
            "help": "TV show title or fragment to search for"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Max rows to return (1-50)"
          }
        ]
      },
      {
        "site": "tvmaze",
        "name": "show",
        "description": "Single TVmaze TV show detail by id (network, schedule, rating, IMDB/TheTVDB cross-refs)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "id",
            "type": "int",
            "required": true,
            "positional": true,
            "help": "TVmaze show id (positive integer)"
          }
        ]
      }
    ]
  },
  {
    "site": "uisdc",
    "title": "优设网",
    "description": "UI 与设计教程、设计资讯社区。",
    "category": "creation",
    "iconKey": "creation",
    "requiresLogin": false,
    "commands": [
      {
        "site": "uisdc",
        "name": "news",
        "description": "优设读报 - 最新 AI/设计行业新闻",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "limit",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Number of news items to return (max 50)"
          }
        ]
      }
    ]
  },
  {
    "site": "uiverse",
    "title": "Uiverse",
    "description": "前端 UI 开源组件素材库。",
    "category": "creation",
    "iconKey": "creation",
    "requiresLogin": false,
    "commands": [
      {
        "site": "uiverse",
        "name": "code",
        "description": "Export Uiverse component code (HTML, CSS, React, or Vue)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "input",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Uiverse URL or author/slug identifier"
          },
          {
            "name": "target",
            "type": "str",
            "required": true,
            "help": "Code target to export",
            "choices": [
              "html",
              "css",
              "react",
              "vue"
            ]
          }
        ]
      },
      {
        "site": "uiverse",
        "name": "preview",
        "description": "Capture a screenshot of the Uiverse preview element",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "input",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Uiverse URL or author/slug identifier"
          },
          {
            "name": "output",
            "type": "str",
            "required": false,
            "help": "Output image path (defaults to a temp file)"
          },
          {
            "name": "padding",
            "type": "int",
            "default": 8,
            "required": false,
            "help": "Extra padding around the captured preview in pixels"
          }
        ]
      }
    ]
  },
  {
    "site": "wanfang",
    "title": "万方数据",
    "description": "中文期刊与学位论文数据库。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "wanfang",
        "name": "search",
        "description": "万方数据论文搜索",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "query",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "搜索关键词"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 10,
            "required": false,
            "help": "返回结果数量 (max 20)"
          }
        ]
      }
    ]
  },
  {
    "site": "weread-official",
    "title": "微信读书公开页",
    "description": "微信读书公开书籍信息页面，不包含个人书架。",
    "category": "document",
    "iconKey": "document",
    "requiresLogin": false,
    "commands": [
      {
        "site": "weread-official",
        "name": "book",
        "description": "Show WeRead book metadata, chapters, and reading progress",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "bookId",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "WeRead bookId (from `weread-official search`)"
          },
          {
            "name": "no-chapters",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Skip /book/chapterinfo call"
          },
          {
            "name": "no-progress",
            "type": "boolean",
            "default": false,
            "required": false,
            "help": "Skip /book/getprogress call"
          }
        ]
      },
      {
        "site": "weread-official",
        "name": "discover",
        "description": "Personalized or similar-book recommendations from WeRead",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "bookId",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Anchor bookId for similar-book mode; omit for personalized recommendations"
          },
          {
            "name": "count",
            "type": "int",
            "default": 12,
            "required": false,
            "help": "Page size (default 12)"
          },
          {
            "name": "max-idx",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Pagination cursor (recommend: previous searchIdx; similar: previous idx)"
          },
          {
            "name": "session-id",
            "type": "str",
            "required": false,
            "help": "Carry-forward sessionId for /book/similar paging"
          }
        ]
      },
      {
        "site": "weread-official",
        "name": "list-apis",
        "description": "List every api_name supported by the WeRead agent gateway",
        "access": "read",
        "browser": false,
        "args": []
      },
      {
        "site": "weread-official",
        "name": "notes",
        "description": "List notebooks overview or merged highlights+thoughts for a book",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "bookId",
            "type": "str",
            "required": false,
            "positional": true,
            "help": "Limit to one book; omit for full notebook overview"
          },
          {
            "name": "count",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Page size for the notebooks overview (1-100)"
          },
          {
            "name": "last-sort",
            "type": "int",
            "required": false,
            "help": "Cursor: pass previous page sort value to fetch the next page (/user/notebooks)"
          }
        ]
      },
      {
        "site": "weread-official",
        "name": "readdata",
        "description": "Reading statistics: time, streak, preferences, top books",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "mode",
            "type": "str",
            "default": "monthly",
            "required": false,
            "help": "Stat window: weekly / monthly / annually / overall",
            "choices": [
              "weekly",
              "monthly",
              "annually",
              "overall"
            ]
          },
          {
            "name": "base-time",
            "type": "int",
            "required": false,
            "help": "Optional Unix timestamp inside the target period; default is current period"
          }
        ]
      },
      {
        "site": "weread-official",
        "name": "review",
        "description": "Browse public reviews of a WeRead book",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "bookId",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "WeRead bookId (from `weread-official search`)"
          },
          {
            "name": "type",
            "type": "str",
            "default": "all",
            "required": false,
            "help": "Review filter (all/recommend/thumbs-down/newest/neutral)",
            "choices": [
              "all",
              "recommend",
              "thumbs-down",
              "newest",
              "neutral"
            ]
          },
          {
            "name": "count",
            "type": "int",
            "default": 20,
            "required": false,
            "help": "Page size (1-100, default 20)"
          },
          {
            "name": "max-idx",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Pagination cursor — pass idx from last row of previous page"
          },
          {
            "name": "synckey",
            "type": "int",
            "required": false,
            "help": "Sync cursor returned by previous response"
          }
        ]
      },
      {
        "site": "weread-official",
        "name": "search",
        "description": "Search WeRead store via the official agent gateway",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search keyword"
          },
          {
            "name": "scope",
            "type": "str",
            "default": "ebook",
            "required": false,
            "help": "Search type (all/ebook/webnovel/audio/author/fulltext/booklist/mp/article)",
            "choices": [
              "all",
              "ebook",
              "webnovel",
              "audio",
              "author",
              "fulltext",
              "booklist",
              "mp",
              "article"
            ]
          },
          {
            "name": "count",
            "type": "int",
            "required": false,
            "help": "Page size (gateway default 15 when omitted)"
          },
          {
            "name": "max-idx",
            "type": "int",
            "default": 0,
            "required": false,
            "help": "Pagination offset, use searchIdx of last item from previous page"
          }
        ]
      },
      {
        "site": "weread-official",
        "name": "shelf",
        "description": "Sync your WeRead shelf (books + albums + article bookmark entry) via the official gateway",
        "access": "read",
        "browser": false,
        "args": []
      }
    ]
  },
  {
    "site": "wttr",
    "title": "wttr.in",
    "description": "极简命令行风格天气查询网站。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "wttr",
        "name": "current",
        "description": "Current weather conditions for a location (city, lat,lon, or airport code)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "location",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "City name, \"lat,lon\", airport ICAO code, or \"@domain\""
          }
        ]
      },
      {
        "site": "wttr",
        "name": "forecast",
        "description": "Multi-day weather forecast (up to 3 days, wttr.in free tier max)",
        "access": "read",
        "browser": false,
        "args": [
          {
            "name": "location",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "City name, \"lat,lon\", airport ICAO code, or \"@domain\""
          },
          {
            "name": "days",
            "type": "int",
            "default": 3,
            "required": false,
            "help": "Max forecast days (1-3, wttr.in caps the response at 3 days)"
          }
        ]
      }
    ]
  },
  {
    "site": "yahoo",
    "title": "Yahoo",
    "description": "新闻、搜索与综合资讯门户。",
    "category": "news",
    "iconKey": "news",
    "requiresLogin": false,
    "commands": [
      {
        "site": "yahoo",
        "name": "search",
        "description": "Search Yahoo (powered by Bing)",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "keyword",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Search query"
          },
          {
            "name": "limit",
            "type": "int",
            "default": 7,
            "required": false,
            "help": "Number of results per page (max 7)"
          },
          {
            "name": "page",
            "type": "int",
            "default": 1,
            "required": false,
            "help": "Page number (1, 2, 3...). Yahoo returns ~7 results per page"
          }
        ]
      }
    ]
  },
  {
    "site": "youdao",
    "title": "有道翻译",
    "description": "在线翻译与词典网站。",
    "category": "research",
    "iconKey": "search",
    "requiresLogin": false,
    "commands": [
      {
        "site": "youdao",
        "name": "note",
        "description": "Read a public shared Youdao Note",
        "access": "read",
        "browser": true,
        "args": [
          {
            "name": "url",
            "type": "str",
            "required": true,
            "positional": true,
            "help": "Full share URL of the Youdao Note"
          }
        ]
      }
    ]
  }
];
