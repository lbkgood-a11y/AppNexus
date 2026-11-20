package com.appnexus.platform.persistence.authorization;
import com.appnexus.platform.authorization.PolicyTemplates;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.authorization.port.PolicyTemplateStorage;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
/** 模板固定SQL、JSON与REQUIRED事务技术适配，角色替换仍使用正式统一权限用例。 */
@Component
public final class MyBatisPolicyTemplateStorage implements PolicyTemplateStorage {
 private final AuthorizationTemplateMapper mapper;private final TransactionTemplate tx;private final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
 /** @param mapper 原固定模板SQL @param manager 同授权执行器数据源事务管理器 */
 public MyBatisPolicyTemplateStorage(AuthorizationTemplateMapper mapper,PlatformTransactionManager manager){this.mapper=mapper;tx=new TransactionTemplate(manager);}
 public void save(UUID id,UUID owner,String name,RolePermissionManagement.AuthorizationAssignment value,UUID actor){mapper.statement1(id.toString(),owner.toString(),name,json.writeValueAsString(value),actor.toString());}
 public List<PolicyTemplates.View> list(UUID owner){return mapper.summaries(owner.toString()).stream().map(row->new PolicyTemplates.View(UUID.fromString((String)row.get("id")),(String)row.get("name"))).toList();}
 public RolePermissionManagement.AuthorizationAssignment load(UUID template,UUID owner){var rows=mapper.statement2(template.toString(),owner.toString());if(rows.size()!=1)throw new PolicyTemplates.Rejection();return json.readValue(rows.getFirst(),RolePermissionManagement.AuthorizationAssignment.class);}
 public <T>T atomic(java.util.function.Supplier<T> work){return tx.execute(status->work.get());}
}
