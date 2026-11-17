<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { offlineIconNames } from "./offlineIcon";
import IconifyIconOffline from "./iconifyIconOffline";

/** 模板图标选择器：仅选择已注册本地图标，以斜杠名称保持菜单 API 与导航渲染契约。 */
defineOptions({ name: "IconSelect" });
/** 保存期间禁用输入、弹层与清空；默认允许选择。 */
const props = defineProps<{ disabled?: boolean }>();
const inputValue = defineModel<string>({ default: "" });
const { t } = useI18n();
const visible = ref(false);
const filterValue = ref("");
const currentPage = ref(1);
/** 每页显示 35 个图标，限制弹层高度；搜索后回到第一页。 */
const pageSize = 35;
const filtered = computed(() => offlineIconNames.filter(name =>
  name.toLowerCase().includes(filterValue.value.trim().toLowerCase())
));
const pageList = computed(() => filtered.value.slice(
  (currentPage.value - 1) * pageSize, currentPage.value * pageSize
));
const preview = computed(() => offlineIconNames.includes(inputValue.value) ? inputValue.value : "ri/search-line");
watch(filterValue, () => { currentPage.value = 1; });
watch(() => props.disabled, disabled => { if (disabled) visible.value = false; });
/** 每次打开重新定位已有图标；未知历史值保留原文，索引缺省时显示第一页。 */
function openPicker() {
  filterValue.value = "";
  currentPage.value = Math.max(1, Math.floor(offlineIconNames.indexOf(inputValue.value) / pageSize) + 1);
}
/** 选择或清空只修改表单草稿，实际持久化仍由菜单保存流程执行。 */
function selectIcon(value: string) {
  if (props.disabled) return;
  inputValue.value = value;
  visible.value = false;
}
</script>

<template>
  <el-input :model-value="inputValue" readonly :disabled="disabled" class="icon-selector">
    <template #append>
      <el-popover v-model:visible="visible" :disabled="disabled" :width="350" trigger="click"
        popper-class="menu-icon-picker" @before-enter="openPicker">
        <template #reference>
          <el-button :disabled="disabled" :aria-label="t('iconPicker.choose')" :title="t('iconPicker.choose')">
            <IconifyIconOffline :icon="preview" width="18" height="18" aria-hidden="true" />
          </el-button>
        </template>
        <el-input v-model="filterValue" :placeholder="t('iconPicker.search')" :aria-label="t('iconPicker.search')" clearable />
        <el-scrollbar height="220px">
          <div class="icon-grid">
            <el-button v-for="name in pageList" :key="name" :title="name" :aria-label="name"
              :aria-pressed="inputValue === name" :type="inputValue === name ? 'primary' : 'default'"
              :disabled="disabled" @click="selectIcon(name)">
              <IconifyIconOffline :icon="name" width="20" height="20" aria-hidden="true" />
            </el-button>
          </div>
          <el-empty v-if="!filtered.length" :description="t('iconPicker.empty')" :image-size="60" />
        </el-scrollbar>
        <div class="icon-picker-footer">
          <el-pagination v-model:current-page="currentPage" :total="filtered.length" :page-size="pageSize"
            :pager-count="5" layout="pager" size="small" />
          <el-button text :disabled="disabled || !inputValue" @click="selectIcon('')">{{ t('iconPicker.clear') }}</el-button>
        </div>
      </el-popover>
    </template>
  </el-input>
</template>

<style scoped>
.icon-selector { width: 100%; }
.icon-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 4px; padding: 8px 0; }
.icon-grid .el-button { margin: 0; padding: 8px; min-width: 0; height: 36px; }
.icon-picker-footer { display: flex; align-items: center; justify-content: space-between; border-top: 1px solid var(--el-border-color-light); }
</style>
<style>
.menu-icon-picker.el-popover { max-width: calc(100vw - 24px); }
</style>
