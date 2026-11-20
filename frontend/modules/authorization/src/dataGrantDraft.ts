import type {DataPolicy,DataGrantRule,DataDimension} from "./index";

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
