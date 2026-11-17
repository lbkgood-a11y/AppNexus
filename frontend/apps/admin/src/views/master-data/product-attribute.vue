<script setup lang="ts">
import CatalogManagement from "@appnexus/master-data/product-catalog";
import {productCatalogApi} from "@/api/productCatalog";
import {requestErrorMessage} from "@/utils/http";
/** 商品配置应用装配，领域表单通过公开入口复用，HTTP与CSRF由壳层提供。 */
</script>
<template><CatalogManagement kind="attribute" :api="productCatalogApi" :error-text="requestErrorMessage"/></template>
