<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';
import { Probe } from './probe';
const host=ref<HTMLElement>();
let probe:Probe;
onMounted(async()=>{probe=new Probe(host.value!);await probe.init();Object.assign(window,{probe});});
onBeforeUnmount(()=>probe?.destroy());
</script>
<template><header><strong>DevHub BPMN 技术验证</strong><el-button @click="probe.theme(false)">浅色</el-button><el-button @click="probe.theme(true)">深色</el-button><span>隔离实验 · 不连接业务接口</span></header><main ref="host" /></template>
<style>body{margin:0;background:var(--el-bg-color);color:var(--el-text-color-primary);font-family:sans-serif}header{display:flex;align-items:center;gap:12px;padding:12px}main{height:calc(100dvh - 64px);background:var(--el-fill-color-light)}.dark .djs-container{filter:invert(.88) hue-rotate(180deg)}@media(max-width:600px){header{flex-wrap:wrap}main{height:75dvh}}</style>
