import {
  AtSign,
  BookOpenText,
  ChartNoAxesColumnIncreasing,
  Clapperboard,
  FileInput,
  FileOutput,
  FolderSearch,
  Globe,
  Image as ImageIcon,
  MessageCircle,
  MonitorPlay,
  Newspaper,
  NotebookPen,
  Radio,
  Search,
  ShoppingBag,
  Wrench,
  type LucideIcon,
} from "lucide-react";

const iconByKey: Record<string, LucideIcon> = {
  search: Search,
  chart: ChartNoAxesColumnIncreasing,
  "file-input": FileInput,
  "file-output": FileOutput,
  "folder-search": FolderSearch,
  browser: Globe,
  image: ImageIcon,
  douyin: Clapperboard,
  xiaohongshu: NotebookPen,
  "twitter-x": AtSign,
  reddit: MessageCircle,
  bilibili: MonitorPlay,
  weibo: Radio,
  zhihu: BookOpenText,
  social: MessageCircle,
  document: FileInput,
  creation: NotebookPen,
  media: MonitorPlay,
  news: Newspaper,
  commerce: ShoppingBag,
  finance: ChartNoAxesColumnIncreasing,
};

export function getToolCatalogIcon(iconKey: string): LucideIcon {
  return iconByKey[iconKey] ?? Wrench;
}
