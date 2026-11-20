<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import type { LoadFunction, TreeInstance } from "element-plus";
import { TreeListTemplate, FloatingError, setErrorFeedback, AppPagination } from "@appnexus/ui";
import Management from "./Management.vue";
import type { MasterDataApi, MasterDataKind, MasterDataRow } from "./index";

/** 仓库、库区、库位同页导航；树仅缩小查询范围，所有读取及写入仍由服务端授权。 */
const props=defineProps<{api:MasterDataApi;errorText:(error:unknown)=>string}>();
const {t}=useI18n();
type StorageNode={key:string;kind:MasterDataKind;row:MasterDataRow;leaf:boolean;more?:boolean;page?:number;scope?:string};
const tree=ref<TreeInstance>(),collapsed=ref(false),error=ref(""),revision=ref(0);
const warehouse=ref<MasterDataRow>(),area=ref<MasterDataRow>();
const rootPage=ref(1),rootTotal=ref(0),treeLoading=ref(false);
/** 每层按 20 条读取；根节点分页，子层以续载节点分页，避免大目录全量预取。 */
async function readNodes(kind:MasterDataKind,scope:string|undefined,page:number){
  const result=await props.api.list(kind,{keyword:"",page,size:20,scope});
  const entries:StorageNode[]=result.rows.map(row=>({key:kind+":"+row.id,kind,row,leaf:kind==="storage-location"}));
  if(scope&&result.rows.length&&page*20<result.total)entries.push({key:kind+":"+scope+":more:"+page,kind,row:{...result.rows[0],name:t("geographyHierarchy.more")},leaf:true,more:true,page:page+1,scope});
  if(kind==="warehouse")rootTotal.value=result.total;
  return entries;
}
/** 加载失败拒绝当前层，保留展开重试，不将无权或故障伪装为空目录。 */
const loadTree:LoadFunction=async(node,resolve,reject)=>{
  treeLoading.value=true;
  try{
    const data=node.data as StorageNode;
    const kind=node.level===0?"warehouse":data.kind==="warehouse"?"storage-area":"storage-location";
    resolve(await readNodes(kind,node.level===0?undefined:data.row.code,node.level===0?rootPage.value:1));
  }catch(cause){reject();await setErrorFeedback(error,props.errorText(cause));}
  finally{treeLoading.value=false;}
};
/** 选择库区联动库位列表；选择库位保留其父级范围，不把叶节点编码当作库区。 */
async function selectNode(data:StorageNode){
  if(data.more){
    if(treeLoading.value)return;
    treeLoading.value=true;
    try{
      const entries=await readNodes(data.kind,data.scope,data.page!);
      const parent=tree.value?.getNode(data.key)?.parent;
      if(parent){entries.forEach(entry=>tree.value?.append(entry,parent));tree.value?.remove(data);}
    }catch(cause){await setErrorFeedback(error,props.errorText(cause));}
    finally{treeLoading.value=false;}
    return;
  }
  if(data.kind==="warehouse"){warehouse.value=data.row;area.value=undefined;return;}
  const node=tree.value?.getNode(data.key);
  if(data.kind==="storage-area"){warehouse.value=node?.parent?.data.row;area.value=data.row;}
  else{warehouse.value=node?.parent?.parent?.data.row;area.value=node?.parent?.data.row;}
}
/** 写入后重建已授权目录，保留主从上下文；列表自身重新读取版本及审计。 */
function refreshTree(){revision.value++;}
/** 根分页切换清理原范围，避免隐藏选择导致新增归属不明。 */
function changeRoot(){warehouse.value=undefined;area.value=undefined;refreshTree();}
</script>

<template>
  <TreeListTemplate :title="t('masterData.storage-area')" :tree-label="t('storageHierarchy.treeTitle')" v-model:collapsed="collapsed">
    <template #treeHeader><strong>{{t('storageHierarchy.treeTitle')}}</strong></template>
    <template #tree>
      <FloatingError :title="error"><el-button v-operation="'Warehouse.list'" :loading="treeLoading" @click="refreshTree">{{t('masterData.retry')}}</el-button></FloatingError>
      <el-tree :key="revision" ref="tree" node-key="key" lazy highlight-current :load="loadTree" :props="{label:(data:StorageNode)=>data.more?data.row.name:data.row.code+' · '+data.row.name,isLeaf:'leaf'}" :expand-on-click-node="false" @node-click="selectNode" />
      <AppPagination v-if="rootTotal>20" v-model:current-page="rootPage" :page-size="20" :total="rootTotal" :disabled="treeLoading" @current-change="changeRoot" />
    </template>
    <template #header>
      <el-breadcrumb separator="/"><el-breadcrumb-item>{{warehouse?warehouse.code+' · '+warehouse.name:t('storageHierarchy.selectWarehouse')}}</el-breadcrumb-item><el-breadcrumb-item v-if="area">{{area.code}} · {{area.name}}</el-breadcrumb-item></el-breadcrumb>
    </template>
    <template #table>
      <div class="business-workspace-stack">
        <section :aria-label="t('masterData.storage-area')">
          <Management :key="'area:'+warehouse?.id" embedded kind="storage-area" :api="api" :error-text="errorText" :storage-parent="warehouse" @selected="area=$event" @changed="area=undefined;refreshTree()" />
        </section>
        <section :aria-label="t('masterData.storage-location')">
          <Management :key="'location:'+area?.id" embedded kind="storage-location" :api="api" :error-text="errorText" :storage-parent="area" @changed="refreshTree" />
        </section>
      </div>
    </template>
  </TreeListTemplate>
</template>
