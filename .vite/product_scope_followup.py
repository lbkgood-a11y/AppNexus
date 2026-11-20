from pathlib import Path
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product')
for file,marker,call in [('ProductImageService.java','private void product(VerifiedTenantContext c,String id,boolean write,Long version){','products.authorizeRecord(c,id,write?"update":"read",write);'),('ProductStructureService.java','private Map<String,Object> parent(VerifiedTenantContext c,String id,boolean lock,Long version){','products.authorizeRecord(c,id,lock?"manage_structure":"read",lock);')]:
 p=root/file;s=p.read_text(encoding='utf-8');assert marker in s;s=s.replace(marker,marker+call);p.write_text(s,encoding='utf-8')
# 保留每个主档写入的完整事务和明确归属，移动必须同时经过原范围及目标范围。
p=root/'ProductApprovalService.java';s=p.read_text(encoding='utf-8').replace('return tx.execute(s->{var lock=','return tx.execute(s->{products.authorizeRecord(c,product,"read",true);var lock=');p.write_text(s,encoding='utf-8')
p=root/'ProductV1Service.java';s=p.read_text(encoding='utf-8')
s=s.replace('require(c,"migrate_structure");require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);authorizeRecord(c,id,"migrate_structure",false);\n  return write','require(c,"migrate_structure");require(c,"read");uuid(id);authorizeRecord(c,id,"read",false);authorizeRecord(c,id,"migrate_structure",false);\n  return write')
p.write_text(s,encoding='utf-8')
# UI使用既有表单组件和真实操作候选，不自动选择第一个组织。
p=Path('frontend/modules/master-data/src/product/product.ts');s=p.read_text(encoding='utf-8').replace('export interface ProductRow {','export interface ProductRow {organizationId?:string|null;').replace('export interface ProductDraft {','export interface ProductDraft {organizationId:string|null;').replace('export interface ProductUpdate {','export interface ProductUpdate {organizationId?:string|null;').replace(' operations():Promise<string[]>;',' /** 当前操作可写归属组织；来源为服务端统一权限范围，不依赖组织管理权限。 */\n organizations(action:"create"|"update"):Promise<Array<{id:string;name:string}>>;\n operations():Promise<string[]>;');p.write_text(s,encoding='utf-8')
p=Path('frontend/apps/admin/src/api/productV1.ts');s=p.read_text(encoding='utf-8').replace('export const productV1Api:ProductApi={','export const productV1Api:ProductApi={\n organizations:action=>http.request("get",base+"/organizations/"+action),');p.write_text(s,encoding='utf-8')
p=Path('frontend/modules/master-data/src/product/ProductEditor.vue');s=p.read_text(encoding='utf-8').replace('const draft=reactive<ProductDraft>({','const organizationOptions=ref<Array<{id:string;name:string}>>([]);\nconst draft=reactive<ProductDraft>({organizationId:null,').replace('Object.assign(draft,{code:row.code','Object.assign(draft,{organizationId:row.organizationId??null,code:row.code').replace('{name:draft.name,barcode:draft.barcode','{organizationId:draft.organizationId,name:draft.name,barcode:draft.barcode')
s=s.replace('operations.value=await props.api.operations();if(id.value)','operations.value=await props.api.operations();if(operations.value.includes(id.value?"Product.update":"Product.create"))organizationOptions.value=await props.api.organizations(id.value?"update":"create");if(id.value)')
marker='<el-form v-else ref="form" class="business-border-label-form" :model="draft" label-position="top" @submit.prevent="migrationMode?previewMigration():save()">'
assert marker in s
s=s.replace(marker,marker+'''
<el-form-item :label="t('productV1.organizationId')" prop="organizationId" :rules="!id?[{required:true,message:t('productV1.organizationRequired'),trigger:'change'}]:[]">
 <el-select v-model="draft.organizationId" :disabled="!editing||migrationMode" filterable :placeholder="t('productV1.organizationRequired')">
  <el-option v-for="organization in organizationOptions" :key="organization.id" :value="organization.id" :label="organization.name"/>
 </el-select>
 <span v-if="id&&!draft.organizationId" class="text-[var(--el-text-color-secondary)]">{{t('productV1.organizationUnknown')}}</span>
</el-form-item>''');p.write_text(s,encoding='utf-8')
for lang,text in [('zh-CN',{'organizationId':'归属组织','organizationRequired':'请选择有权的归属组织','organizationUnknown':'历史归属未知；请按实际业务归属维护'}),('en',{'organizationId':'Owning organization','organizationRequired':'Select an authorized organization','organizationUnknown':'Historical ownership is unknown; assign the actual business organization'})]:
 p=Path('frontend/apps/admin/locales/'+lang+'.yaml');s=p.read_text(encoding='utf-8');marker='productV1:\n';assert marker in s;s=s.replace(marker,marker+''.join('  '+k+': '+v+'\n' for k,v in text.items()),1);p.write_text(s,encoding='utf-8')
