<script setup lang="ts">
import { AppPagination, FloatingError, ManagedDialog, ManagementTable, RecordAuditDetails, TreeListTemplate, type RecordAudit, type ManagementColumn } from "@appnexus/ui";
import { AppIcon } from "@appnexus/icons";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { ElTree } from "element-plus";
import { useI18n } from "vue-i18n";
import { eventWorkbenchApi, type EventAttempt, type EventDetail, type EventSubscription, type PlatformEvent } from "@/api/eventWorkbench";
import { displayInstant } from "@/utils/displayFormat";
import { requestErrorMessage } from "@/utils/http";

const { t, locale, te } = useI18n();

/** 已知技术值使用业务文案，其他值保留明确的通用标签，原值在技术信息中可查。 */
function label(group: string, code: string, fallback: string) {
  const key = "eventWorkbench." + group + "." + code;
  return te(key) ? t(key) : t("eventWorkbench." + fallback);
}

/** 复用授权响应中的审计事实，缺失字段交由标准组件展示。 */
function recordAudit(value: Partial<RecordAudit>): RecordAudit {
  return {createdBy: null, createdAt: null, updatedBy: null, updatedAt: null, ...value};
}

function eventScope(id: string) {
  return t("eventWorkbench." + (id === "00000000-0000-0000-0000-000000000000" ? "platformScope" : "tenantScope"));
}

function showTime(value?: string) {
  return value ? displayInstant(value, locale.value) : t("recordAudit.unknown");
}

/** 汇总状态优先提示需要人工处理的投递，完整数量仍与状态同时展示。 */
function eventProgress(event: PlatformEvent) {
  if (event.manual) return { text: t("eventWorkbench.MANUAL"), tone: "danger" as const };
  if (event.processing) return { text: t("eventWorkbench.PROCESSING"), tone: "primary" as const };
  if (event.pending) return { text: t("eventWorkbench.PENDING"), tone: "warning" as const };
  if (event.done) return { text: t("eventWorkbench.DONE"), tone: "success" as const };
  return { text: t("eventWorkbench.noDeliveries"), tone: "info" as const };
}
/** 列表只展示有记录的投递结果，详情仍展示各状态完整计数。 */
function deliverySummary(event: PlatformEvent) {
  return (["done", "manual", "pending", "processing"] as const)
    .filter(status => event[status] > 0)
    .map(status => t("eventWorkbench.deliveryCounts." + status, { count: event[status] }))
    .join(" · ") || "—";
}

const states = ["PENDING", "PROCESSING", "DONE", "MANUAL"];
const subscriptions = ref<EventSubscription[]>([]);
const allEvents = "all";
const collapsed = ref(false);
const typeSearch = ref("");
const typeTree = ref<InstanceType<typeof ElTree>>();
const topologyType = ref("");
const topologyExpanded = ref(true);
const topologyLoading = ref(false);
const topologyError = ref("");
const changingSubscription = ref(false);
const selectedDelivery = ref("");
const inspectedDeliveryId = ref("");
const expandedEventDetails = ref(["technical"]);
const registeredTypes = computed(() => [...new Set(subscriptions.value.map(row => row.type))]);
type EventTypeNode = { key: string; label: string; type?: string; children?: EventTypeNode[] };

/** 用业务分类组织已注册类型，未知分类的类型仍可直接选择。 */
const eventTypeTree = computed(() => {
  const nodes: EventTypeNode[] = [{ key: allEvents, label: t("eventWorkbench.allEvents"), type: "" }];
  const groups = new Map<string, EventTypeNode[]>();
  for (const eventType of registeredTypes.value) {
    const node = { key: "type:" + eventType, label: label("eventNames", eventType, "unknownEvent"), type: eventType };
    const groupKey = "eventWorkbench.eventGroups." + eventType;
    if (!te(groupKey)) {
      nodes.push(node);
      continue;
    }
    const group = eventType.startsWith("INBOUND_") ? "warehouse" : "authority";
    let children = groups.get(group);
    if (!children) {
      children = [];
      groups.set(group, children);
      nodes.push({ key: "group:" + group, label: t(groupKey), children });
    }
    children.push(node);
  }
  return nodes;
});
const currentTypeKey = computed(() => topologyType.value ? "type:" + topologyType.value : allEvents);
const selectedTypeLabel = computed(() => topologyType.value
  ? label("eventNames", topologyType.value, "unknownEvent")
  : t("eventWorkbench.allEvents"));
const routes = computed(() => subscriptions.value.filter(row => row.type === topologyType.value));
const pausedConsumers = computed(() => new Set(subscriptions.value
  .filter(route => route.type === detail.value?.event.type && route.paused)
  .map(route => route.consumer)));
