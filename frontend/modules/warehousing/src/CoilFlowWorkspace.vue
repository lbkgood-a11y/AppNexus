<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox, type FormInstance } from "element-plus";
import { BusinessWorkspace, FloatingError, ManagementTable, setErrorFeedback, type ManagementColumn } from "@appnexus/ui";
import { steps, type AuthorizationRecovery, type CoilFlowApi, type CoilTrace, type FlowStep, type PositionOption, type ProductOptions } from "./flow";
import type { ReturnTransit, StockRestriction } from "./returnTransit";
import type { AuthorizationBalance, WarehouseOption } from "./types";

/** 单卷履约从获准通知开始。实际过账及单据追溯始终由各原业务 API 维护。 */
const props=defineProps<{api:CoilFlowApi;errorText:(error:unknown)=>string;initialReceipt?:string;
  startReturn:(handoverId:string)=>void; reissueTransit?:string;
  resumeAuthorization?:string; saveResume:(authorizationId:string)=>void;
  loadRestrictions:(receiptId:string)=>Promise<StockRestriction[]>;
  restrictionLink:(transitId:string,receiptId:string)=>string;
  loadTransit:(id:string)=>Promise<ReturnTransit>; viewTransit:(id:string)=>void}>();
const {t,te,locale}=useI18n();
const form=ref<FormInstance>(), mode=ref<"execute"|"trace">("execute");
const operations=ref<string[]>([]), errors=ref(""), submitting=ref(false), tracing=ref(false);
const recovery=ref<AuthorizationRecovery>(), restrictionHistory=ref<StockRestriction[]>([]);
const trace=ref<CoilTrace>(), authorization=ref<AuthorizationBalance>(), lookup=ref("");
const owners=ref<WarehouseOption[]>([]), products=ref<WarehouseOption[]>([]);
const warehouses=ref<PositionOption[]>([]), areas=ref<PositionOption[]>([]), locations=ref<PositionOption[]>([]);
const product=ref<ProductOptions>();
const state=ref(0);
const current=computed(()=>steps[state.value]);
const reissueReady=ref(false), gateFrozen=ref(false), cancelledTrip=ref("");
const input=reactive<Record<string,string>>({});
const setup=reactive({owner:"",warehouse:"",area:"",location:"",product:"",item:""});
const progress=reactive({
  inbound:"",owner:"",outbound:"",authorization:"",pickup:"",trip:"",vehicle:"",review:"",handover:"",returned:""
});
function blankCoil() { return {custody:"",receipt:"",serial:"",allocation:""}; }
const count=ref(1), activeCoilIndex=ref(0);
const coils=ref([blankCoil()]);
const activeCoil=computed(()=>coils.value[activeCoilIndex.value]!);
const perCoil=(step:FlowStep)=>["custody","receive","title","quality","identify","transfer","allocate"].includes(step);
let command:{step:FlowStep;id:string;signature:string}|undefined;
let productEpoch=0, areaEpoch=0, locationEpoch=0, traceEpoch=0;
let ownerEpoch=0, productOptionsEpoch=0;

