-- 新的配置启停权限独立于编辑/发布，普通角色不自动扩权。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
(UUID_TO_BIN('019a65d0-0109-0000-8000-000000000001'),'TENANT','masterdata.productattribute:set_status','masterdata.productattribute','set_status','启停商品属性配置','Set product attribute status','独立启停分组/定义，不删除历史修订。'),
(UUID_TO_BIN('019a65d0-0109-0000-8000-000000000002'),'TENANT','masterdata.producttemplate:set_status','masterdata.producttemplate','set_status','启停商品模板','Set product template status','独立启停模板，不改变已有商品固定版本。');
