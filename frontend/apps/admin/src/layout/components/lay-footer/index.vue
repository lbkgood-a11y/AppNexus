<script setup lang="ts">
import { getConfig } from "@/config";

const TITLE = getConfig("Title");
</script>

<template>
  <footer
    class="layout-footer text-[rgba(0,0,0,0.6)] dark:text-[rgba(220,220,242,0.8)]"
  >
    Copyright © 2020-present
    <a
      class="hover:text-primary!"
      href="https://github.com/pure-admin"
      target="_blank"
    >
      &nbsp;{{ TITLE }}
    </a>
  </footer>
</template>

<style lang="scss" scoped>
.layout-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  /* 版权栏固定为 24px 紧凑高度，不参与内容区伸缩；文字使用全局辅助色。 */
  box-sizing: border-box;
  flex: 0 0 24px;
  height: 24px;
  padding: 0;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}
</style>