const operation:Record<FlowStep,string>={
  inbound:"BulkInboundReceipt.issue",custody:"BulkInboundReceipt.take_custody",receive:"BulkInboundReceipt.receive",
  title:"BulkInboundTitleRegistration.register",quality:"BulkInboundQualityRelease.release",identify:"BulkCoilOutbound.identify",
  transfer:"BulkCoilTitleTransfer.transfer",outbound:"BulkCoilOutbound.issue",authorize:"BulkCoilOutbound.authorize",
  trip:"BulkCoilOutbound.plan",allocate:"BulkCoilOutbound.allocate",vehicle:"BulkCoilOutbound.vehicle_limits",
  weigh:"BulkCoilOutbound.review_weighing",handover:"BulkCoilOutbound.handover",return:"BulkCoilOutbound.return_coils"
};
const activeOperation=computed(()=>current.value ? operation[current.value] : "");
type Field=[key:string,kind:"amount"|"uuid"|"time"|"text"|"partner"];
const fields:Record<FlowStep,Field[]>={
  inbound:[["limit","amount"]],custody:[["custodyEvidence","uuid"]],receive:[["measurement","uuid"],["quantity","amount"]],
  title:[["titleEvidence","uuid"],["effective","time"]],quality:[["qualityEvidence","uuid"]],identify:[["serial","text"]],
  transfer:[["newOwner","partner"],["transferEvidence","uuid"]],outbound:[["cap","amount"]],
  authorize:[["pickup","partner"],["authorizationCap","amount"],["expiry","time"]],
  trip:[["vehicleNumber","text"],["tripCap","amount"]],allocate:[],
  vehicle:[["vehicleEvidence","uuid"],["payload","amount"],["grossLimit","amount"]],
  weigh:[["ticket","uuid"],["gross","amount"],["tare","amount"],["net","amount"]],
  handover:[["gateEvidence","uuid"]],return:[["returnCustody","uuid"],["returnTitle","uuid"]]
};
const selectedFields=computed<Field[]>(()=>{
  const step=current.value;
  if (!step) return [];
  if (step==="transfer" && progress.owner) return fields.transfer.filter(field=>field[0]!=="newOwner");
  if (step==="weigh" && coils.value.length>1) {
    return [...fields.weigh.filter(field=>field[0]!=="net"),
      ...coils.value.map((_,index):Field=>["net-"+index,"amount"])];
  }
  return fields[step];
});
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const amount=/^(0|[1-9][0-9]{0,17})(\.[0-9]{1,12})?$/;
const instant=/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?(?:Z|[+-]\d\d:\d\d)$/;
const required=computed(()=>[{required:true,message:t("masterData.required"),trigger:"change"}]);
function rules(field:Field) {
  const [key,kind]=field;
  const validator=(_:unknown,value:string,done:(error?:Error)=>void)=>{
    if (!value) return done();
    const valid=kind==="uuid" ? uuid.test(value)
      : kind==="amount" ? amount.test(value) && (key==="tare" || Number(value)>0)
      : kind==="time" ? instant.test(value) && !Number.isNaN(new Date(value).getTime())
      : key==="serial" ? /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(value) : value.trim()===value;
    done(valid ? undefined : new Error(t("coilFlow.invalid")));
  };
  return [...required.value,{validator,trigger:"blur"}];
}

const columns=computed<ManagementColumn[]>(()=>
  ["kind","id","parentId","owner","quantity"].map(key=>({
    key,prop:key,label:t("coilFlow."+key),minWidth:key==="id"||key==="parentId"?235:110
  })));
const recoveryColumns=computed<ManagementColumn[]>(()=>[
  ["tripId",220],["vehicleNumber",150],["kind",128],["id",220],["evidenceId",220],["occurredAt",172],
  ["cap",100],["reserved",100],["issued",100]
].map(([key,minWidth])=>({key:String(key),prop:String(key),label:text("recoveryColumns."+key),minWidth:Number(minWidth)})));
const restrictionColumns=computed<ManagementColumn[]>(()=>["id","evidenceId","releaseEvidenceId"]
  .map(key=>({key,prop:key,label:text("restrictionColumns."+key),minWidth:225})));
const auditFormat=computed(()=>new Intl.DateTimeFormat(locale.value,{dateStyle:"medium",timeStyle:"medium"}));
const formatter=computed(()=>new Intl.NumberFormat(locale.value,{maximumFractionDigits:12}));
const quantity=(value:number)=>formatter.value.format(value);
const text=(key:string)=>t("coilFlow."+key);
const ownerLabel=(id:string)=>owners.value.find(row=>row.id===id)?.label??id;

function label(field:Field) {
  const [key,kind]=field;
  const label=key.startsWith("net-")
    ? t("coilFlow.coilNet",{serial:coils.value[Number(key.slice(4))]?.serial})
    : text("fields."+key);
  return label+(kind==="amount"&&product.value ? " ("+product.value.unitName+")" : "");
}

