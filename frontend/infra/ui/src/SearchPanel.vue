<script setup lang="ts">
import { ref, useId } from "vue";
import { useI18n } from "vue-i18n";
import { AppIcon } from "@appnexus/icons";
/** 展开时向上表示收起、收起时向下表示展开；与详情分区共用 AppIcon 18px 语义图标。 */
/** 公共查询区：默认展开，收起时保留表单实例、查询条件与结果，不触发业务请求。 */
/** 已国际化的查询区标题；缺省使用公共查询条件文案。 */
defineProps<{ title?: string; /** 当前业务名称；未指定标题时用于生成有业务归属的查询标题。 */ scope?: string }>();
const expanded = ref(true);
const contentId = useId();
const { t } = useI18n();
</script>
<template>
  <div class="search-panel" :class="{ 'is-collapsed': !expanded }">
    <div class="search-panel__header">
      <h2 class="management-filter-title">{{ title || (scope ? t('management.queryFor', { name: scope }) : t('management.queryConditions')) }}</h2>
      <el-button text :title="t(expanded ? 'administration.collapseFilter' : 'administration.expandFilter')" :aria-expanded="expanded" :aria-controls="contentId" :aria-label="t(expanded ? 'administration.collapseFilter' : 'administration.expandFilter')" @click="expanded = !expanded">
        <AppIcon :name="expanded ? 'arrowUp' : 'arrowDown'" :size="18" />
      </el-button>
    </div>
    <el-collapse-transition><div v-show="expanded" :id="contentId" class="search-panel__content"><slot /></div></el-collapse-transition>
  </div>
</template>
<style scoped>
/* 标题和折叠按钮固定在同一行；标题在收起后保留，表单使用完整可用宽度。 */
.search-panel { min-width: 0; }
/* 标题与条件之间只保留 4px，不通过缩小折叠按钮命中区换取紧凑高度。 */
.search-panel__header { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:4px; }
.search-panel.is-collapsed .search-panel__header { margin-bottom:0; }
.search-panel__content { min-width:0; }

</style>
