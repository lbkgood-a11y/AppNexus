<script setup lang="ts">
import {computed} from 'vue';
import {useI18n} from 'vue-i18n';
import {nodePermissions,type MenuPermissionNode} from './menuPermissionView';
/** 递归组合 Element Plus 复选框与折叠面板；现有平面表格无法表达任意深度菜单和共享操作。 */
const props=defineProps<{node:MenuPermissionNode;selected:string[];disabled:boolean}>();
const emit=defineEmits<{toggle:[ids:string[],enabled:boolean]}>();
const {locale,t}=useI18n();
const items=computed(()=>nodePermissions(props.node));
const checked=computed(()=>items.value.length>0&&items.value.every(item=>props.selected.includes(item.id)));
const partial=computed(()=>!checked.value&&items.value.some(item=>props.selected.includes(item.id)));
</script>
<template>
 <el-collapse class="menu-permission-branch" :model-value="[node.id]">
  <el-collapse-item :name="node.id">
   <template #title><div class="branch-heading"><span>{{node.label}}</span><el-checkbox :model-value="checked" :indeterminate="partial" :disabled="disabled||!items.length" :aria-label="node.label+' · '+t('authorizationCenter.selectResource')" @click.stop @change="value=>emit('toggle',items.map(item=>item.id),Boolean(value))">{{t('authorizationCenter.selectResource')}}</el-checkbox></div></template>
   <div v-if="node.items.length" class="menu-operation-grid"><el-checkbox v-for="item in node.items" :key="item.id" :model-value="selected.includes(item.id)" :disabled="disabled||!item.enabled" @change="value=>emit('toggle',[item.id],Boolean(value))"><span>{{locale.startsWith('zh')?item.nameZh:item.nameEn}}</span><small>{{item.code}}</small></el-checkbox></div>
   <MenuPermissionBranch v-for="child in node.children" :key="child.id" :node="child" :selected="selected" :disabled="disabled" @toggle="(ids,enabled)=>emit('toggle',ids,enabled)" />
  </el-collapse-item>
 </el-collapse>
</template>
<style scoped>
.menu-permission-branch{border-top:0;margin:0 0 12px;border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base);padding:0 16px}
.branch-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;width:100%;padding-right:12px;text-align:left;font-weight:600}
.menu-operation-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 24px;padding:12px 0}
.menu-operation-grid :deep(.el-checkbox){height:auto;min-height:40px;margin:0;align-items:flex-start}
.menu-operation-grid :deep(.el-checkbox__input){margin-top:4px}
.menu-operation-grid :deep(.el-checkbox__label){display:flex;flex-direction:column;gap:4px;white-space:normal;overflow-wrap:anywhere}
small{color:var(--el-text-color-secondary)}
@media(max-width:700px){.menu-operation-grid{grid-template-columns:1fr}.branch-heading{flex-wrap:wrap;gap:4px}.menu-permission-branch{padding:0 12px}}
</style>
