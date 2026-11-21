<script setup lang="ts">
import { VxeTable, VxeColumn } from "@appnexus/ui";
import { setErrorFeedback } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import { getAccountSessions, revokeOtherSessions, revokeAccountSession, type AccountSession } from "@/api/account";
import { requestErrorMessage } from "@/utils/http";
import { displayInstant, refreshDisplayFormat } from "@/utils/displayFormat";
const { t, locale } = useI18n();
const rows = ref<AccountSession[]>([]);
const loading = ref(false);
const revoking = ref("");
const error = ref("");
/** 保留加载失败的明确反馈，不将服务故障当作没有在线会话。 */
async function load() {
  loading.value = true; error.value = "";
  try { rows.value = await getAccountSessions(); }
  catch (cause) { await setErrorFeedback(error, requestErrorMessage(cause)); }
  finally { loading.value = false; }
}
/** 破坏性操作使用模板采用的确认弹窗；用户取消不产生请求。 */
async function revoke(row: AccountSession) {
  try {
    await ElMessageBox.confirm(t("account.revokeConfirm"), t("account.revokeSession"), {
      type: "warning", confirmButtonText: t("account.revokeSession"), cancelButtonText: t("account.cancel")
    });
  } catch { return; }
  revoking.value = row.handle; error.value = "";
  try {
    await revokeAccountSession(row.handle);
    ElMessage.success(t("account.sessionRevoked")); await load();
  } catch (cause) { await setErrorFeedback(error, requestErrorMessage(cause)); }
  finally { revoking.value = ""; }
}
/** 批量操作复用模板确认弹窗，服务端决定目标范围，客户端不提交会话 ID 列表。 */
async function revokeOthers() {
  try {
    await ElMessageBox.confirm(t("account.revokeOthersConfirm"), t("account.revokeOthers"), {
      type: "warning", confirmButtonText: t("account.revokeOthers"), cancelButtonText: t("account.cancel")
    });
  } catch { return; }
  revoking.value = "all"; error.value = "";
  try {
    const result = await revokeOtherSessions();
    ElMessage.success(t("account.othersRevoked", { count: result.revoked }));
    await load();
  } catch (cause) { await setErrorFeedback(error, requestErrorMessage(cause)); }
  finally { revoking.value = ""; }
}
/** 会话时间使用当前界面语言及租户展示时区，原始 UTC 时间保留在提示中。 */
function time(value: string) {
  return displayInstant(value,locale.value);
}
onMounted(()=>{void refreshDisplayFormat();void load();});
</script>
<template>
  <section>
    <div class="session-heading"><div><h1>{{ t("account.sessions") }}</h1><p>{{ t("account.sessionsIntro") }}</p></div>
      <div class="session-actions">
        <el-button v-operation="'Account.sessionRevokeOthers'" type="danger" plain :loading="revoking === 'all'" :disabled="loading || Boolean(revoking) || Boolean(error) || !rows.some(row => !row.current)" @click="revokeOthers">{{ t("account.revokeOthers") }}</el-button>
        <el-button :loading="loading" :disabled="Boolean(revoking)" @click="load">{{ t("account.refresh") }}</el-button>
      </div></div>
    <FloatingError v-if="error" :title="error" scope="error" type="error" show-icon :closable="false" class="mb-4" />
    <vxe-table :column-config="{ resizable: true }"  :loading="loading" :data="error ? [] : rows" :empty-text="error ? t('account.logsRetry') : t('account.noSessions')" border="inner">
      <vxe-column :title="t('account.sessionType')" min-width="150">
        <template #default="{ row }"><el-tag v-if="row.current" type="success" effect="plain">{{ t("account.currentSession") }}</el-tag>
          <span v-else>{{ t("account.otherSession") }}</span></template>
      </vxe-column>
      <vxe-column :title="t('account.sessionCreated')" min-width="200"><template #default="{ row }">
        <time :title="row.createdAt">{{ time(row.createdAt) }}</time></template></vxe-column>
      <vxe-column :title="t('account.sessionAccess')" min-width="200"><template #default="{ row }">
        <time :title="row.lastAccessedAt">{{ time(row.lastAccessedAt) }}</time></template></vxe-column>
      <vxe-column :title="t('account.sessionOperation')" width="120" fixed="right"><template #default="{ row }">
        <el-button v-operation="'Account.sessionRevoke'" v-if="!row.current" link type="danger" :loading="revoking === row.handle"
          :disabled="Boolean(revoking) && revoking !== row.handle" @click="revoke(row as AccountSession)">{{ t("account.revokeSession") }}</el-button>
        <span v-else>—</span></template></vxe-column>
    </vxe-table>
    <p class="session-note">{{ t("account.sessionsNote") }}</p>
  </section>
</template>
<style scoped>
/* 页面外围留白引用全局内容与区块 Token，局部字段间距和触控高度按原交互保留。 */
.session-heading { display: flex; justify-content: space-between; gap: 16px; margin-bottom:var(--app-section-gap,8px); }
.session-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.session-actions :deep(.el-button + .el-button) { margin-left: 0; }
@media (max-width: 800px) { .session-heading { flex-wrap: wrap; } }
h1 { font-size: 23px; font-weight: 600; margin: 0 0 10px; }
p { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.8; }
.session-note { margin-top:var(--app-section-gap,8px); }
</style>
