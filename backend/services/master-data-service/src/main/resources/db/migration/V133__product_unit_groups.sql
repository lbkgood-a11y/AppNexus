-- 使用存量维度编码作为分组稳定键，保留单位、属性引用及换算关系的原有身份。
CREATE TABLE md_product_unit_group (
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，分组与单位使用同租户复合键',
  code VARCHAR(64) NOT NULL COMMENT '分组稳定编码，必填，创建后不可修改',
  name VARCHAR(200) NOT NULL COMMENT '分组显示名称，必填，最多200字符',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，初始0，成功更新时递增',
  created_by BINARY(16) NULL COMMENT '可信创建账号UUID；迁移及无账号系统执行为空',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒精度',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；迁移及无账号系统执行为空',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC最后修改时间，与正文同事务',
  PRIMARY KEY (tenant_id,code),
  CONSTRAINT ck_pug_version CHECK (version>=0)
) COMMENT='计量单位分组，供单位管理筛选和通用换算校验';

-- 仅依据已保存的维度建立目录，初始显示名称使用该维度编码。
INSERT INTO md_product_unit_group(tenant_id,code,name)
SELECT tenant_id,MIN(dimension_code),MIN(dimension_code)
FROM md_product_unit
GROUP BY tenant_id,dimension_code;

ALTER TABLE md_product_unit
  ADD KEY ix_pu_group (tenant_id,dimension_code,code),
  ADD CONSTRAINT fk_pu_group FOREIGN KEY (tenant_id,dimension_code)
    REFERENCES md_product_unit_group(tenant_id,code);
