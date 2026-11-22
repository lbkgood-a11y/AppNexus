<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, type FormInstance } from "element-plus";
import { BusinessWorkspace, FloatingError, ManagedDialog, ManagementTable, setErrorFeedback,
  type ManagementColumn } from "@appnexus/ui";
import type { CoilTrace, PositionOption } from "./flow";
import type { DispatchInput, ReturnPreparation, ReturnTransit, ReturnTransitApi, StockRestriction, TransitAction, TransitCoil } from "./returnTransit";
import type { WarehouseOption } from "./types";

/** 逐卷读取新的接收仓和处置历史，实物及额度只显示后端已提交的状态。 */
const props=defineProps<{api:ReturnTransitApi;errorText:(error:unknown)=>string;handoverId?:string;transitId?:string; restrictionReceipt?:string;
  openOriginal:(receiptId:string)=>void; startOutbound:(receiptId:string,transitId:string)=>void; showTransit:(id:string)=>void}>();
const {t,te,locale}=useI18n();
const text=(key:string)=>t("returnTransit."+key);
const mode=ref<"dispatch"|"track">("dispatch");
const manifest=ref<ReturnTransit>(), lookup=ref("");
const dispatchForm=ref<FormInstance>(), actionForm=ref<FormInstance>();
const dispatchDraft=reactive({handoverId:"",receivingWarehouseId:"",evidenceId:"",receiptText:""});
const preparation=ref<ReturnPreparation>();
const selectedCandidates=ref<string[]>([]);
const preparing=ref(false);
let preparationEpoch=0;
const selected=ref<TransitCoil>(), action=ref<TransitAction>(), actionOpen=ref(false);
const draft=reactive({warehouseId:"",areaId:"",locationId:"",responsiblePartnerId:"",reason:"",evidenceId:"",titleEvidenceId:""});
const warehouses=ref<PositionOption[]>([]), areas=ref<PositionOption[]>([]), locations=ref<PositionOption[]>([]);
const partners=ref<WarehouseOption[]>([]), units=ref<WarehouseOption[]>([]);
const restrictionCoil=ref<TransitCoil>(), restrictions=ref<StockRestriction[]>([]), restrictionLoading=ref(false);
const restrictionId=ref("");
let restrictionEpoch=0;
const canReadRestriction=ref(false);
const canStartOutbound=ref(false);
const canReleaseQuality=ref(false);
const canTrace=ref(false), selectedTrace=ref<CoilTrace>();
const busy=ref(false), loading=ref(false), tracing=ref(false), error=ref(""), actionError=ref("");
let dispatchAttempt:DispatchInput|undefined, actionAttempt:{signature:string;id:string}|undefined;
let loadEpoch=0, traceEpoch=0, areaEpoch=0, locationEpoch=0, partnerEpoch=0;
const operation:Record<TransitAction,string>={
  freeze:"BulkStockRestriction.freeze",
  lift:"BulkStockRestriction.lift",
  quality:"BulkCoilOutbound.release_returned_quality",
  receive:"BulkCoilOutbound.return_coils",
  loss:"BulkCoilOutbound.settle_transit_loss",
  reopen:"BulkCoilOutbound.reopen_transit_loss",
  reroute:"BulkCoilOutbound.reroute_transit_coil"
};
const activeOperation=computed(()=>action.value ? operation[action.value] : "");
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const required=computed(()=>[{required:true,message:t("masterData.required"),trigger:"change" as const}]);
const evidenceRules=computed(()=>[...required.value,{pattern:uuid,message:text("invalid"),trigger:"blur"}]);
const formatter=computed(()=>new Intl.NumberFormat(locale.value,{maximumFractionDigits:12}));
const amount=(n:number)=>formatter.value.format(n);
const auditTime=computed(()=>new Intl.DateTimeFormat(locale.value,{dateStyle:"medium",timeStyle:"medium"}));
const warehouseLabel=(id:string|null)=>warehouses.value.find(row=>row.id===id)?.label??id??"—";
const unit=computed(()=>units.value.find(row=>row.id===manifest.value?.unitId)?.label??manifest.value?.unitId??"—");
const columns=(entries:[key:string,width:number][]):ManagementColumn[] =>
  entries.map(([key,minWidth])=>({key,label:text(key),minWidth,prop:key}));
