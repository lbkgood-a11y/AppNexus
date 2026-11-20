from pathlib import Path
root=Path('backend/services/platform-service/adapters/security/src/main/java/com/appnexus/platform/security')
def mapper_add(name,text):
 p=root/(name+'SqlMapper.java');s=p.read_text(encoding='utf-8');s=s.rstrip()[:-1]+text+'\n}\n';p.write_text(s,encoding='utf-8')
def clean(s):return s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('this.jdbc=jdbc;','').replace('JdbcTemplate jdbc,','').replace(',JdbcTemplate jdbc,',',')
mapper_add('TenantInvitations','''
 /** @param tenant 受权平台操作者已验证的租户UUID @return 最多100个邀请摘要，禁止返回Token哈希 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(account_id) account,status,expires_at FROM tenant_invitation WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY created_at DESC,id LIMIT 100")
 List<Map<String,Object>> summaries(@Param("tenant") String tenant);''')
p=root/'TenantInvitations.java';s=p.read_text(encoding='utf-8');start=s.index('return jdbc.query(');end=s.index(';}',start)
s=s[:start]+'''return statements.summaries(tenant.toString()).stream().map(row->new View(UUID.fromString((String)row.get("id")),tenant,UUID.fromString((String)row.get("account")),(String)row.get("status"),((java.sql.Timestamp)row.get("expires_at")).toInstant())).toList()'''+s[end:];p.write_text(clean(s),encoding='utf-8')
mapper_add('IntegrationClients','''
 /** @param tenant 已验证租户UUID @return 最多100条客户端摘要，不含密钥 */
 @Select("SELECT id,name,enabled,authority_version FROM integration_client WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY created_at,id LIMIT 100") List<Map<String,Object>> summaries(@Param("tenant") String tenant);
 /** @param id 机密客户端标识 @return 权威租户及启用事实，故障不缓存放行 */
 @Select("SELECT c.id,BIN_TO_UUID(c.tenant_id) tenant,c.authority_version,c.enabled clientEnabled,t.enabled tenantEnabled FROM integration_client c JOIN platform_tenant t ON t.id=c.tenant_id WHERE c.id=#{id}") List<Map<String,Object>> policy(@Param("id") String id);''')
p=root/'IntegrationClients.java';s=p.read_text(encoding='utf-8');start=s.index('return jdbc.query(');end=s.index(';',start)
s=s[:start]+'''return statements.summaries(tenant.toString()).stream().map(row->view((String)row.get("id"),tenant,(String)row.get("name"),Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("authority_version")).intValue())).toList()'''+s[end:]
start=s.index('var values=jdbc.query(');end=s.index(';',start);s=s[:start]+'''var values=statements.policy(id).stream().map(row->new Policy((String)row.get("id"),UUID.fromString((String)row.get("tenant")),((Number)row.get("authority_version")).intValue(),Boolean.TRUE.equals(row.get("clientEnabled"))&&Boolean.TRUE.equals(row.get("tenantEnabled")))).toList()'''+s[end:];p.write_text(clean(s),encoding='utf-8')
mapper_add('PolicyTemplates','''
 /** @param owner 平台或租户授权上下文确定的模板所有者UUID @return 最多100条模板名称，不返回可执行脚本 */
 @Select("SELECT BIN_TO_UUID(id) id,name FROM access_policy_template WHERE owner_id=UUID_TO_BIN(#{owner}) ORDER BY created_at DESC,id LIMIT 100") List<Map<String,Object>> summaries(@Param("owner") String owner);''')
p=root/'PolicyTemplates.java';s=p.read_text(encoding='utf-8');start=s.index('return jdbc.query(');end=s.index(';',start);s=s[:start]+'''return statements.summaries(owner(context).toString()).stream().map(row->new View(UUID.fromString((String)row.get("id")),(String)row.get("name"))).toList()'''+s[end:];p.write_text(clean(s),encoding='utf-8')
mapper_add('ReliableEvents','''
 /** @param limit 最多100条，由平台任务读取准入校验 @return 任务摘要，不返回租约或业务正文 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(tenant_id) tenant,event_type,status,attempts,failure_code FROM platform_outbox ORDER BY created_at DESC,id DESC LIMIT #{limit}") List<Map<String,Object>> summaries(@Param("limit") int limit);''')
p=root/'ReliableEvents.java';s=p.read_text(encoding='utf-8');start=s.index('return jdbc.query(');end=s.index(';',start);s=s[:start]+'''return statements.summaries(limit).stream().map(row->new Task(UUID.fromString((String)row.get("id")),UUID.fromString((String)row.get("tenant")),(String)row.get("event_type"),(String)row.get("status"),((Number)row.get("attempts")).intValue(),(String)row.get("failure_code"))).toList()'''+s[end:];p.write_text(clean(s),encoding='utf-8')
mapper_add('PartnerCallFilter','''
 /** 生成键仅来自数据库，不使用SELECT LAST_INSERT_ID跨连接查询。 */
 final class Start {
  private Long id;public Long getId(){return id;}public void setId(Long id){this.id=id;}
  public final String tenant,client,operation,trace;
  /** @param tenant Token验证租户 @param client 验证机密客户端 @param operation 固定操作摘要 @param trace 服务端追踪码 */
  public Start(String tenant,String client,String operation,String trace){this.tenant=tenant;this.client=client;this.operation=operation;this.trace=trace;}
 }
 /** @param start 可信调用审计起始摘要，状态0表示结果未确认 @return 插入行数，生成键回填start.id */
 @Insert("INSERT INTO integration_call_audit(tenant_id,client_id,operation,trace_id,status) VALUES(UUID_TO_BIN(#{tenant}),#{client},#{operation},#{trace},0)")
 @Options(useGeneratedKeys=true,keyProperty="id") int start(Start start);''')
p=root/'PartnerCallFilter.java';s=p.read_text(encoding='utf-8');start=s.index('            var holder=');end=s.index('            Long count=',start);s=s[:start]+'''            var beginning=new PartnerCallFilterSqlMapper.Start(tenant,client,operation,trace);statements.start(beginning);
            auditId=Objects.requireNonNull(beginning.getId());
'''+s[end:];p.write_text(clean(s),encoding='utf-8')
p=root/'WebSecurityConfiguration.java';s=p.read_text(encoding='utf-8').replace('JdbcTemplate jdbc,org.springframework.data.redis.core.StringRedisTemplate redis)','PartnerCallFilterSqlMapper partnerCalls,org.springframework.data.redis.core.StringRedisTemplate redis)').replace('new PartnerCallFilter(jdbc,redis)','new PartnerCallFilter(redis,partnerCalls)');p.write_text(s,encoding='utf-8')