const deliveryChoices = computed(() => detail.value?.deliveries ?? []);
const inspectedDelivery = computed(() => deliveryChoices.value.find(row => row.id === inspectedDeliveryId.value));
/** 消费时间取实际成功尝试的完成时刻；历史缺失时不使用投递审计时间推断。 */
const inspectedConsumptionTime = computed(() => {
  if (inspectedDelivery.value?.status !== "DONE") return "—";
  const succeeded = detail.value?.attempts.reduce<EventAttempt | undefined>((latest, attempt) => {
    if (attempt.deliveryId !== inspectedDeliveryId.value || attempt.outcome !== "DONE") return latest;
    return !latest || attempt.attemptNo > latest.attemptNo ? attempt : latest;
  }, undefined);
  return showTime(succeeded?.updatedAt);
});
let topologyRequest: AbortController | undefined;
const tenant = ref("");
const type = ref("");
const status = ref("");
const dates = ref<[Date, Date]>();
const page = ref(1);
const total = ref(0);
const rows = ref<PlatformEvent[]>([]);
const loading = ref(false);
const detailLoading = ref(false);
const retrying = ref(false);
const error = ref("");
const detailError = ref("");
const visible = ref(false);
const detail = ref<EventDetail>();
let filters: Omit<Parameters<typeof eventWorkbenchApi.list>[0], "page"> = {};
let listRequest: AbortController | undefined;
let detailRequest: AbortController | undefined;

const columns = computed<ManagementColumn[]>(() => [
  {key: "progress", label: t("eventWorkbench.status"), minWidth: 112},
  {key: "result", label: t("eventWorkbench.deliveryResult"), minWidth: 170},
  {key: "type", label: t("eventWorkbench.type"), minWidth: 220},
  {key: "tenant", label: t("eventWorkbench.eventScope"), minWidth: 110},
  {key: "createdAt", label: t("eventWorkbench.recordedAt"), minWidth: 190},
  {key: "operations", label: t("eventWorkbench.detail"), width: 110}
]);
const deliveryColumns = computed<ManagementColumn[]>(() => [
  {key: "consumer", label: t("eventWorkbench.consumer"), minWidth: 128},
  {key: "status", label: t("eventWorkbench.status"), minWidth: 108},
  {key: "attempts", label: t("eventWorkbench.attemptRatio"), minWidth: 110},
  {key: "operations", label: t("eventWorkbench.deliveryActions"), minWidth: 110}
]);
/**
 * 从已有审计和尝试记录生成时间线。旧数据没有逐次记录时保留该缺口，
 * 尝试完成时间采用结果更新时间，不能用投递更新时间回填。
 */
const timeline = computed(() => {
  const current = detail.value;
  if (!current) return [];
  const entries: { key: string; time: string; title: string; description: string; success?: boolean }[] = [];
  if (current.event.createdAt) {
    entries.push({ key: "event", time: current.event.createdAt, title: t("eventWorkbench.recorded"), description: "" });
  }
  const consumers = new Map(current.deliveries.map(row => [row.id, row.consumer]));
  for (const delivery of current.deliveries) {
    if (selectedDelivery.value && selectedDelivery.value !== delivery.id) continue;
    if (delivery.createdAt) {
      entries.push({ key: delivery.id, time: delivery.createdAt, title: t("eventWorkbench.deliveryCreated"), description: label("consumerNames", delivery.consumer, "unknownConsumer") });
    }
  }
  for (const attempt of current.attempts) {
    if (selectedDelivery.value && selectedDelivery.value !== attempt.deliveryId) continue;
    const key = attempt.deliveryId + ":" + attempt.attemptNo;
    const description = t("eventWorkbench.attemptSummary", {
      consumer: label("consumerNames", consumers.get(attempt.deliveryId) ?? "", "unknownConsumer"),
      number: attempt.attemptNo
    });
    entries.push({ key, time: attempt.createdAt, title: t("eventWorkbench.attemptStarted"), description });
    if (attempt.outcome !== "PROCESSING") {
      entries.push({
        key: key + ":result",
        time: attempt.updatedAt,
        success: attempt.outcome === "DONE",
        title: t("eventWorkbench." + (attempt.outcome === "DONE" ? "attemptSucceeded" : attempt.outcome), attempt.outcome),
        description: [description, attempt.failureCode ? label("failureNames", attempt.failureCode, "unknownFailure") : ""].filter(Boolean).join(" · ")
      });
    }
  }
  return entries.sort((left, right) => Date.parse(left.time) - Date.parse(right.time));
});

