import { FC, useEffect, useMemo, useState } from "react";
import { Drawer, Segmented, Input, Table } from "antd";
import { agentApi } from "../../services/agent";
import type { TableColumnType } from "antd";

type Props = {
  show: boolean;
  dataShow: (show: boolean) => void;
  modelInfo: CHAT.ModelInfo;
};

interface DataDataType {
  dataList?: Record<string, any>[];
}

const ColsAndDataDrawer: FC<Props> = (props) => {
  const { show, modelInfo, dataShow } = props;
  const { Search } = Input;
  const [segType, setSegType] = useState<string>("字段详情");
  const [searchValue, setSearchValue] = useState<string>("");
  const [viewDataSource, setViewDataSource] = useState<any[]>([]);

  const colColumns: TableColumnType<Record<string, any>>[] = [
    {
      title: "字段名称",
      dataIndex: "columnName",
      key: "columnName",
    },
    {
      title: "字段类型",
      dataIndex: "dataType",
      key: "dataType",
    },
    {
      title: "字段描述",
      dataIndex: "columnComment",
      key: "columnComment",
    },
  ];

  const dataSource = useMemo(() => {
    return (modelInfo.schemaList || []).filter(
      (item) => item.columnName?.includes(searchValue) || item.columnComment?.includes(searchValue) || item.dataType?.includes(searchValue)
    );
  }, [modelInfo, searchValue]);

  const viewShowData = useMemo(() => {
    return viewDataSource.filter((item) => {
      const vals = Object.values(item);
      return vals.some((value) => `${value}`.includes(searchValue));
    });
  }, [viewDataSource, searchValue]);

  // 数据预览的列字段
  const dataColumns = useMemo(() => {
    return (modelInfo.schemaList || []).map((item) => {
      return {
        title: item.columnName,
        dataIndex: item.columnId,
        key: item.columnId,
        ellipsis: true,
      };
    });
  }, [modelInfo]);

  useEffect(() => {
    agentApi.previewData(modelInfo.modelCode).then((res) => {
      const _ddt = res as DataDataType;
      setViewDataSource(Array.isArray(_ddt.dataList) ? _ddt.dataList! : []);
    });
  }, []);

  return (
    <Drawer title={modelInfo.modelName} width={1000} onClose={() => dataShow(false)} open={show}>
      <div className="flex align-middle justify-between mb-[20px]">
        <Segmented<string>
          options={["字段详情", "数据预览"]}
          value={segType}
          onChange={(value) => {
            setSegType(value); // string
          }}
        />
        <Search placeholder="请输入内容搜索" style={{ width: 200 }} onSearch={(value) => setSearchValue(value)} />
      </div>
      {segType === "字段详情" && <Table size="middle" dataSource={dataSource} columns={colColumns} scroll={{ y: "calc(100vh - 200px)" }} pagination={false} />}
      {segType === "数据预览" && <Table size="middle" dataSource={viewShowData} columns={dataColumns} scroll={{ y: "calc(100vh - 200px)", x: "max-content" }} pagination={false} />}
    </Drawer>
  );
};

export default ColsAndDataDrawer;
