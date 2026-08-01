import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Empty, Input } from "antd";
import { useRequest } from "ahooks";
import * as XLSX from "xlsx";
import { Search } from "lucide-react";

import Loading from "./Loading";
import { cn } from "@/lib/utils";
import { copyText } from "@/utils";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";

const ERROR_CLASS =
  "m-12 md:m-24 min-w-[260px] max-w-[calc(100%-24px)] md:max-w-[calc(100%-48px)] [&_.ant-alert-description]:break-words [&_.ant-alert-description]:whitespace-normal";

const MIN_ROWS = 36;
const MIN_COLS = 10;
const MAX_ROWS = 2000;
const MAX_COLS = 80;

type SheetGrid = {
  name: string;
  rows: string[][];
  colCount: number;
  rowCount: number;
  truncated: boolean;
};

type WorkbookView = {
  sheets: SheetGrid[];
};

const resolveUnavailableReason = (error: Error) => {
  const message = error?.message || "";
  if (
    message.includes("Failed to fetch") ||
    message.includes("Network response was not ok") ||
    message.includes("网络错误") ||
    message.includes("NetworkError")
  ) {
    return "引用资源不存在或已失效";
  }
  return message || "引用资源不存在或已失效";
};

/** 0-based index → A, B, … Z, AA, AB… */
const toColumnLetter = (index: number): string => {
  let n = index + 1;
  let label = "";
  while (n > 0) {
    const rem = (n - 1) % 26;
    label = String.fromCharCode(65 + rem) + label;
    n = Math.floor((n - 1) / 26);
  }
  return label;
};

const cellKey = (row: number, col: number) => `${row}:${col}`;

const normalizeCell = (value: unknown): string => {
  if (value === null || value === undefined) return "";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  if (value instanceof Date) return value.toISOString();
  return String(value);
};

const buildSheetGrid = (name: string, matrix: unknown[][]): SheetGrid => {
  const rawRowCount = matrix.length;
  let rawColCount = 0;
  for (const row of matrix) {
    if (Array.isArray(row) && row.length > rawColCount) {
      rawColCount = row.length;
    }
  }

  const truncated = rawRowCount > MAX_ROWS || rawColCount > MAX_COLS;
  const dataRows = Math.min(rawRowCount, MAX_ROWS);
  const dataCols = Math.min(Math.max(rawColCount, 1), MAX_COLS);
  const rowCount = Math.max(dataRows, MIN_ROWS);
  const colCount = Math.max(dataCols, MIN_COLS);

  const rows: string[][] = Array.from({ length: rowCount }, (_, r) => {
    const source = Array.isArray(matrix[r]) ? matrix[r] : [];
    return Array.from({ length: colCount }, (_, c) =>
      c < dataCols && r < dataRows ? normalizeCell(source[c]) : ""
    );
  });

  return { name, rows, colCount, rowCount, truncated };
};

const parseWorkbook = (data: ArrayBuffer | string, fileType: "csv" | "excel"): WorkbookView => {
  if (fileType === "csv") {
    const text = typeof data === "string" ? data : new TextDecoder("utf-8").decode(data);
    const workbook = XLSX.read(text, { type: "string", raw: false, FS: "," });
    const sheetName = workbook.SheetNames[0] || "Sheet1";
    const worksheet = workbook.Sheets[sheetName];
    const matrix = worksheet
      ? (XLSX.utils.sheet_to_json(worksheet, {
          header: 1,
          defval: "",
          raw: false,
          blankrows: true,
        }) as unknown[][])
      : [];
    return { sheets: [buildSheetGrid(sheetName || "Sheet1", matrix)] };
  }

  const workbook = XLSX.read(data, { type: "array", cellDates: true });
  const sheets = workbook.SheetNames.map((name) => {
    const worksheet = workbook.Sheets[name];
    const matrix = worksheet
      ? (XLSX.utils.sheet_to_json(worksheet, {
          header: 1,
          defval: "",
          raw: false,
          blankrows: true,
        }) as unknown[][])
      : [];
    return buildSheetGrid(name, matrix);
  });

  return {
    sheets: sheets.length ? sheets : [buildSheetGrid("Sheet1", [])],
  };
};

