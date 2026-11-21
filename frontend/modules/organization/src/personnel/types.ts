/** 组织模块人员档案；页面不能声明任意实体或 API 路径。 */
export type PersonnelKind = "person";
/** 当前租户公共档案；人员档案不是登录账号或组织任职。 */
export interface PersonnelRow { id:string; code:string; name:string; attributes:Record<string,string>; enabled:boolean; version:number; audit:import("@appnexus/ui").RecordAudit; fieldRestrictions?:Record<string,string> }
/** 新增或编辑输入；version 为非负版本，编码仅在创建时确定，属性限于此类白名单。 */
export interface PersonnelInput { code:string; name:string; attributes:Record<string,string>; version:number }
/** 页面请求由应用壳注入，复用真实会话、CSRF 和统一操作准入；不存在模拟数据回退。 */
export interface PersonnelBaseApi {
  operations():Promise<string[]>;
  list(kind:PersonnelKind,query:{keyword:string;enabled?:boolean;page:number;size:number}):Promise<{rows:PersonnelRow[];total:number}>;
  create(kind:PersonnelKind,input:PersonnelInput):Promise<{id:string}>;
  update(kind:PersonnelKind,id:string,input:PersonnelInput):Promise<void>;
  setStatus(kind:PersonnelKind,id:string,input:{enabled:boolean;version:number}):Promise<void>;
}
