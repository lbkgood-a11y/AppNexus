<script setup lang="ts">
import ProductSkuManagement from "@appnexus/master-data/product-sku";
import {productV1Api} from "@/api/productV1";
import {requestErrorMessage} from "@/utils/http";
/** 商品明细正式装配入口；复用Cookie/CSRF适配，不持久化业务身份或Token。 */
</script>
<template><ProductSkuManagement :api="productV1Api" :error-text="requestErrorMessage"/></template>
