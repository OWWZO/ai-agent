package org.wwz.ai.domain.agent.runtime.util;

import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {
    private static final String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMBER = "0123456789";
    private static final String DATA_FOR_RANDOM_STRING = CHAR_LOWER + NUMBER;
    private static final SecureRandom random = new SecureRandom();
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9\\._%\\+\\-]+@[a-zA-Z0-9\\.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern ID_PATTERN = Pattern.compile("(?:[^\\dA-Za-z_]|^)((?:[1-6][1-7]|50|71|81|82)\\d{4}(?:19|20)\\d{2}(?:0[1-9]|10|11|12)(?:[0-2][1-9]|10|20|30|31)\\d{3}[0-9Xx])(?:[^\\dA-Za-z_]|$)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:[^\\dA-Za-z_]|^)(1[3456789]\\d{9})(?:[^\\dA-Za-z_]|$)");
    private static final Pattern BANKCARD_PATTERN = Pattern.compile("(?:[^\\dA-Za-z_]|^)(62(?:\\d{14}|\\d{17}))(?:[^\\dA-Za-z_]|$)");

    public static String generateRandomString(int length) {
        if (length < 1) throw new IllegalArgumentException();

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            // 生成 0 到 DATA_FOR_RANDOM_STRING 长度之间的随机索引
            int rndCharAt = random.nextInt(DATA_FOR_RANDOM_STRING.length());
            char rndChar = DATA_FOR_RANDOM_STRING.charAt(rndCharAt);
            sb.append(rndChar);
        }
        return sb.toString();
    }

    // 银行卡Luhn校验算法
    private static boolean luhnBankCardVerify(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    public static String textDesensitization(String content, Map<String, String> sensitivePatternsMapping) {
        // 邮箱地址脱敏
        Matcher emailMatcher = EMAIL_PATTERN.matcher(content);
        while (emailMatcher.find()) {
            String snippet = emailMatcher.group();
            int maskIdx = snippet.indexOf("@");
            // 内部邮箱不处理
            if (content.contains("@jd.com")) {
                continue;
            }
            content = content.replace(snippet, snippet.substring(0, maskIdx) + "＠" + snippet.substring(maskIdx + 1));
        }

        // 身份证号脱敏
        Matcher idMatcher = ID_PATTERN.matcher(content);
        while (idMatcher.find()) {
            String snippet = idMatcher.group(1);
            content = content.replace(snippet, snippet.substring(0, 12) + "✿✿✿✿✿✿");
        }

        // 手机号脱敏
        Matcher phoneMatcher = PHONE_PATTERN.matcher(content);
        while (phoneMatcher.find()) {
            String snippet = phoneMatcher.group(1);
            content = content.replace(snippet, snippet.substring(0, 3) + "✿✿✿✿" + snippet.substring(7));
        }

        // 银行卡号脱敏
        Matcher bankcardMatcher = BANKCARD_PATTERN.matcher(content);
        while (bankcardMatcher.find()) {
            String snippet = bankcardMatcher.group(1);
            if (luhnBankCardVerify(snippet)) {
                content = content.replace(snippet, snippet.substring(0, 12) + "✿✿✿✿✿✿");
            }
        }

        // 密码及其他敏感词脱敏
        for (Map.Entry<String, String> entry : sensitivePatternsMapping.entrySet()) {
            String pattern = entry.getKey();
            String wordMapping = entry.getValue();

            int startIndex = pattern.indexOf("^)") + 2;
            int endIndex = pattern.lastIndexOf("[^");

            if (startIndex + 1 < endIndex) {
                String sensitiveWord = pattern.substring(startIndex, endIndex);
                Pattern sensitivePattern = Pattern.compile(pattern);
                Matcher sensitiveMatcher = sensitivePattern.matcher(content);

                while (sensitiveMatcher.find()) {
                    String snippet = sensitiveMatcher.group();
                    if (content.startsWith(sensitiveWord)) {
                        content = content.replace(snippet, wordMapping + snippet.substring(snippet.length() - 1));
                    } else {
                        content = content.replace(snippet, snippet.charAt(0) + wordMapping + snippet.substring(snippet.length() - 1));
                    }
                }
            } else {
                content = content.replace(pattern, wordMapping);
            }
        }

        return content;
    }

    public static String removeSpecialChars(String input) {
        if (Objects.isNull(input) || input.isEmpty()) {
            return "";
        }
        // 定义需要过滤的特殊字符集合
        String specialChars = " \"&$@=;+?\\{^}%~[]<>#|'";
        Set<Character> specialCharsSet = new HashSet<>();
        for (char c : specialChars.toCharArray()) {
            specialCharsSet.add(c);
        }
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (!specialCharsSet.contains(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 截断文本到指定最大长度，支持空白字符归一化。
     *
     * @param text      原始文本，允许 null 或空串
     * @param maxLength 最大长度（字符数），必须 > 0
     * @param normalize 是否先将空白字符归一化为单个空格
     * @return 截断后的文本，若输入为 null/空则返回空串
     */
    public static String abbreviate(String text, int maxLength, boolean normalize) {
        if (!org.springframework.util.StringUtils.hasText(text)) {
            return "";
        }
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive, got: " + maxLength);
        }
        String result = text.trim();
        if (normalize) {
            result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ");
        }
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    /**
     * 截断文本到指定最大长度（不归一化空白）。
     */
    public static String abbreviate(String text, int maxLength) {
        return abbreviate(text, maxLength, false);
    }

    public static String getUUID() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    /**
     * 返回参数中第一个非空白字符串，没找到则返回 null。
     */
    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (org.springframework.util.StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public static void main(String[] args) {

        System.out.println(getUUID());

        /*String name = "123 $ 456 %%% ^ ";
        System.out.println(removeSpecialChars(name));

        name = null;
        System.out.println(">>" + removeSpecialChars(name) + "<<");

        Map<String, String> patterns = new HashMap<>();
        patterns.put("(?:[^A-Za-z0-9_-]|^)password[^A-Za-z0-9_-]", "PASSWORD");
        patterns.put("(?:[^A-Za-z0-9_-]|^)asd[^A-Za-z0-9_-]", "ASD");

        String testContent = "asd 我的邮箱是test@example.com，身份证号是510104199001011234，手机号是13800138000，银行卡号是6226327514303272，哈哈password:::admin123 asd";
        String result = textDesensitization(testContent, patterns);
        System.out.println(result);*/
    }
}
