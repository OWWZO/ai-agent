import { z } from "zod";

const answerEnvelopeSchema = z.object({
  status: z.string(),
  packageType: z.string(),
  finished: z.boolean(),
  errorMsg: z.string().nullable().optional().transform((value) => value ?? ""),
  resultMap: z.object({ eventData: z.unknown().optional() }).passthrough().optional().default({}),
}).passthrough();

const eventDataSchema = z.object({
  messageOrder: z.number(),
  messageType: z.string(),
  messageId: z.string(),
  taskId: z.string(),
  taskOrder: z.number(),
  resultMap: z.object({ messageType: z.string().optional() }).passthrough(),
  artifactRefs: z.array(z.object({}).passthrough()).optional(),
}).passthrough();

const dataChatEventSchema = z.discriminatedUnion("eventType", [
  z.object({
    eventType: z.literal("THINK"),
    data: z.string(),
  }),
  z.object({
    eventType: z.literal("CHART_DATA"),
    data: z.array(z.record(z.string(), z.unknown())),
  }),
  z.object({
    eventType: z.literal("ERROR"),
    data: z.string(),
  }),
  z.object({
    eventType: z.literal("READY"),
    data: z.unknown().optional(),
  }),
]);

/**
 * 这里只校验 SSE 顶层协议骨架，内部 payload 继续交给业务解析层消费，
 * 避免前端跟后端富结构结果做过紧耦合。
 */
export function parseAgentAnswer(raw: unknown): MESSAGE.Answer {
  return answerEnvelopeSchema.parse(raw) as unknown as MESSAGE.Answer;
}

/**
 * SSE eventData 入口统一做一次骨架校验，确保 message/task 主键存在，
 * 后续 combineData 只处理结构合法的事件。
 */
export function parseEventData(raw: unknown): MESSAGE.EventData {
  return eventDataSchema.parse(raw) as unknown as MESSAGE.EventData;
}

export function parseDataChatEvent(raw: unknown): CHAT.DataChatEvent {
  return dataChatEventSchema.parse(raw);
}