const coilColumns=computed(()=>columns([["serial",138],["bookQuantity",105],["measuredQuantity",105],
  ["receivingWarehouseId",160],["state",112],["qualityStatus",132],["operations",258]]));
const quotaColumns=computed(()=>columns([["quotaKind",118],["id",220],["cap",115],["reserved",102],["issued",102],["remaining",105]]));
const restrictionColumns=computed(()=>columns([["restrictionStatus",105],["id",220],["freezeEvidence",220],
  ["liftEvidence",220],["createdBy",220],["createdAt",182],["updatedBy",220],["updatedAt",182],["operations",115]]));
const historyColumns=computed(()=>columns([["kind",112],["serial",130],["id",220],["evidenceId",220],
  ["titleEvidenceId",220],["qualityEvidenceId",220],["warehouseId",175],["reason",175]]));
// 质检凭证以本次接管记录为准，原入仓放行凭证不代表返仓已放行。
const releasedReturns=computed(()=>new Set(manifest.value?.history
  .filter(row=>row.kind==="RECEIVE"&&row.qualityEvidenceId).map(row=>row.id+"/"+row.receiptId)));
const quotas=computed(()=>{
  const current=manifest.value;
  if (!current) return [];
  return [
    {kind:"notice",value:current.noticeQuota},
    {kind:"authorization",value:current.authorizationQuota},
    {kind:"trip",value:current.tripQuota}
  ].flatMap(row=>row.value ? [{quotaKind:row.kind,...row.value}] : []);
});
const receivingPosition=computed(()=>{
  const w=warehouses.value.find(row=>row.id===draft.warehouseId);
  const a=areas.value.find(row=>row.id===draft.areaId);
  const l=locations.value.find(row=>row.id===draft.locationId);
  return w&&a&&l ? {warehouseId:w.id,areaId:a.id,locationId:l.id,
    warehouseVersion:w.version,areaVersion:a.version,locationVersion:l.version} : undefined;
});

function message(cause:unknown) {
  const code=(cause as {response?:{data?:{code?:string}}})?.response?.data?.code;
  const key="returnTransit.errors."+code;
  return code&&te(key) ? t(key) : props.errorText(cause);
}

/** 发运只登记实际发出的原卷，重复请求沿用业务键。 */
async function dispatch() {
  if (busy.value) return;
  busy.value=true;
  try {
    if (!await dispatchForm.value?.validate().catch(()=>false)) return;
    if (props.handoverId && !preparation.value) return;
    const receiptIds=props.handoverId ? selectedCandidates.value : dispatchDraft.receiptText.trim().split(/\s+/);
    if (receiptIds.length<1 || receiptIds.length>500 || receiptIds.some(id=>!uuid.test(id)) || new Set(receiptIds).size!==receiptIds.length) {
      await setErrorFeedback(error,text("invalidCoils"));
      return;
    }
    const input={handoverId:dispatchDraft.handoverId,receivingWarehouseId:dispatchDraft.receivingWarehouseId,
      evidenceId:dispatchDraft.evidenceId,receiptIds:[...receiptIds].sort()};
    if (!dispatchAttempt || JSON.stringify({...dispatchAttempt,id:undefined})!==JSON.stringify(input)) {
      dispatchAttempt={id:crypto.randomUUID(),...input};
    }
    manifest.value=await props.api.dispatch(dispatchAttempt);
    dispatchAttempt=undefined;
    lookup.value=manifest.value.id;
    mode.value="track";
    props.showTransit(manifest.value.id);
    error.value="";
    ElMessage.success(text("posted"));
  } catch(cause) {await setErrorFeedback(error,message(cause));}
  finally {busy.value=false;}
}

