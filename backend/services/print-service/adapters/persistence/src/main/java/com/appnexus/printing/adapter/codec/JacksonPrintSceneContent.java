package com.appnexus.printing.adapter.codec;

import com.appnexus.printing.application.PrintSceneContent;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** 固定场景JSON技术转换，保留原JsonMapper及PrintJson限制，不执行表达式或多态类型。 */
public final class JacksonPrintSceneContent implements PrintSceneContent {
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final PrintJson json = new PrintJson(mapper);

    public Object plain(Object value) {
        return value == null ? null : mapper.convertValue(value, Object.class);
    }

    public Object decode(String value) {
        return mapper.readValue(value, Object.class);
    }

    public String snapshot(Object value) {
        return json.snapshot(value == null ? null : mapper.valueToTree(value));
    }

    public String object(Object value) {
        return json.object(value == null ? null : mapper.valueToTree(value));
    }
}
