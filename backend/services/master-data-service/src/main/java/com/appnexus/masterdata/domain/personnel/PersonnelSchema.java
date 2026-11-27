package com.appnexus.masterdata.domain.personnel;
import com.appnexus.masterdata.organization.PersonData;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import static com.appnexus.masterdata.organization.PersonData.*;

/** 人员基础字段与十类明细固定契约；不提供自由 JSON 属性或客户端定义字段能力。 */
public final class PersonnelSchema {
  private PersonnelSchema(){}
  /** 明细分类与固定白名单；业务日期始终 yyyy-MM-dd，不按浏览器时区转换。 */
  public enum Section {
    ORGANIZATIONS("organizations",List.of("organizationId","isPrimary","enabled","startsOn","endsOn","remarks"),Set.of("organizationId")),
    POSITIONS("positions",List.of("organizationId","positionId","enabled","startsOn","endsOn","remarks"),Set.of("organizationId","positionId")),
    CERTIFICATES("certificates",List.of("certificateType","certificateNumber","certificateStatus","passedOn","startsOn","endsOn","remarks"),Set.of("certificateType","certificateNumber")),
    TRAINING("training",List.of("trainingName","provider","startsOn","endsOn","result","remarks"),Set.of("trainingName")),
    AWARDS("awards",List.of("awardName","issuer","awardedOn","remarks"),Set.of("awardName")),
    CONTACTS("contacts",List.of("contactType","contactValue","remarks"),Set.of("contactType","contactValue")),
    ADDRESSES("addresses",List.of("addressType","countryRegion","province","city","address","postalCode","remarks"),Set.of("addressType","address")),
    WORKHISTORY("workHistory",List.of("employer","department","jobTitle","startsOn","endsOn","remarks"),Set.of("employer")),
    REWARDSPENALTIES("rewardsPenalties",List.of("recordType","reason","issuer","recordedOn","remarks"),Set.of("recordType","reason")),
    EDUCATION("education",List.of("school","major","educationLevel","degree","startsOn","endsOn","remarks"),Set.of("school"));
    /** API 分类标识；区分大小写，不能作为 SQL 标识符。 */
    public final String code;
    /** 此分类允许保存的固定字段，缺省空文本，每项最多 500 字符。 */
    public final List<String> fields;
    /** 此分类必须填写的字段，无缺省值。 */
    public final Set<String> required;
    Section(String code,List<String> fields,Set<String> required){this.code=code;this.fields=fields;this.required=required;}
    /**
   * @param code 固定分类编码
   * @return 分类；未知值返回 400，不访问数据库 */
    public static Section parse(String code){return Arrays.stream(values()).filter(s->s.code.equals(code)).findFirst().orElseThrow(PersonnelSchema::invalid);}
  }
  /** 可配置分类由业务字典端口校验；此处仅约束真实固定业务状态，空值允许未采集；不把停用标志与在职状态混为一谈。 */
  public static void validateProfile(Map<String,String> values){
    choice(values,"employmentStatus",Set.of("ACTIVE","PROBATION","LEAVE","RETIRED"));
    String birth=values.get("birthDate");if(!birth.isEmpty()&&date(birth).isAfter(LocalDate.now(ZoneOffset.UTC)))throw invalid();
  }
  /**
   *
   * @param section 固定十类明细之一
   *
   * @param input 白名单字段值，最多 500 字符每项，不接受控制字符；日期字段允许空但不得无效
   *
   * @return 去首尾空白且补齐空字段的有序映射
   * @throws Rejection 未知字段、缺失必填、无效日期或区间颠倒返回 400
   */
  public static Map<String,String> validate(Section section,Map<String,String> input){
    if(input==null||!section.fields.containsAll(input.keySet()))throw invalid();
    var result=new LinkedHashMap<String,String>();
    for(String field:section.fields){
      String value=Objects.toString(input.get(field),"").trim();
      if(value.length()>500||value.chars().anyMatch(Character::isISOControl)||section.required.contains(field)&&value.isEmpty())throw invalid();
      if(field.endsWith("On")&&!value.isEmpty())date(value);
      if(field.endsWith("Id")&&!value.isEmpty())uuid(value);
      if(field.equals("enabled")&&value.isEmpty())value="true";
      if(field.equals("isPrimary")&&value.isEmpty())value="false";
      if((field.equals("isPrimary")||field.equals("enabled"))&&!Set.of("true","false").contains(value))throw invalid();
      result.put(field,value);
    }
    if(!result.getOrDefault("startsOn","").isEmpty()&&!result.getOrDefault("endsOn","").isEmpty()&&date(result.get("endsOn")).isBefore(date(result.get("startsOn"))))throw invalid();
    if(section==Section.CERTIFICATES)choice(result,"certificateStatus",Set.of("VALID","EXPIRED","REVOKED","PENDING"));
    if(section==Section.REWARDSPENALTIES)choice(result,"recordType",Set.of("REWARD","PENALTY"));
    return result;
  }
  /** 固定枚举允许空表示尚未采集；非空未知值拒绝，不自动猜测。 */
  private static void choice(Map<String,String> values,String field,Set<String> allowed){String value=values.getOrDefault(field,"");if(!value.isEmpty()&&!allowed.contains(value))throw invalid();}
  private static LocalDate date(String value){try{LocalDate date=LocalDate.parse(value);if(!date.toString().equals(value)||date.getYear()<1900||date.getYear()>9999)throw invalid();return date;}catch(DateTimeParseException e){throw invalid();}}
  /**
   * @param value 规范 UUID 文本
   * @return UUID；缺省或不规范返回 400 */
  public static UUID uuid(String value){try{UUID id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw invalid();return id;}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
