-- 打印策略固定场景与投影契约，不把业务原文存入解析表。
ALTER TABLE doc_resolution MODIFY actor VARCHAR(128) NOT NULL,
 ADD COLUMN scene_code VARCHAR(64) NULL,ADD COLUMN data_contract VARCHAR(128) NULL;
