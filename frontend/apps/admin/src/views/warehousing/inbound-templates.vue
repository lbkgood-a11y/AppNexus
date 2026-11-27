<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import { AppPagination, BusinessWorkspace, FloatingError, ManagedDialog, ManagementTable, RecordAuditDetails, type RecordAudit } from "@appnexus/ui";
import { warehouseTitleApi } from "@/api/warehouseTitle";
import { executeObjectCommand } from "@/api/objectExecution";
import { http, requestErrorMessage } from "@/utils/http";

/** 配置维护入口。浏览器不生成质量门槛，发布使用服务端版本。 */
type Profile = "QUALITY_DEDUCTION" | "SAMPLED_BULK" | "STEEL_LPN";
type Metric = { code: string; unit: string; minimum: string; maximum: string; acceptMinimum: string; acceptMaximum: string };
type Rule = { metric: string; baseline: string; multiplier: string };
type Definition = {
  noticeProductIds: string[]; qualityMetrics?: Record<string, Omit<Metric,"code">>;
  deductionFormula?: { version: string; moisture: Rule; impurity: Rule };
  maxVarianceRatio?: string; requireBothWeights?: boolean;
};
type Draft = { code: string; version: number; profile: Profile; definition: Definition };
type Template = { id: string; rowVersion: number; status: string; draft: Draft; audit?: RecordAudit };
const path = "/api/bulk-commodity-templates";
const { locale } = useI18n();
const text = (zh: string,en: string) => locale.value.startsWith("zh") ? zh : en;
const title = computed(() => text("入库品类模板","Inbound commodity templates"));
const profiles = computed<[Profile,string][]>(() => [
  ["QUALITY_DEDUCTION",text("质检合格与监卸后公式扣重","Qualified and unloaded with deduction")],
  ["SAMPLED_BULK",text("散货采样与异步化验","Bulk sampling and asynchronous lab")],
  ["STEEL_LPN",text("钢铁双计量与卷包","Steel measurement and LPN")]
]);
const profileName = (profile: Profile) => profiles.value.find(([key]) => key===profile)?.[1] ?? profile;
const rows = ref<Template[]>([]);
const limit = ref(20);
const page = ref(1);
const total = ref(0);
const busy = ref(false);
const error = ref("");
const issue = ref("");
const dialog = ref(false);
const editing = ref<Template>();
const form = ref<Draft>();
const viewOnly = ref(false);
const metrics = ref<Metric[]>([]);
const codesForFormula = computed(() => metrics.value.map(metric=>metric.code.trim()).filter(Boolean));
const products = ref<{id:string;label:string}[]>([]);
const productNames = ref<Record<string,string>>({});
let productRequest = 0;
const newMetric = (): Metric => ({ code:"",unit:"",minimum:"",maximum:"",acceptMinimum:"",acceptMaximum:"" });
const newRule = (): Rule => ({ metric:"",baseline:"",multiplier:"" });
const metricFields = [
  ["code","指标代码","Metric code"],["unit","单位","Unit"],["minimum","有效最小值","Valid minimum"],
  ["maximum","有效最大值","Valid maximum"],["acceptMinimum","合格下限","Passing minimum"],["acceptMaximum","合格上限","Passing maximum"]
] as const;
const columns = computed(() => [
  { key:"code",label:text("编码","Code") },{key:"version",label:text("版本","Version")},
  { key:"profile",label:text("策略","Profile") },{key:"status",label:text("状态","Status")},
  { key:"operations",label:text("操作","Actions"),width:250}
]);
const statusText = (status: string) => ({
  DRAFT: text("草稿","Draft"), PUBLISHED: text("已发布","Published"), DISABLED: text("已停用","Disabled")
} as Record<string,string>)[status] ?? status;

async function load() {
  busy.value=true;
  error.value="";
  try {
    const result=await http.request<{rows:Template[];total:number}>("get",path,{params:{page:page.value,pageSize:limit.value}});
    rows.value=result.rows;
    total.value=result.total;
  } catch(cause) { error.value=requestErrorMessage(cause); }
  finally { busy.value=false; }
}

function changePage(next:number,size:number) {
  page.value=next;
  limit.value=size;
  void load();
}
onMounted(load);

/** 按名称搜索已授权商品，保留已选择项名称。 */
async function findProducts(keyword: string) {
  const generation=++productRequest;
  try {
    const found=await warehouseTitleApi.products(keyword);
    if(generation!==productRequest)return;
    products.value=found;
    for(const item of found) productNames.value[item.id]=item.label;
  } catch(cause) { if(generation===productRequest)issue.value=requestErrorMessage(cause); }
}

