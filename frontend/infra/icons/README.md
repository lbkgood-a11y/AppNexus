# 平台公共图标

公共入口为 @appnexus/icons，统一使用 Remix Icon 线性图标。由现有 unplugin-icons 构建期编译为本地 SVG，不在浏览器请求远程图标。

```vue
<script setup lang="ts">
import { AppIcon } from "@appnexus/icons";
</script>
<template>
  <AppIcon name="user" :size="18" />
  <AppIcon name="shield" label="安全认证" />
</template>
```

name 使用 PlatformIconName 定义的语义名；size 为像素正数，默认 20。label 为空时图标视为装饰，自动设置 aria-hidden；图标按钮应给按钮自身提供无障碍名称。

新增图标在 src/index.ts 的 platformIcons 中注册，统一线性风格和语义命名。包不依赖 admin 模板内部组件。消费应用须启用 unplugin-icons/vue3，本仓库 admin 已配置。

本包沿用 vue-pure-admin 已采用的 Iconify/Remix Icon 风格，只提供平台语义入口；不得另行建立与模板不一致的图标体系。优先使用已登记图标，新增时检查模板已有对应图标及线条风格。