/** 日期分组与节点时刻采用同一展示时区，跨日执行保持实际时间顺序。 */
const timelineDays = computed(() => {
  const days = new Map<string, Array<{ entry: (typeof timeline.value)[number]; time: string }>>();
  for (const entry of timeline.value) {
    const date = displayInstant(entry.time, locale.value, "date");
    const items = days.get(date) ?? [];
    items.push({ entry, time: displayInstant(entry.time, locale.value, "time") });
    days.set(date, items);
  }
  return [...days].map(([date, items]) => ({ date, items }));
});

/** 读取实际注册关系，界面可在没有历史投递时展示订阅。 */
async function loadTopology() {
  topologyRequest?.abort();
  const request = topologyRequest = new AbortController();
  topologyLoading.value = true;
  topologyError.value = "";
  try {
    const result = await eventWorkbenchApi.subscriptions(request.signal);
    if (request.signal.aborted) return;
    subscriptions.value = result;
  } catch (failure) {
    if (!request.signal.aborted) topologyError.value = requestErrorMessage(failure);
  } finally {
    if (!request.signal.aborted) topologyLoading.value = false;
  }
}

/** 操作由统一权限准入，成功后读取共享数据库中的执行状态。 */
async function setSubscriptionState(subscription: EventSubscription) {
  if (changingSubscription.value) return;
  changingSubscription.value = true;
  const paused = !subscription.paused;
  const action = t("eventWorkbench." + (paused ? "pause" : "resume"));
  try {
    const reason = await ElMessageBox.prompt(t("eventWorkbench.subscriptionReason"), action + " · " + label("consumerNames", subscription.consumer, "unknownConsumer"), {
      inputValidator: value => {
        const length = (value ?? "").trim().length;
        return length > 0 && length <= 200 || t("eventWorkbench.subscriptionReasonLength");
      }
    }).then(result => result.value, () => undefined);
    if (reason === undefined) return;
    await eventWorkbenchApi.setSubscriptionPaused(subscription, paused, reason.trim());
    ElMessage.success(t("eventWorkbench.subscriptionSaved"));
    await loadTopology();
  } catch (failure) {
    topologyError.value = requestErrorMessage(failure);
  } finally {
    changingSubscription.value = false;
  }
}

/** 导航改变时沿用已提交的条件，避免应用表单中尚未提交的编辑。 */
function selectEventType(node: EventTypeNode) {
  if (node.type === undefined) {
    typeTree.value?.setCurrentKey(currentTypeKey.value, false);
    return;
  }
  if (node.type === topologyType.value) return;
  topologyType.value = node.type;
  type.value = node.type;
  filters = { ...filters, type: node.type || undefined };
  page.value = 1;
  void load();
}

function filterEventType(value: string, node: EventTypeNode) {
  const query = value.trim().toLocaleLowerCase();
  return node.label.toLocaleLowerCase().includes(query) || Boolean(node.type?.toLocaleLowerCase().includes(query));
}

watch([typeSearch, eventTypeTree], () => typeTree.value?.filter(typeSearch.value), { flush: "post" });

/** 以已提交的条件查询，页码变更不会应用尚未提交的表单内容。 */
async function load() {
  listRequest?.abort();
  const request = listRequest = new AbortController();
  loading.value = true;
  error.value = "";
  try {
    const result = await eventWorkbenchApi.list({...filters, page: page.value}, request.signal);
    if (!request.signal.aborted) {
      rows.value = result.rows;
      total.value = result.total;
    }
  } catch (failure) {
    if (!request.signal.aborted) error.value = requestErrorMessage(failure);
  } finally {
    if (!request.signal.aborted) loading.value = false;
  }
}

function search() {
  const interval = dates.value;
  const until = interval ? new Date(interval[1]) : undefined;
  if (until) until.setDate(until.getDate() + 1);
  filters = {tenant: tenant.value.trim() || undefined, type: type.value.trim() || undefined,
    status: status.value || undefined, from: interval?.[0].toISOString(), until: until?.toISOString()};
  page.value = 1;
  void load();
}

function resetSearch() {
  tenant.value = "";
  type.value = topologyType.value;
  status.value = "";
  dates.value = undefined;
  search();
}

/** 查询选中的事件，关闭详情后忽略迟到响应。 */
async function show(id: string) {
  detailRequest?.abort();
  const request = detailRequest = new AbortController();
  visible.value = true;
  if (detail.value?.event.id !== id) {
    selectedDelivery.value = "";
    inspectedDeliveryId.value = "";
    expandedEventDetails.value = ["technical"];
    detail.value = undefined;
  }
  detailError.value = "";
  detailLoading.value = true;
  try {
    const result = await eventWorkbenchApi.detail(id, request.signal);
    if (!request.signal.aborted) {
      detail.value = result;
      if (!result.deliveries.some(row => row.id === selectedDelivery.value)) {
        selectedDelivery.value = result.deliveries.length === 1 ? result.deliveries[0].id : "";
      }
    }
  } catch (failure) {
    if (!request.signal.aborted) detailError.value = requestErrorMessage(failure);
  } finally {
    if (!request.signal.aborted) detailLoading.value = false;
  }
}

