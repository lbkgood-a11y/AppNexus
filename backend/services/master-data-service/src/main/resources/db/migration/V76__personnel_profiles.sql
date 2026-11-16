-- 人员详细档案增量迁移；不修改已发布 V75，存量审计信息保持未知，不伪造创建人或时间。

ALTER TABLE md_person
 ADD COLUMN gender VARCHAR(500) NOT NULL DEFAULT '' COMMENT '性别；文本最多 500 字符，缺省空',
 ADD COLUMN birth_date VARCHAR(500) NOT NULL DEFAULT '' COMMENT '出生日期，ISO yyyy-MM-dd，缺省空，不得为未来日期；文本最多 500 字符，缺省空',
 ADD COLUMN ethnicity VARCHAR(500) NOT NULL DEFAULT '' COMMENT '民族；文本最多 500 字符，缺省空',
 ADD COLUMN native_place VARCHAR(500) NOT NULL DEFAULT '' COMMENT '籍贯；文本最多 500 字符，缺省空',
 ADD COLUMN political_status VARCHAR(500) NOT NULL DEFAULT '' COMMENT '政治面貌；文本最多 500 字符，缺省空',
 ADD COLUMN identity_type VARCHAR(500) NOT NULL DEFAULT '' COMMENT '证件类型；文本最多 500 字符，缺省空',
 ADD COLUMN identity_number VARCHAR(500) NOT NULL DEFAULT '' COMMENT '证件号码，敏感档案字段，仅详情授权读取；文本最多 500 字符，缺省空',
 ADD COLUMN work_nature VARCHAR(500) NOT NULL DEFAULT '' COMMENT '工作性质；文本最多 500 字符，缺省空',
 ADD COLUMN blood_type VARCHAR(500) NOT NULL DEFAULT '' COMMENT '血型；文本最多 500 字符，缺省空',
 ADD COLUMN emergency_contact VARCHAR(500) NOT NULL DEFAULT '' COMMENT '紧急联系人；文本最多 500 字符，缺省空',
 ADD COLUMN emergency_phone VARCHAR(500) NOT NULL DEFAULT '' COMMENT '紧急联系电话；文本最多 500 字符，缺省空',
 ADD COLUMN marital_status VARCHAR(500) NOT NULL DEFAULT '' COMMENT '婚姻状况；文本最多 500 字符，缺省空',
 ADD COLUMN hobbies VARCHAR(500) NOT NULL DEFAULT '' COMMENT '爱好特长；文本最多 500 字符，缺省空',
 ADD COLUMN skill_level VARCHAR(500) NOT NULL DEFAULT '' COMMENT '工作技能等级；文本最多 500 字符，缺省空',
 ADD COLUMN education_level VARCHAR(500) NOT NULL DEFAULT '' COMMENT '最高学历；文本最多 500 字符，缺省空',
 ADD COLUMN employment_status VARCHAR(500) NOT NULL DEFAULT 'ACTIVE' COMMENT '人员在职状态，ACTIVE 在职、PROBATION 试用、LEAVE 离职、RETIRED 退休，存量默认 ACTIVE；文本最多 500 字符，缺省 ACTIVE',
 ADD COLUMN remarks VARCHAR(500) NOT NULL DEFAULT '' COMMENT '备注；文本最多 500 字符，缺省空',
 ADD COLUMN created_by BINARY(16) NULL COMMENT '实际创建账号，存量未知为 NULL，不允许客户端指定',
 ADD COLUMN created_at TIMESTAMP(6) NULL COMMENT '真实创建时间，UTC 时间点，存量未知为 NULL',
 ADD COLUMN updated_by BINARY(16) NULL COMMENT '实际最后修改账号，存量未知为 NULL',
 ADD COLUMN updated_at TIMESTAMP(6) NULL COMMENT '最后修改时间，UTC 时间点，存量未知为 NULL',
 ADD UNIQUE KEY uk_md_person_tenant_identity(tenant_id,id);

