package com.dxy.homework.eval;

import java.util.Locale;

/**
 * 比对用的文本规范化。
 *
 * <p>目的：让"学生答案"和"标注答案"的比对只关注答案本身，不被格式差异干扰。
 * 例如 {@code "２６８"}、{@code "268 "}、{@code "268。"} 都应视为同一个答案。
 */
public final class AnswerNormalizer {

    private AnswerNormalizer() {
    }

    /** 全角转半角 + 去空白 + 去尾部句读 + 转小写。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u3000' || Character.isWhitespace(c)) {
                continue;
            }
            if (c >= '\uFF01' && c <= '\uFF5E') {
                c = (char) (c - 0xFEE0);
            }
            sb.append(c);
        }
        return sb.toString().replaceAll("[.。,，;；、:：]+$", "").toLowerCase(Locale.ROOT);
    }

    public static boolean equal(String a, String b) {
        return normalize(a).equals(normalize(b));
    }
}