async function retry(deliveryId: string) {
  if (!detail.value || retrying.value) return;
  const eventId = detail.value.event.id;
  retrying.value = true;
  try {
    const reason = await ElMessageBox.prompt(t("eventWorkbench.reason"), t("eventWorkbench.retry"), {
      inputValidator: value => {
        const length = (value ?? "").trim().length;
        return length > 0 && length <= 200 || t("eventWorkbench.reasonLength");
      }
    }).then(result => result.value, () => undefined);
    if (reason === undefined) return;
    await eventWorkbenchApi.retry(deliveryId, reason.trim());
    ElMessage.success(t("eventWorkbench.retried"));
    if (visible.value && detail.value?.event.id === eventId) void show(eventId);
    void load();
  } catch (failure) {
    detailError.value = requestErrorMessage(failure);
  } finally {
    retrying.value = false;
  }
}

function close() {
  if (visible.value) return;
  detailRequest?.abort();
  detail.value = undefined;
}

onMounted(() => { void load(); void loadTopology(); });
onBeforeUnmount(() => { listRequest?.abort(); topologyRequest?.abort(); detailRequest?.abort(); });
</script>

<template>
  <TreeListTemplate v-model:collapsed="collapsed" class="is-height-constrained" :title="t('eventWorkbench.title')"
    :tree-label="t('eventWorkbench.type')" :search-title="selectedTypeLabel">
    <template #treeHeader><strong>{{ t("eventWorkbench.type") }}</strong></template>
    <template #tree>
      <el-input v-model="typeSearch" clearable :placeholder="t('eventWorkbench.searchTypes')"
        :aria-label="t('eventWorkbench.searchTypes')">
        <template #prefix><AppIcon name="search" :size="16" /></template>
      </el-input>
      <el-tree ref="typeTree" v-loading="topologyLoading" :data="eventTypeTree" node-key="key" highlight-current
        default-expand-all :expand-on-click-node="false" :filter-node-method="filterEventType"
        :current-node-key="currentTypeKey"
        :aria-label="t('eventWorkbench.type')" @node-click="selectEventType">
        <template #default="{ data }">
          <AppIcon :name="data.key === allEvents ? 'layers' : data.children ? (data.key === 'group:authority' ? 'shield' : 'folder') : 'document'" :size="16" />
          <span class="event-tree-label" :title="data.label">{{ data.label }}</span>
        </template>
      </el-tree>
    </template>
    <template #feedback><FloatingError :title="error" /></template>
    <template #search>
      <el-form inline @submit.prevent="search">
        <el-form-item :label="t('eventWorkbench.tenant')"><el-input v-model="tenant" class="event-tenant-input" clearable maxlength="36" /></el-form-item>
        <el-form-item v-if="!topologyType" :label="t('eventWorkbench.type')">
          <el-select v-model="type" class="event-type-select" filterable allow-create clearable :reserve-keyword="false">
            <el-option v-for="eventType in registeredTypes" :key="eventType" :value="eventType"
              :label="label('eventNames', eventType, 'unknownEvent')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('eventWorkbench.status')">
          <el-select v-model="status" class="event-status-select" clearable :placeholder="t('eventWorkbench.allStatuses')">
            <el-option v-for="state in states" :key="state" :value="state" :label="t('eventWorkbench.' + state)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('eventWorkbench.recordedAt')"><el-date-picker v-model="dates" type="daterange" class="event-date-range" /></el-form-item>
        <div class="management-filter-actions">
          <el-button v-operation="'PlatformTask.listEvents'" native-type="submit" type="primary">{{ t("eventWorkbench.search") }}</el-button>
          <el-button v-operation="'PlatformTask.listEvents'" @click="resetSearch">{{ t("management.reset") }}</el-button>
        </div>
      </el-form>
    </template>
    <template #table>
      <div class="business-workspace-stack event-workspace-stack">
        <section class="event-topology" :aria-label="t('eventWorkbench.topology')">
          <div class="event-topology-header">
            <h2 class="management-filter-title">{{ t("eventWorkbench.topology") }}</h2>
            <el-button v-operation="'PlatformTask.listSubscriptions'" link type="primary"
              :loading="topologyLoading" @click="loadTopology"><AppIcon name="refresh" :size="16" />{{ t("eventWorkbench.refreshTopology") }}</el-button>
            <el-button v-if="topologyType" text aria-controls="event-topology-routes" :aria-expanded="topologyExpanded"
              :aria-label="t(topologyExpanded ? 'eventWorkbench.collapseTopology' : 'eventWorkbench.expandTopology')"
              @click="topologyExpanded = !topologyExpanded">
              <AppIcon :name="topologyExpanded ? 'arrowUp' : 'arrowDown'" :size="18" />
            </el-button>
          </div>
          <FloatingError :title="topologyError" />
          <el-collapse-transition>
            <div v-if="topologyType" v-show="topologyExpanded" id="event-topology-routes" class="event-topology-routes">
              <p v-if="routes.some(route => route.manageable)">{{ t("eventWorkbench.pauseBehavior") }}</p>
              <el-empty v-if="!topologyLoading && !topologyError && !routes.length" :description="t('eventWorkbench.noSubscriptions')" />
              <div v-for="route in routes" :key="route.consumer" class="event-route">
                <el-card shadow="never">
                  <div class="event-route-node">
                    <span class="event-route-icon"><AppIcon name="settings" :size="20" /></span>
                    <div><strong>{{ t("eventWorkbench.producer") }}</strong><div>{{ t("eventWorkbench." + route.producer) }}</div></div>
                  </div>
                </el-card>
                <span aria-hidden="true"><AppIcon name="arrowRight" :size="16" /></span>
                <el-card shadow="never">
                  <div class="event-route-node">
                    <span class="event-route-icon"><AppIcon name="layers" :size="20" /></span>
                    <div><strong>{{ t("eventWorkbench.type") }}</strong><div>{{ label("eventNames", route.type, "unknownEvent") }}</div></div>
                  </div>
                </el-card>
                <span aria-hidden="true"><AppIcon name="arrowRight" :size="16" /></span>
                <el-card shadow="never">
                  <div class="event-route-node">
                    <span class="event-route-icon"><AppIcon name="document" :size="20" /></span>
                    <div>
                      <strong>{{ t("eventWorkbench.subscription") }}</strong>
                      <div>{{ label("consumerNames", route.consumer, "unknownConsumer") }}</div>
                      <el-tag class="app-status-tag" :type="route.paused ? 'warning' : 'success'">{{ t("eventWorkbench." + (route.paused ? "paused" : "active")) }}</el-tag>
                      <el-button v-if="route.manageable" v-operation="'PlatformTask.setSubscriptionState'" link type="primary"
                        :disabled="changingSubscription" @click="setSubscriptionState(route)">
                        {{ t("eventWorkbench." + (route.paused ? "resume" : "pause")) }}
                      </el-button>
                    </div>
                  </div>
                </el-card>
                <span aria-hidden="true"><AppIcon name="arrowRight" :size="16" /></span>
                <el-card shadow="never">
                  <div class="event-route-node">
                    <span class="event-route-icon"><AppIcon name="play" :size="20" /></span>
                    <div><strong>{{ t("eventWorkbench.executor") }}</strong><div>{{ label("executorNames", route.consumer, "unknownExecutor") }}</div></div>
                  </div>
                </el-card>
                <span aria-hidden="true"><AppIcon name="arrowRight" :size="16" /></span>
                <el-card shadow="never">
                  <div class="event-route-node">
                    <span class="event-route-icon"><AppIcon name="target" :size="20" /></span>
                    <div><strong>{{ t("eventWorkbench.expectedEffect") }}</strong><div>{{ t("eventWorkbench." + (route.effect || "unknownEffect")) }}</div></div>
                  </div>
                </el-card>
                <el-collapse class="event-subscription-audit">
                  <el-collapse-item :title="t('eventWorkbench.technicalDetails')">
                    <dl class="event-detail-grid">
                      <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.typeCode") }}</dt><dd>{{ route.type }}</dd></div>
                      <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.consumerCode") }}</dt><dd>{{ route.consumer }}</dd></div>
                      <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.executorCode") }}</dt><dd>{{ route.executor }}</dd></div>
                    </dl>
                  </el-collapse-item>
                  <el-collapse-item v-if="route.audit.createdAt" :title="t('eventWorkbench.systemInfo')">
                    <RecordAuditDetails :audit="recordAudit(route.audit)" inline />
                  </el-collapse-item>
                </el-collapse>
              </div>
            </div>
          </el-collapse-transition>
        </section>
        <section class="event-records management-list-host">
          <ManagementTable :title="t('eventWorkbench.records')" :rows="rows" :columns="columns"
            :loading="loading" :total="total" :excel-export="false" embedded fit-height @refresh="load">
            <template #type="{ row }">{{ label("eventNames", row.type, "unknownEvent") }}</template>
            <template #tenant="{ row }">{{ eventScope(row.tenant) }}</template>
            <template #createdAt="{ row }">{{ showTime(row.createdAt) }}</template>
            <template #progress="{ row }">
              <el-tag class="app-status-tag" :type="eventProgress(row).tone">{{ eventProgress(row).text }}</el-tag>
            </template>
            <template #result="{ row }">{{ deliverySummary(row) }}</template>
            <template #operations="scope"><template v-if="scope?.row">
              <el-button v-operation="'PlatformTask.readEvent'" link type="primary" @click="show(scope.row.id)">{{ t("eventWorkbench.detail") }}</el-button>
            </template></template>
            <template #pagination>
              <AppPagination v-model:current-page="page" :page-size="20" :total="total" @current-change="load" />
            </template>
          </ManagementTable>
        </section>
      </div>
      <ManagedDialog v-model="visible" :title="t('eventWorkbench.detail')"
        width="min(88rem, 96vw)" top="16px" @closed="close">
        <template #headerActions>
          <el-button v-if="detail" v-operation="'PlatformTask.readEvent'" :loading="detailLoading" text
            :aria-label="t('eventWorkbench.refreshDetail')" :title="t('eventWorkbench.refreshDetail')"
            :disabled="retrying" @click="show(detail.event.id)">
            <AppIcon v-if="!detailLoading" name="refresh" :size="16" />
          </el-button>
        </template>
        <FloatingError :title="detailError" />
        <div v-loading="detailLoading" class="business-workspace-stack event-detail-body">
          <template v-if="detail">
            <div class="event-detail-heading">
              <h2>{{ label("eventNames", detail.event.type, "unknownEvent") }}</h2>
              <el-tag class="app-status-tag" :type="eventProgress(detail.event).tone">{{ eventProgress(detail.event).text }}</el-tag>
            </div>
            <section class="event-detail-panel event-overview" :aria-label="t('eventWorkbench.overview')">
              <dl class="event-detail-grid">
                <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.eventScope") }}</dt><dd>{{ eventScope(detail.event.tenant) }}</dd></div>
                <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.recordedAt") }}</dt><dd>{{ showTime(detail.event.createdAt) }}</dd></div>
                <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.deliveryResult") }}</dt><dd>{{ t("eventWorkbench.counts", { ...detail.event }) }}</dd></div>
              </dl>
            </section>
            <div class="event-detail-columns">
              <section class="event-detail-panel event-delivery-panel">
                <ManagementTable :title="t('eventWorkbench.deliveries')" :rows="detail.deliveries" :columns="deliveryColumns"
                  :total="detail.deliveries.length" row-key="id" heading-level="h2" embedded readonly-view :excel-export="false" :paginate-locally="false">
                  <template #consumer="{ row }">
                    <span class="event-delivery-consumer">
                      <AppIcon v-if="inspectedDeliveryId === row.id" name="arrowDown" :size="16" />
                      {{ label("consumerNames", row.consumer, "unknownConsumer") }}
                    </span>
                  </template>
                  <template #status="{ row }">
                    <el-tag class="app-status-tag" :type="row.status === 'MANUAL' ? 'danger' : row.status === 'DONE' ? 'success' : row.status === 'PENDING' && row.failureCode ? 'warning' : 'info'">
                      {{ row.status === "PENDING" && pausedConsumers.has(row.consumer)
                        ? t("eventWorkbench.paused")
                        : t("eventWorkbench." + (row.status === "PENDING" && row.failureCode ? "retryWaiting" : row.status)) }}
                    </el-tag>
                  </template>
                  <template #attempts="{ row }">{{ row.attempts }} / {{ row.totalAttempts }}</template>
                  <template #operations="{ row }">
                    <el-button v-operation="'PlatformTask.readEvent'" link type="primary" :aria-expanded="inspectedDeliveryId === row.id"
                      @click="inspectedDeliveryId = inspectedDeliveryId === row.id ? '' : row.id">
                      {{ t(inspectedDeliveryId === row.id ? "eventWorkbench.collapseDeliveryDetails" : "eventWorkbench.deliveryDetails") }}
                    </el-button>
                  </template>
                </ManagementTable>
                <section v-if="inspectedDelivery" class="event-delivery-details">
                  <div class="event-detail-heading">
                    <h4>{{ label("consumerNames", inspectedDelivery.consumer, "unknownConsumer") }}</h4>
                    <el-button v-if="inspectedDelivery.status === 'MANUAL' && !inspectedDelivery.failureCode?.startsWith('LEGACY_')"
                      v-operation="'PlatformTask.retryDelivery'" link type="primary" :disabled="retrying || detailLoading"
                      @click="retry(inspectedDelivery.id)">
                      {{ t("eventWorkbench.retry") }}
                    </el-button>
                  </div>
                  <dl class="event-detail-grid">
                    <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.deliveryId") }}</dt><dd>{{ inspectedDelivery.id }}</dd></div>
                    <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.consumerCode") }}</dt><dd>{{ inspectedDelivery.consumer }}</dd></div>
                    <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.consumedBy") }}</dt><dd>{{ label("consumerNames", inspectedDelivery.consumer, "unknownConsumer") }}</dd></div>
                    <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.consumedAt") }}</dt><dd>{{ inspectedConsumptionTime }}</dd></div>
                    <div v-if="inspectedDelivery.failureCode" class="detail-readonly-field">
                      <dt>{{ t("eventWorkbench.failure") }}</dt>
                      <dd>{{ label("failureNames", inspectedDelivery.failureCode, "unknownFailure") }} · {{ inspectedDelivery.failureCode }}</dd>
                    </div>
                    <div v-if="inspectedDelivery.status === 'PENDING'" class="detail-readonly-field">
                      <dt>{{ t("eventWorkbench.nextAt") }}</dt><dd>{{ showTime(inspectedDelivery.availableAt) }}</dd>
                    </div>
                  </dl>
                </section>
              </section>
              <section class="event-detail-panel business-workspace-stack">
                <div class="event-detail-heading event-lifecycle-heading">
                  <h3 class="management-table-title">{{ t("eventWorkbench.lifecycle") }}</h3>
                  <el-select v-if="deliveryChoices.length" v-model="selectedDelivery" clearable class="event-delivery-picker"
                    :aria-label="t('eventWorkbench.filterDelivery')" :placeholder="t('eventWorkbench.filterDelivery')">
                    <el-option v-for="delivery in deliveryChoices" :key="delivery.id" :value="delivery.id" :label="label('consumerNames', delivery.consumer, 'unknownConsumer')" />
                  </el-select>
                </div>
                <template v-if="timelineDays.length">
                  <div v-for="day in timelineDays" :key="day.date" class="business-workspace-stack event-timeline-day">
                    <div><el-tag type="info" round>{{ day.date }}</el-tag></div>
                    <el-timeline class="event-timeline">
                      <el-timeline-item v-for="{ entry, time } in day.items" :key="entry.key"
                        :type="entry.success ? 'success' : 'primary'">
                        <div class="event-timeline-entry">
                          <time class="event-timeline-time">{{ time }}</time>
                          <div>
                            <strong>{{ entry.title }}</strong>
                            <div v-if="entry.description" class="event-supporting-text">{{ entry.description }}</div>
                          </div>
                        </div>
                      </el-timeline-item>
                    </el-timeline>
                  </div>
                </template>
                <el-empty v-else :description="t('eventWorkbench.noHistory')" />
                <p class="event-history-note event-supporting-text">{{ t("eventWorkbench.historyLimit") }}</p>
              </section>
            </div>
            <el-collapse v-model="expandedEventDetails" class="event-detail-disclosures">
              <el-collapse-item name="technical">
                <template #title><h3 class="management-filter-title">{{ t("eventWorkbench.technicalDetails") }}</h3></template>
                <dl class="event-detail-grid event-technical-grid">
                  <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.eventId") }}</dt><dd><code>{{ detail.event.id }}</code></dd></div>
                  <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.typeCode") }}</dt><dd><code>{{ detail.event.type }} · v{{ detail.event.revision }}</code></dd></div>
                  <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.tenant") }}</dt><dd><code>{{ detail.event.tenant }}</code></dd></div>
                  <div class="detail-readonly-field"><dt>{{ t("eventWorkbench.subject") }}</dt><dd><code>{{ detail.event.subjectReference || "—" }}</code></dd></div>
                </dl>
              </el-collapse-item>
            </el-collapse>
            <el-card class="detail-system readonly-detail-section" shadow="never">
              <h3 class="detail-section-title">{{ t("eventWorkbench.systemInfo") }}</h3>
              <RecordAuditDetails :audit="recordAudit(detail.event)" inline />
            </el-card>
          </template>
        </div>
      </ManagedDialog>
    </template>
  </TreeListTemplate>
