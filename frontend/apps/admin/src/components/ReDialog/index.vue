<script setup lang="ts">
import {
  type EventType,
  type ButtonProps,
  type DialogOptions,
  closeDialog,
  dialogStore
} from "./index";
import { ref, computed } from "vue";
import { useI18n } from "vue-i18n";
import { isFunction } from "@pureadmin/utils";
import { AppIcon } from "@appnexus/icons";

defineOptions({
  name: "ReDialog"
});

const sureBtnMap = ref({});
const fullscreen = ref(false);
const { t } = useI18n();

const footerButtons = computed(() => {
  return (options: DialogOptions) => {
    return options?.footerButtons?.length > 0
      ? options.footerButtons
      : ([
          {
            label: "取消",
            text: true,
            bg: true,
            btnClick: ({ dialog: { options, index } }) => {
              const done = () =>
                closeDialog(options, index, { command: "cancel" });
              if (options?.beforeCancel && isFunction(options?.beforeCancel)) {
                options.beforeCancel(done, { options, index });
              } else {
                done();
              }
            }
          },
          {
            label: "确定",
            type: "primary",
            text: true,
            bg: true,
            popconfirm: options?.popconfirm,
            btnClick: ({ dialog: { options, index } }) => {
              if (options?.sureBtnLoading) {
                sureBtnMap.value[index] = Object.assign(
                  {},
                  sureBtnMap.value[index],
                  {
                    loading: true
                  }
                );
              }
              const closeLoading = () => {
                if (options?.sureBtnLoading) {
                  sureBtnMap.value[index].loading = false;
                }
              };
              const done = () => {
                closeLoading();
                closeDialog(options, index, { command: "sure" });
              };
              if (options?.beforeSure && isFunction(options?.beforeSure)) {
                options.beforeSure(done, { options, index, closeLoading });
              } else {
                done();
              }
            }
          }
        ] as Array<ButtonProps>);
  };
});

function eventsCallBack(
  event: EventType,
  options: DialogOptions,
  index: number,
  isClickFullScreen = false
) {
  if (!isClickFullScreen) fullscreen.value = options?.fullscreen ?? false;
  if (options?.[event] && isFunction(options?.[event])) {
    return options?.[event]({ options, index });
  }
}

function handleClose(
  options: DialogOptions,
  index: number,
  args = { command: "close" }
) {
  closeDialog(options, index, args);
  eventsCallBack("close", options, index);
}
</script>

<template>
  <el-dialog
    v-for="(options, index) in dialogStore"
    :key="index"
    v-bind="options"
    v-model="options.visible"
    :class="['pure-dialog', 'app-managed-dialog', { 'is-dialog-maximized': fullscreen || options?.fullscreen, 'is-dialog-resizable': !options?.fullscreen }]"
    :show-close="false"
    :fullscreen="fullscreen ? true : options?.fullscreen ? true : false"
    @closed="handleClose(options, index)"
    @opened="eventsCallBack('open', options, index)"
    @openAutoFocus="eventsCallBack('openAutoFocus', options, index)"
    @closeAutoFocus="eventsCallBack('closeAutoFocus', options, index)"
  >
    <!-- header -->
    <template #header="{ close, titleId, titleClass }">
      <div class="app-dialog-header">
        <component :is="options?.headerRenderer({ close, titleId, titleClass })" v-if="options?.headerRenderer" />
        <span v-else :id="titleId" :class="['app-dialog-title', titleClass]">{{ options?.title }}</span>
        <div class="app-dialog-actions">
          <el-tooltip v-if="!options?.fullscreen" :content="t(fullscreen ? 'management.restoreDialog' : 'management.maximizeDialog')">
            <el-button text :aria-label="t(fullscreen ? 'management.restoreDialog' : 'management.maximizeDialog')" @click="
              () => {
                fullscreen = !fullscreen;
                eventsCallBack('fullscreenCallBack', { ...options, fullscreen }, index, true);
              }
            "><AppIcon :name="fullscreen ? 'fullscreenExit' : 'fullscreen'" :size="18" /></el-button>
          </el-tooltip>
          <el-tooltip :content="t('management.closeDialog')"><el-button text :aria-label="t('management.closeDialog')" @click="close"><AppIcon name="close" :size="20" /></el-button></el-tooltip>
        </div>
      </div>
    </template>
    <component
      v-bind="options?.props"
      :is="options.contentRenderer({ options, index })"
      @close="args => handleClose(options, index, args)"
    />
    <!-- footer -->
    <template v-if="!options?.hideFooter" #footer>
      <template v-if="options?.footerRenderer">
        <component :is="options?.footerRenderer({ options, index })" />
      </template>
      <span v-else>
        <template v-for="(btn, key) in footerButtons(options)" :key="key">
          <el-popconfirm
            v-if="btn.popconfirm"
            v-bind="btn.popconfirm"
            @confirm="
              btn.btnClick({
                dialog: { options, index },
                button: { btn, index: key }
              })
            "
          >
            <template #reference>
              <el-button v-bind="btn">{{ btn?.label }}</el-button>
            </template>
          </el-popconfirm>
          <el-button
            v-else
            v-bind="btn"
            :loading="key === 1 && sureBtnMap[index]?.loading"
            @click="
              btn.btnClick({
                dialog: { options, index },
                button: { btn, index: key }
              })
            "
          >
            {{ btn?.label }}
          </el-button>
        </template>
      </span>
    </template>
  </el-dialog>
</template>
