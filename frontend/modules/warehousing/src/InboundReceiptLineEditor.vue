<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { FloatingError } from "@appnexus/ui";
import type { InboundDocumentsApi, ReceiptConversionOption, ReceiptReferences, ReceiptStorageOption } from "./inboundDocuments";

/** 入库明细字段组，复用授权来源和主数据读取并组装原单据命令。 */
const props = defineProps<{ noticeId?: string; steel: boolean; api: InboundDocumentsApi; errorText: (error: unknown) => string }>();
const { locale } = useI18n();
const label = (zh: string, en: string) => locale.value.startsWith("zh") ? zh : en;
interface Line {
  key: string;
  noticeLineId: string;
  area?: ReceiptStorageOption;
  location?: ReceiptStorageOption;
  conversion?: ReceiptConversionOption;
  areas: ReceiptStorageOption[];
  locations: ReceiptStorageOption[];
  conversions: ReceiptConversionOption[];
  areaRequest: number;
  locationRequest: number;
  unitRequest: number;
  error: string;
  batchNo: string;
  lpn: string;
  heatNo: string;
  material: string;
  specification: string;
  theoreticalKg: string;
}
const newLine = (noticeLineId = ""): Line => ({
  key: crypto.randomUUID(), noticeLineId, areas: [], locations: [], conversions: [],
  areaRequest: 0, locationRequest: 0, unitRequest: 0, error: "", batchNo: "",
  lpn: "", heatNo: "", material: "", specification: "", theoreticalKg: ""
});
const lines = ref<Line[]>([]);
const references = ref<ReceiptReferences>();
const loading = ref(false);
const error = ref("");
const steelFields = [
  ["lpn", "卷包号", "LPN"], ["heatNo", "炉批号", "Heat number"],
  ["specification", "规格", "Specification"], ["material", "材质", "Material"],
  ["theoreticalKg", "理重（kg）", "Theoretical weight (kg)"]
] as const;
let revision = 0;

function failure(cause: unknown) {
  if (cause instanceof Error && cause.message === "INBOUND_RECEIPT_REFERENCES_UNAVAILABLE") {
    return label("未找到启用的来源仓库或千克单位", "The source warehouse or kilogram unit is unavailable");
  }
  return props.errorText(cause) || label("引用资料加载失败", "Unable to load references");
}

watch(() => props.noticeId, async notice => {
  const generation = ++revision;
  lines.value = [];
  references.value = undefined;
  error.value = "";
  loading.value = !!notice;
  if (!notice) return;
  try {
    const result = await props.api.receiptReferences(notice);
    if (generation !== revision) return;
    references.value = result;
    addLine();
  } catch (cause) {
    if (generation === revision) error.value = failure(cause);
  } finally {
    if (generation === revision) loading.value = false;
  }
}, { immediate: true });

function addLine() {
  const only = references.value?.lines;
  lines.value.push(newLine(only?.length === 1 ? only[0].id : ""));
  const line = lines.value.at(-1)!;
  if (line.noticeLineId) void changeNoticeLine(line);
}

const sourceLine = (line: Line) => references.value?.lines.find(entry => entry.id === line.noticeLineId);
const needsConversion = (line: Line) => {
  const unit = sourceLine(line)?.unitId;
  return Boolean(unit && unit !== references.value?.kilogram.id);
};

async function changeNoticeLine(line: Line) {
  const request = ++line.unitRequest;
  line.conversion = undefined;
  line.conversions = [];
  line.error = "";
  const source = sourceLine(line);
  const kilogram = references.value?.kilogram;
  if (!source || !kilogram || !needsConversion(line)) return;
  try {
    const result = await props.api.conversionOptions(kilogram.id, source.unitId);
    if (request !== line.unitRequest) return;
    line.conversions = result;
    if (result.length === 1) line.conversion = result[0];
  } catch (cause) {
    if (request === line.unitRequest) line.error = failure(cause);
  }
}

async function searchStorage(line: Line, kind: "area" | "location", keyword: string) {
  const parent = kind === "area" ? references.value?.warehouse.code : line.area?.code;
  if (!parent) return;
  const sequence = kind === "area" ? "areaRequest" : "locationRequest";
  const request = ++line[sequence];
  line.error = "";
  try {
    const result = await props.api.storageOptions(kind,parent,keyword.trim());
    if (request !== line[sequence]) return;
    if (kind === "area") line.areas = result;
    else line.locations = result;
  } catch (cause) {
    if (request === line[sequence]) line.error = failure(cause);
  }
}

function changeArea(line: Line) {
  line.locationRequest++;
  line.location = undefined;
  line.locations = [];
}

