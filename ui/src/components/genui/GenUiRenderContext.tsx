import { createContext, useContext, type ReactNode } from "react";
import type { GenUiActionContext } from "./genUiActionBus";

export type GenUiRenderContextValue = GenUiActionContext;

const GenUiRenderContext = createContext<GenUiRenderContextValue>({});

export function GenUiRenderProvider({
  value,
  children,
}: {
  value: GenUiRenderContextValue;
  children: ReactNode;
}) {
  return (
    <GenUiRenderContext.Provider value={value}>
      {children}
    </GenUiRenderContext.Provider>
  );
}

export function useGenUiRenderContext(): GenUiRenderContextValue {
  return useContext(GenUiRenderContext);
}
