import React, { createContext, useContext } from "react";

type PanelItemType = {
  wrapRef?: React.RefObject<HTMLElement | null>;
  scrollToBottom?: () => void;
};

const PanelContext = createContext<PanelItemType | null>(null);

export const usePanelContext = () => {
  return useContext(PanelContext);
};

export default PanelContext.Provider;