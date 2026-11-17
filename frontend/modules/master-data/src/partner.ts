/** 客商固定字段与明细契约；信用代码紧随英文名称，六列布局填满身份首行；未接入字典的分类允许输入真实业务文本。 */
export const partnerBasic=["shortName","chineseName","englishName","creditCode","partnerType","registeredPlace","countryRegion","nature","listed"];
/** 详细资料可以折叠但仍保留表单校验；金额按文本传递，单位万元，不经浮点换算。 */
export const partnerAdditional=["customerLevel","address","legalRepresentative","legalId","controller","establishedOn","registeredCurrency","registeredCapital","taxNumber","organizationCode","productsServices","staffSize","contactName","phone","fax","remarks"];
/** 仅绑定字典字段，不在前端维护选项；BOTH 历史值由详情展开为客户与供应商。 */
export const partnerDictionaryFields=["partnerType","nature","customerLevel","contactType","registeredCurrency","currency","staffSize"];
/** 最多 100 条联系人和 100 条银行账户；布尔文本默认 false，服务端再次校验。 */
export const partnerSections={contacts:["contactType","contactName","phone","address","isDefault","email","remarks"],bankAccounts:["taxAccount","basicAccount","isDefault","bankName","accountNumber","accountAlias","currency","cnaps","swiftCode","remarks"]} as const;
