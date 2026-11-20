import {describe,it,expect,vi} from "vitest";
import {useDataRecordSearch} from "./dataRecordSearch";
import type {DataRecordSearch} from "./index";

describe('授权候选分页与失效边界',()=>{
 it('翻页去重并保留已读取名称，候选为空不修改外部规则',async()=>{
  const source:DataRecordSearch={resources:['partner'],search:vi.fn().mockResolvedValueOnce({items:[{id:'a',name:'A'}],total:21}).mockResolvedValueOnce({items:[{id:'a',name:'A'},{id:'b',name:'B'}],total:21})};
  const state=useDataRecordSearch(vi.fn());await state.search(source,'partner','',1);await state.search(source,'partner','',2);
  expect(source.search).toHaveBeenLastCalledWith('partner','',2);expect(state.page.value).toBe(2);expect(state.items.value).toHaveLength(2);expect(state.labels.value.b).toBe('B');
 });
 it('旧请求不能覆盖新关键词或已重置的上下文',async()=>{
  let resolve!:(value:{items:{id:string;name:string}[];total:number})=>void;
  const source:DataRecordSearch={resources:['partner'],search:vi.fn().mockImplementationOnce(()=>new Promise(r=>{resolve=r})).mockResolvedValueOnce({items:[{id:'new',name:'新'}],total:1})};
  const state=useDataRecordSearch(vi.fn());const old=state.search(source,'partner','old');await state.search(source,'partner','new');resolve({items:[{id:'old',name:'旧'}],total:1});await old;
  expect(state.items.value[0].id).toBe('new');state.reset();expect(state.labels.value).toEqual({});expect(state.loading.value).toBe(false);
 });
 it('403和数据库失败清理候选且只上报一次，禁止未知资源查询',async()=>{
  const error=Object.assign(new Error('Forbidden'),{status:403}),notify=vi.fn().mockResolvedValue(undefined);
  const source:DataRecordSearch={resources:['partner'],search:vi.fn().mockRejectedValue(error)};const state=useDataRecordSearch(notify);
  await state.search(source,'other','');expect(source.search).not.toHaveBeenCalled();
  await state.search(source,'partner','');expect(notify).toHaveBeenCalledExactlyOnceWith(error);expect(state.items.value).toEqual([]);expect(state.loading.value).toBe(false);expect(state.total.value).toBe(0);
 });
});
