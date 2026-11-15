import {readFileSync} from 'node:fs';
import {parseDocument} from 'yaml';
import {expect,it} from 'vitest';
/** 浏览器首次加载所有语言资源；类型检查无法发现 YAML 语法和重复键。 */
it('loads both locale catalogs without syntax errors or duplicate keys',()=>{
 for(const language of ['zh-CN','en']){
  const document=parseDocument(readFileSync(new URL(`../locales/${language}.yaml`,import.meta.url),'utf8'),{uniqueKeys:true});
  expect(document.errors.map(error=>error.message)).toEqual([]);
  const values=document.toJS();
  expect(values.operationDiagnostics.reasons.PAGE_PERMISSION_MISSING).toBeTruthy();
  expect(values.printing.preflightReasons.PRINT_DEVICE_OFFLINE).toBeTruthy();
 }
});
