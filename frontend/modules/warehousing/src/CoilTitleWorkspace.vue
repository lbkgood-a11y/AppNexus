<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, type FormInstance } from "element-plus";
import {
  BusinessWorkspace, FloatingError, ManagedDialog, ManagementTable, setErrorFeedback, type ManagementColumn
} from "@appnexus/ui";
import type {
  AuthorizationBalance, AuthorizationRecoveryEntry, CursorPage, PhysicalBalance, TitleTransferInput,
  WarehouseCoil, WarehouseOption, WarehouseScope, WarehouseTitleApi
} from "./types";

/** 货权工作区独立展示实物与单据。选中库存版本和审批凭证随原请求提交。 */
const props=defineProps<{ api: WarehouseTitleApi; errorText: (error: unknown) => string;
  openRecovery:(authorizationId:string,entry:AuthorizationRecoveryEntry)=>void }>();
const { t, te, locale }=useI18n();
const filters=reactive({ownerId:"",warehouseId:"",productId:"",identityId:""});
const form=ref<FormInstance>(), transferForm=ref<FormInstance>();
const owners=ref<WarehouseOption[]>([]), warehouses=ref<WarehouseOption[]>([]), products=ref<WarehouseOption[]>([]);
const identities=ref<WarehouseOption[]>([]), units=ref<WarehouseOption[]>([]), recipients=ref<WarehouseOption[]>([]);
const labels=reactive(new Map<string,string>());
const canReadAuthorizations=ref(false), canListRecovery=ref(false);
const recoveryOpen=ref(false), recoveryLoading=ref(false), recoveryError=ref("");
const recoveryAuthorization=ref(""), recoveryUnit=ref(""), recoveryPage=ref<CursorPage<AuthorizationRecoveryEntry>>({rows:[],nextAfterId:null});
const recoveryCursor=ref<string[]>([]);
let recoveryEpoch=0;
const applied=ref<WarehouseScope>();
const physical=ref<PhysicalBalance[]>([]);
const coils=ref<CursorPage<WarehouseCoil>>({rows:[],nextAfterId:null});
const authorizations=ref<CursorPage<AuthorizationBalance>>({rows:[],nextAfterId:null});
const coilCursor=ref<string[]>([]), authorizationCursor=ref<string[]>([]);
const loading=ref(false), coilLoading=ref(false), authorizationLoading=ref(false);
const error=ref(""), queryFailed=ref(false), denied=ref(false);
const transferring=ref(false), transferError=ref(""), open=ref(false), selected=ref<WarehouseCoil>();
const draft=reactive({toOwnerId:"",evidenceId:""});
let transferCommand: TitleTransferInput|undefined;
let queryEpoch=0, identityEpoch=0;
const optionEpoch={owners:0,warehouses:0,products:0,recipients:0};

const headers=(entries: [string, number][]):ManagementColumn[] =>
  entries.map(([key,minWidth]) => ({key,label:t("warehouseTitle."+key),minWidth,prop:key}));
const stockColumns=computed(() => headers([["identityId",170],["unitId",105],["onHandTitle",145],["pickable",130]]));
const coilColumns=computed(() => headers([["serial",150],["identityId",155],["unitId",105],["quantity",110],["status",145],["operations",105]]));
const authorizationColumns=computed(() => headers([["authorizationId",175],["noticeId",175],["identityId",145],
  ["unitId",100],["cap",110],["reserved",110],["issued",110],["remaining",110],["status",115],["operations",140]]));
const recoveryColumns=computed(()=>headers([
  ["serial",125],["vehicleNumber",145],["tripId",215],["tripReserved",120],["tripIssued",120],["operations",130]
]));
const required=computed(() => [{required:true,message:t("masterData.required"),trigger:"change" as const}]);
const evidenceRules=computed(() => [
  ...required.value,
  {pattern:/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,message:t("warehouseTitle.evidenceFormat"),trigger:"blur"}
]);
const formatter=computed(()=>new Intl.NumberFormat(locale.value,{maximumFractionDigits:12}));
const amount=(value:number) => formatter.value.format(value);
const unit=(id:string) => units.value.find(row=>row.id===id)?.label ?? id;
const label=(id:string) => labels.get(id) ?? id;
const status=(value:string) => t("warehouseTitle.states."+value);
function errorText(cause:unknown) {
  const code=(cause as {response?:{data?:{code?:string}}})?.response?.data?.code;
  const key="warehouseTitle.errors."+code;
  return code && te(key) ? t(key) : props.errorText(cause);
}

