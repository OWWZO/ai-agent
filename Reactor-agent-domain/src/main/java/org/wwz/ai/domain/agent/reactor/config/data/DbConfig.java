package org.wwz.ai.domain.agent.reactor.config.data;

import lombok.Data;

@Data
public class DbConfig {
    private String type;
    /**
     * 直连 JDBC 地址，优先级高于 host + port + schema 的拼接方式
     */
    private String url;
    private String host;
    private int port;
    private String schema;
    private String username;
    private String password;
    private String key = "reactor-datasource";
}
