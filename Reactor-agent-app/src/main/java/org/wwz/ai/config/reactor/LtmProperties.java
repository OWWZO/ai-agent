package org.wwz.ai.config.reactor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "memory.ltm")
public class LtmProperties {

    private boolean enabled = true;
    private String provider = "none";
    /** OpenViking 可选远程 endpoint；空则仅本地桶 */
    private String openvikingEndpoint = "";
    private long prefetchTimeoutMs = 8000L;
    private int flushMinTurns = 6;
    private Curated curated = new Curated();
    private WriteApproval writeApproval = new WriteApproval();
    private Flush flush = new Flush();
    private BackgroundReview backgroundReview = new BackgroundReview();
    private SessionSearch sessionSearch = new SessionSearch();

    @Data
    public static class Curated {
        private int charLimit = 2200;
        private int userCharLimit = 1375;
    }

    @Data
    public static class WriteApproval {
        private boolean enabled = false;
    }

    @Data
    public static class Flush {
        /** 压前独立 flush 小回合（LLM 抽取写 curated） */
        private boolean enabled = true;
        private long timeoutSeconds = 25L;
        private int materialMaxMessages = 24;
        private int materialMaxCharsPerMsg = 800;
    }

    @Data
    public static class BackgroundReview {
        /** Hermes default: periodic post-turn review (not only on compact). */
        private boolean enabled = true;
        private int nudgeInterval = 10;
        private long timeoutSeconds = 60L;
        private int materialMaxChars = 1200;
    }

    @Data
    public static class SessionSearch {
        private boolean enabled = true;
    }
}