/** 搜索选项以本次服务端响应为准，迟到响应不能覆盖当前输入。 */
async function options(kind:keyof typeof optionEpoch, keyword="", selectedId="") {
  const epoch=++optionEpoch[kind];
  const target={owners,warehouses,products,recipients}[kind];
  try {
    const load=kind==="recipients" ? props.api.owners : props.api[kind];
    const rows=await load(keyword);
    if (epoch!==optionEpoch[kind]) return;
    rows.forEach(row=>labels.set(row.id,row.label));
    const chosen=target.value.find(row=>row.id===selectedId);
    target.value=chosen && !rows.some(row=>row.id===selectedId) ? [chosen,...rows] : rows;
  } catch (cause) {
    if (epoch===optionEpoch[kind]) await setErrorFeedback(kind==="recipients" ? transferError : error,errorText(cause));
  }
}

function clearResults() {
  queryEpoch++;
  loading.value=coilLoading.value=authorizationLoading.value=false;
  applied.value=undefined;
  physical.value=[];
  coils.value={rows:[],nextAfterId:null};
  authorizations.value={rows:[],nextAfterId:null};
  coilCursor.value=[];
  authorizationCursor.value=[];
  queryFailed.value=denied.value=false;
  error.value="";
}

/** 商品变更后先作废原品规及结果，再载入真实规格和 SKU。 */
async function productChanged() {
  clearResults();
  filters.identityId="";
  identities.value=[];
  const id=filters.productId;
  const epoch=++identityEpoch;
  if (!id) return;
  try {
    const rows=await props.api.identities(id);
    if (epoch!==identityEpoch) return;
    rows.forEach(row=>labels.set(row.id,row.label));
    identities.value=rows;
  } catch (cause) {
    if (epoch===identityEpoch) await setErrorFeedback(error,errorText(cause));
  }
}

function reset() {
  Object.assign(filters,{ownerId:"",warehouseId:"",productId:"",identityId:""});
  void productChanged();
}

/** 真实服务端按同一筛选分别加载两个余额视图及明细。越权失败清空结果。 */
async function search() {
  if (!await form.value?.validate().catch(()=>false)) return;
  const scope:WarehouseScope={ownerId:filters.ownerId,warehouseId:filters.warehouseId,productId:filters.productId,
    identityId:filters.identityId || undefined};
  const previous=applied.value;
  const refresh=previous?.ownerId===scope.ownerId && previous.warehouseId===scope.warehouseId
    && previous.productId===scope.productId && previous.identityId===scope.identityId && !queryFailed.value;
  if (refresh) {
    queryEpoch++;
    coilLoading.value=authorizationLoading.value=false;
    error.value="";
  } else {
    clearResults();
  }
  applied.value=scope;
  const epoch=queryEpoch;
  loading.value=true;
  try {
    const [stock, inventory, documents]=await Promise.all([
      props.api.physical(scope),props.api.coils(scope),
      canReadAuthorizations.value ? props.api.authorizations(scope) : Promise.resolve({rows:[],nextAfterId:null})
    ]);
    if (epoch!==queryEpoch) return;
    physical.value=stock;
    coils.value=inventory;
    authorizations.value=documents;
    coilCursor.value=[];
    authorizationCursor.value=[];
  } catch (cause) {
    if (epoch!==queryEpoch) return;
    const forbidden=(cause as {response?:{status?:number}})?.response?.status===403;
    if (forbidden) clearResults();
    denied.value=forbidden;
    queryFailed.value=forbidden || !refresh;
    await setErrorFeedback(error,errorText(cause));
  } finally {
    if (epoch===queryEpoch) loading.value=false;
  }
}

/** 逐表独立沿服务端游标翻页，不伪造分页总数。 */
async function page<T>(target:{value:CursorPage<T>}, cursors:{value:string[]}, busy:{value:boolean},
    load:(scope:WarehouseScope,afterId?:string)=>Promise<CursorPage<T>>, forward:boolean) {
  const scope=applied.value;
  if (!scope || busy.value) return;
  const next=target.value.nextAfterId;
  if (forward && !next) return;
  const stack=forward ? [...cursors.value,next!] : cursors.value.slice(0,-1);
  const epoch=queryEpoch;
  busy.value=true;
  try {
    const result=await load(scope,stack.at(-1));
    if (epoch!==queryEpoch) return;
    target.value=result;
    cursors.value=stack;
  } catch (cause) {
    if (epoch!==queryEpoch) return;
    if ((cause as {response?:{status?:number}})?.response?.status===403) {
      clearResults();
      denied.value=queryFailed.value=true;
    }
    await setErrorFeedback(error,errorText(cause));
  } finally {
    if (epoch===queryEpoch) busy.value=false;
  }
}

