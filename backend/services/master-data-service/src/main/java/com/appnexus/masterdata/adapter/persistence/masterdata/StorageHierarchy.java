package com.appnexus.masterdata.adapter.persistence.masterdata;
import com.appnexus.masterdata.adapter.persistence.StorageHierarchyMapper;

import java.util.*;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 仓库、库区、库位固定三层约束；仅在原服务完成独立权限校验后的写事务内调用。 */
final class StorageHierarchy {
  private StorageHierarchy() {}

  /** 兼容同领域调用，固定字段规则集中在纯领域类型，父子锁逻辑仍在本适配器执行。 */
  static void validate(Kind kind,Map<String,String> values){com.appnexus.masterdata.domain.warehouse.StorageRules.validate(kind,values);}

  /**
   * 锁定直属上级使创建与父级停用串行；父级创建后不可改变，防止隐式搬迁整个子树。
   * @param id 新建为空；修改为本租户已有标识
   * @param values 通过白名单校验的字段；parentCode 为直属上级编码
   */
  static void prepare(StorageHierarchyMapper mapper,VerifiedTenantContext c,Kind kind,String id,Map<String,String> values){
    Kind parent=parent(kind);if(parent==null)return;
    String parentCode=values.get("parentCode");
    // 编辑先比对已经授权并锁定的原记录，不能用伪造父码探测其他仓库。
    if(id!=null){
      var previous=mapper.parentCode(c.tenantId().toString(),kind.name(),id,true);
      if(previous.isEmpty()||!previous.getFirst().equals(parentCode))throw new Rejection(409,"MASTER_DATA_CHANGED");
    }
    var active=mapper.parent(c.tenantId().toString(),kind.name(),parentCode);
    if(active.isEmpty()||(id==null&&!active.getFirst()))throw invalid();
  }

  /** 启用要求上级启用；停用先锁当前记录再检查启用下级，禁止留下启用的孤立子节点。 */
  static void status(StorageHierarchyMapper mapper,VerifiedTenantContext c,Kind kind,String id,boolean enabled){
    Kind parent=parent(kind);
    if(parent!=null){
      var codes=mapper.parentCode(c.tenantId().toString(),kind.name(),id,false);
      if(codes.isEmpty())throw new Rejection(409,"MASTER_DATA_CHANGED");
      var active=mapper.parent(c.tenantId().toString(),kind.name(),codes.getFirst());
      if(active.isEmpty()||(enabled&&!active.getFirst()))throw invalid();
    }
    Kind child=kind==Kind.WAREHOUSE?Kind.STORAGE_AREA:kind==Kind.STORAGE_AREA?Kind.STORAGE_LOCATION:null;
    if(child==null)return;
    var codes=mapper.code(c.tenantId().toString(),kind.name(),id);
    if(!enabled&&!codes.isEmpty()&&!mapper.children(c.tenantId().toString(),kind.name(),codes.getFirst()).isEmpty())throw new Rejection(409,"STORAGE_ACTIVE_CHILDREN");
  }

  /** 固定父对象映射，禁止客户端提供层级或表名。 */
  private static Kind parent(Kind kind){return kind==Kind.STORAGE_AREA?Kind.WAREHOUSE:kind==Kind.STORAGE_LOCATION?Kind.STORAGE_AREA:null;}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
