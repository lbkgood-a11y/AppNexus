package com.appnexus.workflow.adapter.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** 工作流持久化JSON编解码，禁用多态反序列化；不加载模型声明的Java类型。 */
final class WorkflowJson {
  static final ObjectMapper MAPPER =
      new ObjectMapper()
          .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("WORKFLOW_JSON_INVALID");
    }
  }

  static <T> T read(String value, Class<T> type) {
    try {
      return MAPPER.readValue(value, type);
    } catch (Exception e) {
      throw new IllegalArgumentException("WORKFLOW_JSON_INVALID");
    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, String> map(String value) {
    return (Map<String, String>) (Map<?, ?>) read(value, Map.class);
  }
}
