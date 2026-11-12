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
    inspect(node, rejectUrls, 0);
    try {
      String value = mapper.writeValueAsString(node);
      if (value.length() > maximum) throw new PrintFailure("PRINT_JSON_TOO_LARGE", 413);
      return value;
    } catch (JacksonException e) {
      throw new PrintFailure("PRINT_JSON_INVALID", 400);
    }
  }

  /** 模板限制与浏览器适配保持一致；深度最多 40 层，防止嵌套 JSON 耗尽服务线程栈。 */
  private void inspect(JsonNode node, boolean rejectUrls, int depth) {
    if(depth>40) throw new PrintFailure("PRINT_JSON_TOO_DEEP",400);
    if (node.isObject()) {
      for (var property : node.properties()) {
        String name = property.getKey();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        // 不允许通过非 formatter 名称绕过发布检查；空回调为上游原生默认值，不具备执行内容。
        boolean callback=lower.matches(".*(formatter|styler|script|html|rowscolumnsmerge).*")
            ||lower.equals("function")||name.matches("on[A-Z].*");
        JsonNode value=property.getValue();
        // HiPrint 页码使用模板字符串求值；仅接受数字、分隔符和固定页码占位符。
        if(rejectUrls&&name.equals("paperNumberFormat")&&!value.isNull()
            &&(!value.isTextual()||!value.textValue().isEmpty()&&!value.textValue().matches("[- /0-9]*(?:paperNo|paperCount)(?:[- /0-9]|paperNo|paperCount)*")))
          throw new PrintFailure("PRINT_TEMPLATE_EXECUTION_FORBIDDEN",400);
        boolean empty=value.isNull()||value.isTextual()&&value.textValue().isEmpty();
        if (rejectUrls&&callback&&!empty)
          throw new PrintFailure("PRINT_TEMPLATE_EXECUTION_FORBIDDEN", 400);
        if(lower.equals("__proto__")||lower.equals("constructor")||lower.equals("prototype")
            ||rejectUrls&&name.equals("type")&&value.isTextual()&&value.textValue().equalsIgnoreCase("html"))
          throw new PrintFailure("PRINT_TEMPLATE_EXECUTION_FORBIDDEN",400);
        inspect(value, rejectUrls, depth+1);
      }
    } else if (node.isArray()) node.forEach(value -> inspect(value, rejectUrls, depth+1));
    else if (rejectUrls && node.isTextual() && node.textValue().matches("(?i)\\s*(https?|file|javascript|data):.*"))
      throw new PrintFailure("PRINT_EXTERNAL_RESOURCE_FORBIDDEN", 400);
  }
}
