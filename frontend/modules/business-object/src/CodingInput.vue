<script setup lang="ts">
import {ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessageBox} from "element-plus";
import {ManagedDialog,FloatingError,setErrorFeedback} from "@appnexus/ui";
/**
 * 复用 Element Plus 编码输入和 ManagedDialog；现有输入框无服务端发号、幂等重试及覆盖确认能力。
 * 页面显式注入操作标识和公开 API，组件不持有租户、身份或权限源。
 */
const props=defineProps<{
 /** 当前手工或已发放编码；组件不会在挂载时覆盖。 */
 modelValue:string;
 /** 可选的手工录入提示；默认继承 Element Plus 行为。 */
 placeholder?:string;
 /** 同一业务声明的目标操作标识，必填；后端仍重新鉴权。 */
 operation:string;
 /** 最大字符数，1–128，必填；必须与业务字段和Provider声明一致。 */
 maxLength:number;
 /** 只读、保存中等业务状态由调用方显式传入，缺省false；不代表授权。 */
 disabled?:boolean;
 /** 读取当前字段的真实可用编码器，不消耗号码。 */
 options:()=>Promise<{encoderKey:string;name:string}[]>;
 /** 发放编码；requestId为幂等UUID，同一次失败重试必须复用。 */
 issue:(key:string,requestId:string)=>Promise<{code:string}>;
 /** 复用应用统一错误码翻译，不回显服务端内部异常。 */
 errorText:(e:unknown)=>string;
}>();
const emit=defineEmits<{"update:modelValue":[value:string]}>();
const {t}=useI18n();const dialog=ref(false),loading=ref(false),busy=ref(false),error=ref(""),rows=ref<{encoderKey:string;name:string}[]>([]),selected=ref("");
/** 网络失败后保留原请求键；仅主动选择另一编码器或成功后清除，不自动重放写请求。 */
const pending=ref<{encoder:string;request:string}>();
/** 仅读取真实候选，不自动发号；禁用状态不允许重新打开协助流程。 */
async function open(){if(props.disabled)return;dialog.value=true;loading.value=true;error.value="";try{rows.value=await props.options();if(!rows.value.some(r=>r.encoderKey===selected.value))selected.value=rows.value[0]?.encoderKey??"";}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
/** 主动发号在覆盖确认前进入互斥；取消不发请求，失败保留原幂等键供显式重试。 */
async function generate(){
 if(props.disabled||!selected.value||busy.value)return;
 // 确认窗口也属于同一次发号操作，先互斥，防止连续点击打开多个确认后分别耗号。
 busy.value=true;
 if(props.modelValue){try{await ElMessageBox.confirm(t('coding.replace'),t('coding.assist'),{type:'warning'});}catch{busy.value=false;return;}}
 error.value="";
 try{if(!pending.value||pending.value.encoder!==selected.value)pending.value={encoder:selected.value,request:crypto.randomUUID()};const result=await props.issue(pending.value.encoder,pending.value.request);emit('update:modelValue',result.code);pending.value=undefined;dialog.value=false;}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}
}
</script>
<template>
 <el-input :model-value="modelValue" :maxlength="maxLength" :placeholder="placeholder" :disabled="disabled||busy" @update:model-value="emit('update:modelValue',$event)"><template #append><el-button v-operation="operation" :disabled="disabled||busy" @click="open">{{t('coding.assist')}}</el-button></template></el-input>
 <ManagedDialog v-model="dialog" :title="t('coding.assist')" width="min(520px,calc(100vw - 32px))" :close-on-click-modal="false" :busy="busy">
  <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" />
  <div v-loading="loading"><el-alert :title="t('coding.issueHint')" type="info" :closable="false" /><el-form label-position="top" class="business-border-label-form"><el-form-item :label="t('coding.select')"><el-select v-model="selected" :disabled="busy||loading"><el-option v-for="row in rows" :key="row.encoderKey" :label="row.name" :value="row.encoderKey" /></el-select></el-form-item></el-form><el-empty v-if="!loading&&!error&&!rows.length" :description="t('coding.empty')" /></div>
  <template #footer><el-button :disabled="busy" @click="dialog=false">{{t('coding.cancel')}}</el-button><el-button v-operation="operation" :disabled="busy" @click="open">{{t('businessObject.refresh')}}</el-button><el-button v-operation="operation" type="primary" :loading="busy" :disabled="disabled||loading||!selected" @click="generate">{{t('coding.generate')}}</el-button></template>
 </ManagedDialog>
</template>
