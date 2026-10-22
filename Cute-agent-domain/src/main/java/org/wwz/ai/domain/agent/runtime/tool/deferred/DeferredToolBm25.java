package org.wwz.ai.domain.agent.runtime.tool.deferred;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deferred catalog 的轻量 BM25 实现。
 */
final class DeferredToolBm25 {

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final Pattern TOKEN_RE = Pattern.compile("[A-Za-z0-9]+|[\\u4e00-\\u9fff]+");

    private DeferredToolBm25() {
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Matcher matcher = TOKEN_RE.matcher(text);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String raw = matcher.group();
            if (raw.isEmpty()) {
                continue;
            }
            if (isHan(raw.charAt(0))) {
                tokens.add(raw);
                if (raw.length() >= 2) {
                    for (int i = 0; i < raw.length() - 1; i++) {
                        tokens.add(raw.substring(i, i + 2));
                    }
                }
            } else {
                tokens.add(raw.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }

    static double score(List<String> queryTokens,
                        List<String> docTokens,
                        double avgDl,
                        Map<String, Integer> docFreq,
                        int nDocs) {
        if (queryTokens.isEmpty() || docTokens.isEmpty()) {
            return 0.0;
        }
        Map<String, Integer> tf = new HashMap<>();
        for (String token : docTokens) {
            tf.merge(token, 1, Integer::sum);
        }
        double dl = docTokens.size();
        double score = 0.0;
        for (String query : queryTokens) {
            int df = docFreq.getOrDefault(query, 0);
            if (df == 0) {
                continue;
            }
            int termFrequency = tf.getOrDefault(query, 0);
            if (termFrequency == 0) {
                continue;
            }
            double idf = Math.log(1.0 + (nDocs - df + 0.5) / (df + 0.5));
            double norm = termFrequency * (K1 + 1.0)
                    / (termFrequency + K1 * (1.0 - B + B * dl / Math.max(avgDl, 1.0)));
            score += idf * norm;
        }
        return score;
    }

    static Map<String, Integer> documentFrequency(List<List<String>> docs) {
        Map<String, Integer> df = new HashMap<>();
        for (List<String> doc : docs) {
            Set<String> seen = new HashSet<>(doc);
            for (String token : seen) {
                df.merge(token, 1, Integer::sum);
            }
        }
        return df;
    }

    static double averageLength(List<List<String>> docs) {
        if (docs.isEmpty()) {
            return 0.0;
        }
        int total = 0;
        for (List<String> doc : docs) {
            total += doc.size();
        }
        return (double) total / docs.size();
    }

    private static boolean isHan(char ch) {
        return ch >= '\u4e00' && ch <= '\u9fff';
    }
}
