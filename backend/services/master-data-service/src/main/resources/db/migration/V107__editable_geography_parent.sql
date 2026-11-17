-- 国别可挂洲节点；历史空值继续使用标准洲归属，不伪造历史修改人或修改时间。
ALTER TABLE md_country_region ADD COLUMN parent_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '上级洲节点键，使用 continent: 前缀；历史空值沿用标准洲归属' AFTER numeric_code;
-- 自定义区划和国别编码最长 64 字符；扩大关联列，不改写历史编码及引用。
ALTER TABLE md_subdivision MODIFY COLUMN parent_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '上级国别或区划编码，最长64字符；历史空值沿用标准父级或所属国别',
  MODIFY COLUMN country_code VARCHAR(64) NOT NULL COMMENT '所属国别编码，最长64字符；由可信租户内的上级链确定';
-- 标准国别属性仍须匹配快照；自定义国别允许缺省，不能在列说明中继续声称全部必填。
ALTER TABLE md_country_region MODIFY COLUMN alpha3_code VARCHAR(500) NOT NULL DEFAULT '' COMMENT '三位大写字母代码；自定义国别可空，标准国别由快照补齐且非空输入须匹配',
  MODIFY COLUMN numeric_code VARCHAR(500) NOT NULL DEFAULT '' COMMENT '三位数字代码，保留前导零；自定义国别可空，标准国别由快照补齐且非空输入须匹配';
