package org.wwz.ai.application.agent.dataquery.command;

/** Input for an analytical chat query. */
public record DataAgentChatCommand(String content, String traceId) {
}
