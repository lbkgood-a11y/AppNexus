package com.appnexus.masterdata.domain;

import java.util.*;
import com.appnexus.masterdata.domain.geography.GeographyStandards;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 固定主数据输入领域规则；不依赖存储与框架，标准地理快照仅作为不可变参考数据。 */
public final class MasterDataRules {
  private MasterDataRules() {}
  /**
   * 严格白名单与长度校验，避免把任意 JSON 当作已实现的业务扩展能力。
   * @param kind 构建内固定档案类型，不接受任意表名
   * @param input 原始业务输入，不含可信主体和审计时间
   * @param today 应用提供的 UTC 自然日，仅用于客商成立日期的上界校验
   * @return 按固定字段顺序排列的标准化属性；缺省文本为空串
   * @throws Rejection 字段未知、格式不合法或业务约束不成立
   */
  public static LinkedHashMap<String,String> validate(Kind kind,Input input,java.time.LocalDate today) {
    if(input==null||input.code()==null||!input.code().trim().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||input.name()==null||input.name().isBlank()||input.name().trim().length()>200||input.version()<0)throw invalid();
    var attributes=input.attributes()==null?Map.<String,String>of():input.attributes();
    if(!kind.fields().containsAll(attributes.keySet()))throw invalid();
    var values=new LinkedHashMap<String,String>();
    for(String field:kind.fields()){String value=Objects.toString(attributes.get(field),"").trim();if(value.length()>500||value.chars().anyMatch(Character::isISOControl))throw invalid();values.put(field,value);}
    if(kind==Kind.PARTNER&&!com.appnexus.masterdata.domain.partner.PartnerRules.validTypes(values.get("partnerType")))throw invalid();
    if(kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION){
      // 标准目录仅提供缺省值；显式选择上级允许租户调整层级，不再把自定义码伪装成标准码。
      var standard=GeographyStandards.entries(kind==Kind.SUBDIVISION).stream().filter(e->e.code().equals(input.code().trim())).findFirst();
      if(kind==Kind.COUNTRY_REGION&&standard.isPresent())for(String key:List.of("alpha3Code","numericCode")){
        String supplied=values.get(key);if(!supplied.isEmpty()&&!supplied.equals(standard.get().attributes().get(key)))throw invalid();
      }
      standard.ifPresent(e->e.attributes().forEach((key,value)->{if(values.getOrDefault(key,"").isEmpty())values.put(key,value);}));
      if(kind==Kind.COUNTRY_REGION&&(!values.get("alpha3Code").matches("|[A-Z]{3}")||!values.get("numericCode").matches("|[0-9]{3}")))throw invalid();
    }
    if(kind==Kind.WAREHOUSE)com.appnexus.masterdata.domain.warehouse.WarehouseProfile.validate(values);
    com.appnexus.masterdata.domain.warehouse.StorageRules.validate(kind,values);
    if(kind==Kind.PARTNER)com.appnexus.masterdata.domain.partner.PartnerRules.validate(input,values,today);
    else if(input.contacts()!=null||input.bankAccounts()!=null)throw invalid();
    return values;
  }

  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
