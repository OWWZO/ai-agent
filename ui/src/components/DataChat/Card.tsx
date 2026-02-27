import { JSX, useRef } from "react";

const Card: GenieType.FC<{ data: Record<string, any> }> = (props) => {
  const { data } = props;
  const { kpiList } = data;
  const dom = useRef(null);

  const makeItems = () => {
    const items: JSX.Element[] = [];
    kpiList?.forEach((item: any, index: number) => {
      // 分割线：仅在不是第一个元素时添加
      if (index > 0) {
        items.push(<div key={`separator-${index}`} className="border-l-[1px] border-solid border-[#e9e9f0] h-[60px] w-[10px]" />);
      }

      // 展示对象：确保key唯一
      items.push(
        <div key={`item-${index}`} className="flex-[1] overflow-hidden">
          <div className="text-[16px] font-medium leading-[26px] whitespace-nowrap text-ellipsis overflow-hidden">
            {item.label || "未命名"}
          </div>
          <div>{item.showValue ?? "-"}</div> 
        </div>
      );
    });

    return items;
  };

  return (
    <div className="flex items-center gap-[10px] w-full h-full" ref={dom}>
      {makeItems()}
    </div>
  );
};

export default Card;
