import type { ToolCategory } from "./toolCatalog";

export type OpenCliArgumentDefinition = {
  name: string;
  type: string;
  required: boolean;
  help: string;
  default?: string | number | boolean;
  positional?: boolean;
  valueRequired?: boolean;
  choices?: string[];
};

export type OpenCliCommandDefinition = {
  site: string;
  name: string;
  description: string;
  access: "read" | "write";
  browser: boolean;
  args: OpenCliArgumentDefinition[];
};

export type OpenCliSiteDefinition = {
  site: string;
  title: string;
  description: string;
  category: ToolCategory;
  iconKey: string;
  requiresLogin?: boolean;
  commands: OpenCliCommandDefinition[];
};
