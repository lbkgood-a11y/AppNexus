<script setup lang="ts">
import { setErrorFeedback } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import {organizationSaveOperation} from "./operationBinding";
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useI18n } from "vue-i18n";
import { AppIcon } from "@appnexus/icons";
import { BusinessWorkspace, ManagedDialog, ManagementTable, TreeListTemplate } from "@appnexus/ui";
import type { ManagementColumn } from "@appnexus/ui";
import type { BusinessOption, OrganizationApi, OrganizationAssignment, OrganizationCapability, OrganizationDirectoryPerson, OrganizationLeader, OrganizationPosition, OrganizationRelation, OrganizationTreeUnit, OrganizationType } from "./index";

type WorkspaceView = "structure" | "assignments" | "relations" | "types";
type DetailView = "positions" | "leaders" | "relations";
type DialogKind = "type" | "unit" | "move" | "relation" | "position" | "assignment" | "leader" | "";

/** 组织管理工作区；客户端权限仅控制入口可见性，服务端仍负责租户隔离和最终授权。 */
const props = defineProps<{ api: OrganizationApi; errorText: (error: unknown) => string }>();
const { t } = useI18n();
const capabilities = ref<OrganizationCapability[]>([]);
const types = ref<OrganizationType[]>([]), units = ref<OrganizationTreeUnit[]>([]), relations = ref<OrganizationRelation[]>([]);
const positions = ref<OrganizationPosition[]>([]), assignments = ref<OrganizationAssignment[]>([]), leaders = ref<OrganizationLeader[]>([]);
const members = ref<OrganizationDirectoryPerson[]>([]), relationTypes = ref<BusinessOption[]>([]);
const loading = ref(false), saving = ref(false), error = ref(""), dialogError = ref("");
const relationTypesError = ref("");
const workspaceView = ref<WorkspaceView>("structure"), detailView = ref<DetailView>("positions"), dialog = ref<DialogKind>("");
const selected = ref<OrganizationType | OrganizationTreeUnit | OrganizationPosition | null>(null);
const selectedTypeId = ref(""), selectedUnitId = ref(""), treeCollapsed = ref(false);
const treeKeyword = ref(""), assignmentKeyword = ref(""), relationKeyword = ref(""), typeKeyword = ref("");
const assignmentStatus = ref<"all" | "active" | "ended">("active");
const code = ref(""), name = ref(""), typeId = ref(""), parentId = ref<string | null>(null);
const sourceId = ref(""), targetId = ref(""), relationType = ref(""), personId = ref(""), positionId = ref("");
const startsAt = ref(new Date()), endsAt = ref<Date | null>(null);

const can = (value: OrganizationCapability) => capabilities.value.includes(value);
const selectedUnit = computed(() => units.value.find(item => item.id === selectedUnitId.value) || null);
const selectedType = computed(() => types.value.find(item => item.id === selectedTypeId.value) || null);
const activeAssignments = computed(() => assignments.value.filter(isActiveAssignment));
const selectedPositions = computed(() => positions.value.filter(item => item.organizationId === selectedUnitId.value));
const selectedPositionIds = computed(() => new Set(selectedPositions.value.map(item => item.id)));
const selectedAssignments = computed(() => assignments.value.filter(item => selectedPositionIds.value.has(item.positionId)));
const selectedLeaders = computed(() => leaders.value.filter(item => item.organizationId === selectedUnitId.value));
const selectedRelations = computed(() => relations.value.filter(item => item.sourceId === selectedUnitId.value || item.targetId === selectedUnitId.value));

const tree = computed(() => {
  const keyword = treeKeyword.value.trim().toLocaleLowerCase();
  const scoped = units.value.filter(item => item.typeId === selectedTypeId.value);
  const visibleIds = new Set<string>(), byId = new Map(scoped.map(item => [item.id, item]));
  if (keyword) scoped.filter(item => `${item.name} ${item.code}`.toLocaleLowerCase().includes(keyword)).forEach(item => {
    let cursor: OrganizationTreeUnit | undefined = item;
    while (cursor) { visibleIds.add(cursor.id); cursor = cursor.parentId ? byId.get(cursor.parentId) : undefined; }
  });
  const rows = scoped.filter(item => !keyword || visibleIds.has(item.id)).map(item => ({ ...item, children: [] as OrganizationTreeUnit[] }));
  const map = new Map(rows.map(item => [item.id, item]));
  return rows.filter(item => { const parent = item.parentId ? map.get(item.parentId) : undefined; if (!parent) return true; parent.children.push(item); return false; });
});
const filteredAssignments = computed(() => {
  const keyword = assignmentKeyword.value.trim().toLocaleLowerCase();
  return assignments.value.filter(item => {
    const active = isActiveAssignment(item);
    if (assignmentStatus.value === "active" && !active) return false;
    if (assignmentStatus.value === "ended" && active) return false;
    return !keyword || `${labelPerson(item.personId)} ${labelPosition(item.positionId)} ${labelUnit(positionOrganizationId(item.positionId))}`.toLocaleLowerCase().includes(keyword);
  });
});
const filteredRelations = computed(() => { const keyword = relationKeyword.value.trim().toLocaleLowerCase(); return relations.value.filter(item => !keyword || `${labelRelationType(item.relationType)} ${labelUnit(item.sourceId)} ${labelUnit(item.targetId)}`.toLocaleLowerCase().includes(keyword)); });
const filteredTypes = computed(() => { const keyword = typeKeyword.value.trim().toLocaleLowerCase(); return types.value.filter(item => !keyword || `${item.name} ${item.code}`.toLocaleLowerCase().includes(keyword)); });

