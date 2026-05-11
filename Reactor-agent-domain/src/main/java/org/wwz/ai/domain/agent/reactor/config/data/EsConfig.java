package org.wwz.ai.domain.agent.reactor.config.data;

import lombok.Data;

@Data
public class EsConfig {
    private Boolean enable;
    private String host;
    private String user;
    private String password;
    private String apiKey;
    private String scheme;
}
