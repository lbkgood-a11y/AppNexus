import { describe, expect, it } from "vitest";
import { copyTemplate, createBlankTemplate } from "./template";

describe("HiPrint 接入模板边界", () => {
  it("页码只允许固定占位符，拒绝模板字符串表达式",()=>{
    expect(()=>copyTemplate({panels:[{paperNumberFormat:"paperNo / paperCount"}]})).not.toThrow();
    expect(()=>copyTemplate({panels:[{paperNumberFormat:"${fetch('https://example.invalid')}"}]})).toThrow();
  });
  it("复制模板且保留原生 options 和尺寸", () => {
    const input = createBlankTemplate();
    input.panels[0].options = { orientation: "portrait" };
    const copy = copyTemplate(input);
    copy.panels[0].width = 100;
    expect(input.panels[0].width).toBe(210);
    expect(copy.panels[0].options).toEqual({ orientation: "portrait" });
  });
  it.each(["formatter", "styler", "groupFooterFormatter", "rowsColumnsMerge", "onImageChooseClick"])("拒绝嵌套扩展 %s", key => {
    expect(() => copyTemplate({ panels: [{ printElements: [{ options: { [key]: "function(){return 1}" } }] }] }))
      .toThrow("PRINT_TEMPLATE_EXTENSION_DISABLED");
  });
  it("拒绝 HTML 元素、非 JSON 和错误结构", () => {
    expect(() => copyTemplate({ panels: [{ printElementType: { type: "html" } }] })).toThrow();
    expect(() => copyTemplate({ panels: [], extra: () => {} })).toThrow();
    expect(() => copyTemplate({ panels: [null] })).toThrow();
    expect(() => copyTemplate(JSON.parse('{"panels":[],"__proto__":{}}'))).toThrow();
  });
});
