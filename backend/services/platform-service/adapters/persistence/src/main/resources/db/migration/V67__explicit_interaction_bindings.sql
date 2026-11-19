-- 一次性转换开发期配置为唯一服务模型；迁移完成后运行时只读取 service，不保留旧字段解析。
-- doc_configuration.draft 保留配置版本与目标身份，仅规范协议字段。
UPDATE doc_configuration c SET draft=(
 SELECT JSON_ARRAYAGG(JSON_REMOVE(JSON_SET(j.item,
 '$.actionCode',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.actionCode')),'submit'),
 '$.service',CASE COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.service')),JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.mode')),'APPROVAL') WHEN 'DIRECT' THEN 'BUSINESS' WHEN 'APPROVAL' THEN 'WORKFLOW' ELSE COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.service')),JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.mode'))) END,
 '$.operationKey',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.operationKey')),CONCAT(c.type_code,'.',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.actionCode')),'submit')))),'$.mode'))
 FROM JSON_TABLE(c.draft,'$[*]' COLUMNS(item JSON PATH '$')) j
) WHERE draft IS NOT NULL AND JSON_LENGTH(draft)>0;
-- doc_configuration.published 保留配置版本与目标身份，仅规范协议字段。
UPDATE doc_configuration c SET published=(
 SELECT JSON_ARRAYAGG(JSON_REMOVE(JSON_SET(j.item,
 '$.actionCode',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.actionCode')),'submit'),
 '$.service',CASE COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.service')),JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.mode')),'APPROVAL') WHEN 'DIRECT' THEN 'BUSINESS' WHEN 'APPROVAL' THEN 'WORKFLOW' ELSE COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.service')),JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.mode'))) END,
 '$.operationKey',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.operationKey')),CONCAT(c.type_code,'.',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.actionCode')),'submit')))),'$.mode'))
 FROM JSON_TABLE(c.published,'$[*]' COLUMNS(item JSON PATH '$')) j
) WHERE published IS NOT NULL AND JSON_LENGTH(published)>0;
-- doc_configuration_version.bindings 保留配置版本与目标身份，仅规范协议字段。
UPDATE doc_configuration_version c SET bindings=(
 SELECT JSON_ARRAYAGG(JSON_REMOVE(JSON_SET(j.item,
 '$.actionCode',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.actionCode')),'submit'),
 '$.service',CASE COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.service')),JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.mode')),'APPROVAL') WHEN 'DIRECT' THEN 'BUSINESS' WHEN 'APPROVAL' THEN 'WORKFLOW' ELSE COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.service')),JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.mode'))) END,
 '$.operationKey',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.operationKey')),CONCAT(c.type_code,'.',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.actionCode')),'submit')))),'$.mode'))
 FROM JSON_TABLE(c.bindings,'$[*]' COLUMNS(item JSON PATH '$')) j
) WHERE bindings IS NOT NULL AND JSON_LENGTH(bindings)>0;
-- doc_resolution 的执行快照使用服务身份；原提交状态及幂等键不变。
ALTER TABLE doc_resolution RENAME COLUMN submission_mode TO service;
-- 服务必须由解析结果显式写入，删除旧 APPROVAL 列默认值。
ALTER TABLE doc_resolution ALTER COLUMN service DROP DEFAULT;
UPDATE doc_resolution SET service=CASE service WHEN 'DIRECT' THEN 'BUSINESS' WHEN 'APPROVAL' THEN 'WORKFLOW' ELSE service END;
-- leave_poc_submission 的执行快照使用服务身份；原提交状态及幂等键不变。
ALTER TABLE leave_poc_submission RENAME COLUMN submission_mode TO service;
UPDATE leave_poc_submission SET service=CASE service WHEN 'DIRECT' THEN 'BUSINESS' WHEN 'APPROVAL' THEN 'WORKFLOW' ELSE service END;
