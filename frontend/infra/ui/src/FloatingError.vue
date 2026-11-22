<script setup lang="ts">
import { defineComponent, getCurrentInstance, h, onBeforeUnmount, watch } from "vue";
import { reportError } from "./errorFeedback";
/**
 * 兼容页面错误状态的无布局适配器；复用 Element Plus 浮层并保留现有补救插槽。
 * title 为已安全转换的用户文案；不转发布局属性，防止旧 Alert 样式影响原页面。
 */
defineOptions({ inheritAttrs: false });
const props = defineProps<{ title?: string; scope?: string }>();
const slots = defineSlots<{ default?: () => any }>();
// 插槽必须在组件渲染函数内执行，才能持续追踪 loading、disabled 和国际化状态。
const RecoveryContent = defineComponent({
  setup: () => () => h("div", { class: "app-error-feedback__recovery" }, [
    h("div", props.title),
    h("div", { class: "app-error-feedback__actions" }, slots.default?.())
  ])
});
// 同一父页面同一状态的重复展示共享身份；不同页面、弹窗状态不共享重试回调。
const parent = getCurrentInstance()?.parent;
const scopeKeys = parent ? (parent as typeof parent & { feedbackKeys?: Map<string, symbol> }) : undefined;
if (scopeKeys && !scopeKeys.feedbackKeys) scopeKeys.feedbackKeys = new Map();
const identity = props.scope && scopeKeys
  ? scopeKeys.feedbackKeys!.get(props.scope) ?? Symbol(props.scope) : Symbol();
if (props.scope && scopeKeys) scopeKeys.feedbackKeys!.set(props.scope, identity);
let release: (() => void) | undefined;
watch(() => props.title, text => {
  release?.();
  release = text ? reportError(text, slots.default
    ? h(RecoveryContent) : undefined, identity) : undefined;
}, { immediate: true, flush: "post" });
onBeforeUnmount(() => release?.());
</script>
<template></template>
