<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import WorkflowCanvas from "./WorkflowCanvas.vue";
import {
  businessModel,
  appendBusinessReview,
  removeBusinessReview,
} from "./businessConfiguration";
import type { BusinessConfiguration, Edge, WorkflowModel } from "./index";

/** 业务审批配置组合视图；只编辑受控图和目录内能力，保存发布由父设计器统一完成。 */
const props = defineProps<{
  catalog: BusinessConfiguration;
  disabled: boolean;
}>();
const model = defineModel<WorkflowModel>({ required: true });
const { t, locale } = useI18n();
const label = (value: { nameZh: string; nameEn: string }) =>
  locale.value.startsWith("en") ? value.nameEn : value.nameZh;
const document = computed(() =>
  props.catalog.documents.find(
    (d) =>
      d.capability === model.value.entryCapability &&
      d.profile.objectType === model.value.objectType &&
      d.profile.purpose === model.value.purpose,
  ),
);
const profile = computed(() => document.value?.profile);
const reviews = computed(() =>
  model.value.nodes.filter((n) => n.kind === "REVIEW"),
);
const conditions = computed(() =>
  model.value.edges.filter(
    (e) =>
      !e.defaultEdge &&
      model.value.nodes.some((n) => n.id === e.from && n.kind === "CHOICE"),
  ),
);
const options = computed(() => props.catalog.options);
const withdrawal = computed({
  get: () => !!model.value.withdrawalCapability,
  set: (enabled: boolean) => {
    if (props.disabled || !profile.value) return;
    model.value.withdrawalCapability = enabled
      ? profile.value.withdrawalCapability
      : null;
    const outcome = profile.value.outcomes.find(
      (o) => o.result === "WITHDRAWN",
    );
    if (enabled && outcome)
      model.value.terminalActions.WITHDRAWN = outcome.capability;
    else delete model.value.terminalActions.WITHDRAWN;
  },
});
const nameOf = (id: string) =>
  model.value.nodes.find((n) => n.id === id)?.name || id;
function selectDocument(key: string) {
  if (props.disabled || model.value.entryCapability) return;
  const doc = props.catalog.documents.find((d) => d.capability === key);
  if (!doc) return;
  model.value = businessModel(key, doc.profile, {
    title: label(doc.profile) + t("businessWorkflow.approvalSuffix"),
    start: t("businessWorkflow.start"),
    review: t("businessWorkflow.review"),
    end: t("businessWorkflow.end"),
  });
}
function addReview(conditional: boolean) {
  if (props.disabled || !profile.value) return;
  const review = appendBusinessReview(
    model.value,
    conditional,
    {
      review: t("businessWorkflow.review"),
      choice: t("businessWorkflow.condition"),
      merge: t("businessWorkflow.merge"),
    },
    profile.value.fields[0]?.key,
  );
  if (!review) ElMessage.warning(t("businessWorkflow.complexGraph"));
  else if (conditional) {
    const edge = model.value.edges.find(e => e.to === review.id && e.field);
    if (edge) changeField(edge);
  }
}
function changeField(edge: Edge) {
  edge.operator = fieldType(edge) === "NUMBER" ? "NUM_EQ" : "EQ";
  edge.value = "";
}
function removeReview(id: string) {
  if (!props.disabled && !removeBusinessReview(model.value, id))
    ElMessage.warning(t("businessWorkflow.complexGraph"));
}
function operators(edge: Edge) {
  return profile.value?.fields.find((f) => f.key === edge.field)?.type ===
    "NUMBER"
    ? ["NUM_EQ", "NUM_NE", "GT", "GE", "LT", "LE", ...(edge.operator === "EQ" || edge.operator === "NE" ? [edge.operator] : [])]
    : ["EQ", "NE"];
}
function fieldType(edge: Edge) {
  return profile.value?.fields.find((f) => f.key === edge.field)?.type;
}
function setOutcome(result: string, capability?: string) {
  if (props.disabled) return;
  if (capability) model.value.terminalActions[result] = capability;
  else delete model.value.terminalActions[result];
}
</script>

