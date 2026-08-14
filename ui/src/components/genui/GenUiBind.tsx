import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { clamp, formatLabNumber, normalizeParams, type LabParam } from "./parametricMath";
import { derivedScope } from "./bindProps";

export type GenUiBindApi = {
  values: Record<string, number>;
  params: LabParam[];
  set: (id: string, value: number) => void;
  has: (id: string) => boolean;
};

const GenUiBindContext = createContext<GenUiBindApi | null>(null);

export function useGenUiBind(): GenUiBindApi | null {
  return useContext(GenUiBindContext);
}

export function GenUiBindScope({
  params: rawParams,
  outputs,
  showControls = true,
  children,
}: {
  params?: unknown;
  outputs?: unknown;
  showControls?: boolean;
  children: ReactNode;
}) {
  const params = useMemo(() => normalizeParams(rawParams), [rawParams]);
  const [rawValues, setRawValues] = useState<Record<string, number>>(() => {
    const init: Record<string, number> = {};
    params.forEach((p) => {
      init[p.id] = p.value ?? p.min ?? 0;
    });
    return init;
  });

  const values = useMemo(
    () => derivedScope(params, rawValues, outputs),
    [params, rawValues, outputs]
  );

  const set = useCallback((id: string, value: number) => {
    const p = params.find((x) => x.id === id);
    const next = p ? clamp(value, p.min ?? 0, p.max ?? 10) : value;
    setRawValues((prev) => ({ ...prev, [id]: next }));
  }, [params]);

  const api = useMemo<GenUiBindApi>(
    () => ({
      values,
      params,
      set,
      has: (id: string) => params.some((p) => p.id === id),
    }),
    [values, params, set]
  );

  return (
    <GenUiBindContext.Provider value={api}>
      <div className="space-y-3">
        {showControls && params.length ? (
          <div className="grid gap-x-6 gap-y-2 sm:grid-cols-2">
            {params.map((p) => {
              const min = p.min ?? 0;
              const max = p.max ?? 10;
              const val = values[p.id] ?? min;
              return (
                <div key={p.id} className="min-w-0">
                  <div className="mb-1 flex items-baseline justify-between gap-2 text-[12px]">
                    <label className="font-medium text-[var(--chat-text)]" htmlFor={`bind-${p.id}`}>
                      {p.label || p.id}
                    </label>
                    <span className="tabular-nums text-[var(--chat-text)]">
                      {formatLabNumber(val, "fixed:2")}
                      {p.unit ? (
                        <span className="ml-0.5 text-[var(--chat-text-soft)]">{p.unit}</span>
                      ) : null}
                    </span>
                  </div>
                  <input
                    id={`bind-${p.id}`}
                    type="range"
                    min={min}
                    max={max}
                    step={p.step ?? 0.1}
                    value={val}
                    className="h-1.5 w-full cursor-pointer accent-[var(--chat-accent)]"
                    onChange={(e) => set(p.id, parseFloat(e.target.value))}
                  />
                </div>
              );
            })}
          </div>
        ) : null}
        {children}
      </div>
    </GenUiBindContext.Provider>
  );
}
