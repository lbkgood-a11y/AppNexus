/** 按钮操作引用门禁：从 Vue 模板 AST 核对显式操作身份；不把统计数量当成全业务覆盖证明。 */
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require=createRequire(path.resolve('frontend/apps/admin/package.json'));
const {parse}=require('@vue/compiler-sfc');
const {baseParse}=require('@vue/compiler-dom');
const files=[];
function walk(dir){for(const item of fs.readdirSync(dir,{withFileTypes:true})){if(['node_modules','dist','.git'].includes(item.name))continue;const f=path.join(dir,item.name);if(item.isDirectory())walk(f);else if(f.endsWith('.vue'))files.push(f);}}
walk('frontend');
// 例外与动态声明使用仓库相对 POSIX 路径；Windows 分隔符只作路径规范化，不能改变授权覆盖结果。
for(let index=0;index<files.length;index++)files[index]=files[index].replaceAll('\\','/');
const rows=[];
for(const file of files){const source=fs.readFileSync(file,'utf8');const template=parse(source).descriptor.template;if(!template)continue;const ast=baseParse(template.content);function visit(node, submit, confirm){if(node.type===1){const props=node.props.filter(p=>p.type===7);const directive=name=>props.find(p=>p.name===name)?.exp?.content;submit=props.find(p=>p.name==='on'&&p.arg?.content==='submit')?.exp?.content??submit;confirm=props.find(p=>p.name==='on'&&p.arg?.content==='confirm')?.exp?.content??confirm;const click=props.find(p=>p.name==='on'&&p.arg?.content==='click')?.exp?.content;if(['el-button','el-switch','el-checkbox','el-link','el-dropdown-item'].includes(node.tag)){rows.push({file,line:template.loc.start.line+node.loc.start.line-1,tag:node.tag,text:template.content.slice(node.loc.start.offset,node.loc.end.offset).replace(/<[^>]*>/g,' ').replace(/\s+/g,' ').trim(),operation:directive('operation')??null,condition:directive('if')??null,disabled:props.find(p=>p.name==='bind'&&p.arg?.content==='disabled')?.exp?.content??null,click:click??null,submit:node.props.some(p=>p.type===6&&p.name==='native-type'&&p.value?.content==='submit')?submit??null:null,confirm:confirm??null});}}for(const child of node.children??[])visit(child,submit,confirm);}visit(ast);}
const ids=new Set();
function java(dir){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){if(entry.name==='target')continue;const f=path.join(dir,entry.name);if(entry.isDirectory())java(f);else if(f.endsWith('.java'))for(const match of fs.readFileSync(f,'utf8').matchAll(/OperationBinding\(id="([^"]+)"/g))ids.add(match[1]);}}
java('backend/services');
const exclusionPath='frontend/scripts/operation-ui-exclusions.json';
const key=row=>JSON.stringify([row.file,row.tag,row.click,row.submit,row.confirm,row.text]);
const unbound=rows.filter(row=>!row.operation&&(row.click||row.submit||row.confirm));
if(process.argv.includes('--write-exclusions'))throw new Error('禁止批量自动生成授权例外；请逐项审查副作用后手工维护例外及中文原因');

const exclusions=JSON.parse(fs.readFileSync(exclusionPath,'utf8'));
const excluded=new Set(exclusions.filter(item=>item.reason).map(item=>item.key));
const problems=unbound.filter(row=>!excluded.has(key(row))).map(row=>`${row.file}:${row.line} 新增交互未绑定操作或明确声明界面例外`);

const dynamic=JSON.parse(fs.readFileSync('frontend/scripts/operation-dynamic-bindings.json','utf8'));
const roleIds={create:['PlatformRole.create','TenantRole.create'],update:['PlatformRole.update','TenantRole.update'],enable:['PlatformRole.setStatus','TenantRole.setStatus'],delete:['PlatformRole.delete','TenantRole.delete'],grant:['RoleAuthorization.replacePlatform','RoleAuthorization.replaceTenant']};
for(const row of rows.filter(row=>row.operation)){
 if(!/^'[^']+'$/.test(row.operation)){
  const declaration=dynamic.find(item=>item.file===row.file&&item.expression===row.operation);
  if(!declaration||!declaration.reason||!declaration.operations.length){problems.push(`${row.file}:${row.line} 动态操作表达式未逐分支登记`);continue;}
  for(const id of declaration.operations){if(id.startsWith('@role:')){if(!(roleIds[id.slice(6)]??[]).every(value=>ids.has(value)))problems.push(`角色操作未登记：${id}`);}else if(!ids.has(id))problems.push(`${row.file}:${row.line} 动态分支未登记操作 ${id}`);}
  for(const source of declaration.sources)if(!fs.existsSync(source))problems.push(`动态绑定实现文件不存在：${source}`);
 }
}
for(const row of rows.filter(row=>row.operation)){
 for(const match of row.operation.matchAll(/'([^']+)'/g))for(const id of match[1].split('|')){
  if(id.startsWith('@role:')){if(!roleIds[id.slice(6)])problems.push(`未知角色操作别名：${id}`);continue;}
  if(id.includes(':'))problems.push(`${row.file}:${row.line} 仍引用权限码 ${id}`);
  else if(id.includes('.')&&!ids.has(id))problems.push(`${row.file}:${row.line} 未登记操作 ${id}`);
 }
}
if(problems.length){console.error(problems.join('\n'));process.exitCode=1;}
else console.log(`已核对 ${rows.filter(row=>row.operation).length} 个显式操作控件，目录包含 ${ids.size} 个接口操作。另有 ${unbound.length} 个非授权交互已逐项登记例外。`);

