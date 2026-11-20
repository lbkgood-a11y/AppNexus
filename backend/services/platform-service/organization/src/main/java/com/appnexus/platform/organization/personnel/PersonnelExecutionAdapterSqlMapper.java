package com.appnexus.platform.organization.personnel;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** PersonnelExecutionAdapter固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_person","md_person_account"})
public interface PersonnelExecutionAdapterSqlMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_person p JOIN md_person_account a ON a.tenant_id=p.tenant_id AND a.person_id=p.id SET p.version=p.version+1,p.updated_by=UUID_TO_BIN(#{p0}),p.updated_at=CURRENT_TIMESTAMP(6) WHERE a.tenant_id=UUID_TO_BIN(#{p1}) AND a.member_id=UUID_TO_BIN(#{p2})")
 int statement1(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Delete("DELETE FROM md_person_account WHERE tenant_id=UUID_TO_BIN(#{p0}) AND member_id=UUID_TO_BIN(#{p1})")
 int statement2(@Param("p0") Object p0,@Param("p1") Object p1);
}