/** 查询由原发运 UUID 重新取证，切换发运时不保留上一票实物。 */
async function query(id=lookup.value) {
  if (!uuid.test(id)) {
    await setErrorFeedback(error,text("invalid"));
    return;
  }
  const epoch=++loadEpoch;
  if (manifest.value?.id!==id) {
    manifest.value=undefined;
    selectedTrace.value=undefined;
    clearRestrictions();
    traceEpoch++;
    tracing.value=false;
  }
  loading.value=true;
  error.value="";
  try {
    const value=await props.api.load(id);
    if (epoch===loadEpoch) manifest.value=value;
  } catch(cause) {
    if (epoch!==loadEpoch) return;
    if ((cause as {response?:{status?:number}})?.response?.status===403) {
      manifest.value=undefined;
      selectedTrace.value=undefined;
      clearRestrictions();
    }
    await setErrorFeedback(error,message(cause));
  } finally {
    if (epoch===loadEpoch) loading.value=false;
  }
}

async function pickWarehouse() {
  draft.areaId=draft.locationId="";
  areas.value=locations.value=[];
  locationEpoch++;
  const epoch=++areaEpoch;
  const selected=warehouses.value.find(row=>row.id===draft.warehouseId);
  if (!selected) return;
  try {
    const rows=await props.api.locations("storage-area",selected.code);
    if (epoch===areaEpoch) areas.value=rows;
  } catch(cause) {if(epoch===areaEpoch) await setErrorFeedback(actionError,message(cause));}
}
async function pickArea() {
  draft.locationId="";
  locations.value=[];
  const epoch=++locationEpoch;
  const selected=areas.value.find(row=>row.id===draft.areaId);
  if (!selected) return;
  try {
    const rows=await props.api.locations("storage-location",selected.code);
    if (epoch===locationEpoch) locations.value=rows;
  } catch(cause) {if(epoch===locationEpoch) await setErrorFeedback(actionError,message(cause));}
}
async function findPartners(keyword="") {
  const epoch=++partnerEpoch;
  try {
    const rows=await props.api.owners(keyword);
    if (epoch===partnerEpoch) partners.value=rows;
  } catch(cause) {if(epoch===partnerEpoch) await setErrorFeedback(actionError,message(cause));}
}

/** 弹窗固定原卷，后端在提交时重新读取实际状态及接收仓。 */
function edit(row:TransitCoil,kind:TransitAction,originalRestrictionId="") {
  restrictionId.value=originalRestrictionId;
  selected.value=row;
  action.value=kind;
  actionAttempt=undefined;
  actionError.value="";
  Object.assign(draft,{warehouseId:kind==="receive"?row.receivingWarehouseId:"",areaId:"",locationId:"",
    responsiblePartnerId:"",reason:"",evidenceId:"",titleEvidenceId:""});
  actionOpen.value=true;
  if (kind==="receive") void pickWarehouse();
  if (kind==="loss") void findPartners();
}

function command(kind:TransitAction,coil:TransitCoil,current:ReturnTransit) {
  switch(kind) {
    case "freeze": return {receiptId:coil.receiptId,evidenceId:draft.evidenceId};
    case "lift": return {id:restrictionId.value,receiptId:coil.receiptId,evidenceId:draft.evidenceId};
    case "quality": return {returnId:coil.receivedReturnId,receiptId:coil.receiptId,evidenceId:draft.evidenceId};
    case "receive": return {handoverId:current.handoverId,transitId:current.id,custodyEvidenceId:draft.evidenceId,
      titleEvidenceId:draft.titleEvidenceId,coils:[{receiptId:coil.receiptId,locationId:draft.locationId,position:receivingPosition.value}]};
    case "loss": return {transitId:current.id,receiptId:coil.receiptId,responsiblePartnerId:draft.responsiblePartnerId,
      evidenceId:draft.evidenceId,reason:draft.reason};
    case "reopen": return {transitId:current.id,lossId:coil.lossId,evidenceId:draft.evidenceId};
    case "reroute": return {transitId:current.id,receiptId:coil.receiptId,expectedReceivingWarehouseId:coil.receivingWarehouseId,
      newReceivingWarehouseId:draft.warehouseId,evidenceId:draft.evidenceId,reason:draft.reason};
  }
}