const positionColumns = computed<ManagementColumn[]>(() => [
  { key: "name", prop: "name", label: t("organization.positionName"), minWidth: 160 }, { key: "code", prop: "code", label: t("organization.code"), minWidth: 140 },
  { key: "assignments", label: t("organization.assignments"), width: 120 }, { key: "status", label: t("organization.status"), width: 110 }, { key: "operations", label: t("tenant.operation"), width: 190, fixed: "right" }
]);
const assignmentColumns = computed<ManagementColumn[]>(() => [
  { key: "member", label: t("organization.person"), minWidth: 160 }, { key: "position", label: t("organization.positionName"), minWidth: 150 },
  { key: "organization", label: t("organization.name"), minWidth: 170 }, { key: "period", label: t("organization.assignmentPeriod"), minWidth: 250 },
  { key: "status", label: t("organization.status"), width: 110 }, { key: "operations", label: t("tenant.operation"), width: 120, fixed: "right" }
]);
const leaderColumns = computed<ManagementColumn[]>(() => [
  { key: "member", label: t("organization.person"), minWidth: 160 }, { key: "position", label: t("organization.positionName"), minWidth: 160 },
  { key: "period", label: t("organization.assignmentPeriod"), minWidth: 240 }, { key: "operations", label: t("tenant.operation"), width: 140, fixed: "right" }
]);
const relationColumns = computed<ManagementColumn[]>(() => [
  { key: "source", label: t("organization.source"), minWidth: 180 }, { key: "direction", label: t("organization.relationType"), minWidth: 160 },
  { key: "target", label: t("organization.target"), minWidth: 180 }, { key: "operations", label: t("tenant.operation"), width: 110, fixed: "right" }
]);
const typeColumns = computed<ManagementColumn[]>(() => [
  { key: "name", prop: "name", label: t("organization.typeName"), minWidth: 180 }, { key: "code", prop: "code", label: t("organization.code"), minWidth: 160 },
  { key: "unitCount", label: t("organization.unitCount"), width: 130 }, { key: "status", label: t("organization.status"), width: 110 },
  { key: "operations", label: t("tenant.operation"), width: 180, fixed: "right" }
]);

