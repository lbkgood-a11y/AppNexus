-- 流程中心只承载导航分组；原页面标识、路由、权限与启停状态保持不变。
-- 目录默认可见且启用，只有存在本人已授权子菜单时才由导航服务展示，不新增业务授权。
INSERT INTO platform_menu(id,type,code,title_zh,title_en,icon,sort_order,scope_type)
VALUES(UUID_TO_BIN('019a65d0-0054-7000-8000-000000000001'),'DIRECTORY','builtin.navigation.workflow.center','流程中心','Workflow center','ri/flow-chart',20,'TENANT');

-- 沿用原菜单 ID，避免菜单资源关联与授权引用失效；工作台和设计器在目录内依次排列。
UPDATE platform_menu SET parent_id=UUID_TO_BIN('019a65d0-0054-7000-8000-000000000001'),
 title_zh='流程工作台',title_en='Workflow workspace',icon='ri/list-check',sort_order=0,version=version+1
WHERE component_key='workflow/index' AND scope_type='TENANT';
UPDATE platform_menu SET parent_id=UUID_TO_BIN('019a65d0-0054-7000-8000-000000000001'),
 title_zh='流程设计器',title_en='Workflow designer',icon='ri/flow-chart',sort_order=1,version=version+1
WHERE component_key='workflow/designer' AND scope_type='TENANT';

-- 补齐历史目录、页面与外链的缺省标识；按钮不属于导航菜单，不添加展示图标。
UPDATE platform_menu SET icon=CASE WHEN type='DIRECTORY' THEN 'ri/menu-line'
 WHEN type='EXTERNAL' THEN 'ri/links-fill' ELSE 'ri/file-info-line' END,version=version+1
WHERE type IN ('DIRECTORY','MENU','EXTERNAL') AND (icon IS NULL OR TRIM(icon)='');
