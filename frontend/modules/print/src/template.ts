/** 原生 HiPrint JSON 模板；仅表示序列化数据，不授予脚本、网络或文件访问权限。 */
export interface PrintTemplate {
  panels: Record<string, unknown>[];
}

/**
 * 检查基础结构并复制模板，拒绝已知脚本和 HTML 扩展入口。
 * 这是接入期防误用检查，不是完整的恶意模板安全验证；只接受应用内部受信任模板。
 * @param value 模板 JSON 对象，必须包含 panels 数组；不得传入函数、原型或循环引用。
 * @returns 与输入隔离的模板副本。
 * @throws Error 结构异常或包含本期禁止的扩展配置。
 */
export function copyTemplate(value: unknown): PrintTemplate {
  const walk = (node: unknown, depth: number): void => {
    if (depth > 40) throw new Error("PRINT_TEMPLATE_DEPTH");
    if (node === null || typeof node === "string" || typeof node === "boolean") return;
    if (typeof node === "number" && Number.isFinite(node)) return;
    if (Array.isArray(node)) {
      node.forEach(item => walk(item, depth + 1));
      return;
    }
    if (typeof node !== "object" || Object.getPrototypeOf(node) !== Object.prototype) {
      throw new Error("PRINT_TEMPLATE_JSON_ONLY");
    }
    for (const [key, item] of Object.entries(node)) {
      // 已知回调入口只允许空值；options、orientation 等普通字段不按子串误判。
      if ((/formatter|styler|script|html|rowscolumnsmerge/i.test(key) || /^on[A-Z]/.test(key))
          && item !== "" && item !== null && item !== undefined) {
        throw new Error("PRINT_TEMPLATE_EXTENSION_DISABLED");
      }
      if (["__proto__", "constructor", "prototype"].includes(key)) throw new Error("PRINT_TEMPLATE_KEY");
      if (key === "type" && item === "html") throw new Error("PRINT_TEMPLATE_EXTENSION_DISABLED");
      if (item !== undefined) walk(item, depth + 1);
    }
  };
  walk(value, 0);
  const result = value as Partial<PrintTemplate>;
  if (!Array.isArray(result.panels) || result.panels.some(panel => !panel || Array.isArray(panel) || typeof panel !== "object")) {
    throw new Error("PRINT_TEMPLATE_PANELS_REQUIRED");
  }
  const serialized = JSON.stringify(value);
  if (serialized.length > 1_000_000) throw new Error("PRINT_TEMPLATE_TOO_LARGE");
  return JSON.parse(serialized) as PrintTemplate;
}

/**
 * 创建 A4 空白草稿，纸张宽 210 mm、高 297 mm；布局坐标仍沿用 HiPrint 原生单位。
 * @returns 不包含示例业务数据和外部资源的初始模板。
 */
export function createBlankTemplate(): PrintTemplate {
  return { panels: [{ index: 0, width: 210, height: 297, paperType: "A4", printElements: [] }] };
}