/** 首次加载并行读取组织领域快照；写操作后复用该方法刷新所有受影响聚合。 */
async function load() {
  loading.value = true; error.value = "";
  try {
    capabilities.value = await props.api.capabilities();
    if (!can("READ")) return;
    [types.value, units.value, relations.value, positions.value, assignments.value, leaders.value, members.value] = await Promise.all([
      props.api.types(), props.api.treeUnits(), props.api.relations(), props.api.positions(), props.api.assignments(), props.api.leaders(), props.api.directoryPersons()
    ]);
    await loadRelationTypes();
    ensureSelection();
  } catch (cause) { await setErrorFeedback(error, props.errorText(cause)); } finally { loading.value = false; }
}
/** 关系类型是关系编辑的独立依赖；接口暂不可用时不阻断组织树、岗位和任职管理。 */
async function loadRelationTypes() {
  relationTypesError.value = "";
  try { relationTypes.value = await props.api.dictionary("organization_relation_type"); }
  catch (cause) { relationTypes.value = []; await setErrorFeedback(relationTypesError, props.errorText(cause)); }
}
/** 写入期间统一禁止重复提交；成功后重新读取快照，避免跨聚合信息局部过期。 */
async function write(action: () => Promise<unknown>) {
  saving.value = true; error.value = ""; dialogError.value = "";
  try { await action(); dialog.value = ""; ElMessage.success(t("organization.saved")); await load(); }
  catch (cause) { await setErrorFeedback(dialogError, props.errorText(cause)); } finally { saving.value = false; }
}
function ensureSelection() {
  if (!types.value.some(item => item.id === selectedTypeId.value)) selectedTypeId.value = types.value.find(item => item.enabled)?.id || types.value[0]?.id || "";
  if (!units.value.some(item => item.id === selectedUnitId.value && item.typeId === selectedTypeId.value)) selectedUnitId.value = tree.value[0]?.id || "";
}
function selectType(value: string) { selectedTypeId.value = value; selectedUnitId.value = tree.value[0]?.id || ""; }
function open(kind: DialogKind, row?: OrganizationType | OrganizationTreeUnit | OrganizationPosition) {
  if (kind === "relation" && !relationTypes.value.length) { ElMessage.warning(t("organization.relationTypesUnavailable")); return; }
  dialog.value = kind; dialogError.value = ""; selected.value = row || null;
  code.value = row && "code" in row ? row.code : ""; name.value = row?.name || "";
  typeId.value = row && "typeId" in row ? row.typeId : selectedTypeId.value || types.value.find(item => item.enabled)?.id || "";
  parentId.value = row && "parentId" in row ? row.parentId : selectedUnitId.value || null;
  sourceId.value = kind === "relation" || kind === "position" ? selectedUnitId.value : ""; targetId.value = "";
  relationType.value = relationTypes.value[0]?.value || ""; personId.value = "";
  positionId.value = kind === "assignment" && row && "organizationId" in row ? row.id : "";
  startsAt.value = new Date(); endsAt.value = null;
}
/** 根据弹窗任务执行对应聚合命令；必填和时间顺序先在客户端反馈，服务端仍为最终约束。 */
async function save() {
  if (!validateDialog()) return;
  if (dialog.value === "type") await write(() => selected.value ? props.api.updateType(selected.value.id, { name: name.value.trim(), enabled: (selected.value as OrganizationType).enabled, version: (selected.value as OrganizationType).version }) : props.api.createType(code.value.trim(), name.value.trim()));
  if (dialog.value === "unit") await write(() => selected.value ? props.api.updateTreeUnit(selected.value.id, { name: name.value.trim(), enabled: (selected.value as OrganizationTreeUnit).enabled, version: (selected.value as OrganizationTreeUnit).version }) : props.api.createTreeUnit({ code: code.value.trim(), typeId: typeId.value, parentId: parentId.value, name: name.value.trim() }));
  if (dialog.value === "move" && selected.value) await write(() => props.api.moveTreeUnit(selected.value!.id, parentId.value, (selected.value as OrganizationTreeUnit).version));
  if (dialog.value === "relation") await write(() => props.api.createRelation({ relationType: relationType.value, sourceId: sourceId.value, targetId: targetId.value }));
  if (dialog.value === "position") await write(() => selected.value ? props.api.updatePosition(selected.value.id, { name: name.value.trim(), enabled: (selected.value as OrganizationPosition).enabled, version: (selected.value as OrganizationPosition).version }) : props.api.createPosition({ organizationId: sourceId.value, code: code.value.trim(), name: name.value.trim() }));
  if (dialog.value === "assignment") await write(() => props.api.createAssignment({ personId: personId.value, positionId: positionId.value, startsAt: startsAt.value.toISOString(), endsAt: endsAt.value?.toISOString() || null }));
  if (dialog.value === "leader") {
    const assignment = activeAssignments.value.find(item => item.id === targetId.value), position = positions.value.find(item => item.id === assignment?.positionId);
    if (!assignment || !position || position.organizationId !== selectedUnitId.value) { dialogError.value = t("organization.invalidLeader"); return; }
    await write(() => props.api.setLeader({ organizationId: position.organizationId, assignmentId: assignment.id, selected: true }));
  }
}
function validateDialog() {
  const requiresName = ["type", "unit", "position"].includes(dialog.value), requiresCode = requiresName && !selected.value;
  if (requiresName && !name.value.trim()) dialogError.value = t("organization.invalidName");
  else if (requiresCode && !code.value.trim()) dialogError.value = t("organization.invalidCode");
  else if (dialog.value === "relation" && (!relationType.value || !sourceId.value || !targetId.value || sourceId.value === targetId.value)) dialogError.value = t("organization.invalidRelation");
  else if (dialog.value === "assignment" && (!personId.value || !positionId.value || (endsAt.value && endsAt.value <= startsAt.value))) dialogError.value = t("organization.invalidAssignment");
  else if (dialog.value === "leader" && !targetId.value) dialogError.value = t("organization.invalidLeader"); else dialogError.value = "";
  return !dialogError.value;
}
async function toggleType(row: OrganizationType) { await write(() => props.api.updateType(row.id, { name: row.name, enabled: !row.enabled, version: row.version })); }
async function toggleUnit(row: OrganizationTreeUnit) {
  if (row.enabled) { const impact = await props.api.impact(row.id); await ElMessageBox.confirm(t("organization.impactSummary", { ...impact }), t("organization.disable"), { type: "warning", confirmButtonText: t("organization.disable") }); }
  await write(() => props.api.updateTreeUnit(row.id, { name: row.name, enabled: !row.enabled, version: row.version }));
}
async function endAssignment(row: OrganizationAssignment) {
  await ElMessageBox.confirm(t("organization.confirmEndAssignment", { member: labelPerson(row.personId), position: labelPosition(row.positionId) }), t("organization.endAssignment"), { type: "warning", confirmButtonText: t("organization.endAssignment") });
  await write(() => props.api.updateAssignment(row.id, { endsAt: new Date().toISOString(), enabled: false, version: row.version }));
}
async function removeLeader(row: OrganizationLeader) {
  await ElMessageBox.confirm(t("organization.confirmRemoveLeader", { member: labelPerson(assignmentPersonId(row.assignmentId)) }), t("organization.removeLeader"), { type: "warning", confirmButtonText: t("organization.removeLeader") });
  await write(() => props.api.setLeader({ organizationId: row.organizationId, assignmentId: row.assignmentId, selected: false }));
}
async function removeRelation(row: OrganizationRelation) {
  await ElMessageBox.confirm(t("organization.confirmDeleteRelation"), t("organization.delete"), { type: "warning", confirmButtonText: t("organization.delete") });
  await write(() => props.api.removeRelation(row.id, row.version));
}
function isActiveAssignment(item: OrganizationAssignment) { const now = Date.now(); return item.enabled && new Date(item.startsAt).getTime() <= now && (!item.endsAt || new Date(item.endsAt).getTime() > now); }
function labelUnit(id: string) { return units.value.find(item => item.id === id)?.name || "—"; }
function labelPerson(id: string) { return members.value.find(item => item.id === id)?.displayName || id || "—"; }
function labelPosition(id: string) { return positions.value.find(item => item.id === id)?.name || "—"; }
function labelRelationType(value: string) { return relationTypes.value.find(item => item.value === value)?.name || value; }
function positionOrganizationId(id: string) { return positions.value.find(item => item.id === id)?.organizationId || ""; }
function assignmentPersonId(id: string) { return assignments.value.find(item => item.id === id)?.personId || ""; }
function assignmentPositionId(id: string) { return assignments.value.find(item => item.id === id)?.positionId || ""; }
function assignmentCount(id: string) { return assignments.value.filter(item => item.positionId === id && isActiveAssignment(item)).length; }
function unitCount(id: string) { return units.value.filter(item => item.typeId === id).length; }
function formatDate(value: string | null) { return value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : t("organization.noEndDate"); }
onMounted(load);
/** 表单按实际对象类型及创建/更新场景关联接口，不将岗位、成员任职误记成组织单元操作。 */
const saveOperation = computed(() => organizationSaveOperation(dialog.value,Boolean(selected.value)));
/** 默认展开仅在首次取得树数据时初始化，避免其他节点交互触发根节点重新展开。 */
const initialExpandedKeys = ref<string[]>([]);
let expansionInitialized = false;
watch(tree, nodes => {
  if (expansionInitialized || !nodes.length) return;
  expansionInitialized = true;
  initialExpandedKeys.value = nodes.slice(0, 1).map(node => node.id);
}, { immediate: true });
</script>

