<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { AppIcon } from "@appnexus/icons";

/**
 * T02 左树右列表模板：以单一内容表面组织范围树、查询区和列表区。
 * 树与数据请求仍由业务页负责；模板只管理分栏、折叠和响应式，不把树选择当作权限边界。
 */
const props = withDefaults(defineProps<{
  /** 独立页面顶部标题；以模板标签栏呈现，嵌入已有导航工作区时可省略。 */
  title?: string;
  /** 是否收起左侧树；受控状态由业务页持有。 */
  collapsed?: boolean;
  /** 左树宽度，单位 px；有效范围 220–360，默认 248。 */
  treeWidth?: number;
  /** 嵌入 BusinessWorkspace 时移除重复外框，默认 false。 */
  embedded?: boolean;
}>(), { collapsed: false, treeWidth: 248, embedded: false });
const emit = defineEmits<{ "update:collapsed": [value: boolean] }>();
defineSlots<{ navigation?: () => unknown; treeHeader?: () => unknown; tree: () => unknown; header?: () => unknown; search?: () => unknown; toolbar?: () => unknown; table: () => unknown; pagination?: () => unknown; feedback?: () => unknown }>();
const { t } = useI18n();
const style = computed(() => ({ "--tree-panel-width": `${Math.min(360, Math.max(220, props.treeWidth))}px` }));
</script>

<template>
  <div class="tree-list-template" :class="{ 'is-tree-collapsed': collapsed, 'is-embedded': embedded }" :style="style">
    <component :is="embedded ? 'section' : 'el-card'" class="tree-list-template__surface" shadow="never">
      <header v-if="title || $slots.navigation" class="tree-list-template__navigation">
        <slot name="navigation">
          <el-tabs model-value="title">
            <el-tab-pane :label="title" name="title" />
          </el-tabs>
        </slot>
      </header>
      <div class="tree-list-template__layout">
        <aside class="tree-list-template__aside">
          <header class="tree-list-template__tree-header">
            <div v-show="!collapsed" class="tree-list-template__tree-heading"><slot name="treeHeader" /></div>
            <el-tooltip :content="t(collapsed ? 'management.expandTree' : 'management.collapseTree')" placement="right">
              <el-button text class="tree-list-template__toggle" :aria-label="t(collapsed ? 'management.expandTree' : 'management.collapseTree')" :aria-expanded="!collapsed" @click="emit('update:collapsed', !collapsed)">
                <AppIcon name="arrowRight" :size="18" :class="{ 'is-expanded': !collapsed }" />
              </el-button>
            </el-tooltip>
          </header>
          <div v-show="!collapsed" class="tree-list-template__tree"><slot name="tree" /></div>
        </aside>
        <main class="tree-list-template__content">
          <header v-if="$slots.header" class="tree-list-template__header"><slot name="header" /></header>
          <div v-if="$slots.feedback" class="tree-list-template__feedback"><slot name="feedback" /></div>
          <section v-if="$slots.search" class="tree-list-template__search"><slot name="search" /></section>
          <section class="tree-list-template__data">
            <div v-if="$slots.toolbar" class="template-toolbar"><slot name="toolbar" /></div>
            <div class="template-table"><slot name="table" /></div>
            <div v-if="$slots.pagination" class="template-pagination"><slot name="pagination" /></div>
          </section>
        </main>
      </div>
    </component>
  </div>
</template>
