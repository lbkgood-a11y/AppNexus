<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { BusinessWorkspace, ManagementTable, type ManagementColumn } from "@appnexus/ui";
import type { CursorPage, InventoryFilter, InventoryOverviewApi, InventoryRow, WarehouseOption } from "./types";

const props = defineProps<{ api: InventoryOverviewApi; errorText: (cause: unknown) => string }>();
const { t, locale } = useI18n();
const fields = [
  { key: "warehouseId", source: "warehouses" },
  { key: "ownerId", source: "owners" },
  { key: "productId", source: "products" }
] as const;
type FilterKey = typeof fields[number]["key"];
const filters = reactive<InventoryFilter>({});
const options = reactive<Record<FilterKey, WarehouseOption[]>>({ warehouseId: [], ownerId: [], productId: [] });
const optionErrors = reactive<Partial<Record<FilterKey, string>>>({});
const optionRequests = { warehouseId: 0, ownerId: 0, productId: 0 };
const page = ref<CursorPage<InventoryRow>>({ rows: [], nextAfterId: null });
const cursors = ref<string[]>([]);
const applied = ref<InventoryFilter>({});
const loading = ref(false);
const issue = ref("");
const denied = ref(false);
let request = 0;

const columnWidths = {
  product: 190, specification: 150, skuCode: 140, receiptBatchNo: 150, warehouse: 140, owner: 160, serial: 150,
  onHand: 110, pending: 110, unit: 100, receiptId: 235
};
const columns = computed<ManagementColumn[]>(() => Object.entries(columnWidths).map(([key, minWidth]) => ({
  key, prop: key, minWidth, label: t("inventoryOverview." + key)
})));
const amount = computed(() => new Intl.NumberFormat(locale.value, { maximumFractionDigits: 12 }));

/** 保留选中标签，并丢弃晚到的远程选项响应。 */
async function findOptions(field: typeof fields[number], keyword = "") {
  const { key, source } = field;
  const current = ++optionRequests[key];
  optionErrors[key] = undefined;
  try {
    const result = await props.api[source](keyword);
    if (current !== optionRequests[key]) return;
    const selected = options[key].find(item => item.id === filters[key]);
    options[key] = selected && !result.some(item => item.id === selected.id) ? [selected, ...result] : result;
  } catch (cause) {
    if (current === optionRequests[key]) optionErrors[key] = props.errorText(cause);
  }
}

/** 页游标只作用于已提交筛选；清空条件时重新读首屏。 */
async function load(criteria: InventoryFilter, stack: string[] = []) {
  const current = ++request;
  loading.value = true;
  issue.value = "";
  denied.value = false;
  try {
    const response = await props.api.list(criteria, stack.at(-1));
    if (current !== request) return;
    page.value = response;
    applied.value = criteria;
    cursors.value = stack;
  } catch (cause) {
    if (current !== request) return;
    page.value = { rows: [], nextAfterId: null };
    denied.value = (cause as { response?: { status?: number } })?.response?.status === 403;
    issue.value = props.errorText(cause) || t("inventoryOverview.failed");
  } finally {
    if (current === request) loading.value = false;
  }
}

function search() {
  return load({
    warehouseId: filters.warehouseId || undefined,
    ownerId: filters.ownerId || undefined,
    productId: filters.productId || undefined
  });
}

function reset() {
  for (const { key } of fields) filters[key] = undefined;
  void search();
}

onMounted(() => void load({}));
</script>

<template>
  <div class="management-page">
    <BusinessWorkspace :title="t('inventoryOverview.title')">
      <template #search>
        <el-form inline @submit.prevent="search">
          <div class="management-filter">
            <el-form-item v-for="field in fields" :key="field.key"
              :label="t('warehouseTitle.' + field.key)" :error="optionErrors[field.key]">
              <el-select v-model="filters[field.key]" filterable remote clearable class="w-48 max-w-full"
                :placeholder="t('inventoryOverview.all')" :remote-method="keyword => findOptions(field, keyword)"
                @visible-change="open => { if (open) void findOptions(field); }">
                <el-option v-for="item in options[field.key]" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
          </div>
          <div class="management-filter-actions">
            <el-button v-operation="'BulkWarehouseStockBalance.read_overview'" type="primary" native-type="submit" :loading="loading">
              {{ t("masterData.search") }}
            </el-button>
            <el-button v-operation="'BulkWarehouseStockBalance.read_overview'" :disabled="loading" @click="reset">{{ t("masterData.reset") }}</el-button>
          </div>
        </el-form>
      </template>
      <template #data>
        <el-result v-if="issue" :icon="denied ? 'warning' : 'error'"
          :title="t(denied ? 'account.forbidden' : 'inventoryOverview.failed')" :sub-title="denied ? undefined : issue">
          <template v-if="!denied" #extra>
            <el-button v-operation="'BulkWarehouseStockBalance.read_overview'" @click="search">{{ t("masterData.retry") }}</el-button>
          </template>
        </el-result>
        <ManagementTable v-else embedded :title="t('inventoryOverview.title')" :columns="columns"
          :rows="page.rows" :loading="loading" :empty-text="t('inventoryOverview.empty')" @refresh="search">
          <template #specification="{ row }">{{ row.specification || "—" }}</template>
          <template #skuCode="{ row }">{{ row.skuCode || "—" }}</template>
          <template #receiptBatchNo="{ row }">{{ row.receiptBatchNo || "—" }}</template>
          <template #serial="{ row }">{{ row.serial || "—" }}</template>
          <template #unit="{ row }">{{ row.unit || t('referenceName.unavailable') }}</template>
          <template #onHand="{ row }">{{ amount.format(row.onHand) }}</template>
          <template #pending="{ row }">{{ amount.format(row.pending) }}</template>
          <template #pagination>
            <div class="flex gap-2">
              <el-button v-if="cursors.length" v-operation="'BulkWarehouseStockBalance.read_overview'" :disabled="loading"
                @click="load(applied, cursors.slice(0, -1))">{{ t("warehouseTitle.previous") }}</el-button>
              <el-button v-if="page.nextAfterId" v-operation="'BulkWarehouseStockBalance.read_overview'" :disabled="loading"
                @click="load(applied, [...cursors, page.nextAfterId])">{{ t("warehouseTitle.next") }}</el-button>
            </div>
          </template>
        </ManagementTable>
      </template>
    </BusinessWorkspace>
  </div>
</template>
