package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 客商联系人/银行账户固定SQL，父行授权与字段保护先在同聚合事务完成。 */
@Mapper
@PlatformScoped(tables={"md_partner_contact","md_partner_bank_account"})
public interface PartnerDetailMapper {
 /** 已授权父档案明细；不提供按任意明细ID直接查询入口。 */
 List<Map<String,Object>> read(@Param("tenant") String tenant,@Param("parent") String parent,@Param("bank") boolean bank);
 /** 原创建与修改账号来自可信上下文，时间由数据库UTC生成。 */
 int create(@Param("tenant") String tenant,@Param("parent") String parent,@Param("bank") boolean bank,@Param("id") String id,@Param("values") Map<String,String> values,@Param("actor") String actor);
 /** 修改限定租户、父档案及明细，保留创建审计。 */
 int update(@Param("tenant") String tenant,@Param("parent") String parent,@Param("bank") boolean bank,@Param("id") String id,@Param("values") Map<String,String> values,@Param("actor") String actor);
 /** 逻辑删除保留历史，受限字段删除由调用者先验证。 */
 int delete(@Param("tenant") String tenant,@Param("parent") String parent,@Param("bank") boolean bank,@Param("id") String id,@Param("actor") String actor);
}
