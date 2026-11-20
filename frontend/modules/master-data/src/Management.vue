<script setup lang="ts">
import { validateNavigationPage, GeographyTreeContractError } from "./navigation";
import WarehouseEditor from "./WarehouseEditor.vue";
import PartnerEditor from "./PartnerEditor.vue";
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type LoadFunction, type TreeInstance } from "element-plus";
import { BusinessWorkspace, TreeListTemplate, ManagementTable, ManagedDialog, RecordAuditDetails, AppPagination, InfoHint, FloatingError, setErrorFeedback, type ManagementColumn } from "@appnexus/ui";
import { masterDataFields, masterDataOperations, type MasterDataApi, type MasterDataKind, type MasterDataRow, type MasterDataInput } from "./index";

/** 主数据档案复用现有工作区、表格和弹窗；表单差异来自固定字段契约，不构建平行 UI 体系。 */
const props=defineProps<{kind:MasterDataKind;api:MasterDataApi;errorText:(error:unknown)=>string;
  /** 嵌入库区主从工作区时隐藏重复横幅，默认 false。 */
  embedded?:boolean;
  /** 库区或库位的直属上级编码，必填；名称仅展示，不参与授权或写入。 */
  storageParent?:{code:string;name:string;enabled:boolean}}>();
const emit=defineEmits<{changed:[];selected:[row:MasterDataRow]}>();
const {t,te}=useI18n();
/** 区划类型仅做中英双语展示；未知标准类型原样保留，不改变提交及校验原值。 */
function subdivisionTypeLabel(value?:string){
  if(!value)return "—";
  const key="subdivisionTypes."+value;
  return te(key)?t(key):value;
}
/** 库区/库位只读详情与编辑共用弹窗；查看不授予写权限。 */
const storageReadonly=ref(false);
const isStorage=computed(()=>props.kind==="storage-area"||props.kind==="storage-location");
const areaTypes=["RECEIVING","WORK","GOOD","DEFECTIVE","PACKAGING","VIRTUAL","FINISHED"];
const isGeography=computed(()=>props.kind==="country-region"||props.kind==="subdivision");
/** 左树只加载展开层，每次最多 50 条；更多节点通过显式按钮续载，不一次获取完整目录。 */
type NavigationNode=import("./index").GeographyNavigationNode & {moreParent?:string;nextPage?:number};
const treeCollapsed=ref(false),treeRef=ref<TreeInstance>(),treeScope=ref(""),treeScopeName=ref("");
const treeMoreLoading=ref(false),treeFailed=ref(false),treeRevision=ref(0);
/** 每次展开独立读取当前层；失败调用 reject 保留重试能力。 */
const loadTree:LoadFunction=async(node,resolve,reject)=>{
  if(props.kind!=="country-region"&&props.kind!=="subdivision")return resolve([]);
  const parent=node.level===0?"":node.data.code;
  try{
    const result=await props.api.geographyTree(props.kind,{parent,page:1,size:50});
    const excluded=new Set<string>();let ancestor=node;
    while(ancestor.level>0){excluded.add(ancestor.data.code);ancestor=ancestor.parent!;}
    const valid=validateNavigationPage(result,parent,node.level===0?"":node.data.hierarchyCode,excluded,1);
    treeFailed.value=false;resolve(navigationPage(valid,parent,1));
  }catch(cause){reject();treeFailed.value=true;await setErrorFeedback(error,cause instanceof GeographyTreeContractError?t("geographyHierarchy.invalidTree"):props.errorText(cause));}
}
/** 续载占位节点不代表业务范围；分页仅作用于同一父级的子节点。 */
function navigationPage(result:{rows:import("./index").GeographyNavigationNode[];total:number},parent:string,current:number):NavigationNode[]{
  const nodes:NavigationNode[]=[...result.rows];
  if(current*50<result.total)nodes.push({code:"more:"+parent+":"+(current+1),name:t("geographyHierarchy.more"),originalName:"",hierarchyCode:"",parentCode:parent,parentHierarchyCode:"",leaf:true,moreParent:parent,nextPage:current+1});
  return nodes;
}
/** 选树节点只缩小列表范围；查询与重置保留范围，全部按钮显式清除范围。 */
async function selectTree(node:NavigationNode){
  if(node.moreParent!==undefined){
    if(treeMoreLoading.value||props.kind!=="country-region"&&props.kind!=="subdivision")return;
    treeMoreLoading.value=true;
    try{
      const result=await props.api.geographyTree(props.kind,{parent:node.moreParent,page:node.nextPage!,size:50});
      const parent=treeRef.value?.getNode(node.code)?.parent;
      if(parent){
        const excluded=new Set<string>(parent.childNodes.filter(child=>child.data.moreParent===undefined).map(child=>child.data.code));
        let ancestor=parent;while(ancestor.level>0){excluded.add(ancestor.data.code);ancestor=ancestor.parent!;}
        const valid=validateNavigationPage(result,node.moreParent,parent.level===0?"":parent.data.hierarchyCode,excluded,node.nextPage!);
        for(const entry of navigationPage(valid,node.moreParent,node.nextPage!))treeRef.value?.append(entry,parent);
        treeRef.value?.remove(node);treeRef.value?.setCurrentKey(treeScope.value||undefined);
      }
    }catch(cause){await setErrorFeedback(error,cause instanceof GeographyTreeContractError?t("geographyHierarchy.invalidTree"):props.errorText(cause));}finally{treeMoreLoading.value=false;}
    return;
  }
  if(loading.value||busy.value){treeRef.value?.setCurrentKey(treeScope.value||undefined);return;}
  treeScope.value=node.code;treeScopeName.value=node.name;page.value=1;void load();
}
/** 显式重建树以重试根加载，避免错误响应自动反复请求。 */
function retryTree(){treeFailed.value=false;treeRevision.value++;}
/** 清除地理范围，关键词与启停筛选保持用户输入。 */
function clearTreeScope(){if(loading.value||busy.value)return;treeScope.value="";treeScopeName.value="";treeRef.value?.setCurrentKey(undefined);page.value=1;void load();}
/** 上级选择复用 Element Plus TreeSelect；仅展开当前层，分页读取同层，不预取后代。 */
const parentCache=ref<{code:string;value:string;label:string;hierarchyCode:string}[]>([]);
/** 编辑层级快照只用于展示，不进入写入属性白名单；不使用左树查询范围覆盖记录父级。 */
const editingHierarchy=ref({code:"",parentCode:"",parentHierarchyCode:""});
/** 当前上级从回显缓存或已加载选项获取，换上级后立即更新层级预览。 */
const selectedParentHierarchy=computed(()=>parentCache.value.find(entry=>entry.code===draft.attributes.parentCode)?.hierarchyCode??"");
const currentHierarchy=computed(()=>{
  const parent=selectedParentHierarchy.value;if(!parent||!draft.code)return "";
  const original=editingHierarchy.value;
  if(editingId.value&&draft.attributes.parentCode===original.parentCode)return original.code;
  const prefix=original.parentHierarchyCode+"-";
  const segment=editingId.value&&original.parentHierarchyCode&&original.code.startsWith(prefix)?original.code.slice(prefix.length):draft.code;
  return parent+"-"+segment;
});
const parentRevision=ref(0);
const parentLabel=ref("");
const loadParents:LoadFunction=async(node,resolve,reject)=>{
  if(props.kind!=="country-region"&&props.kind!=="subdivision")return resolve([]);
  const parent=node.level===0?"":node.data.code,revision=parentRevision.value;
  try{
    const entries:NavigationNode[]=[];let current=1,total=0;
    do{
      const result=await props.api.geographyTree(props.kind,{parent,page:current,size:50});
      const excluded=new Set(entries.map(item=>item.code));let ancestor=node;
      while(ancestor.level>0){excluded.add(ancestor.data.code);ancestor=ancestor.parent!;}
      const valid=validateNavigationPage(result,parent,node.level===0?"":node.data.hierarchyCode,excluded,current);
      entries.push(...valid.rows);total=valid.total;current++;
    }while(entries.length<total);
    if(revision!==parentRevision.value)return resolve([]);
    const options=entries.map(entry=>({code:entry.code,value:entry.code,label:entry.hierarchyCode+' · '+entry.name,hierarchyCode:entry.hierarchyCode}));
    const cache=new Map(parentCache.value.map(entry=>[entry.code,entry]));
    for(const entry of options)cache.set(entry.code,entry);parentCache.value=[...cache.values()];
    resolve(entries.map(entry=>({...entry,value:entry.code,label:entry.hierarchyCode+' · '+entry.name,disabled:entry.code===draft.code||(props.kind==="country-region"?!entry.code.startsWith("continent:"):entry.code.startsWith("continent:"))})));
  }catch(cause){reject();await setErrorFeedback(error,props.errorText(cause));}
};
/** 切换父级仅修改草稿；所属国别由服务端父链推导，不接受客户端指定归属。 */
function changeParent(value:string){
  parentLabel.value=parentCache.value.find(item=>item.value===value)?.label??value;
  if(props.kind==="subdivision")draft.attributes.countryCode="";
}
/** 标准检索作为可选填充，不限制手工输入；请求序号防止旧响应覆盖新搜索。 */
const geography=ref<import("./index").GeographyOption[]>([]),geographyLoading=ref(false);
const geographyPage=ref(1),geographyTotal=ref(0),geographyKeyword=ref("");
let geographyRequest=0;
async function loadGeography(){
  if(props.kind!=="country-region"&&props.kind!=="subdivision")return;
  const request=++geographyRequest;geographyLoading.value=true;
  try{const result=await props.api.geographyOptions(props.kind,{keyword:geographyKeyword.value,page:geographyPage.value,size:20});if(request===geographyRequest){geography.value=result.rows;geographyTotal.value=result.total;}}
  catch(cause){if(request===geographyRequest){geography.value=[];geographyTotal.value=0;await setErrorFeedback(error,props.errorText(cause));}}
  finally{if(request===geographyRequest)geographyLoading.value=false;}
}
/** 编码或名称子串查询，变更关键词回第一页，每页20条。 */
function searchGeography(query:string){geographyKeyword.value=query;geographyPage.value=1;void loadGeography();}
/** 标准仅用于填充本级资料；显式选择的上级保持不变，提交仍由后端验证父链。 */
function selectGeography(code:string){
  const entry=geography.value.find(item=>item.code===code);if(!entry)return;
  draft.code=entry.code;draft.name=entry.name;
  for(const field of fields.value)if(field!=="parentCode")draft.attributes[field]=entry.attributes[field]??(field==="englishName"?entry.name:"");
}
const partnerOptions=ref<import("./index").DictionaryOption[]>([]);
const operation=computed(()=>masterDataOperations[props.kind]);
/** 列显隐取自服务端字段限制；无数据时仍使用权限元数据，不能以表格偏好代替权限。 */
const partnerFieldRestrictions=ref<Record<string,Record<string,string>>>({});
const fields=computed(()=>masterDataFields[props.kind].filter(field=>props.kind!=="partner"||partnerFieldRestrictions.value["masterdata.partner"]?.[field]!=="HIDDEN"));
/** 上级统一放在表单首项选择，避免与只读列重复展示。 */
const visibleFields=computed(()=>fields.value.filter(field=>(!isGeography.value&&!isStorage.value)||field!=="parentCode"));
const title=computed(()=>t("masterData."+props.kind));
const rows=ref<MasterDataRow[]>([]),total=ref(0),page=ref(1),size=ref(20);
const keyword=ref(""),status=ref("all"),loading=ref(false),busy=ref(false),failed=ref(false),ready=ref(false);
const operations=ref<string[]>([]),error=ref(""),open=ref(false),editingId=ref("");
const form=ref<FormInstance>();
/** 客商复杂档案复用主数据详情模板，其他对象保留原维护入口。 */
const partnerEditor=ref(false);
/** 详情入口只读；编辑入口显式传 edit，避免两个按钮共享同一编辑态。 */
const partnerEditorMode=ref<"view"|"edit">("edit");
/** 仓库详情独立保存字段与字典契约。 */
const warehouseEditor=ref(false);
const warehouseEditorMode=ref<"view"|"edit">("edit");
const warehouseOptions=ref<Record<string,import("./index").DictionaryOption[]>>({});
const partnerId=ref("");
/** 审计只取原已授权记录，独立于编辑草稿，不进入写入参数。 */
const editingAudit=computed(()=>rows.value.find(row=>row.id===editingId.value)?.audit);
const draft=reactive<MasterDataInput>({code:"",name:"",attributes:{},version:0});
/** 已有停用单位保留名称，历史未收录值只读保留，禁止作为其他记录的新选择。 */
const saveOperation=computed(()=>editingId.value?operation.value.update:operation.value.create);
const canRead=computed(()=>operations.value.includes(operation.value.list));
const columns=computed<ManagementColumn[]>(()=>[
  ...(isGeography.value?[{key:"hierarchyCode",label:t("geographyHierarchy.code"),minWidth:230},{key:"parentHierarchyCode",label:t("geographyHierarchy.parentCode"),minWidth:210}]:[]),
  {key:"code",prop:"code",label:t("masterData.code"),minWidth:140},
  {key:"name",prop:"name",label:t("masterData.name"),minWidth:180},
  ...visibleFields.value.map(key=>({key,label:t((props.kind==="warehouse"?"warehouseProfile.fields.":"masterData.fields.")+key),minWidth:150})),
  {key:"status",label:t("masterData.status"),width:110},
  {key:"operations",label:t("masterData.actions"),width:260,fixed:"right"}
]);
/** 前后端字段长度、必填和标准代码约束一致；只改善输入体验，不能替代服务端校验。 */
const rules=computed<FormRules>(()=>{
  const required={required:true,message:t("masterData.required"),trigger:"blur"};
  const result:FormRules={code:[required,{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t("masterData.codeRule"),trigger:"blur"}],name:[required]};
  if(props.kind==="partner")result["attributes.partnerType"]=[required];
  if(props.kind==="country-region"){
    result["attributes.alpha3Code"]=[{pattern:/^[A-Z]{3}$/,message:t("masterData.alpha3Rule"),trigger:"blur"}];
    result["attributes.numericCode"]=[{pattern:/^[0-9]{3}$/,message:t("masterData.numericRule"),trigger:"blur"}];
  }
  if(isGeography.value)result["attributes.parentCode"]=[{required:true,message:t("masterData.required"),trigger:"change"}];
  if(isStorage.value)result["attributes.capacity"]=[{pattern:/^(?:[0-9]{1,18}(?:\.[0-9]{1,6})?)?$/,message:t("storageHierarchy.capacityRule"),trigger:"blur"}];
  return result;
});
/** 查询失败保留上次行数据并显示原区域故障状态；重新获取实时准入，避免撤权后继续展示旧资料。 */
async function load(){
  if(loading.value||busy.value)return;
  loading.value=true;failed.value=false;
  try{
    operations.value=await props.api.operations();ready.value=true;
    if(!canRead.value){rows.value=[];total.value=0;return;}
    if(props.kind==="partner")partnerOptions.value=(await props.api.partnerOptions()).partnerType??[];
    if(props.kind==="warehouse")warehouseOptions.value=await props.api.warehouseOptions();
    if(isStorage.value&&!props.storageParent?.code){rows.value=[];total.value=0;return;}
    if(props.kind==="partner")partnerFieldRestrictions.value=(await props.api.partnerAccess()).fieldRestrictions;
    const result=await props.api.list(props.kind,{keyword:keyword.value,enabled:status.value==="all"?undefined:status.value==="enabled",page:page.value,size:size.value,...(isGeography.value?{scope:treeScope.value}:isStorage.value?{scope:props.storageParent?.code}:{})});
    rows.value=result.rows;total.value=result.total;
  }catch(e){
    failed.value=true;
    // 撤权拒绝时清除已有正文，避免仍展示之前获准读取的敏感档案；网络故障保留原列表以便核实结果。
    if((e as {response?:{status?:number}})?.response?.status===403){rows.value=[];total.value=0;operations.value=[];}
    await setErrorFeedback(error,props.errorText(e));
  }
  finally{loading.value=false;}
}
/** 筛选变化回到第一页；只读取，不写业务数据。 */
function search(){page.value=1;void load();}
/** 重置查询条件后显式重新读取。 */
function reset(){keyword.value="";status.value="all";search();}
/** 编辑使用行快照及原版本；复制属性避免草稿修改直接影响表格。 */
function edit(row?:MasterDataRow,mode:"view"|"edit"="edit"){
  if(props.kind==="partner"){partnerId.value=row?.id??"";partnerEditorMode.value=mode;partnerEditor.value=true;return;}
  if(props.kind==="warehouse"){partnerId.value=row?.id??"";warehouseEditorMode.value=mode;warehouseEditor.value=true;return;}
  storageReadonly.value=isStorage.value&&mode==="view";
  editingId.value=row?.id??"";draft.code=row?.code??"";draft.name=row?.name??"";draft.version=row?.version??0;
  draft.attributes=Object.fromEntries(fields.value.map(key=>[key,row?.attributes[key]??""]));
  if(isGeography.value){
    // 编辑严格取本条记录的直属上级；仅新增沿用左树上下文，避免从其他分支编辑时错挂节点。
    const parentCode=row?(row.attributes.parentCode||(props.kind==="subdivision"?row.attributes.countryCode:row.attributes.parentHierarchyCode?"continent:"+row.attributes.parentHierarchyCode:"")):treeScope.value;
    const parentNode=treeRef.value?.getNode(parentCode)?.data;
    const parentHierarchyCode=row?.attributes.parentHierarchyCode??parentNode?.hierarchyCode??"";
    const parentName=row?.attributes.parentName||parentNode?.name||(!row?treeScopeName.value:"");
    draft.attributes.parentCode=parentCode;
    parentLabel.value=[parentHierarchyCode||parentCode,parentName].filter(Boolean).join(" · ");
    parentCache.value=parentCode?[{code:parentCode,value:parentCode,label:parentLabel.value,hierarchyCode:parentHierarchyCode}]:[];
    editingHierarchy.value={code:row?.attributes.hierarchyCode??"",parentCode,parentHierarchyCode};
    parentRevision.value++;
  }
  if(isStorage.value)draft.attributes.parentCode=row?.attributes.parentCode??props.storageParent?.code??"";
  error.value="";open.value=true;

}
/** 保存成功才关闭；失败保留草稿，409 通过公共浮层提供重新读取入口，不自动覆盖输入。 */
async function save(){
  if(storageReadonly.value||busy.value||!(await form.value?.validate().catch(()=>false)))return;
  busy.value=true;
  try{
    const input={...draft,attributes:{...draft.attributes}};
    if(editingId.value)await props.api.update(props.kind,editingId.value,input);else await props.api.create(props.kind,input);
    open.value=false;emit("changed");ElMessage.success(t("masterData.saved"));busy.value=false;if(isGeography.value)retryTree();await load();
  }catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}
}
/** 启停先确认对象及后果；使用所选行版本，失败不乐观改变表格状态。 */
async function toggle(row:MasterDataRow){
  if(busy.value)return;
  try{await ElMessageBox.confirm(t("masterData."+(row.enabled?"confirmDisable":"confirmEnable"),{name:row.name}),t("masterData.confirmTitle"),{type:row.enabled?"warning":"info",confirmButtonText:t("masterData."+(row.enabled?"disable":"enable")),cancelButtonText:t("masterData.cancel")});}catch{return;}
  busy.value=true;
  try{await props.api.setStatus(props.kind,row.id,{enabled:!row.enabled,version:row.version});emit("changed");ElMessage.success(t("masterData.statusSaved"));busy.value=false;await load();}
  catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}
}
/** 发生冲突后重新读取列表，保留当前编辑草稿，用户可取消后重新打开最新档案。 */
function retry(){void load();}
onMounted(load);
</script>

