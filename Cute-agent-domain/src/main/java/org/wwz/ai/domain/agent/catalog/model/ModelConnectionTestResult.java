package org.wwz.ai.domain.agent.catalog.model;

/**
 * 模型连接探测结果。
 */
public record ModelConnectionTestResult(boolean ok, long ms, String message) {
}
