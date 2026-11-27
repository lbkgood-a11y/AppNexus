-- 客商本页下载需要独立租户功能权限。由管理员通过统一授权中心显式分配。
INSERT INTO access_permission (id, scope_type, code, resource, action, name_zh, name_en, description)
VALUES (UUID_TO_BIN('019a65d0-0216-7000-8000-000000000001'), 'TENANT',
        'masterdata.partner:export', 'masterdata.partner', 'export',
        '导出本页客商', 'Export current partner page', '允许将已授权的客商列表当前页可见列导出为 Excel。');
