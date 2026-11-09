<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AccountSessions from "./components/AccountSessions.vue";
import SecurityLogs from "./components/SecurityLogs.vue";
import ThemeToggle from "@/components/ThemeToggle/index.vue";
import { useTranslationLang } from "@/layout/hooks/useTranslationLang";
import { useUserStoreHook } from "@/store/modules/user";
import { useI18n } from "vue-i18n";
import { AppIcon } from "@appnexus/icons";
import { SplitDetailTemplate } from "@appnexus/ui";
import { ElMessage } from "element-plus";
import { getProfile, saveProfile, changePassword, type AccountProfile } from "@/api/account";
import { restoreSession } from "@/api/session";
import { clearSession } from "@/utils/sessionLifecycle";
import { requestErrorMessage } from "@/utils/http";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const user = useUserStoreHook();
const { locale, translationCh, translationEn } = useTranslationLang();
/** 分区写入路由查询参数，刷新与浏览器前进后退保持当前分区；只接受已实现的固定值。 */
type Section = "profile" | "preferences" | "security" | "logs" | "sessions";
const section = computed<Section>(() =>
  ["profile", "preferences", "security", "logs", "sessions"].includes(String(route.query.section))
    ? route.query.section as Section : "profile");
const navigation = [
  { key: "profile", icon: "user", title: "account.personalInfo" },
  { key: "preferences", icon: "settings", title: "account.preferences" },
  { key: "sessions", icon: "monitor", title: "account.sessions" },
  { key: "logs", icon: "shield", title: "account.securityLogs" },
  { key: "security", icon: "shield", title: "account.accountSecurity" }
] as const;
const editingPassword = ref(false);
/** 离开安全分区或关闭表单即释放密码，防止隐藏表单长期保留凭据。 */
watch([section, editingPassword], () => {
  passwords.current = ""; passwords.next = ""; passwords.confirm = ""; passwordError.value = "";
});
/** 分区相同不重复导航；仅更新右侧内容，外壳实例由路由复用策略保持。 */
function selectSection(value: Section) {
  if (value === section.value) return;
  editingPassword.value = false;
  void router.replace({ path: route.path, query: { section: value } });
}
const profile = ref<AccountProfile>();
const displayName = ref("");
const email = ref("");
const phoneNumber = ref("");
const loading = ref(false);
const saving = ref(false);
const changing = ref(false);
const error = ref("");
const passwordError = ref("");
/** 密码仅在当前组件内存保存，离开页面即释放，成功或失败后主动清空。 */
const passwords = reactive({ current: "", next: "", confirm: "" });

/** 加载个人资料；网络故障保留重试入口，不将故障解释成退出登录。 */
async function load() {
  loading.value = true; error.value = "";
  try {
    profile.value = await getProfile(); displayName.value = profile.value.displayName;
    email.value = profile.value.email || ""; phoneNumber.value = profile.value.phoneNumber || "";
  }
  catch (cause) { error.value = requestErrorMessage(cause); }
  finally { loading.value = false; }
}
/** 服务端保存后刷新会话展示资料，使顶部导航名称立即一致。 */
async function save() {
  if (!displayName.value.trim()) return;
  saving.value = true; error.value = "";
  try {
    await saveProfile(displayName.value.trim(), email.value.trim(), phoneNumber.value.trim());
    await restoreSession(); await load();
    ElMessage.success(t("account.saved"));
  } catch (cause) { error.value = requestErrorMessage(cause); }
  finally { saving.value = false; }
}
/** 成功改密后清理本地展示状态并要求重新登录，不继续使用旧 Cookie。 */
async function submitPassword() {
  passwordError.value = "";
  if (passwords.next !== passwords.confirm) { passwordError.value = t("account.mismatch"); return; }
  changing.value = true;
  try {
    await changePassword(passwords.current, passwords.next);
    ElMessage.success(t("account.passwordSaved"));
    await clearSession("passwordChanged");
  } catch (cause) { passwordError.value = requestErrorMessage(cause); }
  finally { changing.value = false; passwords.current = ""; passwords.next = ""; passwords.confirm = ""; }
}
onMounted(load);
</script>

