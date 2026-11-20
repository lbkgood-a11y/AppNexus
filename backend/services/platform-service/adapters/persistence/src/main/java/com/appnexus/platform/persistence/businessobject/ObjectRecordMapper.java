package com.appnexus.platform.persistence.businessobject;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 对象所有者构建映射的MyBatis执行；只接受受控映射，不接受SQL、任意请求表名或表达式。 */
@Mapper
@PlatformScoped(tables={"leave_poc_request","leave_poc_approval_cycle","md_product_approval"})
public interface ObjectRecordMapper {
    /** 已通过唯一Provider映射与平台准入的正文重读，租户/对象键始终绑定。 */
    @SelectProvider(type=Sql.class,method="read")
    List<Map<String,Object>> read(@Param("mapping") ObjectRecordMapping mapping,@Param("context") BusinessObjects.Context context,@Param("id") String id,@Param("lock") boolean lock);
    /** 原业务事务内CAS终态，不绕过版本、原状态、可信实例及审计。 */
    @UpdateProvider(type=Sql.class,method="transition")
    int transition(@Param("mapping") ObjectRecordMapping mapping,@Param("context") BusinessObjects.Context context,@Param("id") String id,@Param("version") long version,@Param("from") String from,@Param("to") String to,@Param("invocation") String invocation,@Param("instance") String instance);
    /** 已验证回执随业务状态同事务落地，重复结果仍遵守原幂等校验。 */
    @UpdateProvider(type=Sql.class,method="receipt")
    int receipt(@Param("mapping") ObjectRecordMapping mapping,@Param("context") BusinessObjects.Context context,@Param("id") String id,@Param("version") long version,@Param("to") String to,@Param("invocation") String invocation,@Param("instance") String instance);
    /** 原构建映射的固定列编译器；SQL标识符由不可变契约构造器验证，绝不从管理配置反序列化。 */
    final class Sql {
        private Sql(){}
        private static String value(ObjectRecordMapping.Column column,String parameter){return column.encoding()==ObjectRecordMapping.Encoding.BINARY_UUID?"UUID_TO_BIN(#{"+parameter+"})":"#{"+parameter+"}";}
        private static String select(ObjectRecordMapping.Column column){return column.encoding()==ObjectRecordMapping.Encoding.BINARY_UUID?"BIN_TO_UUID("+column.name()+")":column.name();}
        private static ObjectRecordMapping mapping(Map<String,Object> parameters){var mapping=(ObjectRecordMapping)parameters.get("mapping");if(!Set.of("leave_poc_request","md_product_approval").contains(mapping.table()))throw new SecurityException("OBJECT_MAPPING_TABLE_UNSUPPORTED");return mapping;}
        private static String where(ObjectRecordMapping mapping){return mapping.tenant().name()+"="+value(mapping.tenant(),"context.tenantId")+" AND "+mapping.id().name()+"="+value(mapping.id(),"id");}
        /** @param parameters 构建映射及可信记录键 @return 参数化固定重读语句 */
        public static String read(Map<String,Object> parameters){var m=mapping(parameters);var columns=new ArrayList<String>(List.of(m.version()+" version",m.state()+" state",select(m.organization())+" organization",select(m.applicant())+" applicant",m.instance()+" instance",m.delivery()+" delivery"));if(m.person()!=null)columns.add(select(m.person())+" person");m.fields().forEach((key,value)->columns.add(select(value.column())+" field_"+key));return "SELECT "+String.join(",",columns)+" FROM "+m.table()+" WHERE "+where(m)+(Boolean.TRUE.equals(parameters.get("lock"))?" FOR UPDATE":"");}
        /** @param parameters 已锁定原记录、版本、状态及实例 @return CAS终态更新语句 */
        public static String transition(Map<String,Object> parameters){var m=mapping(parameters);return "UPDATE "+m.table()+" SET "+m.state()+"=#{to},"+m.delivery()+"=#{invocation}"+(m.auditColumns()?",updated_by=UUID_TO_BIN(#{context.actorId}),updated_at=CURRENT_TIMESTAMP(6)":"")+" WHERE "+where(m)+" AND "+m.version()+"=#{version} AND "+m.state()+"=#{from}"+(parameters.get("instance")!=null?" AND "+m.instance()+"=#{instance}":"");}
        /** @param parameters 已核验可信实例及交付标识 @return 原回执同事务更新 */
        public static String receipt(Map<String,Object> parameters){var r=Objects.requireNonNull(mapping(parameters).receipt());if(!r.table().equals("leave_poc_approval_cycle"))throw new SecurityException("OBJECT_RECEIPT_TABLE_UNSUPPORTED");return "UPDATE "+r.table()+" SET "+r.delivery()+"=#{invocation},"+r.result()+"=#{to},"+r.completed()+"=CURRENT_TIMESTAMP(6) WHERE "+r.tenant().name()+"="+value(r.tenant(),"context.tenantId")+" AND "+r.objectId().name()+"="+value(r.objectId(),"id")+" AND "+r.version()+"=#{version} AND "+r.instance()+"=#{instance} AND "+r.delivery()+" IS NULL";}
    }
}
