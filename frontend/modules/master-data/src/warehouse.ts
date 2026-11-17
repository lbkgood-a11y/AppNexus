/** 仓库字段固定白名单；单位、精度与服务端一致，不包含外部系统映射。 */
export const warehouseGroups={"basic": ["shortCode", "shortName", "warehouseType", "totalArea", "capacity", "logisticsOrganization", "contactName", "contactTitle", "phone", "email", "longitude", "latitude", "region", "address", "builtOn", "openedOn", "introduction", "structureDescription", "operatingMode", "billAddress", "remarks"], "property": ["partner", "owner", "ownershipCertificate", "personalGuarantee", "wholeLease", "leaseChainDescription", "subleaseDescription", "otherStorageDescription", "pledgee"]};
/** 分类来自租户业务字典；布尔标志不作为可扩展分类。 */
export const warehouseDictionaryFields=["warehouseType","operatingMode"];
/** 非负数量最多18位整数、6位小数；经纬度范围另行校验，空串表示历史未记录。 */
export const warehouseNumericFields=["totalArea","capacity","longitude","latitude"];
