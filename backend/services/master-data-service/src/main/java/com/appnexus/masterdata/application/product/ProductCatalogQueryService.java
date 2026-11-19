package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.MasterData.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;
/** 固定目录查询用例；先真实读取权限，再访问参数化租户目录，不因候选查询扩大管理权限。 */
public final class ProductCatalogQueryService {
  private final ProductCatalogReads reads;private final AccessAuthorization access;
  /** @param reads 固定目录端口 @param access 每次复核的可信功能授权 */
  public ProductCatalogQueryService(ProductCatalogReads reads,AccessAuthorization access){this.reads=reads;this.access=access;}
  /** @param c 可信主体 @param kind category/unit @param keyword 最多200字 @param page 1起 @param size 最多100 @return 授权目录候选 */
  public Object options(VerifiedTenantContext c,String kind,String keyword,int page,int size){require(c);return reads.options(c,kind,keyword,page,size);}
  /** @param c 可信主体 @param id 分类UUID @param page 1起 @param size 最多100 @return 固定配置版本目录 */
  public Object configurations(VerifiedTenantContext c,String id,int page,int size){require(c);return reads.configurations(c,id,page,size);}
  /** @param c 可信主体 @param id 配置UUID @return 固定版本表单投影，不读取其他租户 */
  public Map<String,Object> configuration(VerifiedTenantContext c,String id){require(c);return reads.configuration(c,id);}
  private void require(VerifiedTenantContext c){if(c==null||c.accountId()==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.product:read"))throw new Rejection(403,"MASTER_DATA_DENIED");}
}