function moveCoils(forward:boolean) {
  return page(coils,coilCursor,coilLoading,props.api.coils,forward);
}

function moveAuthorizations(forward:boolean) {
  return page(authorizations,authorizationCursor,authorizationLoading,props.api.authorizations,forward);
}

function edit(row:WarehouseCoil) {
  if (!row.transferable || !applied.value) return;
  selected.value=row;
  transferCommand=undefined;
  Object.assign(draft,{toOwnerId:"",evidenceId:""});
  transferError.value="";
  open.value=true;
  void options("recipients");
}

/** 网络失败允许沿原请求键重试；换货主或凭证时生成新请求键。 */
async function transfer() {
  const scope=applied.value, row=selected.value;
  if (!scope || !row || transferring.value || !await transferForm.value?.validate().catch(()=>false)) return;
  if (!transferCommand || transferCommand.toOwnerId!==draft.toOwnerId || transferCommand.evidenceId!==draft.evidenceId) {
    transferCommand={id:crypto.randomUUID(),receiptId:row.receiptId,warehouseId:scope.warehouseId,
      fromOwnerId:scope.ownerId,toOwnerId:draft.toOwnerId,expectedTitleRevision:row.titleRevision,
      evidenceId:draft.evidenceId};
  }
  transferring.value=true;
  try {
    await props.api.transfer(transferCommand);
    open.value=false;
    ElMessage.success(t("warehouseTitle.transferSuccess"));
    await search();
  } catch (cause) {
    await setErrorFeedback(transferError,errorText(cause));
  } finally {
    transferring.value=false;
  }
}

/** 只按所选授权查询，切换或关闭弹窗时丢弃晚返回的页面。 */
async function readRecovery(authorizationId:string,nextCursor:string[]) {
  const epoch=++recoveryEpoch;
  recoveryLoading.value=true;
  recoveryError.value="";
  try {
    const page=await props.api.recoveryEntries(authorizationId,nextCursor.at(-1));
    if (epoch!==recoveryEpoch) return;
    recoveryPage.value=page;
    recoveryCursor.value=nextCursor;
  } catch(cause) {
    if (epoch===recoveryEpoch) await setErrorFeedback(recoveryError,errorText(cause));
  } finally {
    if (epoch===recoveryEpoch) recoveryLoading.value=false;
  }
}

function showRecovery(authorization:AuthorizationBalance) {
  recoveryAuthorization.value=authorization.authorizationId;
  recoveryUnit.value=authorization.unitId;
  recoveryPage.value={rows:[],nextAfterId:null};
  recoveryOpen.value=true;
  void readRecovery(authorization.authorizationId,[]);
}
function closeRecovery() {
  recoveryEpoch++;
  recoveryLoading.value=false;
}
function nextRecovery() {
  const next=recoveryPage.value.nextAfterId;
  if (next) void readRecovery(recoveryAuthorization.value,[...recoveryCursor.value,next]);
}

onMounted(async()=>{
  try {
    const operations=await props.api.operations();
    canReadAuthorizations.value=operations.includes("BulkCoilOutbound.read_authorization_balances");
    canListRecovery.value=["BulkCoilOutbound.list_recovery_entries","BulkCoilOutbound.read_return_transit",
      "BulkWarehouseStockBalance.trace"].every(operation=>operations.includes(operation));
    void options("owners");
    void options("warehouses");
    void options("products");
    units.value=await props.api.units();
  } catch (cause) {
    await setErrorFeedback(error,errorText(cause));
  }
});
</script>

