package com.appnexus.masterdata.adapter.media;
import com.appnexus.masterdata.application.product.ProductImageContent;
import com.appnexus.masterdata.api.masterdata.MasterData;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
/** 图片技术规范化；只允许PNG/JPEG，限制尺寸与解码规模，重编码去除原始元数据。 */
@Component
public final class ImageIoProductImageContent implements ProductImageContent {
 public Image canonicalize(byte[] bytes){byte[] canonical;int width,height;
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid();var reader=readers.next();try{String format=reader.getFormatName();if(!Set.of("png","jpeg","jpg").contains(format.toLowerCase(Locale.ROOT)))throw invalid();reader.setInput(input,true,true);width=reader.getWidth(0);height=reader.getHeight(0);if(width<1||height<1||width>4096||height>4096||(long)width*height>16000000)throw invalid();var decoded=reader.read(0);var output=new ByteArrayOutputStream();if(!ImageIO.write(decoded,"png",output))throw invalid();canonical=output.toByteArray();if(canonical.length>5242880)throw invalid();}finally{reader.dispose();}}catch(IOException e){throw invalid();}
  return new Image(canonical,width,height);
 }
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_IMAGE_INVALID");}
}