interface TableRendererProps {
  fileUrl: string;
  mode?: "csv" | "excel";
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
  className?: string;
}

const TableRenderer: ReactorType.FC<TableRendererProps> = (props) => {
  const { fileUrl, mode, fileName, missingReason, className } = props;
  const [activeSheet, setActiveSheet] = useState(0);
  const [selected, setSelected] = useState<{ row: number; col: number } | null>(
    null
  );
  const [search, setSearch] = useState("");

  const ext = (fileName || fileUrl).split(".").pop()?.toLowerCase();
  const fileType: "csv" | "excel" =
    mode ||
    (ext === "xlsx" || ext === "xlsm" || ext === "xlsb" || ext === "xls"
      ? "excel"
      : "csv");

  const resolvedUrl = useMemo(
    () => normalizeFileUrlForBrowser(fileUrl || ""),
    [fileUrl]
  );

  const {
    data: workbook,
    loading,
    error,
  } = useRequest(
    async () => {
      if (missingReason) {
        throw new Error(missingReason);
      }
      if (!resolvedUrl) {
        throw new Error("引用资源不存在或已失效");
      }
      const res = await fetch(resolvedUrl);
      if (!res.ok) throw new Error("网络错误");

      if (fileType === "excel") {
        const buffer = await res.arrayBuffer();
        return parseWorkbook(buffer, "excel");
      }

      const text = await res.text();
      return parseWorkbook(text, "csv");
    },
    {
      refreshDeps: [resolvedUrl, missingReason, fileType],
      onSuccess: () => {
        setActiveSheet(0);
        setSelected(null);
      },
    }
  );

  const sheet = workbook?.sheets[activeSheet] || workbook?.sheets[0];

  const selectedValue = useMemo(() => {
    if (!sheet || !selected) return "";
    return sheet.rows[selected.row]?.[selected.col] ?? "";
  }, [sheet, selected]);

  const selectedRef = useMemo(() => {
    if (!selected) return "";
    return `${toColumnLetter(selected.col)}${selected.row + 1}`;
  }, [selected]);

  const handleSelect = useCallback((row: number, col: number) => {
    setSelected({ row, col });
  }, []);

  const searchLower = search.trim().toLowerCase();
  const matchedCells = useMemo(() => {
    if (!sheet || !searchLower) return null;
    const set = new Set<string>();
    sheet.rows.forEach((row, r) => {
      row.forEach((value, c) => {
        if (value && String(value).toLowerCase().includes(searchLower)) {
          set.add(cellKey(r, c));
        }
      });
    });
    return set;
  }, [sheet, searchLower]);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey) || e.key.toLowerCase() !== "c") return;
      if (!selected || !sheet) return;
      const value = sheet.rows[selected.row]?.[selected.col] ?? "";
      if (value === "") return;
      e.preventDefault();
      copyText(value);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [selected, sheet]);

  const colLetters = useMemo(() => {
    if (!sheet) return [];
    return Array.from({ length: sheet.colCount }, (_, i) => toColumnLetter(i));
  }, [sheet]);

  if (loading) {
    return <Loading className="mr-32" />;
  }

  if (error) {
    return (
      <Alert
        type="error"
        message="内容不可读取"
        description={resolveUnavailableReason(error as Error)}
        showIcon
        className={ERROR_CLASS}
      />
    );
  }

  if (!workbook || !sheet) {
    return (
      <div className="p-32">
        <Empty description="暂无数据" />
      </div>
    );
  }

  return (
    <div
      className={cn(
        "flex h-full min-h-[420px] w-full flex-col overflow-hidden bg-[#f3f3f3] text-[13px] text-[#1f1f1f]",
        className
      )}
    >
      {/* 名称框 + 编辑栏 + 搜索（文件头由工作区外层提供，避免重复） */}
      <div className="flex shrink-0 items-center gap-2 border-b border-[#e5e5e5] bg-white px-2 py-1">
        <div className="flex h-7 w-[72px] shrink-0 items-center justify-center rounded border border-[#d1d1d1] bg-[#fafafa] font-mono text-[12px] text-[#424242]">
          {selectedRef || "—"}
        </div>
        <div className="flex h-7 min-w-0 flex-1 items-center overflow-hidden rounded border border-[#d1d1d1] bg-white px-2 text-[12px] text-[#242424]">
          <span className="truncate">
            {selectedValue ||
              (sheet.truncated
                ? `已截断 · ${workbook.sheets.length} 个工作表`
                : workbook.sheets.length > 1
                  ? `${workbook.sheets.length} 个工作表 · ${sheet.name}`
                  : sheet.name)}
          </span>
        </div>
        <Input
          allowClear
          size="small"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="搜索"
          prefix={<Search className="h-3.5 w-3.5 text-[#8a8a8a]" />}
          className="w-[140px] shrink-0 sm:w-[180px]"
        />
      </div>

      {/* 网格 */}
      <div className="relative min-h-0 flex-1 overflow-auto bg-white">
        <table className="border-separate border-spacing-0">
          <thead>
            <tr>
              <th
                className={cn(
                  "sticky left-0 top-0 z-30 h-6 w-12 min-w-12 border-b border-r border-[#d0d0d0]",
                  "bg-[#f3f3f3] shadow-[1px_1px_0_0_#d0d0d0]"
                )}
              />
              {colLetters.map((letter, col) => (
                <th
                  key={letter}
                  className={cn(
                    "sticky top-0 z-20 h-6 min-w-[88px] border-b border-r border-[#d0d0d0]",
                    "bg-[#f3f3f3] px-1 text-center text-[11px] font-medium text-[#616161]",
                    selected?.col === col && "bg-[#d7e8d3] text-[#0f6b36]"
                  )}
                >
                  {letter}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sheet.rows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                <th
                  className={cn(
                    "sticky left-0 z-10 h-6 w-12 min-w-12 border-b border-r border-[#d0d0d0]",
                    "bg-[#f3f3f3] text-center text-[11px] font-medium text-[#616161]",
                    selected?.row === rowIndex && "bg-[#d7e8d3] text-[#0f6b36]"
                  )}
                >
                  {rowIndex + 1}
                </th>
                {row.map((value, colIndex) => {
                  const key = cellKey(rowIndex, colIndex);
                  const isSelected =
                    selected?.row === rowIndex && selected?.col === colIndex;
                  const isMatch = matchedCells?.has(key);
                  return (
                    <td
                      key={key}
                      onClick={() => handleSelect(rowIndex, colIndex)}
                      className={cn(
                        "h-6 max-w-[280px] min-w-[88px] cursor-default truncate border-b border-r border-[#e8e8e8] bg-white px-1.5 text-left align-middle text-[12px] leading-[22px] text-[#1f1f1f]",
                        isMatch && !isSelected && "bg-[#fff3bf]",
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
          </tbody>
        </table>
      </div>

      {/* 底栏 sheet tabs */}
      <div className="flex shrink-0 items-center gap-1 border-t border-[#d0d0d0] bg-[#f3f3f3] px-2 py-1">
        <div className="flex min-w-0 flex-1 items-center gap-0.5 overflow-x-auto">
          {workbook.sheets.map((item, index) => {
            const active = index === activeSheet;
            return (
              <button
                key={`${item.name}-${index}`}
                type="button"
                onClick={() => {
                  setActiveSheet(index);
                  setSelected(null);
                }}
                className={cn(
                  "relative shrink-0 rounded-t px-3 py-1 text-[12px] transition-colors",
                  active
                    ? "bg-white font-medium text-[#107c41] shadow-[0_-1px_0_0_#107c41_inset]"
                    : "text-[#616161] hover:bg-[#e8e8e8] hover:text-[#1f1f1f]"
                )}
              >
                {item.name}
                {active ? (
                  <span className="absolute inset-x-2 bottom-0 h-0.5 rounded-full bg-[#107c41]" />
                ) : null}
              </button>
            );
          })}
        </div>
        <div className="shrink-0 px-1 font-mono text-[11px] text-[#8a8a8a]">
          {selectedRef
            ? selectedRef
            : `${sheet.rowCount} × ${sheet.colCount}`}
        </div>
      </div>
    </div>
  );
};

export default TableRenderer;
