import { h, ref, watch, nextTick } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
const shown = vi.hoisted(() => ({ message: vi.fn(), notification: vi.fn() }));
vi.mock("element-plus", () => ({ ElMessage: shown.message, ElNotification: shown.notification }));
import { clearErrorFeedback, presentLoadError, reportError, setErrorFeedback, type LoadIssue } from "../../../infra/ui/src/errorFeedback";

describe("统一浮层生命周期", () => {
  afterEach(() => { clearErrorFeedback(); vi.clearAllMocks(); });
  it("阻断加载只更新内容区，保留独立操作反馈", async () => {
    const floating = ref("保存失败");
    const inline = ref<LoadIssue>();
    await presentLoadError(floating, inline, "服务不可用", true);
    expect(inline.value).toEqual({ message: "服务不可用", retryable: true });
    expect(floating.value).toBe("保存失败");
  });
  it("有旧数据的刷新失败只更新浮层，无权和静默失败遵守各自契约", async () => {
    const floating = ref("");
    const inline = ref<LoadIssue>();
    await presentLoadError(floating, inline, "刷新失败", false);
    expect(floating.value).toBe("刷新失败");
    expect(inline.value).toBeUndefined();
    floating.value = "";
    await presentLoadError(floating, inline, "无权", false, true);
    expect(inline.value).toEqual({ message: "无权", retryable: false });
    await presentLoadError(floating, inline, "", true);
    expect(inline.value).toBeUndefined();
    expect(floating.value).toBe("");
  });
  it("多个展示位置共享实例，最后一个释放时才关闭", () => {
    const close = vi.fn();
    shown.message.mockReturnValue({ close });
    const identity = Symbol();
    const first = reportError("编码重复", undefined, identity);
    const second = reportError("编码重复", undefined, identity);
    expect(shown.message).toHaveBeenCalledTimes(1);
    first(); expect(close).not.toHaveBeenCalled();
    second(); expect(close).toHaveBeenCalledTimes(1);
  });
  it("长文本保持显示，清理上下文后允许重新展示", () => {
    const close = vi.fn();
    shown.message.mockReturnValue({ close });
    reportError("长".repeat(101));
    expect(shown.message.mock.calls[0][0].duration).toBe(0);
    clearErrorFeedback(); expect(close).toHaveBeenCalledOnce();
    reportError("长".repeat(101));
    expect(shown.message).toHaveBeenCalledTimes(2);
  });
  it("短文案、长文案和补救操作共用消息入口且保留关闭行为", () => {
    shown.message.mockReturnValue({ close: vi.fn() });
    const content = h("button", "重新加载");
    reportError("普通异常"); reportError("长".repeat(101)); reportError("冲突", content);
    expect(shown.message.mock.calls.map(([options]) => options.duration)).toEqual([5000, 0, 0]);
    expect(shown.message.mock.calls[2][0].message).toBe(content);
    expect(shown.message.mock.calls.every(([options]) => options.showClose && options.type === "error")).toBe(true);
    expect(shown.notification).not.toHaveBeenCalled();
  });
  it("空错误静默，超过数量限制关闭最早提示", () => {
    const close = vi.fn(); shown.message.mockReturnValue({ close });
    reportError(""); expect(shown.message).not.toHaveBeenCalled();
    for (const text of ["a", "b", "c", "d"]) reportError(text);
    expect(close).toHaveBeenCalledTimes(1);
  });
  it("不同操作同文案不合并", () => {
    shown.message.mockReturnValue({ close: vi.fn() });
    reportError("冲突"); reportError("冲突");
    expect(shown.message).toHaveBeenCalledTimes(2);
  });
  it("同文案连续操作仍产生新的状态事件", async () => {
    const state = ref(""); const values: string[] = [];
    const stop = watch(state, value => values.push(value));
    await setErrorFeedback(state, "冲突"); await nextTick();
    await setErrorFeedback(state, "冲突"); await nextTick();
    expect(values).toEqual(["冲突", "", "冲突"]); stop();
  });

  it("上下文清理使等待中的旧错误失效", async () => {
    const state = ref(""); const pending = setErrorFeedback(state, "旧租户错误");
    clearErrorFeedback(); await pending;
    expect(state.value).toBe("");
  });
  it("并发错误更新只发布最新事件", async () => {
    const state = ref("");
    await Promise.all([setErrorFeedback(state, "旧错误"), setErrorFeedback(state, "新错误")]);
    expect(state.value).toBe("新错误");
  });
  it("释放函数可重复调用且不关闭其他持有方", () => {
    const close = vi.fn(); shown.message.mockReturnValue({ close }); const identity = Symbol();
    const first = reportError("冲突", undefined, identity);
    const second = reportError("冲突", undefined, identity);
    first(); first(); expect(close).not.toHaveBeenCalled();
    second(); expect(close).toHaveBeenCalledOnce();
  });

});
