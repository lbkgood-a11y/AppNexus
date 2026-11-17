package com.appnexus.masterdata;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import static com.appnexus.masterdata.MasterData.*;

/** 客商聚合明细；所有变更先由父对象权限、租户与版本锁准入，再在同一事务维护明细及审计。 */
final class PartnerDetails {
  /** 固定字段白名单，列名仅由这些构建内标识生成；不允许请求决定 SQL 标识符。 */
  static final List<String> CONTACT=List.of("contactType","contactName","phone","address","isDefault","email","remarks");
  static final List<String> BANK=List.of("taxAccount","basicAccount","isDefault","bankName","accountNumber","accountAlias","currency","cnaps","swiftCode","remarks");
  private PartnerDetails() {}
  /** 多重角色以逗号存储；保留原 BOTH 语义，拒绝重复、空片段及未知角色。 */
  static boolean validTypes(String value){
    if("BOTH".equals(value))return true;
    var types=Arrays.asList(value.split(",",-1));
    return !types.isEmpty()&&new HashSet<>(types).size()==types.size()&&types.stream().allMatch(type->type.matches("[A-Z][A-Z0-9_]{0,63}"));
  }
  /** 校验完整聚合；业务日期不转时间点，金额单位万元，最多 18 位整数及 6 位小数，缺省为空且不填造字典。 */
  static void validate(Input input,Map<String,String> values){
    try{
      String date=values.get("establishedOn");if(!date.isEmpty()&&(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||LocalDate.parse(date).isAfter(LocalDate.now(java.time.ZoneOffset.UTC))))throw invalid();
      String capital=values.get("registeredCapital");if(!capital.isEmpty()&&(!capital.matches("[0-9]{1,18}(\\.[0-9]{1,6})?")||new BigDecimal(capital).signum()<0))throw invalid();
      if(!values.get("registeredCurrency").matches("(?:[A-Z]{3})?"))throw invalid();
      String listed=values.get("listed");if(!listed.isEmpty()&&!Set.of("true","false").contains(listed))throw invalid();
    }catch(java.time.DateTimeException|NumberFormatException e){throw invalid();}
    validateRows(input.contacts(),false);validateRows(input.bankAccounts(),true);
  }
  /** 默认联系人/银行账户各最多一个；默认值、基本户与税务专用标志必须是显式布尔文本。 */
  private static void validateRows(List<DetailInput> rows,boolean bank){
    if(rows==null)return;if(rows.size()>100)throw invalid();
    var ids=new HashSet<String>();int defaults=0,basic=0;
    for(var row:rows){
      if(row==null||row.values()==null||!(bank?BANK:CONTACT).containsAll(row.values().keySet()))throw invalid();
      if(row.id()!=null&&!row.id().isBlank()){try{if(!UUID.fromString(row.id()).toString().equals(row.id())||!ids.add(row.id()))throw invalid();}catch(IllegalArgumentException e){throw invalid();}}
      for(var entry:row.values().entrySet())if(entry.getValue()==null||entry.getValue().length()>500||entry.getValue().chars().anyMatch(Character::isISOControl))throw invalid();
      for(String key:bank?List.of("bankName","accountNumber","currency"):List.of("contactType","contactName","phone","address"))if(value(row,key).isBlank())throw invalid();
      for(String key:bank?List.of("taxAccount","basicAccount","isDefault"):List.of("isDefault"))if(!Set.of("true","false").contains(value(row,key)))throw invalid();
      if("true".equals(value(row,"isDefault")))defaults++;
      if(bank){
        if("true".equals(value(row,"basicAccount")))basic++;
        if(!value(row,"currency").matches("[A-Z]{3}")||!value(row,"cnaps").matches("(?:[0-9]{12})?")||!value(row,"swiftCode").matches("(?:[A-Z]{6}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)?"))throw invalid();
      }else if(!value(row,"email").isEmpty()&&(value(row,"email").length()>200||!value(row,"email").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw invalid();
    }
    if(defaults>1||basic>1)throw invalid();
  }
  private static String value(DetailInput row,String field){return row.values().getOrDefault(field,"").trim();}
  private static String column(String field){return field.replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
  private static String table(boolean bank){return bank?"md_partner_bank_account":"md_partner_contact";}
  /** 明细只跟随已授权父记录读取，不提供按任意明细 ID 查询的入口。 */
  static List<Detail> read(JdbcTemplate jdbc,VerifiedTenantContext c,String parent,boolean bank){
    return jdbc.query("SELECT *,BIN_TO_UUID(id) detail_id,BIN_TO_UUID(created_by) creator,BIN_TO_UUID(updated_by) updater FROM "+table(bank)+" WHERE tenant_id=UUID_TO_BIN(?) AND partner_id=UUID_TO_BIN(?) AND deleted=FALSE ORDER BY created_at,id",(r,n)->{
      var values=new LinkedHashMap<String,String>();for(var field:bank?BANK:CONTACT)values.put(field,r.getString(column(field)));
      return new Detail(r.getString("detail_id"),values,new Audit(r.getString("creator"),r.getTimestamp("created_at").toInstant().toString(),r.getString("updater"),r.getTimestamp("updated_at").toInstant().toString()));
    },c.tenantId().toString(),parent);
  }
  /** 父版本更新成功后维护聚合；外租户/外客商明细 ID 一律冲突，异常令整个事务回滚。 */
  static void save(JdbcTemplate jdbc,VerifiedTenantContext c,String parent,Input input,com.appnexus.platform.configuration.BusinessValueCatalog dictionaries){
    saveRows(jdbc,c,parent,input.contacts(),false,dictionaries);saveRows(jdbc,c,parent,input.bankAccounts(),true,dictionaries);
  }
  /** 只更新有变化的明细以保持审计事实；删除采用逻辑标记保留历史，重新引用已删除 ID 会冲突。 */
  private static void saveRows(JdbcTemplate jdbc,VerifiedTenantContext c,String parent,List<DetailInput> rows,boolean bank,com.appnexus.platform.configuration.BusinessValueCatalog dictionaries){
    if(rows==null)return;
    var existing=read(jdbc,c,parent,bank);var remaining=new HashSet<String>();existing.forEach(row->remaining.add(row.id()));
    var fields=bank?BANK:CONTACT;String table=table(bank);
    for(var row:rows){
      boolean fresh=row.id()==null||row.id().isBlank();String id=fresh?UUID.randomUUID().toString():row.id();
      if(!fresh&&!remaining.remove(id))throw new Rejection(409,"MASTER_DATA_CHANGED");
      var values=new LinkedHashMap<String,String>();for(var field:fields)values.put(field,value(row,field));
      var previous=existing.stream().filter(old->old.id().equals(id)).findFirst().map(Detail::values).orElse(Map.of());
      if(!dictionaries.reference(c.tenantId(),c.accountId(),"BusinessPartnerDetail",parent+":"+id,com.appnexus.platform.configuration.BusinessValueCatalog.PARTNER,values,previous))throw invalid();
      if(!fresh&&existing.stream().anyMatch(old->old.id().equals(id)&&old.values().equals(values)))continue;
      var args=new ArrayList<Object>(values.values());args.add(c.accountId().toString());
      if(fresh){
        args.add(c.accountId().toString());args.add(id);args.add(c.tenantId().toString());args.add(parent);
        jdbc.update("INSERT INTO "+table+"("+String.join(",",fields.stream().map(PartnerDetails::column).toList())+",created_by,updated_by,id,tenant_id,partner_id) VALUES("+String.join(",",Collections.nCopies(fields.size(),"?"))+",UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",args.toArray());
      }else{
        args.add(c.tenantId().toString());args.add(parent);args.add(id);
        jdbc.update("UPDATE "+table+" SET "+String.join(",",fields.stream().map(f->column(f)+"=?").toList())+",updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND partner_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",args.toArray());
      }
    }
    for(var id:remaining)jdbc.update("UPDATE "+table+" SET deleted=TRUE,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND partner_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.accountId().toString(),c.tenantId().toString(),parent,id);
  }
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
