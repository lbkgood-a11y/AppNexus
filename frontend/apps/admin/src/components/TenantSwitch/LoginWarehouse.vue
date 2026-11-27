<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ManagedDialog, FloatingError } from "@appnexus/ui";
import { http, requestErrorMessage } from "@/utils/http";
import { currentTenant } from "@/utils/tenantContext";

interface Warehouse {
  id: string;
  code: string;
  name: string;
}

const endpoint = "/api/master-data/warehouse/login-selection";
const { t } = useI18n();
const options = ref<Warehouse[]>([]);
const current = ref<Warehouse>();
const selection = ref("");
const open = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref("");
let generation = 0;
let prompted = false;
let disposed = false;

function contextKey() {
  const tenant = currentTenant.value;
  return tenant ? `${tenant.tenantId}:${tenant.memberId}:${tenant.version}` : "";
}

/** 读取真实候选和当前仓库，丢弃旧租户、旧身份和已卸载组件的响应。 */
async function load(show = false) {
  if (disposed || !contextKey() || saving.value) return;
  const request = ++generation;
  if (show) open.value = true;
  loading.value = true;
  error.value = "";
  try {
    const view = await http.request<{ options: Warehouse[]; current: Warehouse | null }>("get", endpoint);
    if (request !== generation) return;
    options.value = view.options;
    current.value = view.current ?? undefined;
    if (show || !selection.value || !options.value.some(warehouse => warehouse.id === selection.value)) {
      selection.value = current.value?.id ?? (options.value.length === 1 ? options.value[0].id : "");
    }
    if (!prompted && !current.value && options.value.length) {
      prompted = true;
      open.value = true;
    }
  } catch (cause) {
    if (request !== generation) return;
    options.value = [];
    current.value = undefined;
    selection.value = "";
    if (open.value) error.value = requestErrorMessage(cause);
  } finally {
    if (request === generation) loading.value = false;
  }
}

/** 只提交候选 UUID；服务端复核授权并保存，会话失效时不保留客户端选择。 */
async function save() {
  if (!selection.value || loading.value || saving.value) return;
  const context = contextKey();
  const request = generation;
  const warehouseId = selection.value;
  saving.value = true;
  error.value = "";
  try {
    const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
    if (context !== contextKey() || request !== generation) return;
    const selected = await http.request<Warehouse>("put", endpoint, {
      data: { warehouseId },
      headers: { [csrf.headerName]: csrf.token }
    });
    if (request === generation) {
      current.value = selected;
      open.value = false;
    }
  } catch (cause) {
    if (request === generation) {
      error.value = requestErrorMessage(cause);
      current.value = undefined;
    }
  } finally {
    saving.value = false;
    if (request !== generation) void load();
  }
}

function reload() {
  void load();
}

function identityChanged() {
  generation++;
  prompted = false;
  options.value = [];
  current.value = undefined;
  selection.value = "";
  open.value = false;
  loading.value = false;
  reload();
}

watch(contextKey, identityChanged, { immediate: true });
onMounted(() => {
  window.addEventListener("focus", reload);
  window.addEventListener("appnexus:authority-changed", reload);
  window.addEventListener("appnexus:person-selected", identityChanged);
});
onUnmounted(() => {
  disposed = true;
  generation++;
  window.removeEventListener("focus", reload);
  window.removeEventListener("appnexus:authority-changed", reload);
  window.removeEventListener("appnexus:person-selected", identityChanged);
});
</script>

<template>
  <el-button v-operation="'Warehouse.selectLogin'" v-if="options.length" text :title="current?.name ?? t('loginWarehouse.title')"
    class="login-warehouse-button" @click="load(true)">
    {{ current?.name ?? t("loginWarehouse.title") }}
  </el-button>
  <ManagedDialog v-model="open" :title="t('loginWarehouse.title')" :busy="saving">
    <FloatingError :title="error" />
    <el-empty v-if="!loading && !options.length" :description="t('loginWarehouse.empty')" />
    <el-form v-else label-position="top">
      <el-form-item :label="t('loginWarehouse.title')">
        <el-select v-model="selection" filterable :loading="loading" :disabled="saving">
          <el-option v-for="warehouse in options" :key="warehouse.id" :value="warehouse.id"
            :label="`${warehouse.code} · ${warehouse.name}`" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="open = false">{{ t("organization.cancel") }}</el-button>
      <el-button v-operation="'Warehouse.selectLogin'" type="primary" :loading="saving" :disabled="loading || !selection || !options.length" @click="save">
        {{ t("loginWarehouse.confirm") }}
      </el-button>
    </template>
  </ManagedDialog>
</template>

<style scoped>
.login-warehouse-button {
  max-width: 140px;
  height: 48px;
}
.login-warehouse-button :deep(span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 600px) {
  .login-warehouse-button { max-width: 96px; }
}
</style>
