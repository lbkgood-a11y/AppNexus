-- 全面替换对象/操作命名；不保留旧表、旧列或旧 JSON 键，不修改历史迁移校验和。
UPDATE `access_policy_template` SET `policy_json`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`policy_json` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `policy_json` IS NOT NULL;
ALTER TABLE `doc_audit` RENAME COLUMN `type_code` TO `object_type`;
ALTER TABLE `doc_configuration` RENAME COLUMN `type_code` TO `object_type`;
UPDATE `doc_configuration` SET `draft`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`draft` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `draft` IS NOT NULL;
UPDATE `doc_configuration` SET `published`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`published` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `published` IS NOT NULL;
ALTER TABLE `doc_configuration_version` RENAME COLUMN `type_code` TO `object_type`;
UPDATE `doc_configuration_version` SET `bindings`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`bindings` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `bindings` IS NOT NULL;
ALTER TABLE `doc_entry_control_audit` RENAME COLUMN `type_code` TO `object_type`;
ALTER TABLE `doc_entry_control_audit` RENAME COLUMN `operation_key` TO `operation_id`;
ALTER TABLE `doc_page_relationships` RENAME COLUMN `type_code` TO `object_type`;
UPDATE `doc_page_relationships` SET `relationships`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`relationships` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `relationships` IS NOT NULL;
UPDATE `doc_page_relationships` SET `disabled_operations`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`disabled_operations` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `disabled_operations` IS NOT NULL;
ALTER TABLE `doc_resolution` RENAME COLUMN `type_code` TO `object_type`;
UPDATE `doc_resolution` SET `facts`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`facts` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `facts` IS NOT NULL;
ALTER TABLE `doc_resolution` RENAME COLUMN `action_code` TO `operation_code`;
ALTER TABLE `doc_tenant_grant` RENAME COLUMN `type_code` TO `object_type`;
ALTER TABLE `doc_type` RENAME COLUMN `type_code` TO `object_type`;
UPDATE `leave_poc_approval_cycle` SET `basis_snapshot`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`basis_snapshot` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `basis_snapshot` IS NOT NULL;
UPDATE `print_data_contract` SET `definition_json`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`definition_json` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `definition_json` IS NOT NULL;
UPDATE `print_device` SET `capabilities_json`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`capabilities_json` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `capabilities_json` IS NOT NULL;
UPDATE `print_document` SET `data_json`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`data_json` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `data_json` IS NOT NULL;
UPDATE `print_job` SET `data_snapshot`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`data_snapshot` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `data_snapshot` IS NOT NULL;
UPDATE `print_job` SET `result_json`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`result_json` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `result_json` IS NOT NULL;
UPDATE `print_job` SET `data_contract_schema`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`data_contract_schema` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `data_contract_schema` IS NOT NULL;
UPDATE `print_template` SET `scope_json`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`scope_json` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `scope_json` IS NOT NULL;
UPDATE `print_template` SET `draft_json`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`draft_json` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `draft_json` IS NOT NULL;
UPDATE `print_template_version` SET `template_json`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`template_json` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `template_json` IS NOT NULL;
UPDATE `print_template_version` SET `scope_json`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`scope_json` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `scope_json` IS NOT NULL;
UPDATE `print_template_version` SET `data_contract_schema`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`data_contract_schema` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `data_contract_schema` IS NOT NULL;
UPDATE `wf_instance` SET `data_contract_schema`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`data_contract_schema` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `data_contract_schema` IS NOT NULL;
UPDATE `wf_version` SET `data_contract_schema`=CAST(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`data_contract_schema` AS CHAR CHARACTER SET utf8mb4), '"typeCode":', '"objectType":'), '"actionCode":', '"operationCode":'), '"operationKey":', '"operationId":'), '"compatibleActionCode":', '"compatibleOperationCode":') AS JSON) WHERE `data_contract_schema` IS NOT NULL;
-- 将旧允许列表一次性转换为禁用覆盖；运行时代码不再执行旧协议迁移。
UPDATE doc_page_relationships p JOIN (
 SELECT object_type,JSON_ARRAYAGG(operation_id) disabled FROM (
  SELECT p.object_type,c.operation_id FROM doc_page_relationships p
  CROSS JOIN JSON_TABLE(CASE WHEN p.object_type='LeaveRequest' THEN JSON_ARRAY('LeaveRequest.submit','LeaveRequest.print','LeaveRequest.detail.print') ELSE JSON_ARRAY() END,'$[*]' COLUMNS(operation_id VARCHAR(200) PATH '$')) c
  WHERE p.disabled_operations IS NULL AND NOT JSON_CONTAINS(p.relationships,JSON_OBJECT('operationId',c.operation_id))
 ) missing GROUP BY object_type
) d ON d.object_type=p.object_type SET p.disabled_operations=d.disabled;
UPDATE doc_page_relationships SET disabled_operations=JSON_ARRAY() WHERE disabled_operations IS NULL;
ALTER TABLE doc_page_relationships MODIFY disabled_operations JSON NOT NULL COMMENT '明确禁用的操作标识；空数组表示没有额外禁用，无缺省兼容行为';
RENAME TABLE `doc_type` TO `business_object_type`;
RENAME TABLE `doc_tenant_grant` TO `business_object_tenant_grant`;
RENAME TABLE `doc_configuration` TO `business_object_configuration`;
RENAME TABLE `doc_configuration_version` TO `business_object_configuration_version`;
RENAME TABLE `doc_resolution` TO `business_object_resolution`;
RENAME TABLE `doc_audit` TO `business_object_audit`;
RENAME TABLE `doc_entry_control_audit` TO `business_object_entry_control_audit`;
RENAME TABLE `doc_page_relationships` TO `business_object_page_relationships`;
-- 菜单入口随新页面标识更新，不新增授权。
UPDATE platform_menu SET component_key='platform/business-objects',route_path='/platform/business-objects',title_zh='业务对象目录',title_en='Business object catalog',version=version+1 WHERE component_key='platform/documents';
UPDATE platform_menu SET component_key='business-objects/index',route_path='/business-objects/workbench',title_zh='业务配置',title_en='Business configuration',version=version+1 WHERE component_key='documents/index';
-- 外部解析能力范围同步改名，推进客户端版本使旧 Token 失效；历史审计保留原文。
UPDATE integration_client c JOIN oauth2_registered_client r ON r.id=c.id
SET c.authority_version=c.authority_version+1
WHERE r.scopes LIKE '%document:resolve%';
UPDATE oauth2_registered_client SET scopes=REPLACE(scopes,'document:resolve','business-object:resolve')
WHERE scopes LIKE '%document:resolve%';
