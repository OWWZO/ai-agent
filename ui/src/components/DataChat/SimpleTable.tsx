import { useMemo, useState } from "react";
import { cn } from "@/lib/utils";

interface TableColumn {
  title: string;
  dataIndex: string;
  key?: string;
}

interface SimpleTableProps {
  data: {
    columnList?: TableColumn[];
    dataList?: Record<string, any>[];
  };
}

const toColumnLetter = (index: number): string => {
  // 使用 26 进制把列索引转换成类似 Excel 的 A、Z、AA 标识。
  let n = index + 1;
  let label = "";
  while (n > 0) {
    const rem = (n - 1) % 26;
    label = String.fromCharCode(65 + rem) + label;
    n = Math.floor((n - 1) / 26);
  }
  return label;
};

const isNumericLike = (value: unknown) => {
  if (typeof value === "number") return Number.isFinite(value);
  if (typeof value !== "string") return false;
  const t = value.trim();
  if (!t) return false;
  return /^-?\d+(\.\d+)?%?$/.test(t);
};

const SimpleTable: ReactorType.FC<SimpleTableProps> = ({ data }) => {
  const { columnList = [], dataList = [] } = data || {};
  const [selected, setSelected] = useState<{ row: number; col: number } | null>(
    null
  );

  const columns = useMemo(
    // 为缺少 key 的列补稳定标识，并保留用户可见列字母。
    () =>
      columnList.map((col, index) => ({
        ...col,
        key: col.key || col.dataIndex || String(index),
        letter: toColumnLetter(index),
      })),
    [columnList]
  );

  if (!columns.length) {
    return (
      <div className="flex h-[200px] w-full items-center justify-center text-[13px] text-[var(--chat-text-soft)]">
        暂无表格数据
      </div>
    );
  }

  const selectedValue =
    selected && dataList[selected.row]
      ? String(dataList[selected.row][columns[selected.col]?.dataIndex] ?? "")
      : "";
  const selectedRef = selected
    ? `${columns[selected.col]?.letter || ""}${selected.row + 1}`
    : "";

  return (
    <div className="flex w-full flex-col overflow-hidden rounded-lg border border-[#e5e5e5] bg-white text-[13px]">
      <div className="flex shrink-0 items-center gap-2 border-b border-[#e5e5e5] bg-[#fafafa] px-2 py-1.5">
        <div className="flex h-7 w-[64px] shrink-0 items-center justify-center rounded border border-[#d1d1d1] bg-white font-mono text-[12px] text-[#424242]">
          {selectedRef || "—"}
        </div>
        <div className="flex h-7 min-w-0 flex-1 items-center overflow-hidden rounded border border-[#d1d1d1] bg-white px-2 text-[12px] text-[#242424]">
          <span className="truncate">
            {selectedValue || `${dataList.length} 行 · ${columns.length} 列`}
          </span>
        </div>
      </div>

      <div className="max-h-[420px] overflow-auto">
        <table className="w-full border-separate border-spacing-0">
          <thead>
            <tr>
              <th className="sticky left-0 top-0 z-30 h-7 w-10 min-w-10 border-b border-r border-[#d0d0d0] bg-[#f3f3f3]" />
              {columns.map((col, colIndex) => (
                <th
                  key={col.key}
                  className={cn(
                    "sticky top-0 z-20 h-7 min-w-[96px] border-b border-r border-[#d0d0d0] bg-[#f3f3f3] px-2 text-left text-[11px] font-semibold text-[#424242]",
                    selected?.col === colIndex && "bg-[#d7e8d3] text-[#0f6b36]"
                  )}
                  title={col.title}
                >
                  <span className="mr-1 font-mono text-[10px] font-normal text-[#8a8a8a]">
                    {col.letter}
                  </span>
                  {col.title}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {dataList.map((row, rowIndex) => (
              <tr key={rowIndex} className="odd:bg-white even:bg-[#fcfcfc]">
                <th
                  className={cn(
                    "sticky left-0 z-10 h-7 w-10 min-w-10 border-b border-r border-[#e8e8e8] bg-[#f3f3f3] text-center text-[11px] font-medium text-[#616161]",
                    selected?.row === rowIndex && "bg-[#d7e8d3] text-[#0f6b36]"
                  )}
                >
                  {rowIndex + 1}
                </th>
                {columns.map((col, colIndex) => {
                  const raw = row?.[col.dataIndex];
                  const value = raw == null ? "" : String(raw);
                  // 数值单元格右对齐，选中单元格同时展示坐标和完整值。
                  const numeric = isNumericLike(raw);
                  const isSelected =
                    selected?.row === rowIndex && selected?.col === colIndex;
                  return (
                    <td
                      key={`${rowIndex}-${col.key}`}
                      onClick={() => setSelected({ row: rowIndex, col: colIndex })}
                      className={cn(
                        "h-7 max-w-[240px] min-w-[96px] cursor-default truncate border-b border-r border-[#ececec] px-2 text-[12px] leading-[26px] text-[#1f1f1f]",
                        numeric && "text-right tabular-nums",
                        isSelected &&
                          "relative z-[1] bg-[#e8f5e9] outline outline-2 outline-[#107c41] -outline-offset-1"
                      )}
                      title={value || undefined}
                    >
                      {value}
                    </td>
                  );
                })}
              </tr>
            ))}
            {!dataList.length ? (
              <tr>
                <td
                  colSpan={columns.length + 1}
                  className="px-3 py-8 text-center text-[12px] text-[#8a8a8a]"
                >
                  暂无数据行
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default SimpleTable;
