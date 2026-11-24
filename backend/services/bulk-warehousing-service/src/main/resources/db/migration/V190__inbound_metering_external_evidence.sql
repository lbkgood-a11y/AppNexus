-- 整单外部计量登记将来源类型和凭证标识一并固化。历史无结构化凭证键的记录保持 NULL。
ALTER TABLE bw_metering_order
  ADD COLUMN external_source VARCHAR(24) NULL COMMENT '人工复核外部凭证来源：轨道衡 RAIL_SCALE 或水尺 DRAFT_SURVEY',
  ADD COLUMN external_reference VARCHAR(96) CHARACTER SET ascii COLLATE ascii_general_ci NULL
    COMMENT '来源签发的整单凭证标识，租户内同来源忽略大小写防重，没有结构化凭证时为空',
  ADD UNIQUE KEY uk_bw_meter_external_evidence (tenant_id, external_source, external_reference),
  ADD CONSTRAINT ck_bw_meter_external_evidence CHECK (
    (external_source IS NULL AND external_reference IS NULL) OR
    (method='EXTERNAL_NET' AND external_source IS NOT NULL AND external_source IN ('RAIL_SCALE','DRAFT_SURVEY')
      AND external_reference IS NOT NULL AND CHAR_LENGTH(TRIM(external_reference))>0)
  );

-- 独立计量岗位操作，真实角色及行范围由统一授权中心配置。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0190-7000-8000-000000000001'),'TENANT','warehousing.inboundmetering:register_external',
 'warehousing.inboundmetering','register_external','确认外部计量','Register external inbound measurement',
 '人工复核并登记模板允许的轨道衡或水尺整单净重凭证');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.inboundmetering:register_external';
