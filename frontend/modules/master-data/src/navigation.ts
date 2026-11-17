import type { GeographyNavigationNode } from "./index";

/** 版本不一致或父级循环时停止装载；禁止将旧业务列表误当作树的直接子级。 */
export class GeographyTreeContractError extends Error {
  constructor(){super("GEOGRAPHY_TREE_INVALID");}
}
/**
 * 校验当前层响应，不接受父级回返、祖先循环、同级重复或重复分页。
 * @param response 未信任的接口响应，必须显式回传 parentCode
 * @param parent 请求父级标准键，空串表示洲根
 * @param parentHierarchyCode 当前父级层级码，根为空
 * @param excluded 已加载兄弟及所有祖先的标准键，禁止重复插入
 * @param page 页码，从 1 开始，每页固定 50 条
 * @return 已验证的直接子节点分页
 * @throws GeographyTreeContractError 响应不符合契约或不推进分页时拒绝装载
 */
export function validateNavigationPage(response:unknown,parent:string,parentHierarchyCode:string,excluded:ReadonlySet<string>,page:number):{parentCode:string;rows:GeographyNavigationNode[];total:number}{
  const result=response as {parentCode?:unknown;rows?:unknown;total?:unknown}|null;
  if(!result||result.parentCode!==parent||!Array.isArray(result.rows)||!Number.isSafeInteger(result.total)||(result.total as number)<0||result.rows.length>50)throw new GeographyTreeContractError();
  const total=result.total as number;
  if(result.rows.length!==Math.min(50,Math.max(0,total-(page-1)*50)))throw new GeographyTreeContractError();
  const seen=new Set(excluded);
  for(const row of result.rows){
    if(!row||typeof row.code!=="string"||!row.code||typeof row.name!=="string"||typeof row.originalName!=="string"||typeof row.hierarchyCode!=="string"||!row.hierarchyCode||typeof row.leaf!=="boolean"||row.parentCode!==parent||row.parentHierarchyCode!==parentHierarchyCode||seen.has(row.code)||row.code===parent)throw new GeographyTreeContractError();
    if(parent?!row.hierarchyCode.startsWith(parentHierarchyCode+"-"):!row.code.startsWith("continent:"))throw new GeographyTreeContractError();
    seen.add(row.code);
  }
  return {parentCode:parent,rows:result.rows,total};
}
