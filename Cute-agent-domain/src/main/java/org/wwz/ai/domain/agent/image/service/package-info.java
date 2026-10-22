/**
 * 工作台生图 capability。
 * <p>
 * 承载生图工作台的领域契约与领域服务：模型（{@code model}）、下游调用端口（{@code port}）
 * 与执行内核/批次持久化服务（{@code service}）。技术实现（HTTP 客户端、DAO、文件服务）
 * 归属 {@code org.wwz.ai.infrastructure.imagegeneration}，应用编排归属
 * {@code org.wwz.ai.application.agent.image}，HTTP 适配归属
 * {@code org.wwz.ai.trigger.http.agent.image}。
 */
package org.wwz.ai.domain.agent.image.service;
