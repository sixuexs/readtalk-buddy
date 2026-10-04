package com.backend.util;

import java.util.regex.Pattern;

/**
 * 轻量 PII 脱敏（沟通辅助复盘版）。
 *
 * <p>在转写文本进入 LLM 与落库前，用正则打码高置信度敏感信息，
 * 降低个人信息外泄面（手机号 / 身份证 / 银行卡 / 邮箱）。
 *
 * <p>边界：仅正则可判定的强模式，不做 NER 人名识别（后者列为后续工作）；
 * 打码后不影响五维评分语义（这些片段本身不参与评分判据）。
 *
 * <p>所有数字类模式均带 {@code (?<!\d)} / {@code (?!\d)} 边界断言：
 * 否则 19 位银行卡会被 18 位身份证模式截断匹配成「[身份证]3」。
 */
public final class TextMaskUtil {

    private TextMaskUtil() {}

    /** 邮箱（须先于数字类匹配，避免被拆散）。 */
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    /** 身份证：18 位，末位可为 X；前后不得再接数字。 */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    /** 银行卡：16~19 位连续数字；前后不得再接数字。 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");
    /** 手机号：1 开头 11 位；前后不得再接数字。 */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** 打码敏感信息；null/空原样返回。 */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String s = EMAIL.matcher(text).replaceAll("[邮箱]");
        s = ID_CARD.matcher(s).replaceAll("[身份证]");
        s = BANK_CARD.matcher(s).replaceAll("[卡号]");
        s = PHONE.matcher(s).replaceAll("[手机号]");
        return s;
    }
}