<template>
  <PartnerEditor v-if="kind==='partner'&&partnerEditor" :id="partnerId" :mode="partnerEditorMode" :api="api" :error-text="errorText" @close="partnerEditor=false;load()" />
  <WarehouseEditor v-else-if="kind==='warehouse'&&warehouseEditor" :id="partnerId" :mode="warehouseEditorMode" :api="api" :error-text="errorText" @close="warehouseEditor=false;load()" />
  <component :is="isGeography?TreeListTemplate:BusinessWorkspace" v-else :title="title" :description="t('masterData.description')" :embedded="embedded" :collapsed="treeCollapsed" :tree-label="t('geographyHierarchy.title')" @update:collapsed="treeCollapsed=$event">
    <template v-if="isGeography" #treeHeader><strong>{{t('geographyHierarchy.title')}}</strong></template>
    <template v-if="isGeography" #tree>
      <el-button v-operation="operation.list" text :disabled="loading||busy" @click="clearTreeScope">{{t('geographyHierarchy.all')}}</el-button>
      <el-button v-if="treeFailed" v-operation="operation.list" text @click="retryTree">{{t('masterData.retry')}}</el-button>
      <el-tree :key="treeRevision" ref="treeRef" node-key="code" lazy highlight-current :load="loadTree" :props="{label:'name',isLeaf:'leaf'}" :expand-on-click-node="false" @node-click="selectTree">
        <template #default="{data}"><span :title="data.hierarchyCode?data.name+' · '+data.hierarchyCode:data.name">{{data.name}}<small v-if="data.hierarchyCode"> · {{data.hierarchyCode}}</small></span></template>
      </el-tree>
    </template>
    <template v-if="isGeography" #header><span>{{treeScopeName||t('geographyHierarchy.all')}}</span></template>
    <template v-if="$slots.navigation" #navigation><slot name="navigation" /></template>
    <template #search>
      <el-form inline @submit.prevent="search">
        <el-form-item :label="t('masterData.keyword')"><el-input v-model="keyword" maxlength="200" clearable /></el-form-item>
        <el-form-item :label="t('masterData.status')"><el-select v-model="status" style="width: 140px"><el-option value="all" :label="t('masterData.all')" /><el-option value="enabled" :label="t('masterData.enabled')" /><el-option value="disabled" :label="t('masterData.disabled')" /></el-select></el-form-item>
        <el-form-item class="management-filter-actions">
          <el-button v-operation="operation.list" type="primary" native-type="submit" :loading="loading" :disabled="busy">{{t('masterData.search')}}</el-button>
          <el-button :disabled="loading||busy" @click="reset">{{t('masterData.reset')}}</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #[isGeography?'table':'data']>
      <FloatingError :title="error"><el-button :disabled="busy||loading" @click="retry">{{t('masterData.retry')}}</el-button></FloatingError>
      <el-result v-if="failed&&!rows.length" icon="error" :title="t('masterData.unavailable')"><template #extra><el-button :loading="loading" @click="retry">{{t('masterData.retry')}}</el-button></template></el-result>
      <el-result v-else-if="ready&&!canRead" icon="warning" :title="t('masterData.noPermission')" />
      <ManagementTable v-else :title="title" :rows="rows" :columns="columns" :total="total" :loading="loading" :busy="busy" :empty-text="t('masterData.empty')" embedded @refresh="load">
        <template #titleHelp>
          <InfoHint :content="t('masterData.scopeHint')" />
          <InfoHint v-if="kind==='country-region'" :content="t('masterData.countryHint')" />
        </template>
        <template #actions><el-button v-operation="operation.create" type="primary" :disabled="loading||busy||(isStorage&&(!storageParent?.code||!storageParent.enabled))" @click="edit()">{{t('masterData.create')}}</el-button></template>
        <template v-for="field in visibleFields" :key="field" #[field]="{row}">{{kind==='warehouse'?(warehouseOptions[field]?.find(option=>option.value===row.attributes[field])?.name||row.attributes[field]||'—'):field==='partnerType'?(row.attributes[field]==='BOTH'?['CUSTOMER','SUPPLIER']:row.attributes[field].split(',')).map((value:string)=>partnerOptions.find(option=>option.value===value)?.name??value).join(' / '):field==='subdivisionType'?subdivisionTypeLabel(row.attributes[field]):field==='areaType'?(row.attributes[field]?t('storageHierarchy.types.'+row.attributes[field]):'—'):row.attributes[field]||'—'}}</template>
        <template #name="{row}"><el-button v-if="kind==='storage-area'&&embedded" v-operation="operation.list" link type="primary" @click="emit('selected',row)">{{row.name}}</el-button><span v-else>{{isGeography?(row.attributes.displayName||row.name):row.name}}</span></template>
        <template #hierarchyCode="{row}">{{row.attributes.hierarchyCode||row.code}}</template>
        <template #parentHierarchyCode="{row}">{{row.attributes.parentHierarchyCode||'—'}}</template>
        <template #status="{row}"><el-tag :type="row.enabled?'success':'info'">{{t('masterData.'+(row.enabled?'enabled':'disabled'))}}</el-tag></template>
        <template #operations="{row}">
          <el-button v-if="kind==='partner'||kind==='warehouse'||isStorage" v-operation="operation.list" link type="primary" :disabled="busy||loading" @click="edit(row,'view')">{{t('partner.view')}}</el-button>
          <el-button v-operation="operation.update" link type="primary" :disabled="busy||loading" @click="edit(row,'edit')">{{t('masterData.edit')}}</el-button>
          <el-button v-operation="operation.setStatus" link :type="row.enabled?'danger':'primary'" :disabled="busy||loading" @click="toggle(row)">{{t('masterData.'+(row.enabled?'disable':'enable'))}}</el-button>
        </template>
        <template #pagination><AppPagination v-model:current-page="page" v-model:page-size="size" :total="total" :disabled="loading||busy" @current-change="load" @size-change="search" /></template>
      </ManagementTable>
      <ManagedDialog v-model="open" :title="(storageReadonly?t('partner.view'):t('masterData.'+(editingId?'edit':'create')))+' · '+title" :busy="busy" @closed="form?.clearValidate()">
        <!-- 地理编码约束移至标题；阻止指针事件冒泡，避免提示按钮触发标题栏拖动。 -->
        <template v-if="isGeography" #title>
          <span class="info-hint-title">{{t('masterData.'+(editingId?'edit':'create'))+' · '+title}}<span @pointerdown.stop><InfoHint :content="t('masterData.codeHint')" /></span></span>
        </template>
        <el-form class="business-border-label-form" ref="form" :model="draft" :rules="rules" label-position="top" @submit.prevent="save">
          <el-form-item v-if="isStorage" :label="t(kind==='storage-area'?'storageHierarchy.warehouse':'storageHierarchy.area')"><span>{{storageParent?.name}} · {{draft.attributes.parentCode}}</span></el-form-item>
          <el-form-item v-if="isGeography" :label="t('geographyHierarchy.parentCode')" prop="attributes.parentCode">
            <el-tree-select :key="parentRevision" v-model="draft.attributes.parentCode" :cache-data="parentCache" lazy :load="loadParents" :props="{label:'label',isLeaf:'leaf',disabled:'disabled'}" node-key="code" check-strictly :disabled="busy||storageReadonly" style="width:100%" @change="changeParent" />
          </el-form-item>
          <el-form-item v-if="isGeography&&editingId" :label="t('geographyHierarchy.code')"><span>{{currentHierarchy||'—'}}</span></el-form-item>
          <el-form-item :label="t('masterData.code')" prop="code">
            <template #label>{{t('masterData.code')}}<InfoHint :content="t('masterData.codeHint')" /></template>
            <span v-if="editingId">{{draft.code}}</span><el-input v-else v-model="draft.code" maxlength="64" :disabled="busy||storageReadonly">
              <template v-if="isGeography" #suffix><el-popover trigger="click" placement="bottom-end" :width="320" @show="searchGeography('')">
                <template #reference><el-button text :disabled="busy||storageReadonly">{{t('masterData.selectGeography')}}</el-button></template>
                <el-select :model-value="draft.code" filterable remote :remote-method="searchGeography" :loading="geographyLoading" :placeholder="t('masterData.selectGeography')" style="width:100%" :disabled="busy||storageReadonly" @change="selectGeography">
                  <el-option v-for="entry in geography" :key="entry.code" :value="entry.code" :label="entry.code+' · '+(entry.attributes.displayName||entry.name)" />
                  <template #footer><el-pagination v-model:current-page="geographyPage" small layout="prev, pager, next" :pager-count="5" :page-size="20" :total="geographyTotal" :disabled="geographyLoading" @current-change="loadGeography" /></template>
                </el-select>
              </el-popover></template>
            </el-input>
          </el-form-item>
          <el-form-item :label="t('masterData.name')" prop="name"><el-input v-model="draft.name" maxlength="200" :disabled="busy||storageReadonly" /></el-form-item>
          <el-form-item v-for="field in visibleFields" :key="field" :label="t('masterData.fields.'+field)" :prop="'attributes.'+field">
            <span v-if="isGeography&&field==='countryCode'">{{draft.attributes[field]||'—'}}</span>
            <el-select v-else-if="field==='areaType'" v-model="draft.attributes[field]" clearable :disabled="busy||storageReadonly"><el-option v-for="value in areaTypes" :key="value" :value="value" :label="t('storageHierarchy.types.'+value)" /></el-select>
            <el-input v-else v-model="draft.attributes[field]" :type="field==='remarks'?'textarea':'text'" :maxlength="field==='email'?200:500" :disabled="busy||storageReadonly" />
          </el-form-item>
        </el-form>
        <RecordAuditDetails v-if="editingId && editingAudit" :audit="editingAudit" />
        <template #footer><el-button :disabled="busy" @click="open=false">{{t('masterData.cancel')}}</el-button><el-button v-if="!storageReadonly" v-operation="saveOperation" type="primary" :loading="busy" :disabled="isGeography&&!draft.attributes.parentCode" @click="save">{{t('masterData.save')}}</el-button></template>
      </ManagedDialog>
    </template>
  </component>
</template>

<style scoped>
/* 树节点复用模板高度与滚动；长名称、路径省略并由原生提示展示，避免撑破窄屏分栏。 */
:deep(.el-tree-node__content > span:last-child) { min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
:deep(.el-tree-node__content small) { color:var(--el-text-color-secondary); }
</style>