function problem(cause:unknown) {
  const code=(cause as {response?:{data?:{code?:string}}})?.response?.data?.code;
  if (current.value==="handover" && code==="OUTBOUND_FROZEN") {
    gateFrozen.value=true;
    return setErrorFeedback(errors,text("handoverFrozen"));
  }
  const message=cause instanceof Error && cause.message==="COIL_FLOW_UNIT_REQUIRED" ? text("unitRequired") : props.errorText(cause);
  return setErrorFeedback(errors,message);
}
function remember(rows:WarehouseOption[],old:WarehouseOption[],...ids:string[]) {
  return [...old.filter(row=>ids.includes(row.id) && !rows.some(item=>item.id===row.id)),...rows];
}
async function findOwners(keyword="") {
  const epoch=++ownerEpoch;
  try {
    const rows=await props.api.owners(keyword);
    if (epoch===ownerEpoch) owners.value=remember(rows,owners.value,setup.owner,input.newOwner,input.pickup,progress.owner);
  } catch(cause) { if (epoch===ownerEpoch) await problem(cause); }
}
async function findProducts(keyword="") {
  const epoch=++productOptionsEpoch;
  try {
    const rows=await props.api.products(keyword);
    if (epoch===productOptionsEpoch) products.value=remember(rows,products.value,setup.product);
  } catch(cause) { if (epoch===productOptionsEpoch) await problem(cause); }
}
async function warehouseChanged() {
  setup.area=setup.location="";
  areas.value=locations.value=[];
  locationEpoch++;
  const epoch=++areaEpoch;
  const row=warehouses.value.find(item=>item.id===setup.warehouse);
  if (!row) return;
  try {
    const list=await props.api.locations("storage-area",row.code);
    if (epoch===areaEpoch) areas.value=list;
  } catch (cause) { if (epoch===areaEpoch) await problem(cause); }
}
async function areaChanged() {
  setup.location="";
  locations.value=[];
  const epoch=++locationEpoch;
  const row=areas.value.find(item=>item.id===setup.area);
  if (!row) return;
  try {
    const list=await props.api.locations("storage-location",row.code);
    if (epoch===locationEpoch) locations.value=list;
  } catch (cause) { if (epoch===locationEpoch) await problem(cause); }
}
async function productChanged() {
  setup.item="";
  product.value=undefined;
  const epoch=++productEpoch;
  if (!setup.product) return;
  try {
    const detail=await props.api.product(setup.product);
    if (epoch===productEpoch) product.value=detail;
  } catch (cause) { if (epoch===productEpoch) await problem(cause); }
}

const position=computed(()=>{
  const w=warehouses.value.find(row=>row.id===setup.warehouse);
  const a=areas.value.find(row=>row.id===setup.area);
  const l=locations.value.find(row=>row.id===setup.location);
  return w&&a&&l ? {warehouseId:w.id,areaId:a.id,locationId:l.id,
    warehouseVersion:w.version,areaVersion:a.version,locationVersion:l.version} : undefined;
});
const item=computed(()=>{
  const chosen=product.value?.items.find(row=>row.id===setup.item);
  return chosen&&product.value ? {productId:setup.product,kind:chosen.kind,identityId:chosen.id,
    productVersion:product.value.version} : undefined;
});
const ready=computed(()=>props.reissueTransit ? reissueReady.value && !!item.value : !!(setup.owner&&position.value&&item.value&&product.value)
  && Number.isInteger(count.value) && count.value>=1 && count.value<=20);

/** 每一步用已提交的单据标识构建下一条原业务请求，不由客户端推算权利与额度。 */
function payload(step:FlowStep,id:string):Record<string,unknown> {
  const warehouseId=setup.warehouse, unitId=product.value!.unitId, unitCode=product.value!.unitCode;
  const vehicleNumber=progress.vehicle;
  switch(step) {
    case "inbound": return {id,warehouseId,ownerPartnerId:setup.owner,item:item.value,baseUnitId:unitId,limit:input.limit};
    case "custody": return {id,noticeId:progress.inbound,position:position.value,evidenceId:input.custodyEvidence};
    case "receive": return {id,custodyId:activeCoil.value.custody,measurementId:input.measurement,position:position.value,
      item:item.value,ownerPartnerId:setup.owner,sourceUnitId:unitId,quantity:input.quantity};
    case "title": return {receiptId:activeCoil.value.receipt,evidenceId:input.titleEvidence,effectiveAt:input.effective};
    case "quality": return {receiptId:activeCoil.value.receipt,evidenceId:input.qualityEvidence};
    case "identify": return {receiptId:activeCoil.value.receipt,serial:input.serial,unitCode};
    case "transfer": return {id,receiptId:activeCoil.value.receipt,warehouseId,fromOwnerId:setup.owner,toOwnerId:progress.owner||input.newOwner,
      expectedTitleRevision:0,evidenceId:input.transferEvidence};
    case "outbound": return {id,warehouseId,ownerId:progress.owner,item:item.value,unitId,unitCode,cap:input.cap};
    case "authorize": return {id,noticeId:progress.outbound,pickupPartnerId:input.pickup,expiresAt:input.expiry,cap:input.authorizationCap};
    case "trip": return {id,authorizationId:progress.authorization,vehicleNumber:input.vehicleNumber,cap:input.tripCap,
      recoveryReceiptId:props.resumeAuthorization ? activeCoil.value.receipt : undefined};
    case "allocate": return {id,tripId:progress.trip,receiptId:activeCoil.value.receipt};
    case "vehicle": return {tripId:progress.trip,vehicleNumber,unitId,evidenceId:input.vehicleEvidence,
      payloadLimit:input.payload,grossLimit:input.grossLimit};
    case "weigh": return {id,tripId:progress.trip,ticketId:input.ticket,vehicleNumber,unitId,gross:input.gross,
      tare:input.tare,coils:coils.value.map((coil,index)=>({
        allocationId:coil.allocation,quantity:coils.value.length===1 ? input.net : input["net-"+index]
      }))};
    case "handover": return {id,tripId:progress.trip,reviewId:progress.review,evidenceId:input.gateEvidence,
      vehicleNumber,pickupPartnerId:progress.pickup,receiptIds:coils.value.map(coil=>coil.receipt)};
    case "return": return {id,handoverId:progress.handover,custodyEvidenceId:input.returnCustody,
      titleEvidenceId:input.returnTitle,coils:coils.value.map(coil=>({
        receiptId:coil.receipt,locationId:setup.location,position:position.value
      }))};
  }
}
function commit(step:FlowStep,id:string) {
  switch(step) {
    case "inbound": progress.inbound=id; coils.value=Array.from({length:count.value},blankCoil); break;
    case "custody": activeCoil.value.custody=id; break;
    case "receive": activeCoil.value.receipt=id; break;
    case "identify": activeCoil.value.serial=input.serial; break;
    case "transfer": if (!progress.owner) progress.owner=input.newOwner; break;
    case "outbound": progress.outbound=id; break;
    case "authorize": progress.authorization=id; progress.pickup=input.pickup; break;
    case "trip": progress.trip=id; progress.vehicle=input.vehicleNumber; break;
    case "allocate": activeCoil.value.allocation=id; break;
    case "weigh": progress.review=id; break;
    case "handover": progress.handover=id; break;
    case "return": progress.returned=id; break;
  }
}