async function submit() {
  const current=manifest.value, coil=selected.value, kind=action.value;
  if (busy.value || !current || !coil || !kind) return;
  busy.value=true;
  try {
    if (!await actionForm.value?.validate().catch(()=>false)) return;
    if (kind==="receive" && !receivingPosition.value) return;
    if (kind==="quality" && (!canReleaseQuality.value || !coil.receivedReturnId)) return;
    const payload=command(kind,coil,current);
    if (kind==="quality"||kind==="lift") {
      // 放行与解除沿用原业务标识和凭证识别重试。
      await props.api.submit(kind,payload);
    } else {
      const signature=JSON.stringify(payload);
      if (actionAttempt?.signature!==signature) actionAttempt={signature,id:crypto.randomUUID()};
      await props.api.submit(kind,{id:actionAttempt.id,...payload});
    }
    actionOpen.value=false;
    ElMessage.success(text("posted"));
    await query(current.id);
    if (kind==="freeze"||kind==="lift") await readRestrictions(coil);
    if ((kind==="receive"||kind==="quality") && canTrace.value) await traceStock(coil.receiptId);
  } catch(cause) {await setErrorFeedback(actionError,message(cause));}
  finally {busy.value=false;}
}

/** 从现行库存读余额，运输阶段状态不推导库存或货权。 */
async function traceStock(id:string) {
  const epoch=++traceEpoch;
  selectedTrace.value=undefined;
  tracing.value=true;
  try {
    const row=await props.api.trace(id);
    if (epoch===traceEpoch) selectedTrace.value=row;
  } catch(cause) {if(epoch===traceEpoch) await setErrorFeedback(error,message(cause));}
  finally {if(epoch===traceEpoch) tracing.value=false;}
}

/** 入口只携带交接编号。原卷候选总是由后端按当前已出、退回及发运状态准备。 */
async function prepareFromHandover(handoverId:string) {
  const epoch=++preparationEpoch;
  preparation.value=undefined;
  selectedCandidates.value=[];
  dispatchDraft.handoverId=handoverId;
  preparing.value=true;
  try {
    const result=await props.api.prepare(handoverId);
    if (epoch!==preparationEpoch) return;
    preparation.value=result;
    selectedCandidates.value=result.coils.map(row=>row.receiptId);
  } catch(cause) {if(epoch===preparationEpoch) await setErrorFeedback(error,message(cause));}
  finally {if(epoch===preparationEpoch) preparing.value=false;}
}

function clearRestrictions() {
  restrictionEpoch++;
  restrictionCoil.value=undefined;
  restrictions.value=[];
  restrictionLoading.value=false;
}

/** 监管历史只按所选原卷读取，切换原卷时丢弃迟到响应。 */
async function readRestrictions(coil:TransitCoil) {
  const epoch=++restrictionEpoch;
  restrictionCoil.value=coil;
  restrictions.value=[];
  restrictionLoading.value=true;
  error.value="";
  try {
    const result=await props.api.restrictions(coil.receiptId);
    if (epoch===restrictionEpoch) restrictions.value=result;
  } catch(cause) {
    if (epoch===restrictionEpoch) await setErrorFeedback(error,message(cause));
  } finally {
    if (epoch===restrictionEpoch) restrictionLoading.value=false;
  }
}

const evidenceLabel=computed(()=>{
  switch(action.value) {
    case "receive": return text("custodyEvidence");
    case "quality": return text("qualityEvidence");
    case "freeze": return text("freezeEvidence");
    case "lift": return text("liftEvidence");
    default: return text("approvalEvidence");
  }
});

