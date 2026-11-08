<script setup lang="ts">
import Administration from "@appnexus/tenant/management";
import { administrationApi } from "@/api/administration";
import { requestErrorMessage } from "@/utils/http";
</script>

<template>
  <Administration
    section="users"
    :api="administrationApi"
    :error-text="requestErrorMessage"
  />
</template>
