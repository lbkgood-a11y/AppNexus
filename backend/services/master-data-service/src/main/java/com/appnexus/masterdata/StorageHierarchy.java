package com.appnexus.masterdata;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import static com.appnexus.masterdata.MasterData.*;

/** 仓库、库区、库位固定三层约束；仅在原服务完成独立权限校验后的写事务内调用。 */
final class StorageHierarchy {
  private StorageHierarchy() {}
  private static final Set<String> AREA_TYPES=Set.of("RECEIVING","WORK","GOOD","DEFECTIVE","PACKAGING","VIRTUAL","FINISHED");

  /** 父级编码必填且不可跨租户；库容单位吨，缺省未知，允许非负数且最多18位整数、6位小数。 */
  static void validate(Kind kind,Map<String,String> values){
    if(parent(kind)==null)return;
    if(!values.get("parentCode").matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();
    if(!values.get("capacity").matches("|[0-9]{1,18}(\\.[0-9]{1,6})?"))throw invalid();
    if(kind==Kind.STORAGE_AREA&&!values.get("areaType").isEmpty()&&!AREA_TYPES.contains(values.get("areaType")))throw invalid();
  }

  /**
   * 锁定直属上级使创建与父级停用串行；父级创建后不可改变，防止隐式搬迁整个子树。
   * @param id 新建为空；修改为本租户已有标识
   * @param values 通过白名单校验的字段；parentCode 为直属上级编码
   */
  static void prepare(JdbcTemplate jdbc,VerifiedTenantContext c,Kind kind,String id,Map<String,String> values){
    Kind parent=parent(kind);if(parent==null)return;
    var active=jdbc.queryForList("SELECT enabled FROM "+parent.table()+" WHERE tenant_id=UUID_TO_BIN(?) AND code=? FOR UPDATE",Boolean.class,c.tenantId().toString(),values.get("parentCode"));
    if(active.isEmpty())throw invalid();
    if(id==null){if(!active.getFirst())throw invalid();return;}
    var previous=jdbc.queryForList("SELECT parent_code FROM "+kind.table()+" WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",String.class,c.tenantId().toString(),id);
    if(previous.isEmpty()||!previous.getFirst().equals(values.get("parentCode")))throw new Rejection(409,"MASTER_DATA_CHANGED");
  }

  /** 启用要求上级启用；停用先锁当前记录再检查启用下级，禁止留下启用的孤立子节点。 */
  static void status(JdbcTemplate jdbc,VerifiedTenantContext c,Kind kind,String id,boolean enabled){
    Kind parent=parent(kind);
    if(parent!=null){
      var codes=jdbc.queryForList("SELECT parent_code FROM "+kind.table()+" WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",String.class,c.tenantId().toString(),id);
      if(codes.isEmpty())throw new Rejection(409,"MASTER_DATA_CHANGED");
      var active=jdbc.queryForList("SELECT enabled FROM "+parent.table()+" WHERE tenant_id=UUID_TO_BIN(?) AND code=? FOR UPDATE",Boolean.class,c.tenantId().toString(),codes.getFirst());
      if(active.isEmpty()||(enabled&&!active.getFirst()))throw invalid();
    }
    Kind child=kind==Kind.WAREHOUSE?Kind.STORAGE_AREA:kind==Kind.STORAGE_AREA?Kind.STORAGE_LOCATION:null;
    if(child==null)return;
    var codes=jdbc.queryForList("SELECT code FROM "+kind.table()+" WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE",String.class,c.tenantId().toString(),id);
    if(!enabled&&!codes.isEmpty()&&!jdbc.queryForList("SELECT id FROM "+child.table()+" WHERE tenant_id=UUID_TO_BIN(?) AND parent_code=? AND enabled=TRUE LIMIT 1 FOR UPDATE",byte[].class,c.tenantId().toString(),codes.getFirst()).isEmpty())throw new Rejection(409,"STORAGE_ACTIVE_CHILDREN");
  }

  /** 固定父对象映射，禁止客户端提供层级或表名。 */
  private static Kind parent(Kind kind){return kind==Kind.STORAGE_AREA?Kind.WAREHOUSE:kind==Kind.STORAGE_LOCATION?Kind.STORAGE_AREA:null;}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
