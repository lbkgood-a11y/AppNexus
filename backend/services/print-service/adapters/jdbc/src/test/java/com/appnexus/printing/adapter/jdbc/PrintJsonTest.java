package com.appnexus.printing.adapter.jdbc;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 发布边界验证：不同上游扩展入口都不能绕过服务端模板检查。 */
class PrintJsonTest {
  private final JsonMapper mapper=JsonMapper.builder().build();
  private final PrintJson json=new PrintJson(mapper);

  @Test void rejectsAlternateExecutableExtensions() {
    assertThatThrownBy(()->json.template(mapper.readTree("{\"paperNumberFormat\":\"${globalThis}\"}")))
        .hasMessage("PRINT_TEMPLATE_EXECUTION_FORBIDDEN");
    assertThat(json.template(mapper.readTree("{\"paperNumberFormat\":\"paperNo / paperCount\"}"))).contains("paperNo");
    for(String key:new String[]{"formatter","styler","script","rowsColumnsMerge","onClick","html","tableStyler"}) {
      assertThatThrownBy(()->json.template(mapper.createObjectNode().put(key,"return 1")))
          .hasMessage("PRINT_TEMPLATE_EXECUTION_FORBIDDEN");
    }
    assertThatThrownBy(()->json.template(mapper.readTree("{\"type\":\"html\"}")))
        .hasMessage("PRINT_TEMPLATE_EXECUTION_FORBIDDEN");
  }

  @Test void acceptsEmptyUpstreamCallbacksAndNormalOptions() {
    assertThat(json.template(mapper.readTree("{\"formatter\":\"\",\"styler\":null,\"options\":{\"orientation\":\"portrait\"}}")))
        .contains("portrait");
  }

  @Test void rejectsDeepStructuresAndExternalResources() {
    var root=mapper.createObjectNode();var child=root;
    for(int i=0;i<42;i++) child=child.putObject("nested");
    assertThatThrownBy(()->json.template(root)).hasMessage("PRINT_JSON_TOO_DEEP");
    assertThatThrownBy(()->json.template(mapper.readTree("{\"src\":\"https://internal.invalid\"}")))
        .hasMessage("PRINT_EXTERNAL_RESOURCE_FORBIDDEN");
  }

  @Test void ordinaryBusinessDataIsNotTreatedAsExecutableTemplateOptions() {
    assertThat(json.snapshot(mapper.readTree("{\"online\":true,\"onCall\":\"值班\",\"htmlDescription\":\"原文\"}")))
        .contains("值班");
    assertThatThrownBy(()->json.snapshot(mapper.readTree("{\"__proto__\":{}}")))
        .hasMessage("PRINT_TEMPLATE_EXECUTION_FORBIDDEN");
  }
}
