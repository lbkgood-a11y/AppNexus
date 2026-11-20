package com.appnexus.masterdata.application.product;
/** 商品图片技术规范化输出端口，只有本模块媒体适配器可以实现。 */
public interface ProductImageContent {
 /** 标准PNG内容、像素宽高；不保留原始文件名、EXIF或任意外部地址。 */
 record Image(byte[] bytes,int width,int height) {}
 /** @param bytes 已按5MiB上限读取的原始图片 @return 经格式、尺寸和重编码上限校验的PNG */
 Image canonicalize(byte[] bytes);
}
