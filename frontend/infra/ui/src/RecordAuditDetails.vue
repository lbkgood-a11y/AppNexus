<script setup lang="ts">
import { useI18n } from "vue-i18n";
/** 统一只读审计契约；时间为 UTC ISO 8601，账号为空表示系统执行或历史未记录，不推断人员身份。 */
export interface RecordAudit {
  createdBy: string | null;
  createdAt: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
  createdByName?: string | null;
  updatedByName?: string | null;
}
/** 复用 Element Plus 描述列表；不请求额外数据，也不在前端解析权限或保存操作者。 */
const props=withDefaults(defineProps<{
  audit: RecordAudit;
  /** 未保存记录尚无审计事实，默认false；为true时四字段显示占位，不误称历史未知。 */
  pending?: boolean;
  /** 主数据详情使用既有横向四栏样板，默认false保留审计弹窗描述列表。 */
  inline?: boolean;
}>(),{pending:false,inline:false});
const { t, locale } = useI18n();
/** 已授权的账号名缺失时区分未记录与不可解析，不回退到账号标识。 */
function actor(value: string | null, name?: string | null) {
  if (props.pending) return "—";
  return name?.trim() || t(value ? "referenceName.unavailable" : "recordAudit.unknownActor");
}
/** UTC 时间点按当前语言和浏览器时区显示；缺失值保持未知，不回填当前时刻。 */
function time(value: string | null) {
  if (props.pending) return "—";
  if (!value) return t("recordAudit.unknown");
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? t("recordAudit.unknown") : new Intl.DateTimeFormat(locale.value, { dateStyle: "medium", timeStyle: "medium" }).format(date);
}
</script>
<template>
  <div v-if="inline" class="detail-audit-grid">
    <div v-for="key in ['createdBy','createdAt','updatedBy','updatedAt'] as const" :key="key" class="detail-audit-item">
      <div><span>{{ t('recordAudit.'+key) }}</span><strong>{{ key.endsWith('At') ? time(audit[key]) : actor(audit[key], key==='createdBy' ? audit.createdByName : audit.updatedByName) }}</strong></div>
    </div>
  </div>
  <el-descriptions v-else :column="1" border>
    <el-descriptions-item :label="t('recordAudit.createdBy')"><span class="record-audit-actor">{{ actor(audit.createdBy, audit.createdByName) }}</span></el-descriptions-item>
    <el-descriptions-item :label="t('recordAudit.createdAt')">{{ time(audit.createdAt) }}</el-descriptions-item>
    <el-descriptions-item :label="t('recordAudit.updatedBy')"><span class="record-audit-actor">{{ actor(audit.updatedBy, audit.updatedByName) }}</span></el-descriptions-item>
    <el-descriptions-item :label="t('recordAudit.updatedAt')">{{ time(audit.updatedAt) }}</el-descriptions-item>
  </el-descriptions>
</template>
<style scoped>
.record-audit-actor { overflow-wrap: anywhere; color: var(--el-text-color-secondary); }
</style>
