export const LOCALIZATION_MODULE = "localization" as const;

/**
 * 只格式化时间点，业务日期字符串不得传入此接口做时区偏移。
 * @param value 含 Z 或偏移的 ISO 时间点；无效输入统一显示破折号
 * @param language 当前界面语言，zh/zh-CN 为中文，其他回退英文
 * @param timeZone IANA 展示时区，缺省 Asia/Shanghai，无效值回退 UTC
 */
export function formatInstant(value: string, language: string, timeZone = "Asia/Shanghai"): string {
  if (!/(?:Z|[+-]\d{2}:\d{2})$/.test(value)) return "—";
  const date = new Date(value); if (!Number.isFinite(date.getTime())) return "—";
  const locale = language.startsWith("zh") ? "zh-CN" : "en";
  try { return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "medium", timeZone }).format(date); }
  catch { return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "medium", timeZone: "UTC" }).format(date); }
}

/**
 * 保留服务端十进制原值展示金额，不使用 Number/浮点重算、不折算币种。
 * @param value 服务端规范十进制字符串，支持负数和最多 18 位小数；不接受指数、千分位或非数字
 * @param currency 原始 ISO 4217 三位币种，展示必须保留原始业务币种，不能用偏好覆盖
 * @param language 中文或英文，当前两种均用逗号分组和小数点
 * @return 币种和精确分组金额；不四舍五入、不补造业务精度，无效值返回破折号
 */
export function formatMoney(value: string, currency: string, language: string): string {
  if (!/^-?(?:0|[1-9]\d*)(?:\.\d{1,18})?$/.test(value) || !/^[A-Z]{3}$/.test(currency)) return "—";
  try { new Intl.NumberFormat(language.startsWith("zh") ? "zh-CN" : "en", { style: "currency", currency }); }
  catch { return "—"; }
  const negative = value.startsWith("-"); const parts = (negative ? value.slice(1) : value).split(".");
  const integer = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return `${currency} ${negative ? "-" : ""}${integer}${parts.length === 2 ? "." + parts[1] : ""}`;
}
