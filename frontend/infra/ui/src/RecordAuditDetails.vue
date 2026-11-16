<script setup lang="ts">
import { useI18n } from "vue-i18n";
/** 统一只读审计契约；时间为 UTC ISO 8601，账号为空表示系统执行或历史未记录，不推断人员身份。 */
export interface RecordAudit {
  createdBy: string | null;
  createdAt: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
  createdByName?: string;
  updatedByName?: string;
}
/** 复用 Element Plus 描述列表；不请求额外数据，也不在前端解析权限或保存操作者。 */
defineProps<{ audit: RecordAudit }>();
const { t, locale } = useI18n();
/** UTC 时间点按当前语言和浏览器时区显示；缺失值保持未知，不回填当前时刻。 */
function time(value: string | null) {
  if (!value) return t("recordAudit.unknown");
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? t("recordAudit.unknown") : new Intl.DateTimeFormat(locale.value, { dateStyle: "medium", timeStyle: "medium" }).format(date);
}
</script>
<template>
  <el-descriptions :column="1" border>
    <el-descriptions-item :label="t('recordAudit.createdBy')"><span class="record-audit-actor">{{ audit.createdByName || audit.createdBy || t('recordAudit.unknownActor') }}</span></el-descriptions-item>
    <el-descriptions-item :label="t('recordAudit.createdAt')">{{ time(audit.createdAt) }}</el-descriptions-item>
    <el-descriptions-item :label="t('recordAudit.updatedBy')"><span class="record-audit-actor">{{ audit.updatedByName || audit.updatedBy || t('recordAudit.unknownActor') }}</span></el-descriptions-item>
    <el-descriptions-item :label="t('recordAudit.updatedAt')">{{ time(audit.updatedAt) }}</el-descriptions-item>
  </el-descriptions>
</template>
<style scoped>
.record-audit-actor { overflow-wrap: anywhere; color: var(--el-text-color-secondary); }
</style>