/** 深链接只打开该发运中已接管的卷，监管历史仍由服务端按现行库存范围授权。 */
async function followRoute() {
  if (props.transitId && uuid.test(props.transitId)) {
    lookup.value=props.transitId;
    mode.value="track";
    const transitId=props.transitId;
    const receiptId=props.restrictionReceipt;
    if (manifest.value?.id!==transitId) await query(transitId);
    if (!canReadRestriction.value || !receiptId || !uuid.test(receiptId)
      || props.transitId!==transitId || props.restrictionReceipt!==receiptId || manifest.value?.id!==transitId) return;
    const received=manifest.value.coils.find(coil=>coil.receiptId===receiptId && coil.state==="RECEIVED");
    if (received) await readRestrictions(received);
  } else if (props.handoverId && uuid.test(props.handoverId)) {
    mode.value="dispatch";
    manifest.value=undefined;
    selectedTrace.value=undefined;
    clearRestrictions();
    dispatchDraft.receivingWarehouseId=dispatchDraft.evidenceId="";
    dispatchAttempt=undefined;
    void prepareFromHandover(props.handoverId);
  }
}
watch(()=>[props.handoverId,props.transitId,props.restrictionReceipt],followRoute);

onMounted(async()=>{
  try {
    const [ops,options,stockUnits]=await Promise.all([props.api.operations(),props.api.locations("warehouse"),props.api.units()]);
    canReadRestriction.value=ops.includes("BulkStockRestriction.read");
    canStartOutbound.value=["BulkCoilOutbound.issue","BulkOutboundStock.check","BulkWarehouseStockBalance.trace"]
      .every(operation=>ops.includes(operation));
    canReleaseQuality.value=ops.includes("BulkInboundQualityRelease.release");
    canTrace.value=ops.includes("BulkWarehouseStockBalance.trace");
    warehouses.value=options;
    units.value=stockUnits;
    followRoute();
  } catch(cause) {await setErrorFeedback(error,message(cause));}
});
</script>

