import { Table } from "antd";

// 定义表格列的类型
interface TableColumn {
  title: string;
  dataIndex: string;
  key?: string;
}

// 明确组件Props类型
interface SimpleTableProps {
  data: {
    columnList?: TableColumn[];
    dataList?: Record<string, any>[];
  };
}

const SimpleTable: GenieType.FC<SimpleTableProps> = ({ data }) => {
  // 提供默认空数组，避免undefined导致的错误
  const { columnList = [], dataList = [] } = data || {};
  return <Table dataSource={dataList} columns={columnList} size="middle" className="w-full" scroll={{ y: 400 }} />;
};

export default SimpleTable;
