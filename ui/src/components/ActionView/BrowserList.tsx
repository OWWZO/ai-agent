import { useMemo, useState } from "react";
import ActionViewFrame from "./ActionViewFrame";
import { formatTimestamp, jumpUrl } from "@/utils";
import { keyBy } from "lodash";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import {
  Search,
  ExternalLink,
  Globe,
  ChevronRight,
  Link2,
} from "lucide-react";
import { getSearchList, PanelItemType } from "../ActionPanel";

type BrowserItem = {
  messageTime: string;
  query: string;
  result: Array<{
    name: string;
    pageContent?: string;
    url: string;
  }>;
  id: string;
};

const BrowserDetail: React.FC<{ item: BrowserItem; onBack: () => void }> = ({
  item,
}) => {
  const { result } = item;

  if (!result?.length) {
    return (
      <div className="flex h-full items-center justify-center">
        <Card className="w-64 bg-muted/15 py-8 shadow-none ring-0">
          <CardContent className="flex flex-col items-center justify-center py-0 text-center">
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[#f5f5f7]">
              <Globe className="h-5 w-5 text-[#86868b]" />
            </div>
            <p className="text-sm font-medium text-[#1d1d1f]">暂无搜索结果</p>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="h-full overflow-auto p-4">
      <div className="mb-4 flex items-center gap-2">
        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-50">
          <Search className="h-4 w-4 text-blue-500" />
        </div>
        <div>
          <p className="text-sm font-medium text-[#1d1d1f]">搜索 "{item.query}"</p>
          <p className="text-xs text-[#86868b]">共 {item.result.length} 个结果</p>
        </div>
      </div>

      <Separator className="mb-4 bg-[#e8e8ed]" />

      <div className="space-y-3">
        {result.map((ele, idx) => (
          <Card
            key={idx}
            className="group cursor-pointer rounded-xl bg-transparent py-0 shadow-none ring-0 transition-all duration-200 hover:bg-muted/35"
            onClick={() => jumpUrl(ele.url)}
          >
            <CardContent className="p-3">
              <div className="flex items-start gap-3">
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-[#f5f5f7]">
                  <Link2 className="h-3 w-3 text-[#86868b]" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-[13px] font-medium text-[#0071e3] transition-colors group-hover:underline">
                    {ele.name}
                  </p>
                  {ele.pageContent && (
                    <p className="mt-1 line-clamp-2 text-xs text-[#86868b]">
                      {ele.pageContent}
                    </p>
                  )}
                  <div className="mt-2 flex items-center gap-1 text-[11px] text-[#c7c7cc]">
                    <ExternalLink className="h-3 w-3" />
                    <span className="truncate">{ele.url}</span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

const BrowserList: React.FC<{
  taskList?: PanelItemType[];
}> = ({ taskList }) => {
  const [activeItem, setActiveItem] = useState<string | undefined>();

  const clearActive = () => setActiveItem(undefined);

  const browserList = useMemo(() => {
    return (taskList || []).reduce<BrowserItem[]>((pre, task) => {
      const { toolResult, resultMap, id } = task;
      const { toolParam } = toolResult || {};
      const { searchResult } = resultMap || {};
      const messageTime = formatTimestamp(task.messageTime);
      const resultList = getSearchList(task);

      if (resultList?.length) {
        pre.push({
          messageTime,
          query: toolParam?.query || searchResult?.query + '',
          id,
          result: resultList,
        });
      }
      return pre;
    }, []);
  }, [taskList]);

  const browserMap = useMemo(() => keyBy(browserList, 'id'), [browserList]);
  const browserItem = activeItem && browserMap[activeItem];

  // Detail View
  if (browserItem) {
    return (
      <ActionViewFrame
        titleNode={
          <div className="flex items-center gap-2">
            <Search className="h-4 w-4 text-blue-500" />
            <span className="truncate">搜索 "{browserItem.query}"</span>
            <Badge variant="secondary" className="ml-2 text-[10px]">
              {browserItem.result.length}
            </Badge>
          </div>
        }
        onClickTitle={clearActive}
      >
        <div className="flex-1 overflow-hidden">
          <BrowserDetail item={browserItem} onBack={clearActive} />
        </div>
      </ActionViewFrame>
    );
  }

  // List View
  if (!browserList?.length) {
    return (
      <div className="flex h-full items-center justify-center">
        <Card className="w-64 bg-muted/15 py-8 shadow-none ring-0">
          <CardContent className="flex flex-col items-center justify-center py-0 text-center">
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[#f5f5f7]">
              <Globe className="h-5 w-5 text-[#86868b]" />
            </div>
            <p className="text-sm font-medium text-[#1d1d1f]">暂无浏览记录</p>
            <p className="mt-1 text-xs text-[#86868b]">搜索任务将在这里显示</p>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="h-full overflow-auto p-4">
      <div className="space-y-2">
        {browserList.map((item) => (
          <Card
            key={item.id}
            className="group cursor-pointer rounded-xl bg-transparent py-0 shadow-none ring-0 transition-all duration-200 hover:bg-muted/35"
            onClick={() => setActiveItem(item.id)}
          >
            <CardContent className="flex items-center gap-3 p-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50">
                <Search className="h-4 w-4 text-blue-500" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-[13px] font-medium text-[#1d1d1f]">
                  搜索 "{item.query}"
                </p>
                <div className="mt-0.5 flex items-center gap-2">
                  <Badge variant="secondary" className="h-4 px-1.5 text-[10px]">
                    {item.result.length} 结果
                  </Badge>
                  <span className="text-xs text-[#86868b]">{item.messageTime}</span>
                </div>
              </div>
              <ChevronRight className="h-4 w-4 shrink-0 text-[#c7c7cc] transition-colors group-hover:text-[#86868b]" />
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

export default BrowserList;