/** 分卷前段及配货完成后才推进共用的车次步骤。 */
function advance(step:FlowStep) {
  if (step==="transfer"||step==="allocate") {
    if (activeCoilIndex.value+1<coils.value.length) {
      activeCoilIndex.value++;
      if (step==="transfer") state.value=steps.indexOf("custody");
      return;
    }
    activeCoilIndex.value=0;
  }
  state.value=props.reissueTransit && step==="handover" ? steps.length : state.value+1;
}

/** 查询可在新会话按原实收身份重开，后端返回真实账与关联单据。 */
async function refreshTrace(receiptId:string) {
  if (!uuid.test(receiptId)) {
    await setErrorFeedback(errors,text("invalidReceipt"));
    return;
  }
  const epoch=++traceEpoch;
  if (trace.value?.stock.receiptId!==receiptId) {
    trace.value=undefined;
    authorization.value=undefined;
  }
  tracing.value=true;
  try {
    const result=await props.api.trace(receiptId);
    if (epoch!==traceEpoch) return;
    trace.value=result;
    if (props.resumeAuthorization && receiptId===props.initialReceipt && operations.value.includes("BulkCoilOutbound.recover_authorization")) {
      const saved=await props.api.recovery(props.resumeAuthorization,receiptId);
      if (epoch!==traceEpoch) return;
      recovery.value=saved;
    }
    authorization.value=undefined;
    const last=result.documents.findLast(doc=>doc.kind==="OUTBOUND");
    const authorizationId=(receiptId===props.initialReceipt ? recovery.value?.authorizationId : undefined)
      ?? (mode.value==="execute" ? progress.authorization || last?.parentId : last?.parentId);
    if (authorizationId && operations.value.includes("BulkCoilOutbound.read_authorization_balances")) {
      const {stock}=result;
      const balance=await props.api.authorization({ownerId:last?.ownerId??stock.ownerId,
        warehouseId:last?.warehouseId??stock.warehouseId,productId:stock.productId,identityId:stock.identityId},authorizationId);
      if (epoch===traceEpoch) authorization.value=balance;
    }
  } catch(cause) {
    if (epoch!==traceEpoch) return;
    if ((cause as {response?:{status?:number}})?.response?.status===403) {
      trace.value=undefined;
      authorization.value=undefined;
    }
    await problem(cause);
  } finally {
    if (epoch===traceEpoch) tracing.value=false;
  }
}

