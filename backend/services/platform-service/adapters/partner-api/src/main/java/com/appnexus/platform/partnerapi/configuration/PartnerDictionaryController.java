package com.appnexus.platform.partnerapi.configuration;
import com.appnexus.contracts.operation.EndpointPolicy;
import com.appnexus.platform.security.*;
import com.appnexus.platform.configuration.PartnerDictionaryQueries;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.annotation.*;
/** 合作方字典 HTTP 入口；scope 与固定租户来自统一验证 Token，仅调用公开查询用例。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
@RequestMapping("/v1/dictionaries")
public class PartnerDictionaryController {
 private final PartnerDictionaryQueries queries;
 /** @param queries 不暴露内部主键与审计身份的公开查询用例 */
 public PartnerDictionaryController(PartnerDictionaryQueries queries){this.queries=queries;}
 /** @param code 标准字典语义码 @param page 页码1–100000，默认1 @param pageSize 每页1–100，默认20 @param auth 已验证机密客户端身份 @return 当前固定租户启用项 */
 @com.appnexus.contracts.operation.OperationBinding(id="BusinessDictionary.partnerItems", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
 @GetMapping("/{code}/items") public ResponseEntity<PartnerDictionaryQueries.Page> items(@PathVariable String code,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,BearerTokenAuthentication auth){
 if(auth.getAuthorities().stream().noneMatch(value->value.getAuthority().equals("SCOPE_dictionaries:read")))throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PARTNER_SCOPE_REQUIRED","客户端未获得此操作授权");
 try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.items((String)auth.getTokenAttributes().get("tenantId"),code,page,pageSize));}catch(IllegalArgumentException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_PARTNER_QUERY","查询参数不正确");}catch(org.springframework.dao.DataAccessException unavailable){throw AccountSettingsController.problem(HttpStatus.SERVICE_UNAVAILABLE,"PARTNER_UNAVAILABLE","集成服务暂不可用");}}
}
