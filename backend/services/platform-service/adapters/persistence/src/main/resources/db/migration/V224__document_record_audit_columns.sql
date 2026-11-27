-- 补齐存量审计列的中文注释。MySQL 修改列注释时须重述列定义，保留原类型、空值、默认值及自动更新时间规则。

ALTER TABLE `access_permission`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `access_policy_template`
    MODIFY COLUMN `created_by` BINARY(16) NOT NULL COMMENT '记录创建账号，来源为可信认证上下文',
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `access_role`
    MODIFY COLUMN `created_by` BINARY(16) NULL DEFAULT NULL COMMENT '记录创建账号，来源为可信认证上下文，系统执行或历史未知时为空',
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒',
    MODIFY COLUMN `updated_by` BINARY(16) NULL DEFAULT NULL COMMENT '记录最后修改账号，来源为可信认证上下文，系统执行或历史未知时为空',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `business_object_audit`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `business_object_configuration_version`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `business_object_page_relationships`
    MODIFY COLUMN `updated_by` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '记录最后修改账号，来源为可信认证上下文',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `business_object_resolution`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `identity_account`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `integration_client`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `integration_reference_request`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `leave_poc_approval_cycle`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `leave_poc_request`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `leave_poc_submission`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `platform_administrator`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `platform_menu`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `platform_outbox`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `platform_tenant`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `print_device`
    MODIFY COLUMN `created_by` BINARY(16) NOT NULL COMMENT '记录创建账号，来源为可信认证上下文',
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `print_job`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `print_template`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_dictionary`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_dictionary_item`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_dictionary_item_reference`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_invitation`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_member`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_organization_assignment`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_organization_leader`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_organization_position`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_organization_relation`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_organization_type`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒',
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_organization_unit`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `tenant_parameter`
    MODIFY COLUMN `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后修改时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `wf_audit`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `wf_instance`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `wf_request`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `wf_task`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';

ALTER TABLE `wf_version`
    MODIFY COLUMN `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间，由数据库生成，使用 UTC，精确到微秒';
