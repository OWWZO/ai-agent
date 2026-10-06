package org.wwz.ai.types.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "reactor.opencli")
public class OpenCliProperties {

    private boolean enabled = true;

    private String command = "opencli";

    private List<String> prefixArgs = new ArrayList<>();

    private List<String> extraArgs = new ArrayList<>(List.of("-f", "json"));

    private long defaultTimeoutMs = 180_000L;

    private long maxTimeoutMs = 540_000L;

    private int maxOutputChars = 200_000;

    private String cacheDir = "";

    private String relayUrl = "";
}
