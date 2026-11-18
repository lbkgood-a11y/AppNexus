<script setup lang="ts">
import ProductManagement from "@appnexus/master-data/product";
import {productV1Api} from "@/api/productV1";
import {requestErrorMessage} from "@/utils/http";
/** 正式商品入口；Flyway切换迁移先保留并校验全部旧商品，未对账不能启动。 */
</script>
<template><ProductManagement :api="productV1Api" :error-text="requestErrorMessage"/></template>
