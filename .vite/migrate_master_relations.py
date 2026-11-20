from pathlib import Path
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata');p=root/'PartnerDataAccess.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','private final MasterReadMapper mapper;').replace('JdbcTemplate jdbc,','MasterReadMapper mapper,').replace('this.jdbc=jdbc','this.mapper=mapper').replace('@param jdbc','@param mapper')
start=s.index('        var args=new ArrayList<Object>(List.of(c.tenantId()');end=s.index('        if(rows.size()',start);s=s[:start]+'''        var query=new MasterReadMapper.Query(c.tenantId().toString(),MasterData.Kind.PARTNER,"",null,id,null,List.of(),null,compiled(plan(c,action)),1,0);
        var rows=mapper.lock(query);
'''+s[end:];pos=s.index('    /** 写入先锁定');s=s[:pos]+'''    /** 只返回由统一执行器产生的固定映射谓词，供MyBatisProvider绑定参数；不接受客户端SQL。 */
    public RowAccessControl.Predicate compiled(AccessPlan plan){return rows.predicate(plan,MAPPING);}
'''+s[pos:];p.write_text(s,encoding='utf-8')
p=root/'JdbcMasterDataRelations.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('JdbcMasterDataRelations','MyBatisMasterDataRelations').replace('private final JdbcTemplate jdbc;','private final MasterReadMapper mapper;private final GeographyMapper geographyMapper;private final StorageHierarchyMapper storageMapper;private final PartnerDetailMapper detailsMapper;').replace('MyBatisMasterDataRelations(JdbcTemplate jdbc,','MyBatisMasterDataRelations(MasterReadMapper mapper,GeographyMapper geographyMapper,StorageHierarchyMapper storageMapper,PartnerDetailMapper detailsMapper,').replace('this.jdbc=jdbc;','this.mapper=mapper;this.geographyMapper=geographyMapper;this.storageMapper=storageMapper;this.detailsMapper=detailsMapper;')
s=s.replace('new JdbcGeographyCatalog(jdbc)','new MyBatisGeographyCatalog(geographyMapper)').replace('new TenantGeography(jdbc,','new TenantGeography(geographyMapper,').replace('StorageHierarchy.prepare(jdbc,','StorageHierarchy.prepare(storageMapper,').replace('StorageHierarchy.status(jdbc,','StorageHierarchy.status(storageMapper,').replace('PartnerDetails.save(jdbc,','PartnerDetails.save(detailsMapper,').replace('PartnerDetails.read(jdbc,','PartnerDetails.read(detailsMapper,')
start=s.index('    String where=');end=s.index('    var fieldModes=',start)
s=s[:start]+'''    String parent=null;if((kind==Kind.STORAGE_AREA||kind==Kind.STORAGE_LOCATION)&&scope!=null&&!scope.isBlank()){if(!scope.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();parent=scope;}
    var aliases=geography!=null&&!keyword.isBlank()?geography.aliases(keyword.trim().toLowerCase(Locale.ROOT),kind==Kind.SUBDIVISION):List.<String>of();
    var codes=geography!=null&&scope!=null&&!scope.isEmpty()?geography.codes(scope,kind==Kind.SUBDIVISION):null;
    var query=new MasterReadMapper.Query(c.tenantId().toString(),kind,keyword.trim(),enabled,id,parent,aliases,codes,kind==Kind.PARTNER?partner().compiled(partner().plan(c,"read")):null,size,(page-1)*size);
'''+s[end:]
start=s.index('    long total=jdbc.queryForObject');end=s.index('    return new Page(rows,total);',start)
s=s[:start]+'''    long total=mapper.count(query);
    var listFields=kind==Kind.PARTNER&&id==null?List.of("partnerType","creditCode","contactName","phone","address"):kind.fields();
    var rows=mapper.list(query).stream().map(raw->{
      var attributes=new LinkedHashMap<String,String>();for(String field:listFields)attributes.put(field,(String)raw.get(kind.column(field)));
      String code=(String)raw.get("code");if(geography!=null){attributes.put("parentCode",geography.parent(code));attributes.put("displayName",geography.name(code));attributes.put("hierarchyCode",geography.path(code));attributes.put("parentHierarchyCode",geography.path(geography.parent(code)));attributes.put("parentName",geography.name(geography.parent(code)));}
      Row row=new Row((String)raw.get("id"),code,(String)raw.get("name"),attributes,Boolean.TRUE.equals(raw.get("enabled")),((Number)raw.get("version")).longValue(),new Audit((String)raw.get("audit_created_by"),utc(raw.get("created_at")),(String)raw.get("audit_updated_by"),utc(raw.get("updated_at"))));
      if(kind!=Kind.PARTNER)return row;
      if(id!=null)row=new Row(row.id(),row.code(),row.name(),row.attributes(),row.enabled(),row.version(),row.audit(),PartnerDetails.read(detailsMapper,c,row.id(),false),PartnerDetails.read(detailsMapper,c,row.id(),true));
      return partner().protect(c,row,(String)raw.get("data_organization_id"),fieldModes);
    }).toList();
'''+s[end:]
s=s.replace('jdbc.queryForList("SELECT code,country_code FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?)",c.tenantId().toString())','geographyMapper.subdivisions(c.tenantId().toString())')
start=s.index('      jdbc.update(');end=s.index(';}',start);s=s[:start]+'      geographyMapper.country(c.tenantId().toString(),code,country,c.accountId().toString())'+s[end:]
s=s.rstrip()[:-1]+''' private static String utc(Object value){return value==null?null:((java.sql.Timestamp)value).toInstant().toString();}
}\n''';p.with_name('MyBatisMasterDataRelations.java').write_text(s,encoding='utf-8');p.unlink()
