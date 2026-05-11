package org.wwz.ai.domain.agent.reactor.util;

import java.util.Objects;
import java.util.UUID;

public class ChineseCharacterCounter {
    public static boolean isChineseCharacter(char ch) {
        // 中文字符的 Unicode 范围为 \u4E00 到 \u9FA5
        return ch >= '\u4E00' && ch <= '\u9FA5';
    }

    public static boolean hasChineseCharacters(String str) {
        if (Objects.nonNull(str)) {
            for (int i = 0; i < str.length(); i++) {
                if (isChineseCharacter(str.charAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        String testString = "贸正促销大发送咚咚噶十多个";
        boolean hsChinese = hasChineseCharacters(testString);
        if (hsChinese) {
            System.out.println(UUID.randomUUID().toString().replaceAll("-", ""));
        }
        System.out.println("字符串转换，耗时：" + (System.currentTimeMillis() - start));
    }
}