-- 十类业务明细使用固定 section_code 和严格字段白名单 JSON；不开放自由字段、SQL 或外部地址执行。
CREATE TABLE md_person_detail (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户标识，必填',
 id BINARY(16) NOT NULL COMMENT '服务端生成明细 UUID',
 person_id BINARY(16) NOT NULL COMMENT '同租户人员标识，必填',
 section_code VARCHAR(32) NOT NULL COMMENT '固定十类明细编码，服务端枚举校验，无默认值',
 content JSON NOT NULL COMMENT '该明细类型固定字段键值；最长 500 字符每项，拒绝未知键；字段见人员档案设计',
 PRIMARY KEY(tenant_id,id),KEY ix_person_detail_owner(tenant_id,person_id,section_code),
 CONSTRAINT fk_person_detail_owner FOREIGN KEY(tenant_id,person_id) REFERENCES md_person(tenant_id,id)
) COMMENT='人员十类业务明细，修改使用人员聚合版本';
-- 同一个账号在不同组织可代表不同人员；不创建账号、不授予角色权限，不与档案任职混淆。
CREATE TABLE md_person_user_assignment (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户标识',
 id BINARY(16) NOT NULL COMMENT '服务端生成关系 UUID',
 person_id BINARY(16) NOT NULL COMMENT '同租户人员档案 UUID',
 organization_id BINARY(16) NOT NULL COMMENT '经组织公开契约核验的组织 UUID',
 member_id BINARY(16) NOT NULL COMMENT '该组织下有效租户用户成员 UUID；实际账号由成员关联',
 created_by BINARY(16) NOT NULL COMMENT '实际分配账号；来源为可信请求上下文',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '分配时间，UTC 时间点，默认数据库当前时间',
 PRIMARY KEY(tenant_id,id),UNIQUE KEY uk_person_org_user(tenant_id,person_id,organization_id,member_id),
 KEY ix_person_user(tenant_id,member_id,organization_id),
 CONSTRAINT fk_person_user_owner FOREIGN KEY(tenant_id,person_id) REFERENCES md_person(tenant_id,id)
) COMMENT='人员与组织下用户多对多关系；有效性每次使用重新核验';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0076-7000-8000-000000000001'),'TENANT','masterdata.person:assign','masterdata.person','assign','分配人员与组织用户','Assign personnel users','仅当前租户人员档案，独立于账号、任职与业务操作授权。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0076-7000-8000-000000000002'),'TENANT','masterdata.person:detail_create','masterdata.person','detail_create','新增人员明细','Create personnel details','仅当前租户人员档案，独立于账号、任职与业务操作授权。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0076-7000-8000-000000000003'),'TENANT','masterdata.person:detail_update','masterdata.person','detail_update','编辑人员明细','Edit personnel details','仅当前租户人员档案，独立于账号、任职与业务操作授权。');

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0076-7000-8000-000000000004'),'TENANT','masterdata.person:detail_delete','masterdata.person','detail_delete','删除人员明细','Delete personnel details','仅当前租户人员档案，独立于账号、任职与业务操作授权。');

-- 沿用受控租户管理员补齐规则；普通及自定义角色不自动扩权。

INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT r.id,p.id,'TENANT',COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000) FROM access_role r JOIN access_permission p ON p.code IN ('masterdata.person:assign','masterdata.person:detail_create','masterdata.person:detail_update','masterdata.person:detail_delete') WHERE r.system_key LIKE 'TENANT_ADMIN:%';

INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id) SELECT g.tenant_id,g.member_id,g.role_id,p.id FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id JOIN access_permission p ON p.code IN ('masterdata.person:assign','masterdata.person:detail_create','masterdata.person:detail_update','masterdata.person:detail_delete') WHERE r.system_key LIKE 'TENANT_ADMIN:%';

UPDATE access_role SET version=version+1 WHERE system_key LIKE 'TENANT_ADMIN:%';