<template>
  <div class="management-page">
    <BusinessWorkspace :title="text('heading')">
      <template #navigation>
        <el-tabs v-model="mode">
          <el-tab-pane :label="text('dispatch')" name="dispatch" :disabled="busy" />
          <el-tab-pane :label="text('track')" name="track" :disabled="busy" />
        </el-tabs>
      </template>
      <template #feedback><FloatingError :title="error" /></template>
      <template #search>
        <el-form v-if="mode==='dispatch'" ref="dispatchForm" :model="dispatchDraft" inline :disabled="busy"
          @submit.prevent="dispatch">
          <div class="management-filter">
            <el-form-item :label="text('handoverId')" prop="handoverId" :rules="evidenceRules">
              <el-input v-model="dispatchDraft.handoverId" class="return-transit-control" maxlength="36" :readonly="!!handoverId" />
            </el-form-item>
            <el-form-item :label="text('planWarehouse')" prop="receivingWarehouseId" :rules="required">
              <el-select v-model="dispatchDraft.receivingWarehouseId" class="return-transit-control" filterable>
                <el-option v-for="row in warehouses" :key="row.id" :label="row.label" :value="row.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="text('dispatchEvidence')" prop="evidenceId" :rules="evidenceRules">
              <el-input v-model="dispatchDraft.evidenceId" class="return-transit-control" maxlength="36" />
            </el-form-item>
            <el-form-item v-if="!handoverId" :label="text('receiptIds')" prop="receiptText" :rules="required">
              <el-input v-model="dispatchDraft.receiptText" class="return-transit-control" type="textarea"
                :rows="2" maxlength="18500" :placeholder="text('receiptHint')" />
            </el-form-item>
          </div>
          <div class="management-filter-actions">
            <el-button v-operation="'BulkCoilOutbound.dispatch_return'" native-type="submit" type="primary"
              :loading="busy" :disabled="!!handoverId && (!preparation||preparing||!selectedCandidates.length)">
              {{text('dispatch')}}
            </el-button>
          </div>
        </el-form>
        <el-form v-else inline @submit.prevent="query()">
          <div class="management-filter">
            <el-form-item :label="text('transitId')">
              <el-input v-model.trim="lookup" maxlength="36" class="return-transit-control" />
            </el-form-item>
          </div>
          <div class="management-filter-actions">
            <el-button v-operation="'BulkCoilOutbound.read_return_transit'" native-type="submit" type="primary"
              :loading="loading">{{text('query')}}</el-button>
          </div>
        </el-form>
      </template>
      <template #data>
        <div v-if="handoverId && mode==='dispatch'" v-loading="preparing">
          <el-checkbox-group v-if="preparation?.coils.length" v-model="selectedCandidates" :aria-label="text('eligibleCoils')">
            <el-checkbox v-for="coil in preparation.coils" :key="coil.receiptId" :value="coil.receiptId">{{coil.serial}}</el-checkbox>
          </el-checkbox-group>
          <el-empty v-else :description="text('noEligibleCoils')" />
        </div>
        <div v-else-if="manifest && mode==='track'" class="business-workspace-stack management-table-stack" v-loading="loading">
          <el-descriptions :title="text('original')" :column="2" border>
            <el-descriptions-item :label="text('transitId')"><span class="return-transit-id">{{manifest.id}}</span></el-descriptions-item>
            <el-descriptions-item :label="text('handoverId')">
              <span class="return-transit-id">{{manifest.handoverId}}</span>
              <el-button v-if="manifest.coils.length" v-operation="'BulkWarehouseStockBalance.trace'" link type="primary"
                @click="openOriginal(manifest.coils[0]!.receiptId)">{{text('openOriginal')}}</el-button>
            </el-descriptions-item>
            <el-descriptions-item :label="text('sourceWarehouse')">{{warehouseLabel(manifest.sourceWarehouseId)}}</el-descriptions-item>
            <el-descriptions-item :label="text('planWarehouse')">{{warehouseLabel(manifest.receivingWarehouseId)}}</el-descriptions-item>
            <el-descriptions-item :label="text('dispatchEvidence')"><span class="return-transit-id">{{manifest.evidenceId}}</span></el-descriptions-item>
            <el-descriptions-item :label="text('unit')">{{unit}}</el-descriptions-item>
          </el-descriptions>
          <ManagementTable embedded :title="text('coils')" :rows="manifest.coils" :columns="coilColumns" @refresh="query()">
            <template #receivingWarehouseId="{row}">{{warehouseLabel(row.receivingWarehouseId)}}</template>
            <template #state="{row}"><el-tag :type="row.state==='RECEIVED'?'success':row.state==='LOSS_SETTLED'?'danger':'warning'">
              {{text('states.'+row.state)}}
            </el-tag></template>
            <template #bookQuantity="{row}"><span class="return-transit-number">{{amount(row.bookQuantity)}}</span></template>
            <template #measuredQuantity="{row}"><span class="return-transit-number">{{amount(row.measuredQuantity)}}</span></template>
            <template #qualityStatus="{row}">
              <el-tag v-if="row.receivedReturnId" :type="releasedReturns.has(row.receivedReturnId+'/'+row.receiptId)?'success':'warning'">
                {{text(releasedReturns.has(row.receivedReturnId+'/'+row.receiptId)?'qualityReleased':'qualityPending')}}
              </el-tag>
            </template>
            <template #operations="{row}">
              <template v-if="row.state==='IN_TRANSIT'">
                <el-button v-operation="'BulkCoilOutbound.return_coils'" link type="primary" :disabled="busy"
                  @click="edit(row,'receive')">{{text('actions.receive')}}</el-button>
                <el-button v-operation="'BulkCoilOutbound.settle_transit_loss'" link :disabled="busy"
                  @click="edit(row,'loss')">{{text('actions.loss')}}</el-button>
                <el-button v-operation="'BulkCoilOutbound.reroute_transit_coil'" link :disabled="busy"
                  @click="edit(row,'reroute')">{{text('actions.reroute')}}</el-button>
              </template>
              <el-button v-else-if="row.state==='LOSS_SETTLED'" v-operation="'BulkCoilOutbound.reopen_transit_loss'" link
                :disabled="busy" @click="edit(row,'reopen')">{{text('actions.reopen')}}</el-button>
              <template v-else>
                <el-button v-if="canReleaseQuality && row.receivedReturnId && !releasedReturns.has(row.receivedReturnId+'/'+row.receiptId)"
                  v-operation="'BulkCoilOutbound.release_returned_quality'" link type="primary" :disabled="busy"
                  @click="edit(row,'quality')">{{text('actions.quality')}}</el-button>
                <el-button v-if="canStartOutbound && releasedReturns.has(row.receivedReturnId+'/'+row.receiptId)"
                  v-operation="'BulkCoilOutbound.issue'" link type="primary"
                  @click="startOutbound(row.receiptId,manifest.id)">{{text('startOutbound')}}</el-button>
                <el-button v-operation="'BulkStockRestriction.read'" link :disabled="busy"
                  @click="readRestrictions(row)">{{text('restrictions')}}</el-button>
                <el-button v-operation="'BulkWarehouseStockBalance.trace'" link :disabled="tracing"
                  @click="traceStock(row.receiptId)">{{text('stock')}}</el-button>
              </template>
            </template>
          </ManagementTable>
          <ManagementTable v-if="restrictionCoil" embedded :title="text('restrictionHistory')" :rows="restrictions"
            :columns="restrictionColumns" :loading="restrictionLoading" @refresh="readRestrictions(restrictionCoil)">
            <template #actions>
              <el-tag>{{restrictionCoil.serial}}</el-tag>
              <el-button v-operation="'BulkStockRestriction.freeze'" type="primary" :disabled="busy||restrictionLoading"
                @click="edit(restrictionCoil,'freeze')">{{text('actions.freeze')}}</el-button>
            </template>
            <template #restrictionStatus="{row}">
              <el-tag :type="row.releaseEvidenceId?'info':'danger'">{{text(row.releaseEvidenceId?'lifted':'frozen')}}</el-tag>
            </template>
            <template #freezeEvidence="{row}">{{row.evidenceId}}</template>
            <template #liftEvidence="{row}">{{row.releaseEvidenceId??"—"}}</template>
            <template #createdAt="{row}">{{auditTime.format(new Date(row.createdAt))}}</template>
            <template #updatedAt="{row}">{{auditTime.format(new Date(row.updatedAt))}}</template>
            <template #operations="{row}">
              <el-button v-if="!row.releaseEvidenceId" v-operation="'BulkStockRestriction.lift'" link type="primary" :disabled="busy"
                @click="restrictionCoil && edit(restrictionCoil,'lift',row.id)">{{text('actions.lift')}}</el-button>
            </template>
          </ManagementTable>
          <el-descriptions v-if="selectedTrace" :title="text('stock')" :column="2" border>
            <el-descriptions-item :label="text('serial')">{{selectedTrace.stock.serial}}</el-descriptions-item>
            <el-descriptions-item :label="text('actualWarehouse')">{{warehouseLabel(selectedTrace.stock.warehouseId)}}</el-descriptions-item>
            <el-descriptions-item :label="text('onHand')">{{amount(selectedTrace.stock.onHand)}}</el-descriptions-item>
            <el-descriptions-item :label="text('pending')">{{amount(selectedTrace.stock.pending)}}</el-descriptions-item>
            <el-descriptions-item :label="text('title')">{{amount(selectedTrace.stock.title)}}</el-descriptions-item>
          </el-descriptions>
          <ManagementTable v-if="quotas.length" embedded :title="text('quotas')" :rows="quotas" :columns="quotaColumns" @refresh="query()">
            <template #quotaKind="{row}">{{text('quotaKinds.'+row.quotaKind)}}</template>
            <template v-for="key in (['cap','reserved','issued','remaining'] as const)" :key="key" #[key]="{row}">
              <span class="return-transit-number">{{amount(row[key])}}</span>
            </template>
          </ManagementTable>
          <ManagementTable embedded :title="text('history')" :rows="manifest.history" :columns="historyColumns" @refresh="query()">
            <template #kind="{row}">{{text('actions.'+row.kind.toLowerCase())}}</template>
            <template #serial="{row}">{{manifest?.coils.find(coil=>coil.receiptId===row.receiptId)?.serial??row.receiptId}}</template>
            <template #warehouseId="{row}">
              {{row.previousWarehouseId ? warehouseLabel(row.previousWarehouseId)+' → ' : ''}}{{warehouseLabel(row.warehouseId)}}
            </template>
          </ManagementTable>
        </div>
        <el-empty v-else :description="text('empty')" />
      </template>
    </BusinessWorkspace>

    <ManagedDialog v-model="actionOpen" :title="action ? text('actions.'+action) : ''" :busy="busy">
      <p v-if="selected">
        {{text('serial')}} {{selected.serial}}
        <template v-if="action!=='freeze' && action!=='lift'">
          · {{text('receivingWarehouseId')}} {{warehouseLabel(selected.receivingWarehouseId)}}
        </template>
      </p>
      <p v-if="action==='lift'">{{text("restrictionNumber")}} {{restrictionId}}</p>
      <FloatingError :title="actionError" />
      <el-form ref="actionForm" :model="draft" class="business-border-label-form" label-position="top" :disabled="busy"
        @submit.prevent="submit">
        <el-form-item v-if="action==='receive'||action==='reroute'" :label="text(action==='receive'?'actualWarehouse':'newWarehouse')"
          prop="warehouseId" :rules="required">
          <el-select v-model="draft.warehouseId" filterable @change="action==='receive' && pickWarehouse()">
            <el-option v-for="row in warehouses.filter(w=>action==='receive'||w.id!==selected?.receivingWarehouseId)"
              :key="row.id" :label="row.label" :value="row.id" />
          </el-select>
        </el-form-item>
        <template v-if="action==='receive'">
          <el-form-item :label="text('area')" prop="areaId" :rules="required">
            <el-select v-model="draft.areaId" filterable @change="pickArea">
              <el-option v-for="row in areas" :key="row.id" :label="row.label" :value="row.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="text('location')" prop="locationId" :rules="required">
            <el-select v-model="draft.locationId" filterable>
              <el-option v-for="row in locations" :key="row.id" :label="row.label" :value="row.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="text('titleEvidenceId')" prop="titleEvidenceId" :rules="evidenceRules">
            <el-input v-model="draft.titleEvidenceId" maxlength="36" />
          </el-form-item>
        </template>
        <el-form-item v-if="action==='loss'" :label="text('responsible')" prop="responsiblePartnerId" :rules="required">
          <el-select v-model="draft.responsiblePartnerId" filterable remote :remote-method="findPartners">
            <el-option v-for="row in partners" :key="row.id" :label="row.label" :value="row.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="evidenceLabel" prop="evidenceId" :rules="evidenceRules">
          <el-input v-model="draft.evidenceId" maxlength="36" />
        </el-form-item>
        <el-form-item v-if="action==='loss'||action==='reroute'" :label="text('reason')" prop="reason" :rules="required">
          <el-input v-model="draft.reason" type="textarea" maxlength="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="busy" @click="actionOpen=false">{{t('masterData.cancel')}}</el-button>
        <el-button v-operation="activeOperation" type="primary" :loading="busy" @click="submit">{{text('confirm')}}</el-button>
      </template>
    </ManagedDialog>
  </div>
</template>

<style scoped>
.return-transit-control { inline-size: min(16rem, 100%); }
.return-transit-id { overflow-wrap: anywhere; }
.return-transit-number { display: block; text-align: end; }
</style>
