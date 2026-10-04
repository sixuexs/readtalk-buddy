package com.backend.client;

import com.backend.model.AsrSentence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 阿里云百炼「非实时语音识别」同步版客户端 —— qwen-audio-3.1-asr-flash。
 *
 * <p>关键约束（依据官方 HTTP API 文档）：
 * <ul>
 *   <li>同步调用走 multimodal-generation/generation，一次请求直接返回，无需提交-轮询。</li>
 *   <li>音频可直接以 Base64 Data URI 传入 → <b>免 OSS、免公网 URL</b>。</li>
 *   <li>说话人分离参数为 {@code speaker_diarization_enabled}（仅 3.1-asr-flash 支持）；
 *       开启后按 {@code output.sentences[]} 读取，每句带 {@code speaker_id}。</li>
 *   <li>音频 ≤5 分钟；Base64 编码后 ≤10MB → 录音必须用 mp3/aac 压缩格式，禁止 wav/pcm。</li>
 * </ul>
 *
 * <p>未配置 api-key/base-url（或 mock=true）时进入 Mock 模式返回示例分句，
 * 避免因缺少密钥阻断整条链路（便于前端联调与演示）。
 */
@Component
public class AliyunAsrClient {

    private static final Logger log = LoggerFactory.getLogger(AliyunAsrClient.class);

    private static final String SUBMIT_PATH = "/api/v1/services/aigc/multimodal-generation/generation";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final boolean mock;
    private final long timeoutSeconds;

    public AliyunAsrClient(@Value("${aliyun.asr.base-url:}") String baseUrl,
                           @Value("${aliyun.asr.api-key:}") String apiKey,
                           @Value("${aliyun.asr.model:qwen-audio-3.1-asr-flash}") String model,
                           @Value("${aliyun.asr.mock:false}") boolean mock,
                           @Value("${aliyun.asr.timeout-seconds:60}") long timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.mock = mock;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Mock 模式判定：显式开关，或缺 base-url / api-key。 */
    public boolean isMock() {
        return mock
                || apiKey == null || apiKey.isBlank()
                || baseUrl == null || baseUrl.isBlank();
    }

    /**
     * 同步转写一段音频（开启说话人分离）。
     *
     * @param audio      音频字节（内存持有，调用方负责不落盘、用毕释放）
     * @param format     音频格式：mp3 / wav / aac ...
     * @param sampleRate 采样率（Hz）
     * @return 分句结果（含 speakerId，可能为 null）
     */
    public List<AsrSentence> transcribe(byte[] audio, String format, Integer sampleRate) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("音频内容为空");
        }
        if (isMock()) {
            log.warn("AliyunAsrClient 处于 Mock 模式（未配置 aliyun.asr.api-key/base-url 或 mock=true），返回示例转写结果");
            return mockSentences();
        }

