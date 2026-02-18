import React from 'react';
import { LinkOutlined } from "@ant-design/icons";
import type { SearchListItem } from "./type";
import { jumpUrl } from "@/utils";

const CONTAINER_CLASS = "w-fit flex flex-col";
const ITEM_CLASS = "border-b-[#52649a13] py-8 border-b-1 border-b-solid";
const LINK_CLASS = "flex gap-4 text-[#404040] cursor-pointer hover:text-primary";
const CONTENT_CLASS = "line-clamp-2 text-[#92909c] text-[12px]";

interface SearchListRendererProps {
  list?: SearchListItem[];
}

const SearchListItemComponent: GenieType.FC<SearchListItem> = React.memo(({ name, pageContent, url }) => (
  <div className={ITEM_CLASS}>
    <div
      className={LINK_CLASS}
      onClick={() => jumpUrl(url)}
    >
      <LinkOutlined />
      <span>{name}</span>
    </div>
    <div className={CONTENT_CLASS}>
      {pageContent}
    </div>
  </div>
));

SearchListItemComponent.displayName = 'SearchListItemComponent';

/**
 * SearchListRenderer 组件
 *
 * 渲染搜索结果列表
 *
 * @param list - 搜索结果列表
 */
const SearchListRenderer: GenieType.FC<SearchListRendererProps> = React.memo(({ list }) => (
  <div className={CONTAINER_CLASS}>
    {list?.map((item) => (
      <SearchListItemComponent key={item.name + item.url} {...item} />
    ))}
  </div>
));

SearchListRenderer.displayName = 'SearchListRenderer';

export default SearchListRenderer;