<template>
  <div class="management-page organization-management">
    <el-card v-if="loading && !capabilities.length" v-loading="loading" shadow="never"><el-skeleton :rows="8" /></el-card>
    <BusinessWorkspace v-else :title="t('organization.title')" :description="t('organization.description')">
      <template #navigation><el-tabs v-model="workspaceView" class="organization-navigation"><el-tab-pane :label="t('organization.structure')" name="structure" /><el-tab-pane :label="t('organization.assignmentManagement')" name="assignments" /><el-tab-pane :label="t('organization.relations')" name="relations" /><el-tab-pane :label="t('organization.types')" name="types" /></el-tabs></template>
      <template v-if="error || relationTypesError" #feedback><FloatingError v-if="error" :title="error" scope="error" type="error" show-icon :closable="false" /><FloatingError v-else-if="relationTypesError" :title="relationTypesError" scope="relationTypesError" /></template>
      <template #data>
        <TreeListTemplate v-if="workspaceView === 'structure'" v-model:collapsed="treeCollapsed" :tree-label="t('organization.structure')" embedded>
          <template #treeHeader><strong>{{ t("organization.structure") }}</strong></template>
          <template #tree>
            <div class="organization-tree-controls"><el-select :model-value="selectedTypeId" :aria-label="t('organization.type')" @change="selectType"><el-option v-for="item in types" :key="item.id" :label="item.name" :value="item.id" :disabled="!item.enabled" /></el-select><el-input v-model="treeKeyword" clearable :placeholder="t('organization.searchPlaceholder')"><template #prefix><AppIcon name="search" :size="16" /></template></el-input></div>
            <el-tree :data="tree" node-key="id" :props="{ label: 'name', children: 'children' }" :current-node-key="selectedUnitId" highlight-current :default-expanded-keys="initialExpandedKeys" empty-text="" @node-click="row => selectedUnitId = row.id"><template #default="{ data }"><span class="organization-tree-node"><span>{{ data.name }}</span><el-tag v-if="!data.enabled" size="small" type="info">{{ t("organization.disabled") }}</el-tag></span></template></el-tree>
            <el-empty v-if="!tree.length" :description="treeKeyword ? t('organization.noSearchResults') : t('organization.empty')" :image-size="56" />
          </template>
          <template #header>
            <div v-if="selectedUnit" class="organization-unit-header"><div class="organization-unit-heading"><div><h2>{{ selectedUnit.name }}</h2><p>{{ selectedType?.name }} · {{ selectedUnit.code }}</p></div><el-tag :type="selectedUnit.enabled ? 'success' : 'info'">{{ t(selectedUnit.enabled ? "organization.enabled" : "organization.disabled") }}</el-tag></div><div class="organization-unit-actions"><el-button v-operation="'OrganizationUnit.create'" v-if="can('CREATE')" type="primary" @click="open('unit')">{{ t("organization.createChildUnit") }}</el-button><el-button v-operation="'OrganizationUnit.update'" v-if="can('UPDATE')" @click="open('unit', selectedUnit)">{{ t("organization.edit") }}</el-button><el-dropdown v-operation="'OrganizationUnit.update'" v-if="can('UPDATE')" @command="command => selectedUnit && (command === 'move' ? open('move', selectedUnit) : toggleUnit(selectedUnit))"><el-button :aria-label="t('organization.moreActions')"><AppIcon name="settings" :size="18" /></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-operation="'OrganizationUnit.move'" command="move">{{ t("organization.move") }}</el-dropdown-item><el-dropdown-item v-operation="'OrganizationUnit.update'" command="toggle" :class="{ 'organization-danger-item': selectedUnit.enabled }">{{ t(selectedUnit.enabled ? "organization.disable" : "organization.enable") }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></div>
          </template>
          <template #table>
            <div v-if="selectedUnit" class="organization-detail" :class="{ 'management-table-stack': detailView === 'positions' }">
              <el-tabs v-model="detailView"><el-tab-pane :label="t('organization.positionsAndAssignments')" name="positions" /><el-tab-pane :label="t('organization.leaders')" name="leaders" /><el-tab-pane :label="t('organization.relations')" name="relations" /></el-tabs>
              <ManagementTable v-if="detailView === 'positions'" embedded heading-level="h2" :title="t('organization.positions')" :rows="selectedPositions" :columns="positionColumns" :loading="loading" :busy="saving" @refresh="load"><template #actions><el-button v-operation="'Position.create'" v-if="can('CREATE') && selectedUnit.enabled" type="primary" @click="open('position')">{{ t("organization.createPosition") }}</el-button></template><template #assignments="{ row }">{{ assignmentCount(row.id) }}</template><template #status="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ t(row.enabled ? "organization.enabled" : "organization.disabled") }}</el-tag></template><template #operations="{ row }"><el-button v-operation="'Position.update'" v-if="can('UPDATE')" link type="primary" @click="open('position', row)">{{ t("organization.edit") }}</el-button><el-button v-operation="'PositionAssignment.update'" v-if="can('CREATE') && row.enabled" link type="primary" @click="open('assignment', row)">{{ t("organization.createAssignment") }}</el-button></template></ManagementTable>
              <ManagementTable v-if="detailView === 'positions'" class="organization-secondary-table" embedded heading-level="h2" :title="t('organization.currentAssignments')" :rows="selectedAssignments" :columns="assignmentColumns" :loading="loading" :busy="saving" @refresh="load"><template #member="{ row }">{{ labelPerson(row.personId) }}</template><template #position="{ row }">{{ labelPosition(row.positionId) }}</template><template #organization>{{ selectedUnit.name }}</template><template #period="{ row }">{{ formatDate(row.startsAt) }} — {{ formatDate(row.endsAt) }}</template><template #status="{ row }"><el-tag :type="isActiveAssignment(row) ? 'success' : 'info'">{{ t(isActiveAssignment(row) ? "organization.active" : "organization.ended") }}</el-tag></template><template #operations="{ row }"><el-button v-operation="'PositionAssignment.update'" v-if="can('UPDATE') && isActiveAssignment(row)" link type="danger" @click="endAssignment(row)">{{ t("organization.endAssignment") }}</el-button></template></ManagementTable>
              <ManagementTable v-else-if="detailView === 'leaders'" embedded heading-level="h2" :title="t('organization.leaders')" :rows="selectedLeaders" :columns="leaderColumns" :loading="loading" :busy="saving" @refresh="load"><template #actions><el-button v-operation="'OrganizationLeader.set'" v-if="can('UPDATE') && selectedUnit.enabled" type="primary" @click="open('leader')">{{ t("organization.setLeader") }}</el-button></template><template #member="{ row }">{{ labelPerson(assignmentPersonId(row.assignmentId)) }}</template><template #position="{ row }">{{ labelPosition(assignmentPositionId(row.assignmentId)) }}</template><template #period="{ row }"><template v-if="assignments.find(item => item.id === row.assignmentId)">{{ formatDate(assignments.find(item => item.id === row.assignmentId)!.startsAt) }} — {{ formatDate(assignments.find(item => item.id === row.assignmentId)!.endsAt) }}</template></template><template #operations="{ row }"><el-button v-operation="'OrganizationLeader.set'" v-if="can('UPDATE')" link type="danger" @click="removeLeader(row)">{{ t("organization.removeLeader") }}</el-button></template></ManagementTable>
              <ManagementTable v-else-if="detailView === 'relations'" embedded heading-level="h2" :title="t('organization.relatedRelations')" :rows="selectedRelations" :columns="relationColumns" :loading="loading" :busy="saving" @refresh="load"><template #actions><el-button v-operation="'OrganizationRelation.create'" v-if="can('CREATE') && selectedUnit.enabled" type="primary" @click="open('relation')">{{ t("organization.createRelation") }}</el-button></template><template #source="{ row }">{{ labelUnit(row.sourceId) }}</template><template #direction="{ row }"><span class="relation-direction"><AppIcon name="arrowRight" :size="16" />{{ labelRelationType(row.relationType) }}</span></template><template #target="{ row }">{{ labelUnit(row.targetId) }}</template><template #operations="{ row }"><el-button v-operation="'OrganizationRelation.delete'" v-if="can('DELETE')" link type="danger" @click="removeRelation(row)">{{ t("organization.delete") }}</el-button></template></ManagementTable>
            </div>
            <el-empty v-else :description="t('organization.selectUnitHint')" />
          </template>
        </TreeListTemplate>

        <section v-else-if="workspaceView === 'assignments'" class="organization-list-view"><div class="organization-filter"><el-input v-model="assignmentKeyword" clearable :placeholder="t('organization.searchAssignmentPlaceholder')" /><el-select v-model="assignmentStatus"><el-option :label="t('organization.allStatuses')" value="all" /><el-option :label="t('organization.active')" value="active" /><el-option :label="t('organization.ended')" value="ended" /></el-select></div><ManagementTable embedded :title="t('organization.assignmentManagement')" :rows="filteredAssignments" :columns="assignmentColumns" :loading="loading" :busy="saving" @refresh="load"><template #actions><el-button v-operation="'PositionAssignment.create'" v-if="can('CREATE')" type="primary" @click="open('assignment')">{{ t("organization.createAssignment") }}</el-button></template><template #member="{ row }">{{ labelPerson(row.personId) }}</template><template #position="{ row }">{{ labelPosition(row.positionId) }}</template><template #organization="{ row }"><el-button link type="primary" @click="selectedTypeId = units.find(item => item.id === positionOrganizationId(row.positionId))?.typeId || ''; selectedUnitId = positionOrganizationId(row.positionId); workspaceView = 'structure'">{{ labelUnit(positionOrganizationId(row.positionId)) }}</el-button></template><template #period="{ row }">{{ formatDate(row.startsAt) }} — {{ formatDate(row.endsAt) }}</template><template #status="{ row }"><el-tag :type="isActiveAssignment(row) ? 'success' : 'info'">{{ t(isActiveAssignment(row) ? "organization.active" : "organization.ended") }}</el-tag></template><template #operations="{ row }"><el-button v-operation="'PositionAssignment.update'" v-if="can('UPDATE') && isActiveAssignment(row)" link type="danger" @click="endAssignment(row)">{{ t("organization.endAssignment") }}</el-button></template></ManagementTable></section>
        <section v-else-if="workspaceView === 'relations'" class="organization-list-view"><div class="organization-filter"><el-input v-model="relationKeyword" clearable :placeholder="t('organization.searchRelationPlaceholder')" /></div><ManagementTable embedded :title="t('organization.relations')" :rows="filteredRelations" :columns="relationColumns" :loading="loading" :busy="saving" @refresh="load"><template #actions><el-button v-operation="'OrganizationRelation.create'" v-if="can('CREATE')" type="primary" @click="open('relation')">{{ t("organization.createRelation") }}</el-button></template><template #source="{ row }">{{ labelUnit(row.sourceId) }}</template><template #direction="{ row }"><span class="relation-direction"><AppIcon name="arrowRight" :size="16" />{{ labelRelationType(row.relationType) }}</span></template><template #target="{ row }">{{ labelUnit(row.targetId) }}</template><template #operations="{ row }"><el-button v-operation="'OrganizationRelation.delete'" v-if="can('DELETE')" link type="danger" @click="removeRelation(row)">{{ t("organization.delete") }}</el-button></template></ManagementTable></section>
        <section v-else class="organization-list-view"><div class="organization-filter"><el-input v-model="typeKeyword" clearable :placeholder="t('organization.searchTypePlaceholder')" /></div><ManagementTable embedded :title="t('organization.types')" :rows="filteredTypes" :columns="typeColumns" :loading="loading" :busy="saving" @refresh="load"><template #actions><el-button v-operation="'OrganizationType.create'" v-if="can('CREATE')" type="primary" @click="open('type')">{{ t("organization.createType") }}</el-button></template><template #unitCount="{ row }"><el-button link type="primary" @click="selectType(row.id); workspaceView = 'structure'">{{ unitCount(row.id) }}</el-button></template><template #status="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ t(row.enabled ? "organization.enabled" : "organization.disabled") }}</el-tag></template><template #operations="{ row }"><el-button v-operation="'OrganizationType.update'" v-if="can('UPDATE')" link type="primary" @click="open('type', row)">{{ t("organization.edit") }}</el-button><el-button v-operation="'OrganizationType.update'" v-if="can('UPDATE')" link :type="row.enabled ? 'danger' : 'primary'" @click="toggleType(row)">{{ t(row.enabled ? "organization.disable" : "organization.enable") }}</el-button></template></ManagementTable></section>
      </template>
    </BusinessWorkspace>

    <ManagedDialog :model-value="Boolean(dialog)" :title="t('organization.' + dialog + 'Dialog')" width="min(600px, calc(100vw - 32px))" :busy="saving" @update:model-value="value => { if (!value) dialog = '' }">
      <FloatingError v-if="dialogError" class="organization-dialog-error" :title="dialogError" scope="dialogError" type="error" show-icon :closable="false" />
      <el-form class="business-border-label-form" label-position="top" @submit.prevent="save">
        <template v-if="['type', 'unit', 'position'].includes(dialog)"><el-form-item v-if="!selected" :label="t('organization.code')" required><el-input v-model="code" maxlength="50" show-word-limit /></el-form-item><el-form-item :label="dialog === 'position' ? t('organization.positionName') : t('organization.name')" required><el-input v-model="name" maxlength="100" show-word-limit /></el-form-item></template>
        <el-form-item v-if="dialog === 'unit' && !selected" :label="t('organization.type')" required><el-select v-model="typeId"><el-option v-for="item in types.filter(value => value.enabled)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-if="dialog === 'unit' || dialog === 'move'" :label="t('organization.parent')"><el-select v-model="parentId" clearable :placeholder="t('organization.tenantRoot')"><el-option v-for="item in units.filter(value => value.enabled && value.typeId === (selected && 'typeId' in selected ? selected.typeId : typeId) && value.id !== selected?.id)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <template v-if="dialog === 'relation'"><el-form-item :label="t('organization.relationType')" required><el-select v-model="relationType"><el-option v-for="item in relationTypes" :key="item.value" :label="item.name" :value="item.value" /></el-select></el-form-item><el-form-item :label="t('organization.source')" required><el-select v-model="sourceId" filterable><el-option v-for="item in units.filter(value => value.enabled)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('organization.target')" required><el-select v-model="targetId" filterable><el-option v-for="item in units.filter(value => value.enabled && value.id !== sourceId)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item></template>
        <el-form-item v-if="dialog === 'position' && !selected" :label="t('organization.name')" required><el-select v-model="sourceId" filterable><el-option v-for="item in units.filter(value => value.enabled)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <template v-if="dialog === 'assignment'"><el-form-item :label="t('organization.person')" required><el-select v-model="personId" filterable><el-option v-for="item in members" :key="item.id" :label="item.displayName" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('organization.positionName')" required><el-select v-model="positionId" filterable><el-option v-for="item in positions.filter(value => value.enabled)" :key="item.id" :label="labelUnit(item.organizationId) + ' · ' + item.name" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('organization.startsAt')" required><el-date-picker v-model="startsAt" type="datetime" /></el-form-item><el-form-item :label="t('organization.endsAt')"><el-date-picker v-model="endsAt" type="datetime" clearable /></el-form-item></template>
        <el-form-item v-if="dialog === 'leader'" :label="t('organization.assignments')" required><el-select v-model="targetId" filterable><el-option v-for="item in activeAssignments.filter(value => positionOrganizationId(value.positionId) === selectedUnitId)" :key="item.id" :label="labelPerson(item.personId) + ' · ' + labelPosition(item.positionId)" :value="item.id" /></el-select></el-form-item>
        <div class="management-form-actions"><el-button @click="dialog = ''">{{ t("organization.cancel") }}</el-button><el-button v-operation="saveOperation" type="primary" native-type="submit" :loading="saving">{{ t("organization.save") }}</el-button></div>
      </el-form>
    </ManagedDialog>
  </div>
</template>

<style scoped>
/* 页面外围留白引用全局内容与区块 Token，局部字段间距和触控高度按原交互保留。 */
/* 复用字典左树右表的 220px 分类区、紧凑留白和主题选中态，组织层级仍使用真实树组件。 */



.organization-detail { gap: var(--app-section-gap, 12px); }
/* 双表各自保留分页空间，窄窗口由详情区滚动，避免页码被压出可见表面。 */
.organization-detail > .management-table { flex-shrink: 0; }

.organization-management :deep(.business-workspace__data) { padding: 0; }
/* 组织页含额外的列表和详情包装层，必须继续传递剩余高度，避免公共表体的 flex 基准为零。 */
.organization-management, .organization-list-view, .organization-detail { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.organization-list-view, .organization-detail { overflow: auto; }
/* 岗位与任职同时显示时保留各自的可读高度，空间不足由详情区域滚动。 */
.organization-detail > .management-table { flex: 1 0 260px; min-height: 260px; }
.organization-detail > .el-tabs, .organization-filter { flex-shrink: 0; }
.organization-navigation { min-width: 0; }
.organization-tree-controls { display: grid; gap: 8px; margin-bottom: 12px; }
.organization-tree-node { display: flex; min-width: 0; width: 100%; align-items: center; justify-content: space-between; gap: 8px; }
.organization-tree-node > span:first-child { overflow: hidden; text-overflow: ellipsis; }
.organization-unit-header, .organization-unit-heading, .organization-unit-actions, .relation-direction { display: flex; align-items: center; }
.organization-unit-header { justify-content: space-between; gap: 20px; }
.organization-unit-heading { min-width: 0; gap: 10px; }
.organization-unit-heading h2 { margin: 0; font-size: var(--el-font-size-large); }
.organization-unit-heading p { margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: var(--el-font-size-small); }
.organization-unit-actions { flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.organization-unit-actions :deep(.el-button + .el-button) { margin-left: 0; }
.organization-detail > .el-tabs { margin-bottom: 8px; }
.organization-secondary-table { margin-top: 0; padding-top: var(--app-section-gap, 12px); border-top: 1px solid var(--el-border-color-lighter); }
.organization-list-view { min-width: 0; padding:var(--app-content-padding,12px); }
.organization-filter { display: flex; justify-content: flex-end; gap: 12px; margin-bottom:var(--app-section-gap,8px); padding:var(--app-content-padding,12px); border: 1px solid var(--el-border-color-lighter); border-radius: var(--el-border-radius-base); background: var(--el-fill-color-extra-light); }
.organization-filter .el-input { width: min(360px, 100%); }
.organization-filter .el-select { width: 160px; }
.relation-direction { gap: 6px; color: var(--el-text-color-secondary); }
.organization-dialog-error { margin-bottom:var(--app-section-gap,8px); }
.organization-management :deep(.el-select), .organization-management :deep(.el-date-editor) { width: 100%; }
:global(.organization-danger-item) { color: var(--el-color-danger); }
@media (max-width: 760px) {
  .organization-unit-header, .organization-filter { align-items: stretch; flex-direction: column; }
  .organization-unit-actions { justify-content: flex-start; }
  .organization-unit-actions > .el-button { flex: 1; }
  .organization-filter .el-input, .organization-filter .el-select { width: 100%; }
  .organization-list-view { padding:var(--app-content-padding,12px); }
}
</style>
