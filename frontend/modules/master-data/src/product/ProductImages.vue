<script setup lang="ts">
import {onMounted,ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessageBox,type UploadRequestOptions} from "element-plus";
import {FloatingError,setErrorFeedback} from "@appnexus/ui";
import type {ProductApi} from "./product";
/** 组合现有Upload/Image组件；文件直接上传真实API，不使用模拟进度、公共文件URL或浏览器持久存储。 */
const props=defineProps<{id:string;version:number;readonly:boolean;api:ProductApi;errorText:(e:unknown)=>string}>();
const emit=defineEmits<{changed:[]}>();const {t}=useI18n();const rows=ref<Array<{id:string;width:number;height:number}>>([]),busy=ref(false),error=ref("");
async function load(){try{rows.value=await props.api.images(props.id);}catch(e){await setErrorFeedback(error,props.errorText(e));}}
/** 前端仅做尺寸体验校验；真实格式、像素上限及CAS由服务端校验，失败不重放。 */
async function upload(options:UploadRequestOptions){if(busy.value)return;busy.value=true;try{if(options.file.size>5242880||!["image/png","image/jpeg"].includes(options.file.type))throw new Error(t("productImages.limit"));await props.api.uploadImage(props.id,props.version,options.file);await load();emit("changed");}catch(e){await setErrorFeedback(error,e instanceof Error&&!('response' in e)?e.message:props.errorText(e));}finally{busy.value=false;}}
async function detach(id:string){try{await ElMessageBox.confirm(t("productImages.detachHint"),t("productImages.title"),{confirmButtonText:t("productImages.detach"),cancelButtonText:t("masterData.cancel")});}catch{return;}busy.value=true;try{await props.api.detachImage(props.id,id,props.version);await load();emit("changed");}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
onMounted(load);
</script>
<template><section><h3>{{t('productImages.title')}}</h3><FloatingError :title="error"/><el-upload v-if="!readonly" v-operation="'Product.uploadImage'" action="#" :http-request="upload" :show-file-list="false" accept="image/png,image/jpeg" :disabled="busy||rows.length>=10"><span>{{t('productImages.upload')}}</span><template #tip><p>{{t('productImages.limit')}}</p></template></el-upload><el-empty v-if="!rows.length" :description="t('productImages.empty')" :image-size="48"/><div class="product-images"><div v-for="image in rows" :key="image.id"><el-image :src="api.imageUrl(id,image.id)" fit="contain" :alt="t('productImages.title')" :preview-src-list="rows.map(r=>api.imageUrl(id,r.id))" preview-teleported/><span>{{image.width}} × {{image.height}}</span><el-button v-if="!readonly" v-operation="'Product.detachImage'" link type="danger" :disabled="busy" @click="detach(image.id)">{{t('productImages.detach')}}</el-button></div></div></section></template>
<style scoped>
/* 图片只控制自身缩略图尺寸，不改变应用壳或主题；预览复用Element Plus。 */
.product-images{display:flex;flex-wrap:wrap;gap:16px}.product-images>div{display:flex;flex-direction:column;gap:8px;max-width:100%}.el-image{width:160px;height:120px;background:var(--el-fill-color-light)}
</style>
