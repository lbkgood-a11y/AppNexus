-- 代码模块归并不修改人员 ID、账号关系、权限码和已发布 API；只调整已有菜单的分组和页面注册。
UPDATE platform_menu SET parent_id=UUID_TO_BIN('019a65d0-0027-7000-8000-000000000003'),route_path='/organization/personnel',component_key='organization/personnel',icon='ri/group-line',sort_order=2
WHERE id=UUID_TO_BIN('019a65d0-0075-7000-8000-000000000101');
-- 保留目录稳定标识和管理员已配置的可见性/启停，仅更新缺省业务名称。
UPDATE platform_menu SET title_zh='组织与人员',title_en='Organization and personnel' WHERE id=UUID_TO_BIN('019a65d0-0027-7000-8000-000000000003');
