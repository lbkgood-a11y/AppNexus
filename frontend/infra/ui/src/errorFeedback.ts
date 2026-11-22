import { ElMessage } from "element-plus";
import { nextTick, type Ref, type VNode } from "vue";

/** 当前上下文的活动错误；仅保存在内存，不记录业务正文或身份。 */
const active = new Map<unknown, { close: () => void; owners: number }>();
/** 普通错误停留时间，单位毫秒；默认 5000，延长会增加遮挡时间。 */
const ERROR_DURATION = 5000;
/** 最大活动浮层数量，默认 3 个；超出时关闭最早的提示，避免遮住整个视口。 */
const MAX_ERRORS = 3;
/** 上下文清理的递增世代；阻止等待 Vue 更新的旧错误在切换后重新出现。 */
let contextGeneration = 0;
/** 每个错误状态的最后更新身份；并发更新仅允许最新一次完成。 */
const stateUpdates = new WeakMap<Ref<string>, symbol>();

/** 内容区加载失败的展示状态；文案由调用方按业务错误契约安全转换。 */
export interface LoadIssue {
  /** 已国际化并安全过滤的文案。 */
  message: string;
  /** 是否允许在内容区重试；无权时不提供重试。 */
  retryable: boolean;
}

/**
 * 为一次加载失败选择唯一出口。内容无法使用或无权时占用原数据区域，
 * 仍可使用旧数据时走统一浮层。独立操作的反馈继续使用 setErrorFeedback。
 * @param floating 当前页面的浮层状态；内容区反馈不覆盖独立操作已发布的提示。
 * @param inline 当前加载区域的结果状态；开始下一次加载时由调用方清空。
 * @param text 已安全转换的文案；空字符串表示取消或交由会话流程处理。
 * @param blocking 当前是否缺少可显示的数据。
 * @param denied 当前加载是否被拒绝；无权时优先显示内容区状态。
 */
export async function presentLoadError(
  floating: Ref<string>,
  inline: Ref<LoadIssue | undefined>,
  text: string,
  blocking: boolean,
  denied = false
): Promise<void> {
  inline.value = undefined;
  if (!text) return;
  if (blocking || denied) {
    inline.value = { message: text, retryable: !denied };
    return;
  }
  await setErrorFeedback(floating, text);
}

/**
 * 展示纯文本业务异常；仅同一显式身份共享实例，禁止不同对象按文案合并。
 * @param text 已完成国际化及安全过滤的文案；空字符串不展示。
 * @param content 当前页面提供的补救操作，不接受服务端 HTML 或脚本。
 * @param identity 操作边界的内存身份；缺省每次独立，不使用文案作为去重键。
 * @returns 释放当前调用方持有的提示；最后一个调用方释放时关闭浮层。
 */
export function reportError(text: string, content?: VNode, identity: unknown = Symbol()): () => void {
  if (!text) return () => {};
  let entry = active.get(identity);
  if (!entry) {
    if (active.size >= MAX_ERRORS) active.values().next().value?.close();
    const onClose = () => { if (active.get(identity) === entry) active.delete(identity); };
    // 所有异常共用顶部居中消息栈；长文案和补救操作持续显示，避免操作入口提前消失。
    const handle = ElMessage({
      message: content ?? text, type: "error", customClass: "app-error-feedback",
      duration: content || text.length > 100 ? 0 : ERROR_DURATION, showClose: true, onClose
    });
    entry = { close: () => { handle.close(); onClose(); }, owners: 0 };
    active.set(identity, entry);
  }
  entry.owners++;
  const owned = entry;
  let released = false;
  return () => {
    if (released) return;
    released = true;
    if (--owned.owners === 0) owned.close();
  };
}

/** 清理路由、账号或租户切换前的异常，防止旧上下文的操作继续出现。 */
export function clearErrorFeedback(): void {
  contextGeneration++;
  for (const entry of [...active.values()]) entry.close();
  active.clear();
}

/**
 * 按操作事件更新旧页面的错误状态；先刷新空状态，确保相同文案也触发浮层。
 * @param state 当前页面专用错误状态；不清理草稿或业务数据。
 * @param text 已安全转换的错误文案；空字符串表示静默。
 */
export async function setErrorFeedback(state: Ref<string>, text: string): Promise<void> {
  const generation = contextGeneration;
  const update = Symbol();
  stateUpdates.set(state, update);
  state.value = "";
  await nextTick();
  if (generation !== contextGeneration || stateUpdates.get(state) !== update) return;
  state.value = text;
}
