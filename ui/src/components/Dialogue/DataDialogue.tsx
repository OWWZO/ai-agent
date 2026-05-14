import { FC } from "react";
import DataChat from "@/components/DataChat";
import { Message, MessageContent, MessageResponse } from "@/components/ai-elements/message";
import { Reasoning, ReasoningContent, ReasoningTrigger } from "@/components/ai-elements/reasoning";
import ThinkingMessage from "./ThinkingMessage";

type Props = {
  chat: CHAT.DataChatItem;
};

const DataDialogue: FC<Props> = (props) => {
  const { chat } = props;

  return (
    <div className="flex h-full flex-col text-[14px] font-normal text-[#111827]">
      {chat.query ? (
        <div className="mt-6 flex w-full justify-end">
          <Message from="user" className="max-w-[82%]">
            <MessageContent>{chat.query}</MessageContent>
          </Message>
        </div>
      ) : null}

      {chat.loading && !chat.think && !chat.chartData && !chat.error ? (
        <ThinkingMessage />
      ) : null}

      {chat.think ? (
        <div className="mt-6 w-full">
          <Reasoning isStreaming={chat.loading} defaultOpen>
            <ReasoningTrigger />
            <ReasoningContent>{chat.think}</ReasoningContent>
          </Reasoning>
        </div>
      ) : null}

      {chat.chartData ? (
        <div className="mt-6 flex w-full justify-start">
          <Message from="assistant" className="w-full max-w-full">
            <MessageContent>
              <MessageResponse isStreaming={chat.loading}>输出结果</MessageResponse>
              <div className="mt-3">
                {chat.chartData.map((n, index: number) => {
                  return <DataChat key={index} data={n} />;
                })}
              </div>
            </MessageContent>
          </Message>
        </div>
      ) : null}

      {chat.error?.length > 0 ? (
        <div className="mt-6 flex w-full justify-start">
          <Message from="assistant" className="w-full max-w-full">
            <MessageContent>
              <div className="rounded-xl bg-[#fff1f2] px-4 py-3 text-[13px] leading-6 text-[#991b1b]">
                回答失败，没能理解您的意图。
              </div>
            </MessageContent>
          </Message>
        </div>
      ) : null}

    </div>
  );
};

export default DataDialogue;