async function submit() {
  const step=current.value;
  if (!step || submitting.value || !ready.value || !operations.value.includes(operation[step])) return;
  submitting.value=true;
  try {
    if (!await form.value?.validate().catch(()=>false)) return;
    if (step==="transfer" && input.newOwner===setup.owner) {
      await setErrorFeedback(errors,text("differentOwner"));
      return;
    }
    if (props.reissueTransit && (step==="outbound" || (step==="trip" && cancelledTrip.value))
      && !await checkReissue(activeCoil.value.receipt)) return;
    const signature=JSON.stringify(payload(step,""));
    if (!command || command.step!==step || command.signature!==signature) {
      command={step,id:crypto.randomUUID(),signature};
    }
    const result=await props.api.submit(step,payload(step,command.id));
    if (result?.state==="HELD") {
      await setErrorFeedback(errors,text("held")+" "+(result.reasons??[]).join(", "));
      return;
    }
    commit(step,command.id);
    if (step==="handover") gateFrozen.value=false;
    const receiptId=activeCoil.value.receipt;
    command=undefined;
    Object.keys(input).forEach(key=>delete input[key]);
    advance(step);
    errors.value="";
    ElMessage.success(text("submitted"));
    if (receiptId) {
      await refreshTrace(receiptId);
      if (step==="trip" && props.resumeAuthorization && operations.value.includes("BulkStockRestriction.read")) {
        await refreshRestrictions(receiptId);
      }
    }
  } catch(cause) {
    await problem(cause);
  } finally {
    submitting.value=false;
  }
}

/** 取消只提交原分配标识，成功后才能清除本页已失效的车次和复磅上下文。 */
async function cancelFrozenAllocation() {
  const coil=activeCoil.value, tripId=progress.trip;
  if (!props.reissueTransit || current.value!=="handover" || !gateFrozen.value || !tripId
    || !coil.allocation || submitting.value || !operations.value.includes("BulkCoilOutbound.cancel")) return;
  submitting.value=true;
  try {
    const accepted=await ElMessageBox.confirm(text("cancelAllocationConfirm"),text("cancelAllocation"),{
      type:"warning",confirmButtonText:text("cancelAllocation"),cancelButtonText:t("masterData.cancel")
    }).then(()=>true,()=>false);
    if (!accepted) return;
    await props.api.cancelAllocation({id:coil.allocation,tripId,receiptId:coil.receipt});
    cancelledTrip.value=tripId;
    coil.allocation="";
    progress.trip=progress.vehicle=progress.review="";
    gateFrozen.value=false;
    command=undefined;
    Object.keys(input).forEach(key=>delete input[key]);
    state.value=steps.indexOf("trip");
    errors.value="";
    ElMessage.success(text("allocationCancelled"));
    if (props.resumeAuthorization!==progress.authorization && operations.value.includes("BulkCoilOutbound.recover_authorization")) {
      props.saveResume(progress.authorization);
    } else {
      await refreshTrace(coil.receipt);
    }
  } catch(cause) {
    await problem(cause);
  } finally {
    submitting.value=false;
  }
}

/** 校验当前可发条件，配货与交接仍由后端在原事务中复核。 */
async function checkReissue(receiptId:string) {
  const eligibility=await props.api.eligibility(receiptId);
  if (eligibility==="AVAILABLE") return true;
  const key="coilFlow.reissueGates."+eligibility;
  await setErrorFeedback(errors,t(te(key)?key:"coilFlow.reissueUnavailable"));
  return false;
}

/** 监管凭证单独走原监管查询，不随授权余额权限开放。 */
async function refreshRestrictions(receiptId:string) {
  restrictionHistory.value=[];
  try {restrictionHistory.value=await props.loadRestrictions(receiptId);}
  catch(cause) {await problem(cause);}
}

/** 只接受在途单内已实际接管、原卷已完成返仓质检的库存身份。 */
async function loadReissue(receiptId:string) {
  const transitId=props.reissueTransit!;
  if (!uuid.test(transitId)) return setErrorFeedback(errors,text("invalidReceipt"));
  const [manifest,facts]=await Promise.all([props.loadTransit(transitId),props.api.trace(receiptId)]);
  const stock=facts.stock;
  const coil=manifest.coils.find(row=>row.receiptId===receiptId && row.receivedReturnId
    && row.receivingWarehouseId===stock.warehouseId);
  if (!coil || !manifest.history.some(row=>row.kind==="RECEIVE" && row.id===coil.receivedReturnId
    && row.receiptId===receiptId && row.qualityEvidenceId)) {
    return setErrorFeedback(errors,text("reissueUnavailable"));
  }
  await refreshTrace(receiptId);
  if (props.resumeAuthorization) {
    if (!uuid.test(props.resumeAuthorization) || !recovery.value) {
      return setErrorFeedback(errors,text("recoveryUnavailable"));
    }
    if (operations.value.includes("BulkStockRestriction.read")) {
      await refreshRestrictions(receiptId);
    }
    if (!recovery.value.canPlan && !recovery.value.activeTrip) return;
  } else if (!await checkReissue(receiptId)) return;
  const detail=await props.api.product(stock.productId);
  if (detail.unitId!==stock.unitId || !detail.items.some(row=>row.id===stock.identityId)) {
    return setErrorFeedback(errors,text("unitRequired"));
  }
  product.value=detail;
  Object.assign(setup,{owner:stock.ownerId,warehouse:stock.warehouseId,product:stock.productId,item:stock.identityId});
  progress.owner=stock.ownerId;
  coils.value=[{...blankCoil(),receipt:receiptId,serial:stock.serial??""}];
  if (recovery.value) {
    const saved=recovery.value;
    Object.assign(progress,{outbound:saved.noticeId,authorization:saved.authorizationId,pickup:saved.pickupPartnerId});
    cancelledTrip.value=saved.events.findLast(row=>row.kind==="CANCELLED")?.tripId??"";
    const trip=saved.activeTrip;
    if (trip) {
      Object.assign(progress,{trip:trip.tripId,vehicle:trip.vehicleNumber,review:trip.reviewId??""});
      activeCoil.value.allocation=trip.allocationId??"";
      const next:Record<typeof trip.nextStep,FlowStep>={ALLOCATE:"allocate",VEHICLE:"vehicle",WEIGH:"weigh",HANDOVER:"handover"};
      state.value=steps.indexOf(next[trip.nextStep]);
    } else {
      state.value=steps.indexOf("trip");
    }
  } else {
    state.value=steps.indexOf("outbound");
  }
  mode.value="execute";
  reissueReady.value=true;
}