<template>
  <SplitDetailTemplate unified class="account-shell">
    <template #summary><aside class="account-sidebar">
      <el-button class="back-button" text @click="router.push('/welcome')">‹ <span>{{ t("account.back") }}</span></el-button>
      <div class="sidebar-identity">
        <div class="identity-avatar"><AppIcon name="user" :size="26" /></div>
        <div class="identity-text"><strong>{{ user.nickname || user.username }}</strong><span>{{ user.username }}</span></div>
      </div>
      <el-menu :default-active="section" :aria-label="t('account.title')" class="account-navigation"
        @select="value => selectSection(value as Section)">
        <el-menu-item v-for="item in navigation" :key="item.key" :index="item.key">
          <AppIcon :name="item.icon" :size="18" /><span>{{ t(item.title) }}</span>
        </el-menu-item>
      </el-menu>
    </aside></template>

    <div class="account-content" v-loading="loading">
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb-4" />
      <el-button v-if="!profile && !loading" @click="load">{{ t("account.retry") }}</el-button>

      <section v-if="section === 'profile'" class="content-section">
        <h1>{{ t("account.personalInfo") }}</h1>
        <p class="section-intro">{{ t("account.personalIntro") }}</p>
        <form v-if="profile" class="profile-form" @submit.prevent="save">
          <div class="profile-summary"><div class="profile-avatar"><AppIcon name="user" :size="38" /></div>
            <div><strong>{{ profile.displayName }}</strong><p>{{ t("account.avatarHint") }}</p></div>
          </div>
          <div class="account-field"><label for="account-name">{{ t("platform.account") }}</label>
            <el-input id="account-name" :model-value="profile.username" disabled size="large" />
            <p class="field-hint">{{ t("account.usernameHint") }}</p></div>
          <div class="account-field"><label for="display-name"><span class="required">*</span> {{ t("account.displayName") }}</label>
            <el-input id="display-name" v-model="displayName" :aria-label="t('account.displayName')" maxlength="100" show-word-limit required size="large" /></div>
          <div class="account-field"><label for="account-email">{{ t("account.email") }}</label>
            <el-input id="account-email" v-model="email" type="email" maxlength="254" autocomplete="email" size="large" />
            <p class="field-hint"><el-tag size="small" :type="profile.emailVerified ? 'success' : 'info'">{{ t(profile.emailVerified ? "account.verified" : "account.unverified") }}</el-tag> {{ t("account.contactChangeHint") }}</p></div>
          <div class="account-field"><label for="account-phone">{{ t("account.phoneNumber") }}</label>
            <el-input id="account-phone" v-model="phoneNumber" maxlength="16" autocomplete="tel" placeholder="+8613800000000" size="large" />
            <p class="field-hint"><el-tag size="small" :type="profile.phoneVerified ? 'success' : 'info'">{{ t(profile.phoneVerified ? "account.verified" : "account.unverified") }}</el-tag> {{ t("account.phoneHint") }}</p></div>
          <div class="account-field"><label>{{ t("account.id") }}</label><code>{{ profile.id }}</code></div>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="loading || !displayName.trim()">{{ t("account.save") }}</el-button>
        </form>
      </section>

      <section v-else-if="section === 'preferences'" class="content-section">
        <h1>{{ t("account.preferences") }}</h1>
        <p class="section-intro">{{ t("account.preferencesIntro") }}</p>
        <div class="setting-row"><div><h2>{{ t("platform.theme") }}</h2><p>{{ t("account.themeHint") }}</p></div><ThemeToggle /></div>
        <div class="setting-row"><div><h2>{{ t("account.language") }}</h2><p>{{ t("account.languageHint") }}</p></div>
          <el-select :model-value="locale" :aria-label="t('account.language')" class="language-select"
            @change="value => value === 'zh' ? translationCh() : translationEn()">
            <el-option label="简体中文" value="zh" /><el-option label="English" value="en" />
          </el-select>
        </div>
        <div class="preference-note"><AppIcon name="monitor" :size="17" /><span>{{ t("account.preferenceScope") }}</span></div>
      </section>

      <AccountSessions v-else-if="section === 'sessions'" class="content-section" />
      <SecurityLogs v-else-if="section === 'logs'" class="content-section" />
      <section v-else class="content-section">
        <h1>{{ t("account.accountSecurity") }}</h1>
        <p class="section-intro">{{ t("account.securityIntro") }}</p>
        <div class="setting-row"><div><h2>{{ t("account.security") }}</h2><p>{{ t("account.passwordHint") }}</p></div>
          <el-button type="primary" link @click="editingPassword = !editingPassword">{{ t(editingPassword ? "account.cancel" : "account.modify") }}</el-button></div>
        <form v-if="editingPassword" class="password-form" @submit.prevent="submitPassword">
          <el-alert v-if="passwordError" :title="passwordError" type="error" :closable="false" show-icon class="mb-4" />
          <div class="account-field"><label for="current-password">{{ t("account.currentPassword") }}</label>
            <el-input id="current-password" v-model="passwords.current" type="password" show-password autocomplete="current-password" maxlength="128" required size="large" /></div>
          <div class="account-field"><label for="new-password">{{ t("account.newPassword") }}</label>
            <el-input id="new-password" v-model="passwords.next" type="password" show-password autocomplete="new-password" minlength="12" maxlength="128" required size="large" /></div>
          <div class="account-field"><label for="confirm-password">{{ t("account.confirmPassword") }}</label>
            <el-input id="confirm-password" v-model="passwords.confirm" type="password" show-password autocomplete="new-password" minlength="12" maxlength="128" required size="large" /></div>
          <el-button type="primary" native-type="submit" :loading="changing">{{ t("account.changePassword") }}</el-button>
          <el-button :disabled="changing" @click="editingPassword = false">{{ t("account.cancel") }}</el-button>
        </form>
        <div class="setting-row"><div><h2>{{ t("account.sessionProtection") }}</h2><p>{{ t("account.sessionHint") }}</p></div>
          <el-tag type="success" effect="plain">{{ t("account.enabled") }}</el-tag></div>
      </section>
    </div>
  </SplitDetailTemplate>
