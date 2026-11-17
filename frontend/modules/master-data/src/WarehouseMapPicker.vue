<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ManagedDialog, FloatingError, setErrorFeedback } from "@appnexus/ui";
import { loadAmap, type AmapInstance, type MapPoint, type PlaceResult } from "./amap";

/** 现有组件无地图选点能力；组合统一弹窗与高德画布，仅确认时向仓库草稿输出坐标。 */
const props = defineProps<{ longitude: string; latitude: string; configuration:()=>Promise<{key:string;serviceHost:string}> }>();
const emit = defineEmits<{ select: [longitude: string, latitude: string]; close: [] }>();
const { t } = useI18n();
const container = ref<HTMLElement>(), loading = ref(false), error = ref("");
const point = ref<MapPoint>();
const keyword=ref(""),searching=ref(false),results=ref<PlaceResult[]>([]),searchEmpty=ref(false);
let searchSequence=0;
let searcher:InstanceType<Awaited<ReturnType<typeof loadAmap>>["PlaceSearch"]>|undefined;
let selectPoint:((point:MapPoint)=>void)|undefined;
/** 搜索只定位候选地点，不保存仓库；旧查询与关闭后的回调不能污染新弹窗。 */
function search(){
 const text=keyword.value.trim();if(!text||!searcher||searching.value)return;
 const sequence=++searchSequence,current=generation;error.value="";searching.value=true;searchEmpty.value=false;results.value=[];
 const timeout=window.setTimeout(()=>{if(current===generation&&sequence===searchSequence){searching.value=false;void setErrorFeedback(error,t("warehouseMap.searchFailed"));}},15000);
 searcher.search(text,(status,result)=>{
  window.clearTimeout(timeout);if(current!==generation||sequence!==searchSequence||!searching.value)return;
  searching.value=false;
  if(status==="complete"){results.value=(result.poiList?.pois??[]).filter(row=>row.location);searchEmpty.value=!results.value.length;if(results.value[0]?.location){const first=results.value[0];selectPoint?.(first.location!);map?.setZoomAndCenter(12,[first.location!.lng,first.location!.lat]);}}
  else if(status==="no_data")searchEmpty.value=true;
  else void setErrorFeedback(error,t("warehouseMap.searchFailed"));
 });
}
/** 选中候选后缩放至街道层级并放置标记，仍可点击地图微调。 */
function locate(row:PlaceResult){if(!row.location||!map)return;selectPoint?.(row.location);map.setZoomAndCenter(16,[row.location.lng,row.location.lat]);results.value=[];keyword.value=row.name;}
let map: AmapInstance | undefined, generation = 0;
/** 关闭或重试使未完成的 SDK 加载失效，避免在已销毁的容器上创建地图。 */
function cleanup() { generation++;searchSequence++;searching.value=false;results.value=[];searcher=undefined;selectPoint=undefined; map?.destroy(); map = undefined; }
/** 有效历史坐标作为初始标记；空值不得被 Number 转换成 0，也不自动定位用户。 */
async function initialize() {
  cleanup();
  const current = generation;
  point.value = undefined; error.value = ""; loading.value = true;
  try {
    const configuration = await props.configuration();
    if (current !== generation) return;
    const sdk = await loadAmap(configuration);
    if (current !== generation || !container.value) return;
    const lng = Number(props.longitude), lat = Number(props.latitude);
    const existing = props.longitude.trim() && props.latitude.trim() && Number.isFinite(lng) && Number.isFinite(lat) && Math.abs(lng) <= 180 && Math.abs(lat) <= 90;
    map = new sdk.Map(container.value, { center: existing ? [lng, lat] : [104.195397, 35.86166], zoom: existing ? 16 : 4, resizeEnable: true });
    let marker: InstanceType<typeof sdk.Marker> | undefined;
    function mark(selected: MapPoint) {
      if (!Number.isFinite(selected.lng) || !Number.isFinite(selected.lat) || Math.abs(selected.lng) > 180 || Math.abs(selected.lat) > 90) return;
      point.value = { lng: selected.lng, lat: selected.lat };
      if (marker) marker.setPosition([selected.lng, selected.lat]);
      else marker = new sdk.Marker({ map: map!, position: [selected.lng, selected.lat] });
    }
    selectPoint=mark;searcher=new sdk.PlaceSearch({pageSize:5});
    if (existing) mark({ lng, lat });
    map.on("click", event => mark(event.lnglat));
  } catch (cause) {
    if (current === generation) {
      const code=(cause as {response?:{data?:{code?:string}}}).response?.data?.code;
      await setErrorFeedback(error,t(code==="AMAP_PROXY_NOT_CONFIGURED"?"warehouseMap.proxyMissing":code==="AMAP_WEB_KEY_NOT_CONFIGURED"||(cause as Error).message==="configuration"?"warehouseMap.configuration":"warehouseMap.unavailable"));
    }
  } finally { if (current === generation) loading.value = false; }
}
/** 坐标成对输出十进制字符串，六位小数符合仓库现有前后端校验；不直接写入服务端。 */
function confirm() { if (point.value && !loading.value && !error.value) emit("select", point.value.lng.toFixed(6), point.value.lat.toFixed(6)); }
onBeforeUnmount(cleanup);
</script>
<template>
  <ManagedDialog :model-value="true" :title="t('warehouseMap.title')" width="min(960px, calc(100vw - 32px))" @update:model-value="emit('close')" @opened="initialize" @closed="cleanup">
    <FloatingError :title="error"><el-button @click="initialize">{{ t('warehouseMap.retry') }}</el-button></FloatingError>
    <p>{{ t('warehouseMap.hint') }}</p>
    <div class="map-search"><el-input v-model="keyword" :aria-label="t('warehouseMap.searchPlaceholder')" :placeholder="t('warehouseMap.searchPlaceholder')" maxlength="100" clearable :disabled="loading" @keyup.enter="search" /><el-button :loading="searching" :disabled="loading||!keyword.trim()" @click="search">{{t('warehouseMap.search')}}</el-button></div>
    <div v-if="results.length" class="map-results"><el-button v-for="row in results" :key="row.id" text @click="locate(row)">{{row.name}} · {{row.address}}</el-button></div>
    <el-empty v-if="searchEmpty" :description="t('warehouseMap.searchEmpty')" :image-size="40" />
    <div ref="container" v-loading="loading" class="warehouse-map" :aria-label="t('warehouseMap.title')" />
    <p aria-live="polite">{{ point ? `${t('warehouseProfile.fields.longitude')}: ${point.lng.toFixed(6)} / ${t('warehouseProfile.fields.latitude')}: ${point.lat.toFixed(6)}` : t('warehouseMap.empty') }}</p>
    <template #footer><el-button @click="emit('close')">{{ t('masterData.cancel') }}</el-button><el-button type="primary" :disabled="!point || loading || !!error" @click="confirm">{{ t('warehouseMap.confirm') }}</el-button></template>
  </ManagedDialog>
</template>
<style scoped>
/* 地图本身沿用供应商底图，外框与尺寸复用主题并适配窄屏；不覆盖全站视觉。 */
.map-search{display:flex;gap:8px;margin:8px 0;}.map-results{display:flex;flex-direction:column;align-items:stretch;max-height:160px;overflow:auto;}.map-results .el-button{margin:0;justify-content:flex-start;white-space:normal;height:auto;min-height:36px;text-align:left;}
.warehouse-map { height: min(52vh, 480px); min-height: 240px; width: 100%; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); }
</style>
