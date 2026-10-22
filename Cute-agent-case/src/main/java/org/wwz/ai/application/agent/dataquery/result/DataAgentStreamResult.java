package org.wwz.ai.application.agent.dataquery.result;

/** Case-level stream event; Trigger maps it to the HTTP event VO. */
public record DataAgentStreamResult(String eventType, Object data) {
}
