import { useMemo } from 'react';
import { Table, Alert, Empty } from 'antd';
import { useRequest } from 'ahooks';

import * as XLSX from 'xlsx';
import Loading from './Loading';
import { parseCSVData } from '@/utils';

/**
 * Table 组件
 */
const TableRenderer: GenieType.FC<{
  /**
   * 文件地址
   */
  fileUrl: string;
  /**
   * 文件类型，默认为文件扩展名决定
   */
  mode?: 'csv' | 'excel';
  /**
   * 文件名
   */
  fileName?: string;
}> = (props) => {
  const { fileUrl, mode, fileName } = props;

  const ext = (fileName || fileUrl).split('.').pop()?.toLowerCase();

  const fileType = mode || (ext === 'xlsx' || ext === 'xlsm' || ext === 'xlsb' || ext === 'xls' ? 'excel' : 'csv');

  // 拉取 CSV 文本
  const { data, loading, error } = useRequest(async () => {
    const res = await fetch(fileUrl);
    if (!res.ok) throw new Error('网络错误');

    if (fileType === 'excel') {
      return res.arrayBuffer();
    }

    return res.text();
  },
  { refreshDeps: [fileUrl] }
  );

  // 解析 CSV
  const { columns, dataSource, parseError } = useMemo(() => {
    if (!data) return {
      columns: [],
      dataSource: [],
      parseError: false
    };

    if (typeof data === 'string') {
      const parsed = parseCSVData(data);

      const keys = Object.keys(parsed[0]);

      return {
        columns: keys.map(field => ({
          title: field,
          dataIndex: field,
          key: field,
        })),
        dataSource: parsed.map((row, idx) => ({
          ...row,
          key: idx
        })),
        parseError: false
      };
    } else {
      // 解析 Excel
      const workbook = XLSX.read(data, { type: 'array' });
      const firstSheetName = workbook.SheetNames[0];
      const worksheet = workbook.Sheets[firstSheetName];
      const json = XLSX.utils.sheet_to_json(worksheet, {
        header: 1,
        defval: ''
      });
      if (!json.length) return {
        columns: [],
        dataSource: [],
        parseError: false
      };
      const [headerData, ...rows] = json;
      const header = headerData as string[];
      if (!header || !(header).length) return {
        columns: [],
        dataSource: [],
        parseError: false
      };
      const cols = header.map(field => ({
        title: String(field),
        dataIndex: String(field),
        key: String(field),
      }));
      const ds = rows.map((row, idx) => {
        const obj: Record<string, string | number> = {};
        header.forEach((field, i) => {
          obj[String(field)] = (row as string[])[i] ?? '';
        });
        obj.key = idx;
        return obj;
      });
      return {
        columns: cols,
        dataSource: ds,
        parseError: false
      };
    }
  }, [data]);

  // 加载中
  // 加载中
  if (loading) {
    return (
      <Loading className="mr-32" />
    );
  }

  // 加载错误
  if (error) {
    return (
      <Alert
        type="error"
        message="加载失败"
        description={error.message}
        showIcon
        className='m-24'
      />
    );
  }

  // 解析失败
  if (parseError) {
    return (
      <Alert
        type="error"
        message="解析失败"
        description="文件格式有误，无法解析。"
        showIcon
        className='m-24'
      />
    );
  }

  // 无数据
  if (!columns.length || !dataSource.length) {
    return (
      <div className='p-32'>
        <Empty description="暂无数据" />
      </div>
    );
  }

  // 正常显示表格
  return (
    <Table
      columns={columns}
      dataSource={dataSource}
      scroll={{ x: true }}
      bordered
      pagination={false}
    />
  );
};

export default TableRenderer;
