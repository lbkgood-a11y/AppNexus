<script setup lang="ts">
import ProductManagement from "@appnexus/master-data/product";
import {productV1Api} from "@/api/productV1";
import {requestErrorMessage} from "@/utils/http";
/** V1隔离验证入口；旧商品正式写菜单未切换，存量迁移对账后再替换。 */
</script>
<template><ProductManagement :api="productV1Api" :error-text="requestErrorMessage"/></template>