<template>
  <div class="management-page">
    <BusinessWorkspace :title="t('warehouseTitle.title')" :search-title="t('warehouseTitle.searchTitle')">
      <template #feedback><FloatingError :title="error" /></template>
      <template #search>
        <el-form ref="form" :model="filters" inline @submit.prevent="search">
          <div class="management-filter">
            <el-form-item :label="t('warehouseTitle.ownerId')" prop="ownerId" :rules="required">
              <el-select v-model="filters.ownerId" filterable remote clearable class="warehouse-title-select"
                :remote-method="q=>options('owners',q,filters.ownerId)" @change="clearResults">
                <el-option v-for="item in owners" :key="item.id" :value="item.id" :label="item.label" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('warehouseTitle.warehouseId')" prop="warehouseId" :rules="required">
              <el-select v-model="filters.warehouseId" filterable remote clearable class="warehouse-title-select"
                :remote-method="q=>options('warehouses',q,filters.warehouseId)" @change="clearResults">
                <el-option v-for="item in warehouses" :key="item.id" :value="item.id" :label="item.label" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('warehouseTitle.productId')" prop="productId" :rules="required">
              <el-select v-model="filters.productId" filterable remote clearable class="warehouse-title-select"
                :remote-method="q=>options('products',q,filters.productId)" @change="productChanged">
                <el-option v-for="item in products" :key="item.id" :value="item.id" :label="item.label" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('warehouseTitle.identityId')">
              <el-select v-model="filters.identityId" filterable clearable class="warehouse-title-select"
                :disabled="!filters.productId" :placeholder="t('warehouseTitle.allIdentities')" @change="clearResults">
                <el-option v-for="item in identities" :key="item.id" :value="item.id" :label="item.label" />
              </el-select>
            </el-form-item>
          </div>
          <div class="management-filter-actions">
            <el-button v-operation="'BulkWarehouseStockBalance.read'" type="primary" native-type="submit"
              :loading="loading">{{t('masterData.search')}}</el-button>
            <el-button :disabled="transferring" @click="reset">{{t('masterData.reset')}}</el-button>
          </div>
        </el-form>
      </template>
      <template #data>
        <el-result v-if="queryFailed" :title="t(denied?'account.forbidden':'warehouseTitle.queryFailed')"
          :icon="denied?'warning':'error'">
          <template v-if="!denied" #extra><el-button v-operation="'BulkWarehouseStockBalance.read'" @click="search">{{t('masterData.retry')}}</el-button></template>
        </el-result>
        <div v-else class="business-workspace-stack management-table-stack">
          <ManagementTable embedded :title="t('warehouseTitle.physical')" :columns="stockColumns"
            :rows="physical" :loading="loading" :empty-text="t(applied?'warehouseTitle.empty':'warehouseTitle.choose')"
            @refresh="search">
            <template #identityId="{row}">{{label(row.identityId)}}</template>
            <template #unitId="{row}">{{unit(row.unitId)}}</template>
            <template #onHandTitle="{row}"><span class="warehouse-quantity">{{amount(row.onHandTitle)}}</span></template>
            <template #pickable="{row}"><span class="warehouse-quantity">{{amount(row.pickable)}}</span></template>
          </ManagementTable>
          <ManagementTable embedded :title="t('warehouseTitle.coils')" :columns="coilColumns" :rows="coils.rows"
            :loading="loading||coilLoading" :empty-text="t(applied?'warehouseTitle.empty':'warehouseTitle.choose')"
            @refresh="search">
            <template #identityId="{row}">{{label(row.identityId)}}</template>
            <template #unitId="{row}">{{unit(row.unitId)}}</template>
            <template #quantity="{row}"><span class="warehouse-quantity">{{amount(row.quantity)}}</span></template>
            <template #status="{row}"><el-tag :type="row.status==='FROZEN'?'danger':row.status==='AVAILABLE'?'success':'warning'">
              {{status(row.status)}}
            </el-tag></template>
            <template #operations="{row}"><el-button v-operation="'BulkCoilTitleTransfer.transfer'" link type="primary"
              :disabled="!row.transferable||loading||transferring" @click="edit(row)">{{t('warehouseTitle.transfer')}}</el-button></template>
            <template #pagination>
              <div class="flex gap-2">
              <el-button v-operation="'BulkWarehouseStockBalance.read_coils'" v-if="coilCursor.length" :disabled="coilLoading"
                @click="moveCoils(false)">{{t('warehouseTitle.previous')}}</el-button>
              <el-button v-operation="'BulkWarehouseStockBalance.read_coils'" v-if="coils.nextAfterId" :disabled="coilLoading"
                @click="moveCoils(true)">{{t('warehouseTitle.next')}}</el-button>
              </div>
            </template>
          </ManagementTable>
          <ManagementTable v-if="canReadAuthorizations" embedded :title="t('warehouseTitle.authorizations')"
            :columns="authorizationColumns" :rows="authorizations.rows" :loading="loading||authorizationLoading"
            :empty-text="t(applied?'warehouseTitle.empty':'warehouseTitle.choose')" @refresh="search">
            <template #identityId="{row}">{{label(row.identityId)}}</template>
            <template #unitId="{row}">{{unit(row.unitId)}}</template>
            <template v-for="key in (['cap','reserved','issued','remaining'] as const)" :key="key" #[key]="{row}">
              <span class="warehouse-quantity">{{amount(row[key])}}</span>
            </template>
            <template #status="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{status(row.status)}}</el-tag></template>
            <template #operations="{row}">
              <el-button v-if="canListRecovery" v-operation="'BulkCoilOutbound.list_recovery_entries'" link type="primary"
                @click="showRecovery(row)">{{t('warehouseTitle.recoveryEntries')}}</el-button>
            </template>
            <template #pagination>
              <div class="flex gap-2">
              <el-button v-operation="'BulkCoilOutbound.read_authorization_balances'" v-if="authorizationCursor.length" :disabled="authorizationLoading"
                @click="moveAuthorizations(false)">{{t('warehouseTitle.previous')}}</el-button>
              <el-button v-operation="'BulkCoilOutbound.read_authorization_balances'" v-if="authorizations.nextAfterId" :disabled="authorizationLoading"
                @click="moveAuthorizations(true)">{{t('warehouseTitle.next')}}</el-button>
              </div>
            </template>
          </ManagementTable>
        </div>
      </template>
    </BusinessWorkspace>

    <ManagedDialog v-model="recoveryOpen" :title="t('warehouseTitle.recoveryEntries')" @closed="closeRecovery">
      <p class="warehouse-recovery-id">{{recoveryAuthorization}}</p>
      <FloatingError :title="recoveryError" />
      <ManagementTable embedded :title="t('warehouseTitle.recoveryCoils')" :columns="recoveryColumns"
        :rows="recoveryPage.rows" :loading="recoveryLoading" :empty-text="t('warehouseTitle.recoveryEmpty')"
        @refresh="readRecovery(recoveryAuthorization,recoveryCursor)">
        <template v-for="key in (['tripReserved','tripIssued'] as const)" :key="key" #[key]="{row}">
          <span class="warehouse-quantity">{{amount(row[key])}} {{unit(recoveryUnit)}}</span>
        </template>
        <template #operations="{row}">
          <el-button v-operation="'BulkCoilOutbound.list_recovery_entries'" link type="primary" :disabled="recoveryLoading"
            @click="openRecovery(recoveryAuthorization,row)">
            {{t(row.replacementRequired?'warehouseTitle.replaceRecovery':
              row.executable?'warehouseTitle.continueRecovery':'warehouseTitle.traceRecovery')}}
          </el-button>
        </template>
        <template #pagination>
          <div class="flex gap-2">
            <el-button v-if="recoveryCursor.length" v-operation="'BulkCoilOutbound.list_recovery_entries'" :disabled="recoveryLoading"
              @click="readRecovery(recoveryAuthorization,recoveryCursor.slice(0,-1))">{{t('warehouseTitle.previous')}}</el-button>
            <el-button v-if="recoveryPage.nextAfterId" v-operation="'BulkCoilOutbound.list_recovery_entries'" :disabled="recoveryLoading"
              @click="nextRecovery">{{t('warehouseTitle.next')}}</el-button>
          </div>
        </template>
      </ManagementTable>
    </ManagedDialog>

    <ManagedDialog v-model="open" :title="t('warehouseTitle.dialogTitle')" :busy="transferring">
      <p v-if="selected">{{t('warehouseTitle.transferSummary',{coil:selected.serial,owner:label(filters.ownerId),quantity:amount(selected.quantity),unit:unit(selected.unitId)})}}</p>
      <FloatingError :title="transferError" />
      <el-form ref="transferForm" :model="draft" label-position="top" class="business-border-label-form" @submit.prevent="transfer">
        <el-form-item :label="t('warehouseTitle.toOwner')" prop="toOwnerId" :rules="required">
          <el-select v-model="draft.toOwnerId" filterable remote clearable :disabled="transferring"
            :remote-method="q=>options('recipients',q,draft.toOwnerId)">
            <el-option v-for="item in recipients.filter(r=>r.id!==filters.ownerId)" :key="item.id" :value="item.id" :label="item.label" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('warehouseTitle.evidenceId')" prop="evidenceId" :rules="evidenceRules">
          <el-input v-model="draft.evidenceId" maxlength="36" :disabled="transferring" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="transferring" @click="open=false">{{t('masterData.cancel')}}</el-button>
        <el-button v-operation="'BulkCoilTitleTransfer.transfer'" type="primary" :loading="transferring" @click="transfer">
          {{t('warehouseTitle.confirmTransfer')}}
        </el-button>
      </template>
    </ManagedDialog>
  </div>
</template>

<style scoped>
.warehouse-recovery-id { overflow-wrap: anywhere; }
.warehouse-title-select { inline-size: min(14rem, 100%); }
.warehouse-quantity { display: block; text-align: end; }
</style>
