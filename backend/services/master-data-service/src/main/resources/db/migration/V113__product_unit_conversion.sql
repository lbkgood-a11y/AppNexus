-- 显式同维度直连换算；不自动推导反向、多跳或商品包装关系。
CREATE TABLE md_product_unit_conversion (
 id BINARY(16) NOT NULL COMMENT '服务端生成换算关系UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，复合外键隔离租户',
 source_unit_id BINARY(16) NOT NULL COMMENT '源单位UUID，与目标单位必须同维度',
 target_unit_id BINARY(16) NOT NULL COMMENT '目标单位UUID，禁止与源相同',
 factor DECIMAL(30,12) NOT NULL COMMENT '目标数量等于源数量乘系数，必须正数，最多12位小数',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用，停用保留关系和历史审计',
 version BIGINT NOT NULL DEFAULT 0 COMMENT 'CAS版本，初始0，启停成功递增',
 active_source BINARY(16) GENERATED ALWAYS AS (CASE WHEN enabled THEN source_unit_id ELSE NULL END) STORED COMMENT '仅启用关系占用当前方向唯一空间，停用后可新增修正系数',
 active_target BINARY(16) GENERATED ALWAYS AS (CASE WHEN enabled THEN target_unit_id ELSE NULL END) STORED COMMENT '当前启用目标单位，历史停用关系保留',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒精度',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC修改时间，与正文同事务',
 PRIMARY KEY(id), UNIQUE KEY uk_puc_pair(tenant_id,active_source,active_target),
 CONSTRAINT fk_puc_source FOREIGN KEY(tenant_id,source_unit_id) REFERENCES md_product_unit(tenant_id,id),
 CONSTRAINT fk_puc_target FOREIGN KEY(tenant_id,target_unit_id) REFERENCES md_product_unit(tenant_id,id),
 CONSTRAINT ck_puc_factor CHECK(factor>0),
 CONSTRAINT ck_puc_pair CHECK(source_unit_id<>target_unit_id),
 CONSTRAINT ck_puc_version CHECK(version>=0)
) COMMENT='通用同维度单位直连换算；系数创建后不可修改';
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0113-0000-8000-000000000001'),'TENANT','masterdata.productunit:convert','masterdata.productunit','convert','维护单位换算','Manage unit conversions','同维度直连关系新增与启停，不授予其他商品权限');
