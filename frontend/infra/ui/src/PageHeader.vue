<script setup lang="ts">
import { ref } from "vue";
import { AppIcon } from "@appnexus/icons";

/**
 * 管理页面标题区：统一面包屑、返回导航、标题、说明、状态与右侧操作，不感知路由或权限。
 * 面包屑由业务页面传入已翻译文本，链接跳转仍由应用壳负责。
 */
defineProps<{
  /** 页面唯一主标题。 */
  title: string;
  /** 页面职责与业务边界说明；为空时不占据空间。 */
  description?: string;
  /** 已按当前语言翻译的面包屑文本，最后一项表示当前页。 */
  breadcrumbs?: string[];
  /** 已翻译的返回按钮名称及提示；缺省时隐藏返回入口。 */
  backLabel?: string;
  /** 返回是否暂时禁用；缺省为可用，页面根据原有提交或导航约束传入。 */
  backDisabled?: boolean;
}>();
/** 返回原上下文，具体目标和未保存数据处理由调用页决定。 */
const emit = defineEmits<{ back: [] }>();

const backControl = ref<HTMLElement>();

/** 详情切换后将键盘焦点移至已渲染、可用的返回按钮。 */
function focusBack() {
  backControl.value?.querySelector("button")?.focus();
}

defineExpose({ focusBack });

defineSlots<{
  /** 页面操作按辅助、次要、主要顺序传入，共用右对齐容器。 */
  actions?: () => unknown;
  meta?: () => unknown;
}>();
</script>

<template>
  <header class="page-header">
    <el-breadcrumb v-if="breadcrumbs?.length" separator="/" class="page-header__breadcrumb" aria-label="Breadcrumb">
      <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</el-breadcrumb-item>
    </el-breadcrumb>
    <div class="page-header__main">
      <div class="page-header__identity">
        <div v-if="backLabel" ref="backControl" class="page-header__back">
          <el-button :aria-label="backLabel" :title="backLabel" :disabled="backDisabled"
            @click="emit('back')">
            <AppIcon name="arrowLeft" :size="18" />
          </el-button>
        </div>
        <div class="page-header__copy" :data-page-banner="backLabel ? undefined : ''">
          <div class="page-header__title-row"><h1>{{ title }}</h1><slot name="meta" /></div>
          <p v-if="description">{{ description }}</p>
        </div>
      </div>
      <div v-if="$slots.actions" class="page-header__actions"><slot name="actions" /></div>
    </div>
  </header>
</template>
