-- 在目标 AppNexus 数据库执行，只返回按依赖分类的计数。
START TRANSACTION READ ONLY;

WITH policies AS (
    SELECT '角色范围' AS source, scope_type, rules_json
    FROM access_role_data_policy
    UNION ALL
    SELECT '成员自定义范围', scope_type, rules_json
    FROM access_member_authorization_permission
    WHERE scope_type IS NOT NULL
)
SELECT source, COUNT(CASE WHEN scope_type IN ('ORGANIZATION', 'ORGANIZATION_SUBTREE')
   OR JSON_SEARCH(rules_json, 'one', 'ORGANIZATION', NULL, '$[*].subjectType') IS NOT NULL
   OR JSON_SEARCH(rules_json, 'one', 'organizationId', NULL, '$[*].conditions[*].field') IS NOT NULL
   OR JSON_SEARCH(rules_json, 'one', 'CURRENT!_PERSON', '!', '$[*].conditions[*].parameter') IS NOT NULL
   OR JSON_SEARCH(rules_json, 'one', 'CURRENT!_ORGANIZATION%', '!', '$[*].conditions[*].parameter') IS NOT NULL THEN 1 END) AS affected_policy_rows
FROM policies
GROUP BY source;

-- 流程模型文本筛查可能匹配同名字段，迁移前逐版本复核节点配置。
SELECT '当前启用的已发布流程' AS source, COUNT(*) AS affected_versions
FROM wf_definition d
JOIN wf_version v ON v.tenant_id = d.tenant_id
                 AND v.definition_id = d.id AND v.version = d.published
WHERE d.enabled
  AND REGEXP_LIKE(v.model, '"kind"[[:space:]]*:[[:space:]]*"(POSITION|LEADER)"', 'c')
UNION ALL
SELECT '运行中的流程实例', COUNT(*)
FROM wf_instance i
JOIN wf_version v ON v.tenant_id = i.tenant_id
                 AND v.definition_id = i.definition_id AND v.version = i.definition_version
WHERE i.state = 'RUNNING'
  AND REGEXP_LIKE(v.model, '"kind"[[:space:]]*:[[:space:]]*"(POSITION|LEADER)"', 'c')
UNION ALL
SELECT '租户成员与人员关联', COUNT(*) FROM md_person_account;

COMMIT;
