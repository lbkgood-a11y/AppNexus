-- 扩充模板定义用途，既有模板和已发布版本保留原用途。
-- 类目新增下游默认模板绑定，不生成商品主档属性项。
ALTER TABLE md_product_template
 DROP CHECK ck_ext_md_product_template_purpose,
 ADD CONSTRAINT ck_product_template_purpose CHECK (purpose IN ('BASIC','SPEC','QUALITY','EXTENSION','SKU','BATCH')),
 MODIFY COLUMN purpose VARCHAR(16) NOT NULL COMMENT '模板用途：商品基础、规格轴、商品质量、商品扩展、普通货号属性或批次属性';

-- 类目可发布普通货号和批次模板的默认版本，后续由 SPU 和 SKU 分别确认。
ALTER TABLE md_product_category_template
 DROP CHECK ck_ext_md_product_category_template_purpose,
 ADD CONSTRAINT ck_product_category_template_purpose CHECK (purpose IN ('BASIC','SPEC','QUALITY','EXTENSION','SKU','BATCH')),
 MODIFY COLUMN purpose VARCHAR(16) NOT NULL COMMENT '类目配置用途：商品属性和规格轴，或供下游采用的普通货号与批次默认模板';
