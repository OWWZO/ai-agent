import { useMemo, useState } from "react";
import ActionViewFrame from "./ActionViewFrame";
import { formatTimestamp, jumpUrl } from "@/utils";
import { keyBy } from "lodash";
import { LinkOutlined } from "@ant-design/icons";
import { Empty, Result } from "antd";
import classNames from "classnames";
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

const BrowserItem: GenieType.FC<{item: BrowserItem}> = (props) => {
  const { item } = props;
  const { result } = item;

  if (!result?.length) {
    return <Result title='暂无数据' status="404" />;
  }
  return result.map((ele) => {
    return <div className="flex items-center w-full pb-16" key={ele.name}>
      <LinkOutlined className="text-primary mr-6" />
      <div className="flex-1 flex items-center w-0">
        <span className="hover:text-primary cursor-pointer text-ellipsis whitespace-nowrap overflow-hidden" onClick={() => jumpUrl(ele.url)}>{ele.name}</span>
      </div>
    </div>;
  });
};

const BrowserList: GenieType.FC<{
  taskList?: PanelItemType[]
}> = (props) => {
  const { taskList } = props;

  const [ activeItem, setActiveItem ] = useState<string | undefined>();

  const clearActive = () => {
    setActiveItem(undefined);
  };

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
          query: toolParam?.query || toolParam?.query || searchResult?.query + '',
          id,
          result: getSearchList(task) || []
        });
      }

      return pre;
    }, []);
  }, [taskList]);

  const browserMap = useMemo(() => {
    return keyBy(browserList, 'id');
  }, [browserList]);

  const browserItem = activeItem && browserMap[activeItem];

  const generateQuery = (item: BrowserItem, noHover?: boolean, click?: () => void) => {
    return <div className="flex-1 flex items-center w-0">
      <span
        className={classNames("cursor-pointer text-ellipsis whitespace-nowrap overflow-hidden", {'hover:font-medium': !noHover})}
        onClick={click || (() => setActiveItem(item.id))}
      >
      搜索“{ item.query }”
      </span>
      <span className="ml-8 text-[12px] whitespace-nowrap">
      共{ item.result?.length || 0 }个结果
      </span>
    </div>;
  };

  let content: React.ReactNode = browserList.map((item) => {
    return  <div className="w-full pb-16 flex gap-8 items-center text-[#303133]" key={item.id}>
      <span className="font_family icon-sousuo mr-2"></span>
      {generateQuery(item)}
      <div className="text-[12px] text-[#8d8da5]">
        { item.messageTime}
      </div>
    </div>;
  });

  if (!browserList?.length) {
    content = <Empty />;
  }

  if (browserItem) {
    content = <BrowserItem item={browserItem} />;
  }

  return <ActionViewFrame
    className="p-16 overflow-y-auto"
    titleNode={browserItem && generateQuery(browserItem, true, clearActive)}
    onClickTitle={clearActive}
  >
    {content}
  </ActionViewFrame>;
};

export default BrowserList;
