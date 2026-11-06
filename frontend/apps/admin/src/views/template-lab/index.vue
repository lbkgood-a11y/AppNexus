<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { DetailFormTemplate, PageHeader, VerticalSplitTemplate } from "@appnexus/ui";

/**
 * 模板验收页仅验证 T03/T04 布局、校验与主题，不注册生产菜单、不请求后端或伪造生产业务。
 * 示例项是静态组件状态，刷新即清除，不构成业务数据源。
 */
const { t } = useI18n();
const template = ref<"vertical" | "form">("vertical");
const selected = ref("");
const dirty = ref(false);
const form = reactive({ name: "", code: "", description: "" });
const examples = computed(() => [
  { id: "master-a", name: t("templateLab.masterA"), status: t("administration.enabled") },
  { id: "master-b", name: t("templateLab.masterB"), status: t("administration.disabled") }
]);
</script>

<template>
  <div class="template-lab">
    <el-alert :title="t('templateLab.notice')" type="info" show-icon :closable="false" />
    <el-segmented v-model="template" :options="[{label:t('templateLab.vertical'),value:'vertical'},{label:t('templateLab.form'),value:'form'}]" />
    <VerticalSplitTemplate v-if="template==='vertical'" :selected="Boolean(selected)">
      <template #header><PageHeader :title="t('templateLab.vertical')" :description="t('templateLab.verticalDescription')" /></template>
      <template #master>
        <h2 class="lab-title">{{t('templateLab.master')}}</h2>
        <el-table :data="examples" highlight-current-row @current-change="row=>selected=row?.id || ''"><el-table-column prop="name" :label="t('templateLab.name')" /><el-table-column prop="status" :label="t('administration.status')" /></el-table>
      </template>
      <template #detail><h2 class="lab-title">{{t('templateLab.detail')}}</h2><el-descriptions border><el-descriptions-item :label="t('templateLab.selected')">{{selected}}</el-descriptions-item></el-descriptions></template>
      <template #empty><el-empty :description="t('templateLab.selectMaster')" /></template>
    </VerticalSplitTemplate>
    <DetailFormTemplate v-else mode="edit" :dirty="dirty">
      <template #header><PageHeader :title="t('templateLab.form')" :description="t('templateLab.formDescription')" /></template>
      <el-form :model="form" label-position="top" @input="dirty=true">
        <h2 class="form-section lab-title">{{t('templateLab.basic')}}</h2>
        <el-form-item :label="t('templateLab.name')" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('templateLab.code')" required><el-input v-model="form.code" /></el-form-item>
        <el-form-item class="form-span-full" :label="t('templateLab.description')"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #actions><el-button @click="Object.assign(form,{name:'',code:'',description:''});dirty=false">{{t('organization.cancel')}}</el-button><el-button type="primary" @click="dirty=false">{{t('organization.save')}}</el-button></template>
    </DetailFormTemplate>
  </div>
</template>

<style scoped>
.template-lab { display: grid; gap: 16px; }
.lab-title { margin: 0 0 16px; font-size: var(--el-font-size-medium); font-weight: 600; }
</style>
