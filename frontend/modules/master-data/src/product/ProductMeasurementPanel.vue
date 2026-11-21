<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import { FloatingError, setErrorFeedback } from "@appnexus/ui";
import type { ProductApi, ProductMeasurement, ProductOption } from "./product";

/** 商品计量表单只编辑授权配置，试算始终读取服务端已保存的包装规格。 */
const props = defineProps<{
  id: string;
  readonly: boolean;
  baseUnitName?: string;
  api: ProductApi;
  errorText: (error: unknown) => string;
}>();
const emit = defineEmits<{ changed: [] }>();
const { t } = useI18n();
const current = ref<ProductMeasurement>();
const draft = ref<ProductMeasurement["configuration"]>({ units: [], packaging: [] });
const options = ref<ProductOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const code = ref("");
const quantity = ref("1");
const result = ref("");
const scenes = ["ARRIVAL", "INBOUND", "OUTBOUND"];
const methods = ["DIRECT", "CONVERSION", "PACKAGING", "MEASURED"];

/** 先取得已保存配置，再读取授权单位分页，读失败时保留可重试反馈。 */
async function load() {
  loading.value = true;
  try {
    const measurement = await props.api.measurement(props.id);
    const rows: ProductOption[] = [];
    let page = 1;
    let total = 1;
    while (rows.length < total) {
      const next = await props.api.options("unit", "", page++);
      total = next.total;
      if (!next.rows.length) break;
      rows.push(...next.rows);
    }
    options.value = rows;
    draft.value = structuredClone(measurement.configuration);
    current.value = measurement;
    error.value = "";
  } catch (e) {
    await setErrorFeedback(error, props.errorText(e));
  } finally {
    loading.value = false;
  }
}

/** 以读到的版本原子保存，冲突时保留当前输入。 */
async function save() {
  if (props.readonly || saving.value || !current.value) return;
  saving.value = true;
  try {
    await props.api.saveMeasurement(props.id, { ...draft.value, version: current.value.version });
    ElMessage.success(t("masterData.saved"));
    emit("changed");
  } catch (e) {
    await setErrorFeedback(error, props.errorText(e));
  } finally {
    saving.value = false;
  }
}

/** 数量按字符串传输，展示服务端返回的精确值。 */
async function calculate() {
  if (!code.value || saving.value) return;
  result.value = "";
  saving.value = true;
  try {
    const response = await props.api.calculatePackaging(props.id, code.value, quantity.value);
    result.value = response.exact;
  } catch (e) {
    await setErrorFeedback(error, props.errorText(e));
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div v-loading="loading" class="detail-profile-form">
    <FloatingError :title="error"><el-button v-operation="'Product.readMeasurement'" @click="load">{{ t("masterData.retry") }}</el-button></FloatingError>
    <template v-if="current">
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="t('productV1.baseUnitId')">{{ baseUnitName ?? "—" }}</el-descriptions-item>
      </el-descriptions>
      <el-form class="business-border-label-form detail-profile-form" label-position="top" :disabled="readonly || saving" @submit.prevent="save">
        <el-card class="detail-section" shadow="never">
          <template #header><div class="detail-section-heading"><h2>{{ t("productMeasurement.units") }}</h2></div></template>
          <el-empty v-if="!draft.units.length" :description="t('productMeasurement.inheritsBase')" />
          <el-row v-for="(unit, index) in draft.units" :key="index" :gutter="12">
            <el-col :xs="24" :sm="12" :lg="6"><el-form-item :label="t('productCatalog.unit')"><el-select v-model="unit.unitId" filterable><el-option v-for="option in options" :key="option.id" :value="option.id" :label="option.name"/></el-select></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :lg="6"><el-form-item :label="t('productMeasurement.scene')"><el-select v-model="unit.scene"><el-option v-for="scene in scenes" :key="scene" :value="scene" :label="t('productMeasurement.' + scene)"/></el-select></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :lg="6"><el-form-item :label="t('productMeasurement.method')"><el-select v-model="unit.method"><el-option v-for="method in methods" :key="method" :value="method" :label="t('productMeasurement.' + method)"/></el-select></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :lg="6"><el-form-item><el-checkbox v-model="unit.defaultUnit">{{ t("productMeasurement.defaultUnit") }}</el-checkbox><el-button v-if="!readonly" v-operation="'Product.saveMeasurement'" link @click="draft.units.splice(index, 1)">{{ t("productMeasurement.remove") }}</el-button></el-form-item></el-col>
          </el-row>
          <el-button v-if="!readonly && current.baseUnitId" v-operation="'Product.saveMeasurement'" @click="draft.units.push({ unitId: current.baseUnitId, scene: 'ARRIVAL', method: 'DIRECT', defaultUnit: false })">{{ t("masterData.create") }}</el-button>
        </el-card>
        <el-card class="detail-section" shadow="never">
          <template #header><div class="detail-section-heading"><h2>{{ t("productMeasurement.packaging") }}</h2></div></template>
          <el-empty v-if="!draft.packaging.length"/>
          <el-row v-for="(pack, index) in draft.packaging" :key="index" :gutter="12">
            <el-col :xs="24" :sm="12" :lg="5"><el-form-item :label="t('masterData.code')"><el-input v-model="pack.code" maxlength="64"/></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :lg="5"><el-form-item :label="t('masterData.name')"><el-input v-model="pack.name" maxlength="200"/></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :lg="5"><el-form-item :label="t('productCatalog.unit')"><el-select v-model="pack.unitId" filterable><el-option v-for="option in options" :key="option.id" :value="option.id" :disabled="option.id===current.baseUnitId" :label="option.name"/></el-select></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :lg="5"><el-form-item :label="t('productMeasurement.baseQuantity')"><el-input v-model="pack.quantity" maxlength="31"/></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :lg="4"><el-button v-if="!readonly" v-operation="'Product.saveMeasurement'" link @click="draft.packaging.splice(index, 1)">{{ t("productMeasurement.remove") }}</el-button></el-col>
          </el-row>
          <el-button v-if="!readonly && current.baseUnitId" v-operation="'Product.saveMeasurement'" @click="draft.packaging.push({ code: '', name: '', unitId: '', quantity: '' })">{{ t("masterData.create") }}</el-button>
        </el-card>
        <div v-if="!readonly && current.baseUnitId">
          <el-button v-operation="'Product.saveMeasurement'" type="primary" :loading="saving" @click="save">{{ t("masterData.save") }}</el-button>
        </div>
      </el-form>
      <el-form v-if="current.configuration.packaging.length" inline @submit.prevent="calculate">
        <el-form-item :label="t('productMeasurement.packaging')"><el-select v-model="code" class="measurement-packaging-select" @change="result=''"><el-option v-for="pack in current.configuration.packaging" :key="pack.code" :value="pack.code" :label="pack.name"/></el-select></el-form-item>
        <el-form-item :label="t('productConversion.quantity')"><el-input v-model="quantity" maxlength="31" @input="result=''"/></el-form-item>
        <el-button v-operation="'Product.calculatePackaging'" :loading="saving" :disabled="!code" @click="calculate">{{ t("productConversion.calculate") }}</el-button>
        <el-form-item v-if="result" :label="t('productConversion.exact')">{{ result }} {{ baseUnitName }}</el-form-item>
      </el-form>
    </template>
  </div>
</template>

<style scoped>
/* 行内试算下拉需为已选箱规保留可读宽度，避免控件随占位内容收窄。 */
.measurement-packaging-select {
  width: 10rem;
}
</style>
