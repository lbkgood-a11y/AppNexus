package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.masterdata.api.masterdata.MasterData;

import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.platform.authorization.RowAccessControl;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 主数据固定类型查询映射；业务过滤为结构化参数，权限谓词仅由公共RowAccessControl生成。 */
@Mapper
@PlatformScoped(tables={"md_partner","md_warehouse","md_storage_area","md_storage_location","md_country_region","md_subdivision"})
public interface MasterReadMapper {
    /** 可信查询输入，不接收SQL、列名或权限计划自报；分页及范围已由原用例校验。 */
    record Query(String tenant,MasterData.Kind kind,String keyword,Boolean enabled,String id,String parent,List<String> aliases,List<String> codes,RowAccessControl.Predicate access,int size,int offset){public Query{aliases=List.copyOf(aliases);codes=codes==null?null:List.copyOf(codes);}}
    /** 固定目录列表；详情字段和子表只在父记录授权后读取。 */
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.partner",tenant="query.tenant",when="query.kind",whenValue="PARTNER")
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.warehouse",tenant="query.tenant",when="query.kind",actions={"read"},whenValue="WAREHOUSE")
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.warehouse",tenant="query.tenant",when="query.kind",actions={"read"},whenValue="STORAGE_AREA")
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.warehouse",tenant="query.tenant",when="query.kind",actions={"read"},whenValue="STORAGE_LOCATION")
    @SelectProvider(type=Sql.class,method="list") List<Map<String,Object>> list(@Param("query") Query query);
    /** 列表与COUNT使用同一构建函数，避免范围和分页计数分离。 */
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.partner",tenant="query.tenant",when="query.kind",whenValue="PARTNER")
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.warehouse",tenant="query.tenant",when="query.kind",actions={"read"},whenValue="WAREHOUSE")
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.warehouse",tenant="query.tenant",when="query.kind",actions={"read"},whenValue="STORAGE_AREA")
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.warehouse",tenant="query.tenant",when="query.kind",actions={"read"},whenValue="STORAGE_LOCATION")
    @SelectProvider(type=Sql.class,method="count") long count(@Param("query") Query query);
    /** 授权原记录锁，仅返回归属事实，不先泄漏业务正文。 */
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.partner",tenant="query.tenant",when="query.kind",whenValue="PARTNER")
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.warehouse",tenant="query.tenant",when="query.kind",actions={"update","set_status"},whenValue="WAREHOUSE")
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.warehouse",tenant="query.tenant",when="query.kind",actions={"update","set_status"},whenValue="STORAGE_AREA")
    @com.appnexus.persistence.mybatis.GuardedScope(predicate="query.access",resource="masterdata.warehouse",tenant="query.tenant",when="query.kind",actions={"update","set_status"},whenValue="STORAGE_LOCATION")
    @SelectProvider(type=Sql.class,method="lock") List<Map<String,Object>> lock(@Param("query") Query query);
    /** 固定业务类型与白名单列编译，所有条件值继续参数绑定；不提供execute(sql)。 */
    final class Sql {
        private Sql(){}
        private static Query query(Map<String,Object> parameters){return (Query)parameters.get("query");}
        private static String placeholders(String field,int size){var values=new ArrayList<String>();for(int i=0;i<size;i++)values.add("#{query."+field+"["+i+"]}");return "("+String.join(",",values)+")";}
        private static String where(Query q){
            String sql=" WHERE tenant_id=UUID_TO_BIN(#{query.tenant}) AND (LOCATE(#{query.keyword},code)>0 OR LOCATE(#{query.keyword},name)>0";
            if(q.kind()==MasterData.Kind.COUNTRY_REGION)sql+=" OR LOCATE(#{query.keyword},alpha3_code)>0 OR LOCATE(#{query.keyword},numeric_code)>0";
            if(q.kind()==MasterData.Kind.SUBDIVISION)sql+=" OR LOCATE(#{query.keyword},country_code)>0 OR LOCATE(#{query.keyword},parent_code)>0";
            if(!q.aliases().isEmpty())sql+=" OR code IN "+placeholders("aliases",q.aliases().size());sql+=")";
            if(q.enabled()!=null)sql+=" AND enabled=#{query.enabled}";
            if(q.parent()!=null)sql+=" AND parent_code=#{query.parent}";
            if(q.id()!=null)sql+=" AND id=UUID_TO_BIN(#{query.id})";
            if(q.codes()!=null)sql+=q.codes().isEmpty()?" AND 1=0":" AND code IN "+placeholders("codes",q.codes().size());
            if(q.kind()==MasterData.Kind.PARTNER||isStorage(q.kind())){
                var access=Objects.requireNonNull(q.access(),"Row access predicate required");
                String condition=com.appnexus.persistence.mybatis.ScopedSql.bind(access.sql(),access.parameters(),"query.access");
                // 子记录关联以同租户不可变编码定位根仓库；列表、统计、直查和写入使用同一谓词。
                if(q.kind()==MasterData.Kind.STORAGE_AREA)
                    sql+=" AND EXISTS (SELECT 1 FROM md_warehouse w WHERE w.tenant_id=md_storage_area.tenant_id AND w.code=md_storage_area.parent_code"+condition+")";
                else if(q.kind()==MasterData.Kind.STORAGE_LOCATION)
                    sql+=" AND EXISTS (SELECT 1 FROM md_storage_area a JOIN md_warehouse w ON w.tenant_id=a.tenant_id AND w.code=a.parent_code WHERE a.tenant_id=md_storage_location.tenant_id AND a.code=md_storage_location.parent_code"+condition+")";
                else sql+=condition;
            }
            return sql;
        }
        private static boolean isStorage(MasterData.Kind kind){
            return kind==MasterData.Kind.WAREHOUSE||kind==MasterData.Kind.STORAGE_AREA||kind==MasterData.Kind.STORAGE_LOCATION;
        }
        /** @param parameters 可信结构化查询 @return 相同条件下的分页SQL */
        public static String list(Map<String,Object> parameters){var q=query(parameters);var fields=q.kind()==MasterData.Kind.PARTNER&&q.id()==null?List.of("partnerType","creditCode","contactName","phone","address"):q.kind().fields();return "SELECT BIN_TO_UUID(id) id,code,name,enabled,version,BIN_TO_UUID(created_by) audit_created_by,created_at,BIN_TO_UUID(updated_by) audit_updated_by,updated_at,"+((q.kind()==MasterData.Kind.PARTNER||q.kind()==MasterData.Kind.WAREHOUSE)?"BIN_TO_UUID(organization_id) data_organization_id,":"")+String.join(",",fields.stream().map(q.kind()::column).toList())+" FROM "+q.kind().table()+where(q)+" ORDER BY code,id LIMIT #{query.size} OFFSET #{query.offset}";}
        /** @param parameters 与列表一致的查询 @return 固定COUNT */
        public static String count(Map<String,Object> parameters){var q=query(parameters);return "SELECT COUNT(*) FROM "+q.kind().table()+where(q);}
        /** @param parameters 原操作权限谓词 @return 本租户授权原行锁 */
        public static String lock(Map<String,Object> parameters){var q=query(parameters);if(q.id()==null||q.kind()!=MasterData.Kind.PARTNER&&!isStorage(q.kind()))throw new SecurityException("RECORD_LOCK_MAPPING_REQUIRED");return "SELECT "+(q.kind()==MasterData.Kind.PARTNER?"BIN_TO_UUID(organization_id) organization_id":"BIN_TO_UUID(id) id")+" FROM "+q.kind().table()+where(q)+" FOR UPDATE";}
    }
}
