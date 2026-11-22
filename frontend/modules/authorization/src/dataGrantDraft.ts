import type {DataPolicy,DataGrantRule,DataDimension,DataScopeType,ScopeSupport} from "./index";

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
/**
 * 返回可定位到规则的草稿问题；仅改善配置反馈，不替代后端目录、租户、主体真实性和执行校验。
 * @param rule 待保存规则，不修改输入
 * @param dimensions 可选的服务端维度目录；未提供时只验证基础形状，避免页面加载时猜测字段能力
 */
export function ruleDraftErrors(rule:DataGrantRule,dimensions?:DataDimension[]):string[]{
 const errors=new Set<string>();
 if((rule.subjectType==='ROLE_MEMBERS')!==!rule.subjectIds.length||rule.subjectIds.some(id=>!uuid.test(id)))errors.add('subject');
 if(rule.recordIds.some(id=>!uuid.test(id)))errors.add('record');
 if(rule.allRecords!==(rule.recordIds.length===0&&rule.conditions.length===0))errors.add('selection');
 if(rule.subjectIds.length>500||rule.recordIds.length>500||rule.conditions.length>8)errors.add('limit');
 for(const condition of rule.conditions){
  const dimension=dimensions?.find(item=>item.field===condition.field);
  if(!condition.field||dimensions&&!dimension)errors.add('field');
  if(!['EQ','IN'].includes(condition.operator)||dimension&&!dimension.operators.includes(condition.operator))errors.add('operator');
  if(condition.parameter){
   if(condition.operator!=='EQ'||condition.values.length||dimensions&&!dimension?.parameters?.includes(condition.parameter))errors.add('value');
   continue;
  }
  if(dimension?.source==='ACCOUNT'||dimension?.source==='PERSON')errors.add('value');
  if(!condition.values.length||condition.values.length>100||condition.operator==='EQ'&&condition.values.length!==1||condition.values.some(value=>!value.trim()||value.length>200))errors.add('value');
  if(dimension?.valueType==='UUID'&&condition.values.some(value=>!uuid.test(value)))errors.add('uuid');
 }
 return [...errors];
}

/** 授权草稿仅用于阻止明显缺项提交；服务端仍独立验证类型、主体、目录与复杂度。 */
export function incompleteDataPolicy(policy:DataPolicy|undefined,dimensions?:DataDimension[]):boolean {
 if(!policy)return true;
 if(policy.scopeType==="ORGANIZATION"||policy.scopeType==="ORGANIZATION_SUBTREE")return !policy.organizationIds.length;
 if(policy.scopeType!=="RULES")return false;
 return !policy.rules?.length||policy.rules.length>20||policy.rules.some(rule=>ruleDraftErrors(rule,dimensions).length>0);
}


/** 仅用于选择器；指定记录继续保存为单条 ROLE_MEMBERS 规则。 */
export type ScopeChoice=DataScopeType|"RECORDS";
type RuleEditorChoice="RULES"|"RECORDS";

/** 公开目录已支持 RULES 时才可按记录授权；新增没有既存记录。 */
export function scopeChoices(support:ScopeSupport[],permissionCode:string,hasDimensions:boolean):ScopeChoice[]{
 const result:ScopeChoice[]=support.map(item=>item.scopeType);
 if(!permissionCode.endsWith(":create")&&result.includes("RULES")){
  const ruleIndex=result.indexOf("RULES");
  result.splice(ruleIndex,hasDimensions?0:1,"RECORDS");
 }
 return result;
}

/** 只将精确的原记录策略回显为快捷项，保留原有条件、主体和多分支规则。 */
export function shownScope(policy:DataPolicy|undefined,preferred?:RuleEditorChoice):ScopeChoice|undefined{
 if(policy?.scopeType!=="RULES")return policy?.scopeType;
 if(preferred)return preferred;
 const [rule]=policy.rules??[];
 return policy.rules?.length===1&&rule.subjectType==="ROLE_MEMBERS"&&!rule.subjectIds.length&&
   !rule.allRecords&&!!rule.recordIds.length&&!rule.conditions.length?"RECORDS":"RULES";
}

/** 选择器快捷项从不作为服务端 scopeType 提交。 */
export function persistedScope(choice:ScopeChoice):DataScopeType{
 return choice==="RECORDS"?"RULES":choice;
}
