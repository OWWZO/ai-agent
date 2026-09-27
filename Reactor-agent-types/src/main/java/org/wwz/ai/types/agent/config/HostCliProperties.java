package org.wwz.ai.types.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "reactor.host-cli")
public class HostCliProperties {

    private boolean enabled = true;

    private List<String> allow = new ArrayList<>();

    private long defaultTimeoutMs = 120_000L;

    private long maxTimeoutMs = 300_000L;

    private int maxOutputChars = 200_000;
}
