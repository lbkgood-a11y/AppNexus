<script setup lang="ts">
import DictionaryManagement from "@appnexus/configuration/dictionary-management";
import {dictionaryManagementApi} from "@/api/dictionaryManagement";
import {requestErrorMessage} from "@/utils/http";
</script>
<template><DictionaryManagement :api="dictionaryManagementApi" :error-text="requestErrorMessage" /></template>
