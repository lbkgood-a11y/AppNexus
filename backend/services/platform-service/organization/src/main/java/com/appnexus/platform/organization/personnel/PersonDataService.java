package com.appnexus.platform.organization.personnel;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.appnexus.platform.organization.personnel.PersonData.*;

/**
 * 租户公共档案执行器；所有 SQL 强制带租户，所有调用重新鉴权，不缓存授权结果。
 * 仅表名和列名取自构建内白名单；业务值全部使用绑定参数，禁止任意 SQL 或动态对象执行。
 */
public final class PersonDataService {
  private final JdbcTemplate jdbc;
  private final AccessAuthorization access;
  private final TransactionTemplate transaction;
  /**
   * @param jdbc 宿主数据源
   * @param access 公开授权端口
   * @param manager 宿主事务管理器 */
  public PersonDataService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager) {
    this.jdbc=jdbc;this.access=access;this.transaction=new TransactionTemplate(manager);
  }

  /**
   * 分页读取当前租户同类档案，不开放跨租户或免权限目录。
   *
   * @param c 可信请求上下文，不能从请求体构造
   *
   * @param kind 固定档案类型
   *
   * @param keyword 编码或名称的字面包含查询，缺省空，最长 200 字符；百分号不作通配符
   *
   * @param enabled 缺省 null 查询全部，true/false 分别查询启用/停用
   *
   * @param page 从 1 开始，最大 1000000；缺省 1
   *
   * @param size 每页条数，1–100；缺省 20
   *
   * @throws Rejection 无查看权限返回 403，分页或关键词不合法返回 400
   * @return 授权范围内的分页及真实总数
   */
  public Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size) {
    require(c,kind,"read");
    if(page<1||page>1000000||size<1||size>100||keyword==null||keyword.length()>200)throw invalid();
    String where=" WHERE tenant_id=UUID_TO_BIN(?) AND (LOCATE(?,code)>0 OR LOCATE(?,name)>0)"+(enabled==null?"":" AND enabled=?");
    var args=new ArrayList<Object>(List.of(c.tenantId().toString(),keyword.trim(),keyword.trim()));
    if(enabled!=null)args.add(enabled);
    long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+kind.table()+where,Long.class,args.toArray());
    args.add(size);args.add((page-1)*size);
    // 人员列表只返回摘要，证件、家庭及履历字段仅经详情授权读取。
    var listFields=kind==Kind.PERSON?List.of("englishName","phone","email","employmentStatus"):kind.fields;
    var rows=jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,enabled,version,BIN_TO_UUID(created_by) audit_created_by,created_at,BIN_TO_UUID(updated_by) audit_updated_by,updated_at,"+String.join(",",listFields.stream().map(kind::column).toList())+" FROM "+kind.table()+where+" ORDER BY code,id LIMIT ? OFFSET ?",(r,n)->{
      var attributes=new LinkedHashMap<String,String>();for(String field:listFields)attributes.put(field,r.getString(kind.column(field)));
      return new Row(r.getString("id"),r.getString("code"),r.getString("name"),attributes,r.getBoolean("enabled"),r.getLong("version"),new Audit(r.getString("audit_created_by"),r.getTimestamp("created_at")==null?null:r.getTimestamp("created_at").toInstant().toString(),r.getString("audit_updated_by"),r.getTimestamp("updated_at")==null?null:r.getTimestamp("updated_at").toInstant().toString()));
    },args.toArray());
    return new Page(rows,total);
  }

  /**
   * 创建启用档案；数据库唯一约束防止并发创建同一编码。重复提交不会创建重复档案，也不自动重放。
   *
   * @param c 可信主体
   * @param kind 固定类型
   * @param input 字段契约
   *
   * @throws Rejection 无新增权限返回 403，字段非法返回 400，编码冲突返回 409
   * @return 新建档案标识；编码冲突返回 409，输入错误返回 400
   */
  public Map<String,String> create(VerifiedTenantContext c,Kind kind,Input input) {
    require(c,kind,"create");var values=validate(kind,input);String id=UUID.randomUUID().toString();
    try {
      return transaction.execute(status->{
        var args=new ArrayList<Object>(List.of(id,c.tenantId().toString(),input.code().trim(),input.name().trim()));args.addAll(values.values());
        jdbc.update("INSERT INTO "+kind.table()+"(id,tenant_id,code,name,"+String.join(",",kind.fields.stream().map(kind::column).toList())+") VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,"+String.join(",",Collections.nCopies(kind.fields.size(),"?"))+")",args.toArray());
        // 审计主体来自可信会话；四个字段均在同一事务内写入，系统任务可通过 NULL 表达未知主体。
        jdbc.update("UPDATE "+kind.table()+" SET created_by=UUID_TO_BIN(?),created_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.accountId().toString(),c.accountId().toString(),c.tenantId().toString(),id);
        return Map.of("id",id);
      });
    } catch(DuplicateKeyException e){throw new Rejection(409,"MASTER_DATA_CODE_CONFLICT");}
  }

  /**
   * 修改原档案；编码、启停与租户不可经此接口更改。无权 ID 和不存在 ID 均不暴露其他租户事实。
   *
   * @param c 可信主体
   * @param kind 固定类型
   * @param id 当前租户档案 UUID
   * @param input 含当前版本的字段输入
   * @throws Rejection 无编辑权限返回 403，输入非法返回 400，版本变化或对象不可更新返回 409
   */
  public void update(VerifiedTenantContext c,Kind kind,String id,Input input) {
    require(c,kind,"update");uuid(id);var values=validate(kind,input);
    transaction.executeWithoutResult(status->{
      var args=new ArrayList<Object>(List.of(input.name().trim()));args.addAll(values.values());
      args.add(c.tenantId().toString());args.add(id);args.add(input.version());args.add(input.code().trim());
      int count=jdbc.update("UPDATE "+kind.table()+" SET name=?,"+String.join(",",kind.fields.stream().map(f->kind.column(f)+"=?").toList())+",version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=? AND code=?",args.toArray());
      if(count!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");
      audit(c,kind,id);
    });
  }

  /**
   * 启停独立鉴权并比较版本；不直接删除档案，也不将停用解释为撤销历史业务。
   *
   * @param c 可信主体
   * @param kind 固定类型
   * @param id 当前租户档案 UUID
   * @param input 显式启停和当前版本
   * @throws Rejection 无启停权限返回 403，输入非法返回 400，版本变化或对象不可更新返回 409
   */
  public void setStatus(VerifiedTenantContext c,Kind kind,String id,Status input) {
    require(c,kind,"set_status");uuid(id);
    if(input==null||input.enabled()==null||input.version()<0)throw invalid();
    transaction.executeWithoutResult(status->{
      if(jdbc.update("UPDATE "+kind.table()+" SET enabled=?,version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",input.enabled(),c.tenantId().toString(),id,input.version())!=1)
        throw new Rejection(409,"MASTER_DATA_CHANGED");
      audit(c,kind,id);
    });
  }

  /** 权限每次经公开端口复核；未知权限、无效成员和缺少上下文一律拒绝。 */
  private void require(VerifiedTenantContext c,Kind kind,String action) {
    if(c==null||kind==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),kind.resource()+":"+action))
      throw new Rejection(403,"MASTER_DATA_DENIED");
  }
  /** 严格白名单与长度校验，避免把任意 JSON 当作已实现的业务扩展能力。 */
  static LinkedHashMap<String,String> validate(Kind kind,Input input) {
    if(input==null||input.code()==null||!input.code().trim().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||input.name()==null||input.name().isBlank()||input.name().trim().length()>200||input.version()<0)throw invalid();
    var attributes=input.attributes()==null?Map.<String,String>of():input.attributes();
    if(!kind.fields.containsAll(attributes.keySet()))throw invalid();
    var values=new LinkedHashMap<String,String>();
    for(String field:kind.fields){String value=Objects.toString(attributes.get(field),"").trim();if(value.length()>500||value.chars().anyMatch(Character::isISOControl))throw invalid();values.put(field,value);}
    if(kind==Kind.PERSON&&!values.get("email").isEmpty()&&(values.get("email").length()>200||!values.get("email").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw invalid();
    if(kind==Kind.PERSON){
      if(values.get("employmentStatus").isEmpty())values.put("employmentStatus","ACTIVE");
      PersonnelSchema.validateProfile(values);
    }
    return values;
  }
  /** 更新人员档案的最后修改主体；时间由数据库生成，避免客户端时钟漂移。 */
  private void audit(VerifiedTenantContext c,Kind kind,String id){jdbc.update("UPDATE "+kind.table()+" SET updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.accountId().toString(),c.tenantId().toString(),id);}
  private static void uuid(String id){try{if(!UUID.fromString(id).toString().equalsIgnoreCase(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