function open(row?: Template, readonly=false) {
  editing.value=row;
  viewOnly.value=readonly;
  issue.value="";
  const initial: Draft = { code:"",version:1,profile:"SAMPLED_BULK",definition:{noticeProductIds:[]} };
  form.value=row ? JSON.parse(JSON.stringify(row.draft)) as Draft : initial;
  const definition=form.value.definition;
  definition.noticeProductIds ??= [];
  if(definition.maxVarianceRatio!=null) definition.maxVarianceRatio=String(definition.maxVarianceRatio);
  metrics.value=Object.entries(definition.qualityMetrics ?? {}).map(([code,rule])=>({
    code,unit:rule.unit,minimum:String(rule.minimum),maximum:String(rule.maximum),
    acceptMinimum:String(rule.acceptMinimum),acceptMaximum:String(rule.acceptMaximum)
  }));
  if(!readonly && !metrics.value.length) metrics.value=[newMetric()];
  definition.deductionFormula ??= {version:"",moisture:newRule(),impurity:newRule()};
  dialog.value=true;
  void findProducts("");
}

/** 输入十进制文本以避免浏览器浮点舍入，最终范围校验由发布服务执行。 */
async function save() {
  const current=form.value;
  if(!current || viewOnly.value || busy.value)return;
  const source=current.definition;
  issue.value="";
  const decimal=/^-?\d{1,18}(?:\.\d{1,12})?$/;
  const codes=metrics.value.map(row=>row.code.trim());
  if(!current.code.trim() || !source.noticeProductIds.length || (current.profile!=="STEEL_LPN" &&
      (!metrics.value.length || new Set(codes).size!==codes.length || metrics.value.some(row=>
        !row.code.trim() || !row.unit.trim() || ![row.minimum,row.maximum,row.acceptMinimum,row.acceptMaximum].every(value=>decimal.test(value)))))) {
    issue.value=text("请补齐模板、适用商品和有效指标区间","Complete the template, products and valid metric ranges");
    return;
  }
  const definition: Definition={noticeProductIds:source.noticeProductIds};
  if(current.profile==="STEEL_LPN") {
    if(!source.maxVarianceRatio || !decimal.test(source.maxVarianceRatio) || source.requireBothWeights==null) {
      issue.value=text("请填写磅差率上限和实重凭证要求","Enter the maximum variance ratio and select ticket requirements");
      return;
    }
    definition.maxVarianceRatio=source.maxVarianceRatio;
    definition.requireBothWeights=source.requireBothWeights;
  } else {
    definition.qualityMetrics=Object.fromEntries(metrics.value.map(({code,...rule})=>[code.trim(),rule]));
    if(current.profile==="QUALITY_DEDUCTION") definition.deductionFormula=source.deductionFormula;
  }
  const draft={...current,code:current.code.trim(),definition};
  busy.value=true;
  try {
    if(editing.value) {
      const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
      await http.request("put",`${path}/${encodeURIComponent(editing.value.id)}`,
        {data:{rowVersion:editing.value.rowVersion,draft},headers:{[csrf.headerName]:csrf.token}});
    } else await executeObjectCommand(path,draft);
    dialog.value=false;
    ElMessage.success(text("草稿已保存","Draft saved"));
    await load();
  } catch(cause) {issue.value=requestErrorMessage(cause);}
  finally {busy.value=false;}
}

async function publish(row: Template) {
  const confirmed=await ElMessageBox.confirm(text("发布后新车次使用新版本，同编码的旧版本停止提供给新车次。",
    "New arrivals will use this version. The previous version of this code retires for new arrivals."),
    text("发布模板","Publish template"),{type:"warning"}).then(()=>true,()=>false);
  if(!confirmed || busy.value)return;
  busy.value=true;
  error.value="";
  try {
    await executeObjectCommand(`${path}/${encodeURIComponent(row.id)}:publish`,{rowVersion:row.rowVersion});
    ElMessage.success(text("模板已发布","Template published"));
    await load();
  } catch(cause) {error.value=requestErrorMessage(cause);}
  finally {busy.value=false;}
}
</script>

