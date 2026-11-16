import type {PersonnelBaseApi,PersonnelInput,PersonnelRow} from "./types";
/** 人员组织目录仅含公开标识和名称；不作为权限凭据。 */
export interface PersonnelCatalog {
  organizations:{id:string;name:string;enabled:boolean}[];
  positions:{id:string;organizationId:string;name:string;enabled:boolean}[];
  users:{memberId:string;accountId:string;name:string}[];
}
/** 十类明细的固定字段值；客户端不得添加任意属性，后端重复校验。 */
export interface PersonnelDetail {id:string;section:string;values:Record<string,string>}
/** 多对多关联；同一用户可关联多个人员，关联不赋予业务权限。 */
export interface PersonnelAssignment {id:string;memberId:string;userName:string;active:boolean}
/** 人员聚合，审计账号是实际执行账号，不替换为被代表人员；存量未知审计信息为空。 */
export interface PersonnelProfile {
  person:PersonnelRow;
  audit:{createdBy:string|null;createdByName:string;createdAt:string|null;updatedBy:string|null;updatedByName:string;updatedAt:string|null};
  details:PersonnelDetail[];assignments:PersonnelAssignment[];
}
/** 人员业务请求由应用壳注入，所有变更显式传入当前人员版本，写入不自动重放。 */
export interface PersonnelApi extends PersonnelBaseApi {
  read(id:string):Promise<PersonnelProfile>;
  options():Promise<PersonnelCatalog>;
  saveDetail(person:string,section:string,id:string|undefined,input:{version:number;values:Record<string,string>}):Promise<{id:string}>;
  deleteDetail(person:string,section:string,id:string,version:number):Promise<void>;
  assign(person:string,id:string|undefined,input:{memberId:string;version:number}):Promise<{id:string}>;
  unassign(person:string,id:string,version:number):Promise<void>;
}
/** 基础字段按参考图的基础资料、工作属性和联系信息顺序放置；编码和中文名为独立公共字段。 */
export const personnelFields = ["englishName", "gender", "birthDate", "ethnicity", "nativePlace", "politicalStatus", "identityType", "identityNumber", "phone", "email", "workNature", "bloodType", "emergencyContact", "emergencyPhone", "maritalStatus", "hobbies", "skillLevel", "educationLevel", "employmentStatus", "remarks"] as const;
/** 固定下拉值，不用中文展示文本作为接口编码；空值表示尚未采集。 */
export const personnelChoices:Record<string,string[]> = {"enabled":["true","false"],"isPrimary":["true","false"],"gender": ["MALE", "FEMALE", "OTHER", "UNDISCLOSED"], "identityType": ["NATIONAL_ID", "PASSPORT", "OTHER"], "workNature": ["FULL_TIME", "PART_TIME", "CONTRACT", "INTERN", "OTHER"], "bloodType": ["A", "B", "AB", "O", "UNKNOWN"], "maritalStatus": ["SINGLE", "MARRIED", "DIVORCED", "WIDOWED", "UNDISCLOSED"], "employmentStatus": ["ACTIVE", "PROBATION", "LEAVE", "RETIRED"], "certificateStatus": ["VALID", "EXPIRED", "REVOKED", "PENDING"], "recordType": ["REWARD", "PENALTY"], "educationLevel": ["PRIMARY", "JUNIOR_HIGH", "SENIOR_HIGH", "VOCATIONAL", "ASSOCIATE", "BACHELOR", "MASTER", "DOCTOR", "OTHER"], "skillLevel": ["JUNIOR", "INTERMEDIATE", "SENIOR", "TECHNICIAN", "SENIOR_TECHNICIAN"]};
/** 仅白名单明细定义驱动表单，不支持用户定义脚本、SQL 或任意字段。 */
export const personnelSections = {
  organizations:{fields:["organizationId", "isPrimary", "enabled", "startsOn", "endsOn", "remarks"],required:["organizationId"]},
  positions:{fields:["organizationId", "positionId", "enabled", "startsOn", "endsOn", "remarks"],required:["organizationId", "positionId"]},
  certificates:{fields:["certificateType", "certificateNumber", "certificateStatus", "passedOn", "startsOn", "endsOn", "remarks"],required:["certificateType", "certificateNumber"]},
  training:{fields:["trainingName", "provider", "startsOn", "endsOn", "result", "remarks"],required:["trainingName"]},
  awards:{fields:["awardName", "issuer", "awardedOn", "remarks"],required:["awardName"]},
  contacts:{fields:["contactType", "contactValue", "remarks"],required:["contactType", "contactValue"]},
  addresses:{fields:["addressType", "countryRegion", "province", "city", "address", "postalCode", "remarks"],required:["addressType", "address"]},
  workHistory:{fields:["employer", "department", "jobTitle", "startsOn", "endsOn", "remarks"],required:["employer"]},
  rewardsPenalties:{fields:["recordType", "reason", "issuer", "recordedOn", "remarks"],required:["recordType", "reason"]},
  education:{fields:["school", "major", "educationLevel", "degree", "startsOn", "endsOn", "remarks"],required:["school"]},
} as const;
/** 固定分类编码；与后端 Section 枚举一一对应。 */
export type PersonnelSection=keyof typeof personnelSections;
/** 新建默认在职，其余未采集字段为空；不默认创建账号或组织任职。 */
export function emptyPersonnel():PersonnelInput{return {code:"",name:"",version:0,attributes:Object.fromEntries(personnelFields.map(key=>[key,key==="employmentStatus"?"ACTIVE":""]))};}
