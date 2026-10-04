package com.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 沟通辅助 PII 脱敏单测（无 LLM、无 Spring 上下文）。
 *
 * <p>重点回归：19 位银行卡不得被 18 位身份证模式截断匹配。
 */
class TextMaskUtilTest {

    @Test
    void masksPhoneKeepingSurroundingText() {
        assertEquals("我的号码是[手机号]，你记一下",
                TextMaskUtil.mask("我的号码是13812345678，你记一下"));
    }

    @Test
    void masksEighteenDigitIdCard() {
        assertEquals("身份证[身份证]",
                TextMaskUtil.mask("身份证110101199003074512"));
    }

    @Test
    void masksIdCardEndingWithX() {
        assertEquals("证件[身份证]",
                TextMaskUtil.mask("证件11010119900307451X"));
    }

    @Test
    void nineteenDigitBankCardNotTruncatedAsIdCard() {
        // 曾经的缺陷：18 位身份证模式会吃掉前 18 位，残留一位数字
        assertEquals("卡号[卡号]",
                TextMaskUtil.mask("卡号6222021234567890123"));
    }

    @Test
    void masksSixteenDigitBankCard() {
        assertEquals("[卡号]", TextMaskUtil.mask("6222021234567890"));
    }

    @Test
    void masksEmail() {
        assertEquals("邮箱[邮箱] 收到",
                TextMaskUtil.mask("邮箱zhang.san+test@example.com 收到"));
    }

    @Test
    void leavesNormalConversationUntouched() {
        String s = "我觉得这本书讲得挺有意思的，你怎么看？2026年读完了3本。";
        assertEquals(s, TextMaskUtil.mask(s));
    }

    @Test
    void handlesNullAndEmpty() {
        assertNull(TextMaskUtil.mask(null));
        assertEquals("", TextMaskUtil.mask(""));
    }
}
