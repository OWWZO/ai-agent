export type XlsxModule = typeof import("xlsx");

let xlsxPromise: Promise<XlsxModule> | undefined;

export function loadXlsx(): Promise<XlsxModule> {
  return (xlsxPromise ??= import("xlsx"));
}