/** 只提交选中的引用及其服务端版本，后端仍复核权限、数量和归属。 */
function payload(): Record<string, unknown> {
  const current = references.value;
  const missing = () => new Error(label("请补齐通知行、库位和批次，核对计量单位", "Complete notice line, location, batch and unit"));
  if (!current || !lines.value.length) throw missing();
  const lpns = new Set<string>();
  const items = lines.value.map(line => {
    if (!sourceLine(line) || !line.area || !line.location || !line.batchNo.trim()
      || (needsConversion(line) && !line.conversion)) throw missing();
    const shared = {
      noticeLineId: line.noticeLineId, batchNo: line.batchNo.trim(), kilogramUnitId: current.kilogram.id,
      position: {
        warehouseId: current.warehouse.id, areaId: line.area.id, locationId: line.location.id,
        warehouseVersion: current.warehouse.version, areaVersion: line.area.version, locationVersion: line.location.version
      },
      conversionRuleId: line.conversion?.id, conversionRuleVersion: line.conversion?.version
    };
    if (!props.steel) return shared;
    const { lpn, heatNo, material, specification, theoreticalKg } = line;
    if (![lpn, heatNo, material, specification].every(value => value.trim())
      || !/^\d{1,14}(\.\d{1,4})?$/.test(theoreticalKg) || !/[1-9]/.test(theoreticalKg)) {
      throw new Error(label("请补齐卷包字段，理重须为正数且最多四位小数", "Complete LPN fields and enter a positive theoretical weight"));
    }
    const key = lpn.trim().toUpperCase();
    if (lpns.has(key)) throw new Error(label("卷包号不能重复", "LPN must be unique"));
    lpns.add(key);
    return { ...shared, lpn: lpn.trim(), heatNo: heatNo.trim(), material: material.trim(), specification: specification.trim(), theoreticalKg };
  });
  return props.steel ? { lines: items } : items[0];
}

defineExpose({ payload });
</script>

<template>
  <section v-loading="loading" class="flex min-h-12 flex-col gap-3">
    <FloatingError :title="error" />
    <el-text v-if="!noticeId" type="info">{{ label("选择作业后加载可用的入库明细资料", "Select a task to load receipt references") }}</el-text>
    <template v-if="references">
      <el-form-item :label="label('来源仓库', 'Source warehouse')">
        <el-input :model-value="references.warehouse.label" readonly />
      </el-form-item>
      <el-card v-for="(line, index) in lines" :key="line.key" shadow="never">
        <template v-if="steel" #header>
          <div class="flex items-center justify-between">
            <span>{{ label("卷包明细", "LPN line") }} {{ index + 1 }}</span>
            <el-button link type="danger" :disabled="lines.length === 1" @click="lines.splice(index,1)">
              {{ label("移除", "Remove") }}
            </el-button>
          </div>
        </template>
        <FloatingError :title="line.error" />
        <el-form-item :label="label('通知单行', 'Notice line')" required>
          <el-select v-model="line.noticeLineId" filterable @change="changeNoticeLine(line)">
            <el-option v-for="item in references.lines" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="label('库区', 'Area')" required>
          <el-select v-model="line.area" value-key="id" filterable remote clearable :remote-method="query => searchStorage(line,'area',query)"
            @visible-change="open => { if (open) void searchStorage(line,'area',''); }" @change="changeArea(line)">
            <el-option v-for="item in line.areas" :key="item.id" :label="item.label" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="label('库位', 'Location')" required>
          <el-select v-model="line.location" value-key="id" filterable remote clearable :disabled="!line.area"
            :remote-method="query => searchStorage(line,'location',query)"
            @visible-change="open => { if (open) void searchStorage(line,'location',''); }">
            <el-option v-for="item in line.locations" :key="item.id" :label="item.label" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="label('库存单位', 'Inventory unit')">
          <el-input :model-value="sourceLine(line)?.unitName" readonly />
        </el-form-item>
        <el-form-item v-if="needsConversion(line)" :label="label('千克换算规则', 'Kilogram conversion')" required>
          <el-select v-model="line.conversion" value-key="id"
            :no-data-text="label('没有可用的千克到库存单位换算规则', 'No conversion from kilograms to the inventory unit')">
            <el-option v-for="item in line.conversions" :key="item.id" :label="item.label" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="label('批次号', 'Batch number')" required>
          <el-input v-model.trim="line.batchNo" maxlength="64" />
        </el-form-item>
        <template v-if="steel">
          <el-form-item v-for="[key, zh, en] in steelFields" :key="key" :label="label(zh, en)" required>
            <el-input v-model.trim="line[key]" :maxlength="key === 'theoreticalKg' ? 19 : 64"
              :inputmode="key === 'theoreticalKg' ? 'decimal' : 'text'" />
          </el-form-item>
        </template>
      </el-card>
      <el-button v-if="steel" v-operation="'BulkWarehouseReceiptOrder.create_steel_draft'" type="primary" plain @click="addLine">{{ label("增加卷包", "Add LPN") }}</el-button>
    </template>
  </section>
</template>