        String fmt = normalizeFormat(format);
        int rate = sampleRate == null || sampleRate <= 0 ? 16000 : sampleRate;
        String dataUri = "data:" + mimeOf(fmt) + ";base64," + Base64.getEncoder().encodeToString(audio);
        String body = buildRequestBody(dataUri, fmt, rate);

        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return doCall(body);
            } catch (RuntimeException e) {
                last = e;
                log.warn("ASR 调用失败（第 {} 次）：{}", attempt, e.getMessage());
            }
        }
        throw last == null ? new RuntimeException("ASR 调用失败") : last;
    }

    // ──── 内部实现 ────

    private String buildRequestBody(String dataUri, String format, int sampleRate) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ObjectNode input = root.putObject("input");
        ArrayNode messages = input.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ArrayNode content = userMsg.putArray("content");
        ObjectNode audioItem = content.addObject();
        audioItem.put("type", "input_audio");
        audioItem.putObject("input_audio").put("data", dataUri);

        ObjectNode params = root.putObject("parameters");
        params.put("format", format);
        params.put("sample_rate", String.valueOf(sampleRate));
        // 说话人分离（3.1-asr-flash 专用）。开启后 keep_dialect / language_hints 不生效。
        params.put("speaker_diarization_enabled", true);

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构建 ASR 请求体失败：" + e.getMessage(), e);
        }
    }

    private List<AsrSentence> doCall(String body) {
        String url = stripTrailingSlash(baseUrl) + SUBMIT_PATH;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("X-DashScope-SSE", "disable")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ASR 请求被中断", e);
        } catch (Exception e) {
            throw new RuntimeException("ASR 网络异常：" + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("ASR 返回状态 " + response.statusCode() + "：" + brief(response.body()));
        }

        try {
            JsonNode root = mapper.readTree(response.body());
            if (root.hasNonNull("code")) {
                throw new RuntimeException("ASR 错误 " + root.path("code").asText()
                        + "：" + root.path("message").asText(""));
            }
            return parseSentences(root);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("ASR 响应解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解析识别结果（包级可见便于单测）。
     *
     * <p>官方两份文档对同步接口的返回形状描述不一致，此处一并兼容：
     * <ul>
     *   <li>API 参考：{@code output.sentences[]}（开启说话人分离时）/ {@code output.sentence} / {@code output.text}</li>
     *   <li>用户指南：实际返回 {@code output.output.sentence.text} 与 {@code output.output.text}（多一层嵌套）</li>
     * </ul>
     */
    static List<AsrSentence> parseSentences(JsonNode root) {
        JsonNode out = root.path("output");
        // 兼容同步接口的嵌套返回：output.output.*
        JsonNode nested = out.path("output");
        if (!out.has("sentences") && !out.has("sentence") && !out.hasNonNull("text")
                && nested.isObject()) {
            out = nested;
        }
        List<AsrSentence> list = new ArrayList<>();

        JsonNode sentences = out.path("sentences");
        if (sentences.isArray()) {
            for (JsonNode s : sentences) {
                AsrSentence item = toSentence(s);
                if (item != null) {
                    list.add(item);
                }
            }
        }
        if (list.isEmpty()) {
            JsonNode single = out.path("sentence");
            if (single.isObject()) {
                AsrSentence item = toSentence(single);
                if (item != null) {
                    list.add(item);
                }
            }
        }
        if (list.isEmpty() && out.hasNonNull("text")) {
            String text = out.get("text").asText("").trim();
            if (!text.isEmpty()) {
                list.add(new AsrSentence(null, text, null, null));
            }
        }
        return list;
    }

    static AsrSentence toSentence(JsonNode s) {
        String text = s.path("text").asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        Integer speakerId = s.hasNonNull("speaker_id") ? s.get("speaker_id").asInt() : null;
        Long begin = s.hasNonNull("begin_time") ? s.get("begin_time").asLong() : null;
        Long end = s.hasNonNull("end_time") ? s.get("end_time").asLong() : null;
        return new AsrSentence(speakerId, text, begin, end);
    }

    /** Mock 示例：一段两人对话，用于无密钥时跑通全链路。 */
    private List<AsrSentence> mockSentences() {
        List<AsrSentence> list = new ArrayList<>();
        list.add(new AsrSentence(0, "你最近在读什么书呀？我最近在看一本讲社会心理学的。", 600L, 4200L));
        list.add(new AsrSentence(1, "哦，我最近比较忙，没怎么看。", 4400L, 6400L));
        list.add(new AsrSentence(0, "听起来你最近挺累的，是工作上事情多吗？", 6600L, 9200L));
        list.add(new AsrSentence(1, "嗯，差不多吧，就是那个项目一直延期。", 9400L, 11800L));
        list.add(new AsrSentence(0, "延期确实挺磨人的。那你一般怎么放松？", 12000L, 14200L));
        return list;
    }

    private static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "mp3";
        }
        String f = format.toLowerCase().trim();
        if (f.startsWith(".")) {
            f = f.substring(1);
        }
        return f;
    }

    private static String mimeOf(String format) {
        return switch (format) {
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "aac" -> "audio/aac";
            case "m4a" -> "audio/mp4";
            case "opus" -> "audio/opus";
            case "flac" -> "audio/flac";
            default -> "audio/mpeg";
        };
    }

    private static String stripTrailingSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String brief(String body) {
        if (body == null) {
            return "";
        }
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
