export function deriveConversationMetaFromInput(
  info: Pick<CHAT.TInputInfo, "outputStyle" | "deepThink">,
  params: {
    productType: string;
    currentRole: CHAT.ConversationRole | null;
  }
) {
  const outputStyle = info.outputStyle || params.productType;
  const isChatMode = outputStyle === "chat";
  const deepThink =
    isChatMode || outputStyle === "dataAgent" ? false : Boolean(info.deepThink);

  return {
    productType: outputStyle,
    deepThink,
    role: isChatMode ? params.currentRole : null,
  };
}

export function shouldHydrateConversationHistory(params: {
  conversation: CHAT.ConversationHistory;
  hydratedSessionIds: Set<string>;
}) {
  return Boolean(
    params.conversation.sessionId &&
      params.conversation.chatList.length === 0 &&
      params.conversation.dataChatList.length === 0 &&
      !params.hydratedSessionIds.has(params.conversation.sessionId)
  );
}
