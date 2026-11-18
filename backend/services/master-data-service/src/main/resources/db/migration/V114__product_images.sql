-- 图片采用真实数据库二进制存储，上传原子绑定商品，不产生临时对象或外部公开URL。
CREATE TABLE md_product_image (
 id BINARY(16) NOT NULL COMMENT '服务端生成图片UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 product_id BINARY(16) NOT NULL COMMENT '同租户商品UUID',
 media_type VARCHAR(32) NOT NULL COMMENT '实际解码验证的image/png或image/jpeg',
 image_data MEDIUMBLOB NOT NULL COMMENT '解码后重编码的图片内容，最大5MiB，去除原始元数据',
 content_hash BINARY(32) NOT NULL COMMENT '重编码内容SHA256用于一致性核对，不是访问凭证',
 width_px INT NOT NULL COMMENT '图片宽度，1至4096像素',
 height_px INT NOT NULL COMMENT '图片高度，1至4096像素，总像素不超过1600万',
 deleted_at TIMESTAMP(6) NULL COMMENT '解绑UTC时间，保留受权历史，不再作为当前图片返回',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无主体系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无主体系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC修改时间，微秒，与正文同事务',
 PRIMARY KEY(id), KEY ix_pi_product(tenant_id,product_id,deleted_at,id),
 CONSTRAINT fk_pi_product FOREIGN KEY(tenant_id,product_id) REFERENCES md_product_v1(tenant_id,id),
 CONSTRAINT ck_pi_size CHECK(OCTET_LENGTH(image_data) BETWEEN 1 AND 5242880),
 CONSTRAINT ck_pi_dimensions CHECK(width_px BETWEEN 1 AND 4096 AND height_px BETWEEN 1 AND 4096 AND width_px*height_px<=16000000),
 CONSTRAINT ck_pi_type CHECK(media_type IN ('image/png','image/jpeg'))
) COMMENT='受商品读取权限保护的真实图片存储；不公开外链，无上传临时文件';
