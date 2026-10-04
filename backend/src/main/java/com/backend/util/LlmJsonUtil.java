package com.backend.util;

/** LLM 响应 JSON 提取工具（P2-3）：剥离 markdown 代码块包裹，截取最外层 {...}。 */
public final class LlmJsonUtil {

    private LlmJsonUtil() {}

    /** 去除 LLM 响应中的 ``` 包裹与 JSON 前后杂讯（逻辑同 IceBreakTools.stripMarkdown）。 */
    public static String stripMarkdown(String response) {
        if (response == null) {
            return "";
        }
        String s = response.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start >= 0 && end > start) {
                s = s.substring(start + 1, end).trim();
            }
        }
        if (!s.startsWith("{")) {
            int l = s.indexOf('{');
            int r = s.lastIndexOf('}');
            if (l >= 0 && r > l) {
                s = s.substring(l, r + 1);
            }
        }
        return s;
    }
}
