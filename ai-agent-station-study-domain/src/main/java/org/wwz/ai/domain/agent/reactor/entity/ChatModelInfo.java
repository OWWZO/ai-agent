package org.wwz.ai.domain.agent.reactor.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class ChatModelInfo implements Serializable {
    private static final long serialVersionUID = 8763697882256572393L;
    private Long id;
    private String code;
    private String type;
    private String content;
    private String name;
    private String usePrompt;
    private String businessPrompt;
    private Integer yn;
}
