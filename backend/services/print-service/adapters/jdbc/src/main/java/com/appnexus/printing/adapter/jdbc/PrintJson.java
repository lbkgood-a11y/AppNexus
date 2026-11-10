package com.appnexus.printing.adapter.jdbc;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.*;

/** 模板与数据快照 JSON 校验；禁止可执行键、外部资源地址和超出持久化上限的输入。 */
final class PrintJson {
  private static final int MAX_TEMPLATE = 2_000_000;
  private static final int MAX_SNAPSHOT = 5_000_000;
  private final ObjectMapper mapper;

  PrintJson(ObjectMapper mapper) { this.mapper = mapper; }

  String template(JsonNode node) { return validate(node, MAX_TEMPLATE, true); }
  String snapshot(JsonNode node) { return validate(node, MAX_SNAPSHOT, false); }
  String object(JsonNode node) { return validate(node == null ? mapper.createObjectNode() : node, 100_000, true); }

  private String validate(JsonNode node, int maximum, boolean rejectUrls) {
    if (node == null || !node.isObject()) throw new PrintFailure("PRINT_JSON_OBJECT_REQUIRED", 400);
    inspect(node, rejectUrls);
    try {
      String value = mapper.writeValueAsString(node);
      if (value.length() > maximum) throw new PrintFailure("PRINT_JSON_TOO_LARGE", 413);
      return value;
    } catch (JacksonException e) {
      throw new PrintFailure("PRINT_JSON_INVALID", 400);
    }
  }

  private void inspect(JsonNode node, boolean rejectUrls) {
    if (node.isObject()) {
      for (var property : node.properties()) {
        String name = property.getKey();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("script") || lower.equals("formatter") || lower.equals("function") || lower.startsWith("on"))
          throw new PrintFailure("PRINT_TEMPLATE_EXECUTION_FORBIDDEN", 400);
        inspect(property.getValue(), rejectUrls);
      }
    } else if (node.isArray()) node.forEach(value -> inspect(value, rejectUrls));
    else if (rejectUrls && node.isTextual() && node.textValue().matches("(?i)\\s*(https?|file|javascript|data):.*"))
      throw new PrintFailure("PRINT_EXTERNAL_RESOURCE_FORBIDDEN", 400);
  }
}
