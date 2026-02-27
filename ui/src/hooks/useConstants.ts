import { createContext, useContext } from "react";
import * as constants from "@/utils/constants";

const ConstantContext = createContext(constants);

export const ConstantProvider = ConstantContext.Provider;

export type ConstantType = typeof constants;

export const useConstants = () => {
  return useContext(ConstantContext);
};

export default ConstantContext;