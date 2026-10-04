package com.backend.client;

import com.backend.model.AsrSentence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ASR 响应解析单测（无 HTTP、无 Spring 上下文）。
 *
 * <p>背景：阿里云官方「API 参考」与「用户指南」对同步接口返回形状描述不一致，
 * 本测试同时锁住「单层 output.*」与「嵌套 output.output.*」两种形状，
 * 避免真机联调时因形状差异静默拿到空转写。
 */
class AliyunAsrParseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<AsrSentence> parse(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        return AliyunAsrClient.parseSentences(root);
    }

    @Test
    void parsesDiarizedSentencesWithSpeakerId() throws Exception {
        String json = """
                {"output":{"sentences":[
                  {"begin_time":100,"end_time":3820,"text":"你好，我们今天讨论项目进度。","speaker_id":0},
                  {"begin_time":3820,"end_time":6500,"text":"好的，我先汇报一下。","speaker_id":1}
                ]},"request_id":"r1"}""";

        List<AsrSentence> list = parse(json);
        assertEquals(2, list.size());
        assertEquals(0, list.get(0).getSpeakerId().intValue());
        assertEquals("你好，我们今天讨论项目进度。", list.get(0).getText());
        assertEquals(100L, list.get(0).getBeginTime().longValue());
        assertEquals(1, list.get(1).getSpeakerId().intValue());
    }

    @Test
    void parsesNestedOutputOutputShapeFromUserGuide() throws Exception {
        // 用户指南描述的同步接口实际返回形状（多一层 output 嵌套）
        String json = """
                {"output":{"output":{"sentence":{"text":"Hello World，这里是阿里巴巴语音实验室。"}},
                           "text":"Hello World，这里是阿里巴巴语音实验室。"},
                 "request_id":"r2"}""";

        List<AsrSentence> list = parse(json);
        assertEquals(1, list.size());
        assertEquals("Hello World，这里是阿里巴巴语音实验室。", list.get(0).getText());
        assertNull(list.get(0).getSpeakerId());
    }

    @Test
    void fallsBackToSingleSentenceObject() throws Exception {
        String json = """
                {"output":{"sentence":{"begin_time":760,"end_time":3800,"text":"识别到一句话。"}}}""";

        List<AsrSentence> list = parse(json);
        assertEquals(1, list.size());
        assertEquals("识别到一句话。", list.get(0).getText());
        assertNull(list.get(0).getSpeakerId());
    }

    @Test
    void fallsBackToPlainText() throws Exception {
        String json = """
                {"output":{"text":"只有整段文本。"}}""";

        List<AsrSentence> list = parse(json);
        assertEquals(1, list.size());
        assertEquals("只有整段文本。", list.get(0).getText());
    }

    @Test
    void nullSpeakerIdToleratedAndBlankSentencesSkipped() throws Exception {
        String json = """
                {"output":{"sentences":[
                  {"text":"未识别说话人的句子","speaker_id":null},
                  {"text":"   ","speaker_id":0}
                ]}}""";

        List<AsrSentence> list = parse(json);
        assertEquals(1, list.size());
        assertNull(list.get(0).getSpeakerId());
        assertEquals("未识别说话人的句子", list.get(0).getText());
    }

    @Test
    void emptyOutputGivesEmptyListNotException() throws Exception {
        assertTrue(parse("{\"output\":{}}").isEmpty());
        assertTrue(parse("{}").isEmpty());
    }
}