</template>
<style scoped>
/* 个人中心采用独立内容导航，保留平台外层布局；所有颜色复用全局主题变量。 */
/* 外层直接复用首页 el-card：外边距交给模板 main-content，边框和圆角由组件统一提供。 */
.account-sidebar { padding: 18px 14px; }
.back-button { display: flex; align-items: center; gap: 9px; padding: 4px 12px; color: var(--el-text-color-secondary); background: transparent; border: 0; cursor: pointer; font-size: 20px; }
.back-button span { font-size: 13px; }
.sidebar-identity { display: flex; align-items: center; gap: 12px; margin: 28px 12px; }
.identity-avatar, .profile-avatar { display: grid; place-items: center; flex-shrink: 0; border-radius: 50%; color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.identity-avatar { width: 48px; height: 48px; }
.identity-text { min-width: 0; display: grid; gap: 6px; font-size: 13px; }
.identity-text strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.identity-text span { color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
.account-navigation { border-right: 0; }
.account-navigation :deep(.el-menu-item) { gap: 11px; height: 44px; margin-bottom: 8px; border-radius: var(--el-border-radius-base); }
.account-navigation :deep(.el-menu-item.is-active) { background: var(--el-color-primary); color: var(--el-color-white); }
.account-content { padding: 44px clamp(24px, 4vw, 80px); min-width: 0; }
.content-section { max-width: 1000px; margin: 0 auto; }
h1 { margin: 0 0 10px; font-size: 23px; font-weight: 600; }
.section-intro { margin-bottom: 34px; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.8; }
.profile-form { max-width: 760px; }
.profile-summary { display: flex; align-items: center; gap: 18px; margin-bottom: 32px; }
.profile-avatar { width: 76px; height: 76px; }
.profile-summary strong { font-size: 16px; font-weight: 500; }
.profile-summary p, .field-hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; margin-top: 8px; }
.account-field { margin-bottom: 26px; }
.account-field label { display: block; margin-bottom: 10px; font-size: 14px; }
.account-field code { color: var(--el-text-color-secondary); font-size: 12px; overflow-wrap: anywhere; }
.required { color: var(--el-color-danger); }
.setting-row { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 25px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.setting-row h2 { font-size: 15px; font-weight: 500; margin: 0 0 8px; }
.setting-row p { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.8; max-width: 650px; }
.language-select { width: 150px; flex-shrink: 0; }
.preference-note { display: flex; align-items: center; gap: 9px; color: var(--el-text-color-secondary); font-size: 12px; margin-top: 25px; line-height: 1.8; }
.password-form { max-width: 520px; padding: 28px 0; }
@media (max-width: 800px) { .account-sidebar { border-right: 0; border-bottom: 1px solid var(--el-border-color-lighter); } .sidebar-identity { margin: 15px 12px; } .account-navigation { display: flex; flex-wrap: wrap; gap: 6px; } .account-navigation :deep(.el-menu-item) { padding: 0 12px; margin-bottom: 0; } .account-content { padding: 28px 20px; } .setting-row { flex-wrap: wrap; gap: 14px; } }
</style>