<template>
  <div class="management-page">
    <BusinessWorkspace :title="title">
      <template #data>
        <FloatingError :title="error" />
        <ManagementTable embedded :title="title" :rows="rows" :columns="columns" :loading="busy" :total="total" @refresh="load">
          <template #actions>
            <el-button v-operation="'BulkCommodityTemplate.save'" type="primary" @click="open()">{{ text("新建模板草稿","New template draft") }}</el-button>
          </template>
          <template #code="{row}">{{ row.draft.code }}</template>
          <template #version="{row}">{{ row.draft.version }}</template>
          <template #profile="{row}">{{ profileName(row.draft.profile) }}</template>
          <template #status="{row}">{{ statusText(row.status) }}</template>
          <template #operations="{row}">
            <el-button v-operation="'BulkCommodityTemplate.list'" link @click="open(row,true)">{{ text("查看","View") }}</el-button>
            <el-button v-if="row.status==='DRAFT'" v-operation="'BulkCommodityTemplate.save'" link @click="open(row)">{{ text("编辑","Edit") }}</el-button>
            <el-button v-if="row.status==='DRAFT'" v-operation="'BulkCommodityTemplate.publish'" link type="primary" @click="publish(row)">{{ text("发布","Publish") }}</el-button>
          </template>
          <template #pagination>
            <AppPagination :current-page="page" :page-size="limit" :total="total" :disabled="busy"
              layout="total, sizes, prev, pager, next" @update:current-page="next => changePage(next,limit)"
              @update:page-size="size => changePage(1,size)" />
          </template>
        </ManagementTable>
      </template>
    </BusinessWorkspace>
    <ManagedDialog v-model="dialog" :title="title" :busy="busy" width="min(820px, 95vw)">
      <FloatingError :title="issue" />
      <el-form v-if="form" label-position="top" :disabled="busy || viewOnly" @submit.prevent="save">
        <el-form-item :label="text('模板编码','Template code')" required><el-input v-model.trim="form.code" :readonly="!!editing" maxlength="64" /></el-form-item>
        <el-form-item :label="text('版本','Version')" required><el-input-number v-model="form.version" :min="1" :precision="0" :disabled="!!editing" /></el-form-item>
        <el-form-item :label="text('适用商品','Products')" required>
          <el-select v-model="form.definition.noticeProductIds" multiple filterable remote :remote-method="findProducts">
            <el-option v-for="item in [...products,...form.definition.noticeProductIds.filter(id=>!products.some(item=>item.id===id)).map(id=>({id,label:productNames[id]??id}))]"
              :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="text('入库策略','Inbound profile')" required>
          <el-select v-model="form.profile"><el-option v-for="[key,name] in profiles" :key="key" :label="name" :value="key" /></el-select>
        </el-form-item>
        <template v-if="form.profile==='STEEL_LPN'">
          <el-form-item :label="text('磅差率上限（0–1）','Maximum variance ratio (0–1)')" required><el-input v-model.trim="form.definition.maxVarianceRatio" inputmode="decimal" /></el-form-item>
          <el-form-item :label="text('实重时要求理重和磅票','Require theoretical weight and scale tickets')" required>
            <el-select v-model="form.definition.requireBothWeights">
              <el-option :label="text('是','Yes')" :value="true" /><el-option :label="text('否','No')" :value="false" />
            </el-select>
          </el-form-item>
        </template>
        <template v-else>
          <el-card v-for="(metric,index) in metrics" :key="index" shadow="never">
            <el-form-item v-for="[key,zh,en] in metricFields" :key="key" :label="text(zh,en)" required>
              <el-input v-model.trim="metric[key]" />
            </el-form-item>
            <el-button v-operation="'BulkCommodityTemplate.save'" v-if="!viewOnly" link type="danger" @click="metrics.splice(index,1)">{{ text("移除指标","Remove metric") }}</el-button>
          </el-card>
          <el-button v-operation="'BulkCommodityTemplate.save'" v-if="!viewOnly" plain @click="metrics.push(newMetric())">{{ text("增加化验指标","Add lab metric") }}</el-button>
          <template v-if="form.profile==='QUALITY_DEDUCTION' && form.definition.deductionFormula">
            <el-form-item :label="text('扣重公式版本','Deduction formula version')" required><el-input v-model.trim="form.definition.deductionFormula.version" /></el-form-item>
            <el-card v-for="key in (['moisture','impurity'] as const)" :key="key" shadow="never">
              <template #header>{{ key==='moisture' ? text("扣水","Moisture deduction") : text("扣杂","Impurity deduction") }}</template>
              <el-form-item :label="text('指标','Metric')" required>
                <el-select v-model="form.definition.deductionFormula[key].metric">
                  <el-option v-for="value in codesForFormula" :key="value" :label="value" :value="value" />
                </el-select>
              </el-form-item>
              <el-form-item :label="text('基准百分数','Baseline percent')" required><el-input v-model.trim="form.definition.deductionFormula[key].baseline" inputmode="decimal" /></el-form-item>
              <el-form-item :label="text('扣重系数','Multiplier')" required><el-input v-model.trim="form.definition.deductionFormula[key].multiplier" inputmode="decimal" /></el-form-item>
            </el-card>
          </template>
        </template>
      </el-form>
      <RecordAuditDetails v-if="editing?.audit" :audit="editing.audit" />
      <template #footer>
        <el-button :disabled="busy" @click="dialog=false">{{ text("关闭","Close") }}</el-button>
        <el-button v-if="!viewOnly" v-operation="'BulkCommodityTemplate.save'" type="primary" :loading="busy" @click="save">{{ text("保存草稿","Save draft") }}</el-button>
      </template>
    </ManagedDialog>
  </div>
</template>