function changeMode() {
  traceEpoch++;
  trace.value=undefined;
  authorization.value=undefined;
  errors.value="";
  tracing.value=false;
  if (mode.value==="execute" && activeCoil.value.receipt) void refreshTrace(activeCoil.value.receipt);
}

watch(()=>props.initialReceipt,receiptId=>{
  if (receiptId && uuid.test(receiptId)) {
    mode.value="trace";
    lookup.value=receiptId;
    void refreshTrace(receiptId);
  }
});
onMounted(async()=>{
  try {
    operations.value=await props.api.operations();
    void findOwners();
    void findProducts();
    warehouses.value=await props.api.locations("warehouse");
    if (props.initialReceipt && uuid.test(props.initialReceipt)) {
      mode.value="trace";
      lookup.value=props.initialReceipt;
      if (props.reissueTransit) await loadReissue(props.initialReceipt);
      else await refreshTrace(props.initialReceipt);
    }
  } catch(cause) {
    await problem(cause);
  }
});
</script>

<template>
  <div class="management-page">
    <BusinessWorkspace :title="text('heading')">
      <template #navigation>
        <el-tabs v-model="mode" @tab-change="changeMode">
          <el-tab-pane :label="text('execute')" name="execute" />
          <el-tab-pane :label="text('trace')" name="trace" />
        </el-tabs>
      </template>
      <template #feedback><FloatingError :title="errors" /></template>
      <template #search>
        <el-form v-if="mode==='trace'" inline @submit.prevent="refreshTrace(lookup)">
          <div class="management-filter">
            <el-form-item :label="text('receiptId')"><el-input v-model.trim="lookup" maxlength="36" /></el-form-item>
          </div>
          <div class="management-filter-actions">
            <el-button v-operation="'BulkWarehouseStockBalance.trace'" type="primary" native-type="submit" :loading="tracing">
              {{text('lookup')}}
            </el-button>
          </div>
        </el-form>
        <el-form v-else-if="current && (!reissueTransit || reissueReady)" :key="current+':'+activeCoilIndex" ref="form" :model="input" inline
          :disabled="submitting" @submit.prevent="submit">
          <div class="management-filter">
            <el-tag v-if="perCoil(current) && coils.length>1" type="info">
              {{t('coilFlow.coilProgress',{current:activeCoilIndex+1,total:coils.length})}}
            </el-tag>
            <template v-if="current==='inbound'">
              <el-form-item :label="text('owner')">
                <el-select v-model="setup.owner" class="coil-flow-select" filterable remote :remote-method="findOwners">
                  <el-option v-for="row in owners" :key="row.id" :label="row.label" :value="row.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="text('warehouse')">
                <el-select v-model="setup.warehouse" class="coil-flow-select" filterable @change="warehouseChanged">
                  <el-option v-for="row in warehouses" :key="row.id" :label="row.label" :value="row.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="text('area')">
                <el-select v-model="setup.area" class="coil-flow-select" filterable @change="areaChanged">
                  <el-option v-for="row in areas" :key="row.id" :label="row.label" :value="row.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="text('location')">
                <el-select v-model="setup.location" class="coil-flow-select" filterable>
                  <el-option v-for="row in locations" :key="row.id" :label="row.label" :value="row.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="text('product')">
                <el-select v-model="setup.product" class="coil-flow-select" filterable remote :remote-method="findProducts"
                  @change="productChanged">
                  <el-option v-for="row in products" :key="row.id" :label="row.label" :value="row.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="text('item')">
                <el-select v-model="setup.item" class="coil-flow-select" filterable>
                  <el-option v-for="row in product?.items??[]" :key="row.id" :label="row.label" :value="row.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="text('coilCount')">
                <el-input-number v-model="count" :min="1" :max="20" :step="1" step-strictly />
              </el-form-item>
            </template>
            <el-form-item v-for="field in selectedFields" :key="field[0]"
              :label="label(field)"
              :prop="field[0]" :rules="rules(field)">
              <el-select v-if="field[1]==='partner'" v-model="input[field[0]]" class="coil-flow-select" filterable remote
                :remote-method="findOwners">
                <el-option v-for="row in owners" :key="row.id" :label="row.label" :value="row.id" />
              </el-select>
              <el-input v-else v-model="input[field[0]]" class="coil-flow-select"
                :maxlength="field[1]==='uuid'?36:64" :placeholder="field[1]==='time'?text('timestamp'):undefined" />
            </el-form-item>
          </div>
          <div class="management-filter-actions">
            <el-button v-operation="activeOperation" native-type="submit" type="primary"
              :loading="submitting" :disabled="!ready">{{text('steps.'+current)}}</el-button>
            <el-button v-if="reissueTransit && current==='handover' && gateFrozen && activeCoil.allocation"
              v-operation="'BulkCoilOutbound.cancel'" :disabled="submitting"
              @click="cancelFrozenAllocation">{{text('cancelAllocation')}}</el-button>
          </div>
        </el-form>
      </template>
      <template #data>
        <div class="business-workspace-stack management-table-stack">
          <el-descriptions v-if="mode==='execute' && (progress.inbound || progress.outbound)" :title="text('accepted')" :column="2" border>
            <el-descriptions-item v-if="progress.inbound" :label="text('inboundId')"><span class="coil-flow-id">{{progress.inbound}}</span></el-descriptions-item>
            <el-descriptions-item v-if="activeCoil.custody" :label="text('custodyId')"><span class="coil-flow-id">{{activeCoil.custody}}</span></el-descriptions-item>
            <el-descriptions-item v-if="progress.outbound" :label="text('outboundId')"><span class="coil-flow-id">{{progress.outbound}}</span></el-descriptions-item>
            <el-descriptions-item v-if="cancelledTrip" :label="text('cancelledTrip')">
              <span class="coil-flow-id">{{cancelledTrip}}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="progress.trip" :label="text('tripId')"><span class="coil-flow-id">{{progress.trip}}</span></el-descriptions-item>
            <el-descriptions-item v-if="progress.handover" :label="text('kinds.HANDOVER')">
              <span class="coil-flow-id">{{progress.handover}}</span>
              <el-button v-operation="'BulkCoilOutbound.prepare_return'" type="primary" link
                @click="startReturn(progress.handover)">{{text('startReturn')}}</el-button>
            </el-descriptions-item>
          </el-descriptions>
          <el-descriptions v-if="trace" :title="text('currentStock')" :column="2" border v-loading="tracing">
            <el-descriptions-item :label="text('receiptId')"><span class="coil-flow-id">{{trace.stock.receiptId}}</span></el-descriptions-item>
            <el-descriptions-item v-if="reissueTransit" :label="text('warehouse')">
              <span class="coil-flow-id">{{warehouses.find(row=>row.id===trace?.stock.warehouseId)?.label??trace.stock.warehouseId}}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="reissueTransit" :label="text('returnTransit')">
              <el-button v-if="mode==='execute' && (current==='handover' || (current==='trip' && cancelledTrip))"
                v-operation="'BulkStockRestriction.read'" link type="primary" tag="a" target="_blank" rel="noopener"
                :href="restrictionLink(reissueTransit,activeCoil.receipt)">{{text('openGateRestriction')}}</el-button>
              <el-button v-else v-operation="'BulkCoilOutbound.read_return_transit'" link type="primary"
                @click="viewTransit(reissueTransit)">{{text('viewReturnTransit')}}</el-button>
            </el-descriptions-item>
            <el-descriptions-item :label="text('serial')">{{trace.stock.serial??"—"}}</el-descriptions-item>
            <el-descriptions-item :label="text('owner')"><span class="coil-flow-id">{{ownerLabel(trace.stock.ownerId)}}</span></el-descriptions-item>
            <el-descriptions-item :label="text('unit')"><span class="coil-flow-id">{{product?.unitName??trace.stock.unitId}}</span></el-descriptions-item>
            <el-descriptions-item :label="text('onHand')">{{quantity(trace.stock.onHand)}}</el-descriptions-item>
            <el-descriptions-item :label="text('pending')">{{quantity(trace.stock.pending)}}</el-descriptions-item>
            <el-descriptions-item :label="text('titleQuantity')">{{quantity(trace.stock.title)}}</el-descriptions-item>
            <el-descriptions-item :label="text('custodyId')"><span class="coil-flow-id">{{trace.stock.custodyId}}</span></el-descriptions-item>
            <el-descriptions-item :label="text('measurementId')"><span class="coil-flow-id">{{trace.stock.measurementId}}</span></el-descriptions-item>
            <el-descriptions-item :label="text('fields.titleEvidence')"><span class="coil-flow-id">{{trace.stock.titleEvidenceId??"—"}}</span></el-descriptions-item>
            <el-descriptions-item :label="text('fields.qualityEvidence')"><span class="coil-flow-id">{{trace.stock.qualityEvidenceId??"—"}}</span></el-descriptions-item>
          </el-descriptions>
          <el-descriptions v-if="authorization" :title="text('authorizationBalance')" :column="2" border>
            <el-descriptions-item :label="text('authorizationId')"><span class="coil-flow-id">{{authorization.authorizationId}}</span></el-descriptions-item>
            <el-descriptions-item :label="text('fields.cap')">{{quantity(authorization.cap)}}</el-descriptions-item>
            <el-descriptions-item :label="text('reserved')">{{quantity(authorization.reserved)}}</el-descriptions-item>
            <el-descriptions-item :label="text('issued')">{{quantity(authorization.issued)}}</el-descriptions-item>
            <el-descriptions-item :label="text('remaining')">{{quantity(authorization.remaining)}}</el-descriptions-item>
          </el-descriptions>
          <template v-if="recovery && trace?.stock.receiptId===initialReceipt">
            <el-descriptions :title="text('recoveryInfo')" :column="2" border>
              <el-descriptions-item :label="text('authorizationId')">
                <span class="coil-flow-id">{{recovery.authorizationId}}</span>
              </el-descriptions-item>
              <el-descriptions-item :label="text('outboundId')">
                <span class="coil-flow-id">{{recovery.noticeId}}</span>
              </el-descriptions-item>
            </el-descriptions>
            <ManagementTable embedded :title="text('recoveryTrips')" :columns="recoveryColumns"
              :rows="recovery.events" :loading="tracing" @refresh="refreshTrace(initialReceipt!)">
              <template #kind="{row}">{{text('recoveryKinds.'+row.kind)}}</template>
              <template #evidenceId="{row}">{{row.evidenceId??"—"}}</template>
              <template #occurredAt="{row}">{{auditFormat.format(new Date(row.occurredAt))}}</template>
              <template v-for="key in (['cap','reserved','issued'] as const)" :key="key" #[key]="{row}">
                <span class="coil-flow-amount">{{quantity(row[key])}}</span>
              </template>
            </ManagementTable>
            <ManagementTable v-if="operations.includes('BulkStockRestriction.read')" embedded :title="text('recoveryRestrictions')"
              :columns="restrictionColumns" :rows="restrictionHistory" @refresh="refreshRestrictions(initialReceipt!)">
              <template #releaseEvidenceId="{row}">{{row.releaseEvidenceId??"—"}}</template>
            </ManagementTable>
          </template>
          <ManagementTable embedded :title="text('documents')" :columns="columns" :rows="trace?.documents??[]"
            :loading="tracing" :empty-text="text('empty')" @refresh="trace && refreshTrace(trace.stock.receiptId)">
            <template #kind="{row}">
              {{text("kinds."+row.kind)}}
              <el-button v-if="row.kind==='HANDOVER' && mode==='trace'" v-operation="'BulkCoilOutbound.prepare_return'"
                type="primary" link @click="startReturn(row.id)">{{text('startReturn')}}</el-button>
            </template>
            <template #owner="{row}">{{ownerLabel(row.ownerId)}}<span v-if="row.otherOwnerId"> → {{ownerLabel(row.otherOwnerId)}}</span></template>
            <template #quantity="{row}"><span class="coil-flow-amount">{{quantity(row.quantity)}}</span></template>
          </ManagementTable>
        </div>
      </template>
    </BusinessWorkspace>
  </div>
</template>

<style scoped>
.coil-flow-select { inline-size: min(15rem, 100%); }
.coil-flow-id { overflow-wrap: anywhere; }
.coil-flow-amount { display: block; text-align: end; }
</style>
