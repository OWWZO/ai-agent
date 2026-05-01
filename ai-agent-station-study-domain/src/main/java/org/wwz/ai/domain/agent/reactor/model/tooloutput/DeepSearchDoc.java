package org.wwz.ai.domain.agent.reactor.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * deep_search 文档摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchDoc {

    private String title;

    private String link;

    private String summary;
}