</template>

<style scoped>
.event-detail-body {
  min-height: 4rem;
  container-type: inline-size;
  background: transparent;
}

.event-detail-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--app-section-gap, 8px);
}

.event-detail-heading :is(h2, h3, h4) {
  margin: 0;
  color: var(--el-text-color-primary);
}

.event-detail-heading h2 {
  font-size: var(--el-font-size-extra-large);
}

.event-lifecycle-heading {
  justify-content: space-between;
}

.event-detail-columns {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
  gap: var(--app-section-gap, 8px);
}

.event-detail-panel,
.event-detail-disclosures {
  min-width: 0;
  padding: var(--app-content-padding, 8px);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--app-card-radius, 8px);
  background: var(--el-bg-color);
}

.event-overview,
.event-delivery-details,
.event-technical-grid > div {
  background: var(--el-fill-color-light);
}

.event-overview .event-detail-grid > div {
  padding-inline: var(--app-content-padding, 8px);
}

.event-overview .event-detail-grid > div + div {
  border-inline-start: 1px solid var(--el-border-color-lighter);
}

.event-delivery-consumer {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.event-delivery-consumer > svg {
  flex: none;
  color: var(--el-color-primary);
}

.event-timeline-day {
  background: transparent;
}

.event-delivery-picker {
  width: min(100%, 13rem);
}

.event-delivery-details {
  margin-block-start: var(--app-section-gap, 8px);
  padding: var(--app-content-padding, 8px);
  border-radius: var(--app-card-radius, 8px);
}

.event-timeline {
  margin: 0;
  padding-inline-start: var(--app-content-padding, 8px);
}

.event-detail-disclosures {
  --el-collapse-border-color: transparent;
  padding-block: 0;
}

/* 技术字段卡片已有内边距，折叠正文只保留一层全局容器留白。 */
.event-detail-disclosures :deep(.el-collapse-item__content) {
  padding-block-end: var(--app-content-padding, 8px);
}

.event-history-note {
  margin: auto 0 0;
  padding-block-start: var(--app-content-padding, 8px);
  border-block-start: 1px solid var(--el-border-color-lighter);
}

.event-timeline-entry {
  display: grid;
  grid-template-columns: minmax(5.5em, max-content) minmax(0, 1fr);
  gap: 8px;
}

.event-timeline-time {
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.event-technical-grid > div {
  padding: var(--app-content-padding, 8px);
  border-radius: var(--el-border-radius-base);
}

.event-technical-grid code {
  font-size: var(--el-font-size-small);
}

@container (max-width: 48rem) {
  .event-overview .event-detail-grid > div + div {
    border-inline-start: 0;
  }
}

@container (max-width: 54rem) {
  .event-detail-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}

.event-detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr));
  gap: var(--app-section-gap, 8px);
  margin: 0;
  overflow-wrap: anywhere;
}

