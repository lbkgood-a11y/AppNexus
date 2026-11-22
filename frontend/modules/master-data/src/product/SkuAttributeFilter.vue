<script setup lang="ts">
import { useI18n } from "vue-i18n";

/** 商品明细属性筛选，只接收当前商品已加载的属性；输入值按显示文本做包含匹配。 */
const model = defineModel<{ attributeId: string; attributeValue: string }>({ required: true });
defineProps<{
  options: ReadonlyArray<{ id: string; name: string }>;
}>();
const { t } = useI18n();

/** 切换或清除属性时同步清空旧值，避免将上一属性的条件用于新属性。 */
function selectAttribute(attributeId?: string) {
  model.value = { attributeId: attributeId ?? "", attributeValue: "" };
}
</script>

<template>
  <el-input
    class="sku-attribute-filter"
    :model-value="model.attributeValue"
    :disabled="!model.attributeId"
    :aria-label="t('productSku.attributeValue')"
    :placeholder="t('productSku.attributeValue')"
    clearable
    @update:model-value="attributeValue => model = { ...model, attributeValue }"
  >
    <template #prepend>
      <el-select
        class="sku-attribute-filter__select"
        :model-value="model.attributeId"
        :disabled="!options.length"
        :aria-label="t('productSku.attribute')"
        :placeholder="t('productSku.attribute')"
        clearable
        filterable
        @change="selectAttribute"
      >
        <el-option v-for="option in options" :key="option.id" :value="option.id" :label="option.name" />
      </el-select>
    </template>
  </el-input>
</template>

<style scoped>
.sku-attribute-filter {
  width: 320px;
  max-width: 100%;
}
.sku-attribute-filter__select {
  width: 140px;
}
</style>
