import {describe,expect,it} from "vitest";
import {applyFieldPolicyBatch} from "./fieldPolicyDraft";
import type {FieldDescriptor,FieldPolicy} from "./index";

const email:FieldDescriptor={resource:"customer",field:"email",nameZh:"邮箱",nameEn:"Email",kind:"EMAIL",modes:["HIDDEN","MASKED"]};
const hidden:FieldPolicy={resource:"employee",field:"identityNo",mode:"HIDDEN"};
describe("集中字段策略批量边界",()=>{
  it("只应用当前筛选中的已选字段，保留筛选外强制限制",()=>{
    const original:FieldPolicy[]=[hidden];
    const next=applyFieldPolicyBatch(original,[email],new Set(["customer:email","employee:identityNo","unknown:field"]),"MASKED");
    expect(next).toEqual([hidden,{resource:"customer",field:"email",mode:"MASKED"}]);
    expect(original).toEqual([hidden]);
  });
  it("清除选择不会移除未选或不可见字段限制",()=>{
    const current:FieldPolicy[]=[hidden,{resource:"customer",field:"email",mode:"MASKED"}];
    expect(applyFieldPolicyBatch(current,[email],new Set(["customer:email"]),"NONE")).toEqual([hidden]);
    expect(applyFieldPolicyBatch(current,[email],new Set(),"NONE")).toEqual(current);
  });
  it("后端目录不支持的模式不能由批量操作写入",()=>{
    expect(applyFieldPolicyBatch([hidden],[{...email,modes:["HIDDEN"]}],new Set(["customer:email"]),"MASKED")).toEqual([hidden]);
  });
});