<template>
  <div class="business-configuration">
    <div class="business-configuration-form">
      <el-form label-position="top" :disabled="disabled">
        <h2>{{ t("businessWorkflow.documentTitle") }}</h2>
        <el-form-item :label="t('businessWorkflow.document')" required>
          <el-select
            :model-value="document?.capability"
            :disabled="!!model.entryCapability || disabled"
            @change="selectDocument"
          >
            <el-option
              v-for="doc in catalog.documents"
              :key="doc.capability"
              :value="doc.capability"
              :label="label(doc.profile)"
            />
          </el-select>
        </el-form-item>
        <el-empty
          v-if="!catalog.documents.length"
          :description="t('businessWorkflow.noDocuments')"
        />
        <el-alert
          v-if="model.entryCapability && !profile"
          :title="t('businessWorkflow.unsupportedDocument')"
          type="warning"
          :closable="false"
        />
        <template v-if="profile">
          <el-alert
            :title="
              locale.startsWith('en')
                ? profile.descriptionEn
                : profile.descriptionZh
            "
            type="info"
            :closable="false"
          />
          <el-form-item :label="t('workflow.name')" required
            ><el-input v-model="model.name" maxlength="100"
          /></el-form-item>
          <p class="business-explanation">
            {{ t("businessWorkflow.dataContract") }}
          </p>
          <h2>{{ t("businessWorkflow.reviewTitle") }}</h2>
          <div v-for="review in reviews" :key="review.id" class="business-rule">
            <el-form-item :label="t('businessWorkflow.stepName')" required
              ><el-input v-model="review.name" maxlength="100"
            /></el-form-item>
            <el-form-item :label="t('businessWorkflow.approvers')" required>
              <div
                v-for="(person, index) in review.people"
                :key="index"
                class="business-person"
              >
                <el-select
                  v-model="person.kind"
                  :aria-label="t('businessWorkflow.approverRule')"
                  @change="person.reference = ''"
                >
                  <el-option
                    v-for="kind in ['LEADER', 'MEMBER', 'POSITION']"
                    :key="kind"
                    :value="kind"
                    :label="t('businessWorkflow.people.' + kind)"
                  />
                </el-select>
                <el-select
                  v-if="person.kind !== 'LEADER'"
                  v-model="person.reference"
                  filterable
                  :aria-label="t('businessWorkflow.approverName')"
                >
                  <el-option
                    v-if="
                      person.reference &&
                      !(
                        person.kind === 'MEMBER'
                          ? options.members
                          : options.positions
                      ).some((p) => p.id === person.reference)
                    "
                    :value="person.reference"
                    :label="t('businessWorkflow.unavailablePerson')"
                    disabled
                  />
                  <el-option
                    v-for="option in person.kind === 'MEMBER'
                      ? options.members
                      : options.positions"
                    :key="option.id"
                    :value="option.id"
                    :label="option.name"
                  />
                </el-select>
                <el-button
                  :disabled="disabled || review.people.length === 1"
                  @click="review.people.splice(index, 1)"
                  >{{ t("workflow.remove") }}</el-button
                >
              </div>
            </el-form-item>
            <el-button
              :disabled="disabled"
              @click="review.people.push({ kind: 'MEMBER', reference: '' })"
              >{{ t("businessWorkflow.addApprover") }}</el-button
            >
            <p class="business-explanation">
              {{ t("businessWorkflow.leaderHint") }}
            </p>
            <el-form-item :label="t('workflow.completion')"
              ><el-select v-model="review.completion"
                ><el-option
                  v-for="value in ['ALL', 'ANY', 'SEQUENTIAL']"
                  :key="value"
                  :value="value"
                  :label="t('workflow.completions.' + value)" /></el-select
            ></el-form-item>
            <el-form-item :label="t('businessWorkflow.selfApproval')"
              ><el-switch v-model="review.allowSelf"
            /></el-form-item>
            <p class="business-explanation">
              {{ t("businessWorkflow.nodeActionHint") }}
            </p>
            <el-popconfirm
              :title="t('businessWorkflow.removeConfirm')"
              @confirm="removeReview(review.id)"
              ><template #reference
                ><el-button
                  type="danger"
                  plain
                  :disabled="disabled || reviews.length <= 1"
                  >{{ t("businessWorkflow.removeReview") }}</el-button
                ></template
              ></el-popconfirm
            >
          </div>
          <div class="business-buttons">
            <el-button :disabled="disabled" @click="addReview(false)">{{
              t("businessWorkflow.addReview")
            }}</el-button
            ><el-button
              :disabled="disabled || !profile.fields.length"
              @click="addReview(true)"
              >{{ t("businessWorkflow.addConditionalReview") }}</el-button
            >
          </div>
          <h2>{{ t("businessWorkflow.conditionsTitle") }}</h2>
          <p class="business-explanation">
            {{ t("businessWorkflow.conditionHint") }}
          </p>
          <el-empty
            v-if="!conditions.length"
            :description="t('businessWorkflow.noConditions')"
          />
          <div
            v-for="(edge, index) in conditions"
            :key="index"
            class="business-rule"
          >
            <h3>{{ nameOf(edge.from) }}</h3>
            <el-form-item :label="t('businessWorkflow.field')" required
              ><el-select v-model="edge.field" @change="changeField(edge)"
                ><el-option
                  v-for="field in profile.fields"
                  :key="field.key"
                  :value="field.key"
                  :label="
                    label(field) +
                    ((locale.startsWith('en') ? field.unitEn : field.unitZh)
                      ? ' (' +
                        (locale.startsWith('en')
                          ? field.unitEn
                          : field.unitZh) +
                        ')'
                      : '')
                  " /></el-select
            ></el-form-item>
            <el-form-item :label="t('businessWorkflow.operator')" required
              ><el-select
                :model-value="edge.operator || 'EQ'"
                @update:model-value="edge.operator = $event"
                ><el-option
                  v-for="operator in operators(edge)"
                  :key="operator"
                  :value="operator"
                  :label="
                    t('businessWorkflow.operators.' + operator)
                  " /></el-select
            ></el-form-item>
            <el-form-item :label="t('businessWorkflow.value')" required>
              <el-select
                v-if="fieldType(edge) === 'BOOLEAN'"
                v-model="edge.value"
                ><el-option
                  value="true"
                  :label="t('businessWorkflow.yes')" /><el-option
                  value="false"
                  :label="t('businessWorkflow.no')"
              /></el-select>
              <el-input
                v-else
                v-model="edge.value"
                maxlength="100"
                :inputmode="fieldType(edge) === 'NUMBER' ? 'decimal' : 'text'"
              />
            </el-form-item>
            <p>{{ t("businessWorkflow.then") }} {{ nameOf(edge.to) }}</p>
            <p
              v-if="
                model.edges.find((e) => e.from === edge.from && e.defaultEdge)
              "
            >
              {{ t("businessWorkflow.otherwise") }}
              {{
                nameOf(
                  model.edges.find(
                    (e) => e.from === edge.from && e.defaultEdge,
                  )!.to,
                )
              }}
            </p>
          </div>
          <h2>{{ t("businessWorkflow.outcomeTitle") }}</h2>
          <p class="business-explanation">
            {{ t("businessWorkflow.deliveryHint") }}
          </p>
          <el-form-item
            v-for="result in ['APPROVED', 'REJECTED']"
            :key="result"
            :label="t('workflow.states.' + result)"
            required
          >
            <el-select
              :model-value="model.terminalActions[result]"
              @change="(value) => setOutcome(result, value)"
              ><el-option
                v-for="outcome in profile.outcomes.filter(
                  (o) => o.result === result,
                )"
                :key="outcome.capability"
                :value="outcome.capability"
                :label="label(outcome)"
            /></el-select>
          </el-form-item>
          <el-form-item :label="t('businessWorkflow.allowWithdrawal')"
            ><el-switch
              v-model="withdrawal"
              :disabled="
                disabled ||
                !profile.withdrawalCapability ||
                !profile.outcomes.some((o) => o.result === 'WITHDRAWN')
              "
          /></el-form-item>
          <p>{{ t("businessWorkflow.withdrawHint") }}</p>
          <p v-if="withdrawal">
            {{ label(profile.outcomes.find((o) => o.result === "WITHDRAWN")!) }}
          </p>
        </template>
      </el-form>
    </div>
    <aside v-if="profile" class="business-configuration-preview">
      <h2>{{ t("businessWorkflow.overview") }}</h2>
      <p>{{ t("businessWorkflow.overviewHint") }}</p>
      <div class="business-graph-scroll">
        <WorkflowCanvas :model="model" selected="" @select="() => {}" />
      </div>
      <el-alert
        :title="t('businessWorkflow.versionHint')"
        type="info"
        :closable="false"
      />
    </aside>
  </div>
