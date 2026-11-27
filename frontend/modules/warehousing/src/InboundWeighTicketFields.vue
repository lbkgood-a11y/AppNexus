<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { WeighTicket } from "./inboundWorkbench";
import { workbenchMessages } from "./inboundWorkbenchMessages";

/** 磅票组合字段。采集时间随本机时区录入，提交前统一转换为 UTC。 */
const props = defineProps<{ modelValue: WeighTicket }>();
const emit = defineEmits<{ "update:modelValue": [ticket: WeighTicket] }>();
const { locale } = useI18n();
const label = (key: keyof typeof workbenchMessages.en) =>
  workbenchMessages[locale.value.startsWith("zh") ? "zh" : "en"][key];
const fields = [{ key: "station", limit: 64 }, { key: "ticketNo", limit: 96 }, { key: "kg", limit: 19 }] as const;

function update(key: keyof WeighTicket, value: string | null) {
  emit("update:modelValue", { ...props.modelValue, [key]: value ?? "" });
}
</script>

<template>
  <div class="flex w-full flex-col gap-2">
    <el-form-item v-for="field in fields" :key="field.key" :label="label(field.key)">
      <el-input :model-value="modelValue[field.key]" :maxlength="field.limit"
        :inputmode="field.key === 'kg' ? 'decimal' : 'text'" @update:model-value="value => update(field.key, value)" />
    </el-form-item>
    <el-form-item :label="label('observedAt')">
      <el-date-picker :model-value="modelValue.observedAt" type="datetime"
        value-format="YYYY-MM-DDTHH:mm:ssZ" @update:model-value="value => update('observedAt', typeof value === 'string' ? value : null)" />
    </el-form-item>
  </div>
</template>
