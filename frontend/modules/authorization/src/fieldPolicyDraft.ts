import type {FieldDescriptor,FieldPolicy} from "./index";

/**
 * 在统一字段工作台批量应用规则，只改变本次筛选中明确选中的目录项。
 * @param current 完整草稿；筛选外或未选中的规则必须保留。
 * @param visible 当前筛选后的真实服务端字段目录，不接受任意表名或属性路径。
 * @param selected 已选字段键，格式 resource:field；目录外的键不会产生授权配置。
 * @param mode HIDDEN、MASKED 或 NONE；NONE 仅移除额外限制，不授予原文权。
 * @returns 新草稿，不修改调用方数组；不支持所选模式的字段不参与变更。
 */
export function applyFieldPolicyBatch(current:FieldPolicy[],visible:FieldDescriptor[],selected:ReadonlySet<string>,mode:FieldPolicy["mode"]|"NONE"):FieldPolicy[]{
  const targets=visible.filter(item=>selected.has(item.resource+":"+item.field)&&(mode==="NONE"||item.modes.includes(mode)));
  const keys=new Set(targets.map(item=>item.resource+":"+item.field));
  const result=current.filter(item=>!keys.has(item.resource+":"+item.field));
  if(mode!=="NONE")result.push(...targets.map(item=>({resource:item.resource,field:item.field,mode})));
  return result;
}