</template>

<style scoped>
.business-configuration {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(320px, 1fr);
  border-top: 1px solid var(--el-border-color-lighter);
}
.business-configuration-form,
.business-configuration-preview {
  min-width: 0;
  padding: 24px;
}
.business-configuration-preview {
  border-left: 1px solid var(--el-border-color-lighter);
}
/* 用单一 gap 管理业务配置的区块间距，避免 Alert、表单项与卡片各自外边距叠加。 */
.business-configuration-form > :deep(.el-form) {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.business-configuration-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.business-configuration-form p,
.business-rule h3 {
  margin: 0;
}
.business-buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}
.business-rule {
  padding: 16px;
  margin: 0;
  display: grid;
  gap: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}
.business-person,
.business-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}
.business-person {
  margin-bottom: 0;
}
.business-person .el-select {
  flex: 1;
  min-width: 160px;
}
.business-explanation {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.business-graph-scroll {
  max-height: 680px;
  overflow: auto;
}
h2 {
  margin: 8px 0 0;
  font-size: 16px;
}
h2:first-child {
  margin-top: 0;
}
h3 {
  font-size: 14px;
}
@media (max-width: 1100px) {
  .business-configuration {
    grid-template-columns: minmax(0, 1fr);
  }
  .business-configuration-preview {
    border-left: 0;
    border-top: 1px solid var(--el-border-color-lighter);
  }
}
@media (max-width: 480px) {
  .business-configuration-form,
  .business-configuration-preview {
    padding: 12px;
  }
  .business-person .el-select {
    flex-basis: 100%;
  }
}
</style>
