import type { FC } from "react";
import RunPresenceBar from "./RunPresenceBar";

type ThinkingMessageProps = {
  tip?: string;
};

/**
 * 统一复用深度思考/深度研究的初始 Thinking 占位，避免各模式出现不同的等待体感。
 * tip 用于临时 status 文案（如“正在制定计划…”）。
 */
const ThinkingMessage: FC<ThinkingMessageProps> = ({ tip }) => (
  <RunPresenceBar hint={tip?.trim() ? tip : "正在理解任务…"} />
);

export default ThinkingMessage;
