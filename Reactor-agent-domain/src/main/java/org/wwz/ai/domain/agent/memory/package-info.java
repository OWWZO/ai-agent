/**
 * Agent memory 子域。
 * <p>
 * 分层约定：
 * <ul>
 *   <li>Execution Ledger：执行事实与 UI 回放唯一真相源</li>
 *   <li>Working Memory（session 级）：跨轮 LLM hydrate 热窗口，可压缩</li>
 *   <li>LTM Curated（user/visitor 级）：有界策展记忆，全量冻结注入 system，禁止 embedding</li>
 *   <li>LTM Deep Provider：可选深度召回，运行时最多一个外部 Provider</li>
 * </ul>
 * 本包只保留领域语义、端口契约与编排规则；DAO 与外部适配在 infrastructure。
 */
package org.wwz.ai.domain.agent.memory;
