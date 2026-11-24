-- 货号确认采用的批次模板版本；存量无绑定，无历史单据写入。
ALTER TABLE md_product_sku
 ADD COLUMN batch_template_version_id BINARY(16) NULL COMMENT '货号确认的批次模板版本UUID；NULL表示没有启用批次规则',
 ADD CONSTRAINT fk_sku_batch_template_version FOREIGN KEY (tenant_id,batch_template_version_id)
 REFERENCES md_product_template_version (tenant_id,id);