.event-supporting-text {
  color: var(--el-text-color-secondary);
  font-size: var(--el-font-size-small);
}

.event-topology,
.event-records {
  padding: var(--app-content-padding, 8px);
  background: var(--el-bg-color);
}

/* 将剩余高度交给公共表格测量。极矮视口保留操作栏和至少一行可滚动空间。 */
.event-workspace-stack {
  flex: 1 1 0;
  min-height: 14rem;
}

.event-workspace-stack > .event-records {
  display: flex;
  flex: 1 1 12rem;
  flex-direction: column;
  min-height: 12rem;
}

.event-topology {
  container-type: inline-size;
  flex-shrink: 1;
  min-height: 0;
  max-height: 45%;
  overflow-y: auto;
}

.event-topology-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.event-topology-header > h2 {
  margin-inline-end: auto;
}

.event-topology-header > .el-button {
  margin: 0;
}

.event-topology-routes {
  display: grid;
  gap: var(--app-section-gap, 8px);
}

.event-tenant-input,
.event-status-select,
.event-date-range.el-date-editor {
  max-width: min(100%, 68vw);
}

.event-tenant-input {
  width: 11rem;
}

.event-status-select {
  width: 9rem;
}

.event-date-range.el-date-editor {
  width: 16rem;
}

.event-route {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1.2fr) auto minmax(0, 1fr) auto minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: stretch;
  gap: 6px;
  overflow-wrap: anywhere;
}

.event-route > span {
  align-self: center;
  color: var(--el-text-color-secondary);
}

.event-tree-label {
  margin-inline-start: 6px;
}

.event-route-node {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.event-route-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--app-control-radius, var(--el-border-radius-base));
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.event-subscription-audit {
  grid-column: 1 / -1;
}

.event-type-select {
  width: min(14rem, 65vw);
}

@media (max-width: 600px) {
  .event-workspace-stack {
    min-height: 20rem;
  }

  .event-workspace-stack > .event-records {
    flex-basis: 16rem;
    min-height: 16rem;
  }
}

@container (max-width: 38rem) {
  .event-route {
    grid-template-columns: minmax(0, 1fr);
  }

  .event-route > span {
    justify-self: center;
    transform: rotate(90deg);
  }
}
</style>
