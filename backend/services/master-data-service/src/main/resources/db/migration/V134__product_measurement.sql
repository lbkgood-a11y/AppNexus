-- 空值表示继承商品现有基础单位；存量商品及 SKU 无需生成新身份。
ALTER TABLE md_product_v1
  ADD COLUMN measurement_config JSON NULL COMMENT '商品业务单位及包装规格配置；NULL表示只使用现有基础单位；与商品版本及审计同事务更新';
