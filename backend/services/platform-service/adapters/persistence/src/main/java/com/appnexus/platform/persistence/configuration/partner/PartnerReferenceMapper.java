package com.appnexus.platform.persistence.configuration.partner;
import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.platform.authorization.DataAccessDeclaration;
import com.appnexus.platform.configuration.PartnerReferences;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** PartnerReferenceController固定SQL映射；没有接收SQL、表名或列名的通用执行入口。 */
@Mapper
@PlatformScoped(tables={"integration_client","integration_reference_request","tenant_dictionary","tenant_dictionary_item","tenant_dictionary_item_reference"})
public interface PartnerReferenceMapper extends com.appnexus.platform.configuration.port.PartnerReferenceRepository, DataAccessDeclaration.Executor {
 /** @return 始终由客户端和租户作为查询约束的合作方引用资源，无租户角色策略配置 */
 @Override default Set<String> enforcedResources(){return Set.of(PartnerReferences.RESOURCE);}
 /**
  * lockAuthority 为原PartnerReferenceController固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT id FROM integration_client WHERE id=#{p0} AND tenant_id=UUID_TO_BIN(#{p1}) AND enabled=TRUE AND authority_version=#{p2} FOR UPDATE")
 List<Map<String,Object>> lockAuthority(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /**
  * lockRequest 为原PartnerReferenceController固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT BIN_TO_UUID(reference_id) id,request_hash,reference_key FROM integration_reference_request WHERE client_id=#{p0} AND idempotency_key=#{p1} FOR UPDATE")
 List<Map<String,Object>> lockRequest(@Param("p0") Object p0,@Param("p1") Object p1);

 /**
  * lockItem 为原PartnerReferenceController固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT BIN_TO_UUID(i.id) id FROM tenant_dictionary d JOIN tenant_dictionary_item i ON i.dictionary_id=d.id AND i.tenant_id=d.tenant_id WHERE d.tenant_id=UUID_TO_BIN(#{p0}) AND i.tenant_id=UUID_TO_BIN(#{p1}) AND d.code=#{p2} AND i.item_value=#{p3} AND d.status='ENABLED' AND i.status='ENABLED' AND d.deleted_at IS NULL AND i.deleted_at IS NULL FOR UPDATE")
 List<Map<String,Object>> lockItem(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /**
  * registerUsage 为原PartnerReferenceController固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT IGNORE INTO tenant_dictionary_item_reference(tenant_id,item_id,consumer,reference_key) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3})")
 int registerUsage(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /**
  * saveRequest 为原PartnerReferenceController固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p3 原调用第4个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p4 原调用第5个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p5 原调用第6个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p6 原调用第7个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Insert("INSERT INTO integration_reference_request(client_id,idempotency_key,request_hash,reference_id,tenant_id,item_id,reference_key) VALUES(#{p0},#{p1},#{p2},UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}),#{p6})")
 int saveRequest(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /**
  * findReference 为原PartnerReferenceController固定SQL，准入、事务、锁及业务规则仍由原用例执行。
  * @param p0 原调用第1个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p1 原调用第2个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @param p2 原调用第3个绑定值，来自原已校验业务参数；不可作为SQL标识符
  * @return 原查询结果或写入行数；失败传播并回滚原业务事务
  */
 @Select("SELECT reference_key FROM integration_reference_request WHERE reference_id=UUID_TO_BIN(#{p0}) AND client_id=#{p1} AND tenant_id=UUID_TO_BIN(#{p2})")
 List<Map<String,Object>> findReference(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);
}
