/**
 * 将平台 JSON 消息转换为无响应式代理的独立对象，供 opaque iframe 结构化克隆。
 * @param value 仅允许内部 JSON 数据；对象内 undefined 字段按 JSON 约定省略。
 * @returns 可由 postMessage 克隆的消息副本；不改变字段协议。
 * @throws Error 非 JSON 数据或循环引用无法序列化时拒绝发送。
 */
export function messagePayload<T>(value:T):T {
  return JSON.parse(JSON.stringify(value)) as T;
}
