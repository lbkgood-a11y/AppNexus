-- 对象能力独立权限；不继承workflow:start或print.job:submit的全部数据访问权。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
(UUID_TO_BIN('019a65d0-0131-7000-8000-000000000001'),'TENANT','leave.request:submit','leave.request','submit','提交请假请求','Submit leave requests','对象能力准入；必须另外配置统一数据范围。'),
(UUID_TO_BIN('019a65d0-0131-7000-8000-000000000002'),'TENANT','leave.request:print','leave.request','print','打印请假请求','Print leave requests','对象打印准入；不替代打印基础设施权限及字段保护。'),
(UUID_TO_BIN('019a65d0-0131-7000-8000-000000000003'),'TENANT','masterdata.product.approval:submit','masterdata.product.approval','submit','提交商品审核','Submit product approvals','对象能力准入；必须另外配置统一数据范围。');
-- 删除旧商品审核权限及其授权/范围目录，不保留旧协议数据；管理员须显式授权新资源及范围。
DELETE FROM access_role_permission WHERE permission_id IN(SELECT id FROM access_permission WHERE code='masterdata.product:submit_approval');
DELETE FROM access_member_authorization_permission WHERE permission_id IN(SELECT id FROM access_permission WHERE code='masterdata.product:submit_approval');
DELETE FROM access_permission_scope_type WHERE permission_id IN(SELECT id FROM access_permission WHERE code='masterdata.product:submit_approval');
DELETE FROM access_permission WHERE code='masterdata.product:submit_approval';
