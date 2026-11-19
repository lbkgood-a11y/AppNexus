package com.appnexus.masterdata.domain.product;

import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.masterdata.MasterData.Rejection;
import java.util.UUID;

/** 商品主档文本及标识纯输入规则；不把数据库、授权或属性目录读取混入领域。 */
public final class ProductMainRules {
  private ProductMainRules(){}
  /** @param input 草稿输入，编码1–64个ASCII字母数字或._-，名称1–200字，其余字段按原上限验证 */
  public static void create(Input input){
    if(input==null||input.code()==null||!input.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();
    text(input.name(),200,true);text(input.shortName(),100,false);text(input.brand(),200,false);text(input.origin(),200,false);text(input.description(),20000,false);text(input.barcode(),500,false);
  }
  /** @param input 普通字段输入，版本必填；负数在原父锁约定中拒绝，不提前改变拒绝顺序 */
  public static void update(Update input){
    if(input==null||input.version()==null)throw invalid();
    text(input.name(),200,true);text(input.shortName(),100,false);text(input.brand(),200,false);text(input.origin(),200,false);text(input.description(),20000,false);text(input.barcode(),500,false);
  }
  /** @param id 小写标准 UUID 文本；不允许非标准格式绕过对象键一致性 */
  public static void identifier(String id){try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException error){throw invalid();}}
  private static void text(String value,int max,boolean required){
    if(value==null){if(required)throw invalid();return;}
    if(required&&value.isBlank()||value.length()>max||value.chars().anyMatch(character->Character.isISOControl(character)&&character!='\n'&&character!='\r'&&character!='\t'))throw invalid();
  }
  private static Rejection invalid(){return new Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
}
