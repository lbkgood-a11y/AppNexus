-- 货号计量配置。历史货号保留 NULL 并沿用商品计量规则；无需重写历史单据。
ALTER TABLE md_product_sku ADD COLUMN measurement_config JSON NULL
 COMMENT '货号生效库存单位、计价单位与包装换算配置；NULL 表示沿用商品计量';
