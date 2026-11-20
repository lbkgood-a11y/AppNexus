from pathlib import Path
base=Path('backend/services/platform-service/adapters/persistence/src/main')
p=base/'java/com/appnexus/platform/persistence/organization/MyBatisOrganizationDomain.java';s=p.read_text(encoding='utf-8')
s=s.replace('private final OrganizationDomainMapper mapper;private final com.appnexus.platform.organization.PersonnelCatalog persons; public MyBatisOrganizationDomain(OrganizationDomainMapper mapper,com.appnexus.platform.organization.PersonnelCatalog persons){this.mapper=mapper;this.persons=persons;}', '''private final OrganizationDomainMapper mapper;
 private final com.appnexus.platform.organization.PersonnelCatalog persons;
 private final com.appnexus.platform.authorization.AccessPlanning planning;
 private final com.appnexus.platform.authorization.RowAccessControl rows;
 /** @param mapper 组织固定存储映射 @param persons 人员公开端口 @param planning 实时统一权限计划 @param rows 公共范围执行器，不复制范围合并算法 */
 public MyBatisOrganizationDomain(OrganizationDomainMapper mapper,com.appnexus.platform.organization.PersonnelCatalog persons,com.appnexus.platform.authorization.AccessPlanning planning,com.appnexus.platform.authorization.RowAccessControl rows){this.mapper=mapper;this.persons=persons;this.planning=planning;this.rows=rows;}''')
s=s.replace('public List<Unit> units(){return mapper.units(tenant()).stream()', '''public List<Unit> units(){var plan=plan("read");return mapper.scopedUnits(tenant(),plan.rows().type()==com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ALL,plan.rows().type()==com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ORGANIZATION?plan.rows().organizationIds().stream().sorted().map(UUID::toString).toList():List.of()).stream()''')
s=s.replace('@Transactional public Unit createUnit(String code,UUID typeId,UUID parentId,String name){validateParent', '@Transactional public Unit createUnit(String code,UUID typeId,UUID parentId,String name){if(plan("create").rows().type()!=com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ALL)throw new SecurityException("ORGANIZATION_CREATE_SCOPE_REQUIRED");validateParent')
s=s.replace('public Unit updateUnit(UUID id,String name,boolean enabled,long version){var current=unit(id);', '@Transactional public Unit updateUnit(UUID id,String name,boolean enabled,long version){mapper.lockUnits(tenant());requireUnit("update",id);var current=unit(id);')
s=s.replace('var impact=impact(id);','var impact=rawImpact(id);')
s=s.replace('moveUnit(UUID id,UUID parentId,long version){mapper.lockUnits(tenant());var current=unit(id);','moveUnit(UUID id,UUID parentId,long version){mapper.lockUnits(tenant());requireUnit("update",id);if(parentId!=null)requireUnit("update",parentId);var current=unit(id);')
s=s.replace('public Impact impact(UUID id){unit(id);', 'public Impact impact(UUID id){requireUnit("read",id);return rawImpact(id);}\n private Impact rawImpact(UUID id){unit(id);')
s=s.replace('private Unit unit(UUID id){return units().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow(this::missing);}', '''/** 仅供已准入写用例检查父链及约束，不能作为对外读取入口；不能因缺少read而阻断合法update。 */
 private Unit unit(UUID id){return mapper.units(tenant()).stream().filter(r->id(r,"id").equals(id)).map(r->new Unit(id(r,"id"),s(r,"code"),id(r,"typeId"),nullableId(r,"parentId"),s(r,"name"),b(r,"enabled"),n(r,"version"))).findFirst().orElseThrow(this::missing);}
 /** 每个操作独立解析，不复用read权作为update准入；缺策略及未知范围由公共执行器拒绝。 */
 private com.appnexus.platform.authorization.AccessPlan plan(String action){var c=context();return planning.plan(com.appnexus.platform.authorization.AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"organization.unit:"+action,"organization.unit");}
 private void requireUnit(String action,UUID id){if(!rows.allowsOrganization(plan(action),id))throw new SecurityException("ORGANIZATION_DATA_ACCESS_DENIED");}
 private VerifiedTenantContext context(){var attributes=RequestContextHolder.getRequestAttributes();if(!(attributes instanceof ServletRequestAttributes request)||!(request.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext c))throw new SecurityException("Verified tenant context required");return c;}''')
p.write_text(s,encoding='utf-8')
p=base/'java/com/appnexus/platform/persistence/organization/OrganizationDomainMapper.java';s=p.read_text(encoding='utf-8').replace('"units","lockUnits"','"units","scopedUnits","lockUnits"').replace(' List<String> lockUnits(',''' /** @param tenant 可信租户二进制UUID @param all 已授权ALL范围 @param ids 解析后的组织UUID集合；非ALL空集必须拒绝 @return 在SQL中完成行范围过滤的组织列表 */
 List<Map<String,Object>> scopedUnits(@Param("__verifiedTenant") byte[] tenant,@Param("all") boolean all,@Param("ids") List<String> ids);
 List<String> lockUnits(''');p.write_text(s,encoding='utf-8')
p=base/'resources/com/appnexus/platform/persistence/organization/OrganizationDomainMapper.xml';s=p.read_text(encoding='utf-8').replace(' <select id="lockUnits"', ''' <!-- 所有展示入口使用相同可信租户和已解析范围；空集合拒绝，不在Java中过滤后分页。 -->
 <select id="scopedUnits" resultType="map">
 SELECT BIN_TO_UUID(id) id,code,BIN_TO_UUID(type_id) typeId,BIN_TO_UUID(parent_id) parentId,name,enabled,version FROM tenant_organization_unit WHERE tenant_id=#{__verifiedTenant}
 <if test="!all"><choose><when test="ids != null and !ids.isEmpty()"> AND id IN <foreach collection="ids" item="id" open="(" separator="," close=")">UUID_TO_BIN(#{id})</foreach></when><otherwise> AND 1=0</otherwise></choose></if>
 ORDER BY type_id,parent_id,name
 </select>
 <select id="lockUnits"''');p.write_text(s,encoding='utf-8')
