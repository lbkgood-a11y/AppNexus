-- 当前页下载只使用列表已授权投影，停用旧独立权限并保留历史授权及审计记录。
UPDATE access_permission
SET enabled=FALSE, version=version+1, updated_at=CURRENT_TIMESTAMP(6), updated_by=NULL
WHERE scope_type='TENANT' AND code='masterdata.partner:export' AND enabled=TRUE;
