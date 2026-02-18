import { FC, useEffect, useState } from "react";
import { Drawer } from "antd";
import { agentApi } from "../../services/agent";
import classNames from "classnames";

type Props = {
  show: boolean;
  dbsShow: (show: boolean) => void;
  showDetail: (modelInfo: CHAT.ModelInfo) => void;
};

const DataDrawer: FC<Props> = (props) => {
  const { show, dbsShow, showDetail } = props;
  const [dbList, setDbList] = useState<any[]>([]);
  const [curHover, setCurHover] = useState<string>("");

  useEffect(() => {
    agentApi.allModels().then((res) => {
      setDbList(Array.isArray(res) ? res : []);

      console.log(res);
    });
  }, []);

  return (
    <Drawer title="相关知识库" width={800} onClose={() => dbsShow(false)} open={show}>
      <div className="grid grid-cols-2 gap-10">
        {dbList.map((item, index) => {
          return (
            <div
              key={index}
              className="border border-solid border-[#f0f1f2] overflow-hidden rounded-[8px] p-[12px] relative"
              onMouseOver={() => setCurHover(item.modelName)}
              onMouseOut={() => setCurHover("")}
            >
              <div className="mb-[16px]">{item.modelName}</div>
              <div className="flex flex-wrap gap-10 overflow-y-auto max-h-[150px]">
                {item.schemaList?.map((schema: { columnId: string; columnName: string }) => {
                  return (
                    <div key={schema.columnId} className="text-[12px] text-[#6b7280] px-[8px] py-[2px] bg-[#f0f1f2] rounded-[4px]">
                      {schema.columnName}
                    </div>
                  );
                })}
              </div>
              {/* 字段详情遮罩层 */}
              <div
                className={classNames("flex justify-center items-center absolute top-0 left-0 w-full h-full backdrop-filter-[blur(5px)] opacity-0 transition-opacity duration-500", {
                  "opacity-100": curHover === item.modelName,
                })}
              >
                <div className="border border-solid border-[#dcdee0] text-[#1b1b1b] rounded-[8px] px-[30px] py-[6px] cursor-pointer" onClick={() => showDetail(item)}>预&nbsp;览</div>
              </div>
            </div>
          );
        })}
      </div>
    </Drawer>
  );
};

export default DataDrawer;
