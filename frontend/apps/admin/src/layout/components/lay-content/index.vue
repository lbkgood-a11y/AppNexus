<script setup lang="ts">
import { useI18n } from "vue-i18n";
import LayFrame from "../lay-frame/index.vue";
import LayFooter from "../lay-footer/index.vue";
import { useTags } from "@/layout/hooks/useTag";
import { useGlobal, isNumber } from "@pureadmin/utils";
import BackTopIcon from "@/assets/svg/back_top.svg?component";
import { h, computed, Transition, defineComponent } from "vue";
import { usePermissionStoreHook } from "@/store/modules/permission";

const props = defineProps({
  fixedHeader: Boolean
});

const { t } = useI18n();
const { tagsStyle } = useTags();
const { $storage, $config } = useGlobal<GlobalPropertiesApi>();

const isKeepAlive = computed(() => {
  return $config?.KeepAlive;
});

const transitions = computed(() => {
  return route => {
    return route.meta.transition;
  };
});

const hideTabs = computed(() => {
  return $storage?.configure.hideTabs;
});

const hideFooter = computed(() => {
  return $storage?.configure.hideFooter;
});

const stretch = computed(() => {
  return $storage?.configure.stretch;
});

const layout = computed(() => {
  return $storage?.layout.layout === "vertical";
});

const getMainWidth = computed(() => {
  return isNumber(stretch.value)
    ? stretch.value + "px"
    : stretch.value
      ? "1440px"
      : "100%";
});

const getSectionStyle = computed(() => {
  return [
    hideTabs.value && layout ? "padding-top: 48px;" : "",
    !hideTabs.value && layout
      ? tagsStyle.value == "chrome"
        ? "padding-top: 85px;"
        : "padding-top: 81px;"
      : "",
    hideTabs.value && !layout.value ? "padding-top: 48px;" : "",
    !hideTabs.value && !layout.value
      ? tagsStyle.value == "chrome"
        ? "padding-top: 85px;"
        : "padding-top: 81px;"
      : "",
    props.fixedHeader
      ? ""
      : `padding-top: 0;${
          hideTabs.value
            ? "min-height: calc(100vh - 48px);"
            : "min-height: calc(100vh - 86px);"
        }`
  ];
});

const transitionMain = defineComponent({
  props: {
    route: {
      type: undefined,
      required: true
    }
  },
  render() {
    const transitionName =
      transitions.value(this.route)?.name || "fade-transform";
    const enterTransition = transitions.value(this.route)?.enterTransition;
    const leaveTransition = transitions.value(this.route)?.leaveTransition;
    return h(
      Transition,
      {
        name: enterTransition ? "pure-classes-transition" : transitionName,
        enterActiveClass: enterTransition
          ? `animate__animated ${enterTransition}`
          : undefined,
        leaveActiveClass: leaveTransition
          ? `animate__animated ${leaveTransition}`
          : undefined,
        mode: "out-in",
        appear: true
      },
      {
        default: () => [this.$slots.default()]
      }
    );
  }
});
</script>

<template>
  <section
    :class="[fixedHeader ? 'app-main' : 'app-main-nofixed-header']"
    :style="getSectionStyle"
  >
    <router-view>
      <template #default="{ Component, route }">
        <LayFrame :currComp="Component" :currRoute="route">
          <template #default="{ Comp, fullPath, frameInfo }">
            <el-scrollbar
              v-if="fixedHeader"
              :wrap-style="{
                display: 'flex',
                'flex-wrap': 'wrap',
                'max-width': getMainWidth,
                margin: '0 auto',
                transition: 'all 300ms cubic-bezier(0.4, 0, 0.2, 1)'
              }"
              :view-style="{
                display: 'flex',
                flex: 'auto',
                height: '100%',
                'min-height': '0',
                overflow: 'hidden',
                'flex-direction': 'column'
              }"
            >
              <el-backtop
                :title="t('buttons.pureBackTop')"
                target=".app-main .el-scrollbar__wrap"
              >
                <BackTopIcon />
              </el-backtop>
              <div class="grow viewport-page-host">
                <transitionMain :route="route">
                  <keep-alive
                    v-if="isKeepAlive"
                    :include="usePermissionStoreHook().cachePageList"
                  >
                    <component
                      :is="Comp"
                      :key="route.meta.reuseOnQueryChange ? route.path : fullPath"
                      :frameInfo="frameInfo"
                      class="main-content"
                    />
                  </keep-alive>
                  <component
                    :is="Comp"
                    v-else
                    :key="route.meta.reuseOnQueryChange ? route.path : fullPath"
                    :frameInfo="frameInfo"
                    class="main-content"
                  />
                </transitionMain>
              </div>
              <LayFooter v-if="!hideFooter" />
            </el-scrollbar>
            <div v-else class="grow viewport-page-host">
              <transitionMain :route="route">
                <keep-alive
                  v-if="isKeepAlive"
                  :include="usePermissionStoreHook().cachePageList"
                >
                  <component
                    :is="Comp"
                    :key="route.meta.reuseOnQueryChange ? route.path : fullPath"
                    :frameInfo="frameInfo"
                    class="main-content"
                  />
                </keep-alive>
                <component
                  :is="Comp"
                  v-else
                  :key="route.meta.reuseOnQueryChange ? route.path : fullPath"
                  :frameInfo="frameInfo"
                  class="main-content"
                />
              </transitionMain>
            </div>
          </template>
        </LayFrame>
      </template>
    </router-view>

    <!-- 页脚 -->
    <LayFooter v-if="!hideFooter && !fixedHeader" />
  </section>
</template>

<style scoped>
.app-main {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow-x: hidden;
}

.app-main-nofixed-header {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.viewport-page-host :deep(.main-content) {
  /* 路由可能经多层及 Fragment 组件传递，深层匹配实际内容节点，不依赖壳的 scopeId 透传。
     所有路由统一使用全局外边距，默认 8px；与公共模板高度计算共享同一变量。 */
  margin: var(--app-page-gap, 8px);
}

/* border-box 将模板头部预留纳入视口高度；保留内容区内部滚动，长表单不会被裁切。 */
.app-main { box-sizing:border-box; height:100dvh; min-height:0; overflow:hidden; }
.app-main > :deep(.el-scrollbar) { height:100%; min-height:0; }
.viewport-page-host { min-height:0; overflow:auto; flex:1; }
.app-main-nofixed-header { min-height:0; overflow:hidden; }
.main-content { min-height:0; }
.app-main > :deep(.el-scrollbar > .el-scrollbar__wrap) { height:100%; }
/* 采用自适应工作台的页面独占剩余高度；普通页面仍在 host 内安全滚动。 */
.viewport-page-host:has(> :is(.dictionary-sample,.business-workspace,.management-page,.page-template)) { display:flex; flex-direction:column; overflow:hidden; }
.viewport-page-host > :is(.dictionary-sample,.business-workspace,.management-page,.page-template) { flex:1; min-height:0; }
/* 长详情使用壳内唯一滚动容器；本地 scoped 规则须覆盖列表宿主 overflow:hidden，
   否则公共模板的低优先级覆盖无效，底部数据和分页仍会被固定视口裁切。 */
.viewport-page-host:has(.master-data-detail) {
  display:block;
  overflow-y:auto;
  overflow-x:hidden;
}
.viewport-page-host:has(.master-data-detail) > .main-content {
  /* 标题压缩后短详情填满内容区，底部只保留全局页面边距；内容超高仍自然滚动。 */
  display:flex;
  flex-direction:column;
  height:auto;
  min-height:calc(100% - 2 * var(--app-page-gap,8px));
  flex:none;
  overflow:visible;
}
</style>
