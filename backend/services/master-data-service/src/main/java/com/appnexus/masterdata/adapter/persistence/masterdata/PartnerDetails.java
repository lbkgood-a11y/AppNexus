package com.appnexus.masterdata.adapter.persistence.masterdata;
import com.appnexus.masterdata.adapter.persistence.PartnerDetailMapper;

import com.appnexus.platform.tenant.VerifiedTenantContext;

import java.util.*;
import java.time.LocalDate;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 客商聚合明细；所有变更先由父对象权限、租户与版本锁准入，再在同一事务维护明细及审计。 */
final class PartnerDetails {
  /** 固定字段白名单，列名仅由这些构建内标识生成；不允许请求决定 SQL 标识符。 */
  static final List<String> CONTACT=com.appnexus.masterdata.domain.partner.PartnerRules.CONTACT;
  static final List<String> BANK=com.appnexus.masterdata.domain.partner.PartnerRules.BANK;
  private PartnerDetails() {}
  /** 兼容同领域原校验入口；规则已集中在不依赖 JDBC 的领域类型。 */
  static boolean validTypes(String value){return com.appnexus.masterdata.domain.partner.PartnerRules.validTypes(value);}
  /** 应用提供当前 UTC 自然日，不改变历史业务日期校验语义。 */
  static void validate(Input input,Map<String,String> values){com.appnexus.masterdata.domain.partner.PartnerRules.validate(input,values,LocalDate.now(java.time.ZoneOffset.UTC));}
  private static String value(DetailInput row,String field){return row.values().getOrDefault(field,"").trim();}
  private static String column(String field){return field.replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
  private static String table(boolean bank){return bank?"md_partner_bank_account":"md_partner_contact";}
  /** 明细只跟随已授权父记录读取，不提供按任意明细 ID 查询的入口。 */
  static List<Detail> read(PartnerDetailMapper mapper,VerifiedTenantContext c,String parent,boolean bank,com.appnexus.platform.authorization.RowAccessControl.Predicate access){
    return mapper.read(c.tenantId().toString(),parent,bank,access).stream().map(row->{var values=new LinkedHashMap<String,String>();for(var field:bank?BANK:CONTACT)values.put(field,(String)row.get(field));return new Detail((String)row.get("id"),values,new Audit((String)row.get("creator"),utc(row.get("created_at")),(String)row.get("updater"),utc(row.get("updated_at"))));}).toList();
  }
  /** 父版本更新成功后维护聚合；外租户/外客商明细 ID 一律冲突，异常令整个事务回滚。 */
  static void save(PartnerDetailMapper mapper,VerifiedTenantContext c,String parent,Input input,com.appnexus.platform.configuration.BusinessValueCatalog dictionaries){
    saveRows(mapper,c,parent,input.contacts(),false,dictionaries);saveRows(mapper,c,parent,input.bankAccounts(),true,dictionaries);
  }
  /** 只更新有变化的明细以保持审计事实；删除采用逻辑标记保留历史，重新引用已删除 ID 会冲突。 */
  private static void saveRows(PartnerDetailMapper mapper,VerifiedTenantContext c,String parent,List<DetailInput> rows,boolean bank,com.appnexus.platform.configuration.BusinessValueCatalog dictionaries){
    if(rows==null)return;
    var existing=read(mapper,c,parent,bank,null);var remaining=new HashSet<String>();existing.forEach(row->remaining.add(row.id()));
    var fields=bank?BANK:CONTACT;String table=table(bank);
    for(var row:rows){
      boolean fresh=row.id()==null||row.id().isBlank();String id=fresh?UUID.randomUUID().toString():row.id();
      if(!fresh&&!remaining.remove(id))throw new Rejection(409,"MASTER_DATA_CHANGED");
      var values=new LinkedHashMap<String,String>();for(var field:fields)values.put(field,value(row,field));
      var previous=existing.stream().filter(old->old.id().equals(id)).findFirst().map(Detail::values).orElse(Map.of());
      if(!dictionaries.reference(c.tenantId(),c.accountId(),"BusinessPartnerDetail",parent+":"+id,com.appnexus.platform.configuration.BusinessValueCatalog.PARTNER,values,previous))throw invalid();
      if(!fresh&&existing.stream().anyMatch(old->old.id().equals(id)&&old.values().equals(values)))continue;
      if(fresh)mapper.create(c.tenantId().toString(),parent,bank,id,values,c.accountId().toString());else mapper.update(c.tenantId().toString(),parent,bank,id,values,c.accountId().toString());
    }
    for(var id:remaining)mapper.delete(c.tenantId().toString(),parent,bank,id,c.accountId().toString());
  }
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
 private static String utc(Object value){return value==null?null:((java.sql.Timestamp)value).toInstant().toString();}
}
