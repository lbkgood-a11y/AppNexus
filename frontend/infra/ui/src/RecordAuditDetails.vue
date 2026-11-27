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
/** 使用公共只读审计布局展示授权响应中的审计信息，不发起额外请求。 */
const props=withDefaults(defineProps<{
  audit: RecordAudit;
  /** 未保存记录尚无审计事实，默认false；为true时四字段显示占位，不误称历史未知。 */
  pending?: boolean;
  /** 主数据详情系统卡片内嵌时为 true，默认 false，使用独立的审计清单。 */
  inline?: boolean;
  /** 独立审计弹窗使用带边框的描述列表，默认 false。 */
  dialog?: boolean;
}>(),{pending:false,inline:false,dialog:false});
const { t, locale } = useI18n();
const auditKeys = ["createdBy", "createdAt", "updatedBy", "updatedAt"] as const;
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

/** 两种布局共用已授权字段及缺省值的展示逻辑。 */
function auditValue(key: (typeof auditKeys)[number]) {
  if (key === "createdAt" || key === "updatedAt") return time(props.audit[key]);
  return actor(props.audit[key], key === "createdBy" ? props.audit.createdByName : props.audit.updatedByName);
}
</script>
<template>
  <el-descriptions v-if="dialog" :column="1" border>
    <el-descriptions-item v-for="key in auditKeys" :key="key" :label="t('recordAudit.' + key)" :label-width="160">
      {{ auditValue(key) }}
    </el-descriptions-item>
  </el-descriptions>
  <dl v-else :class="inline ? 'detail-audit-grid' : 'detail-audit-list'">
    <div v-for="key in auditKeys" :key="key" class="detail-audit-item">
      <dt>{{ t('recordAudit.' + key) }}</dt>
      <dd>{{ auditValue(key) }}</dd>
    </div>
  </dl>
</template>
