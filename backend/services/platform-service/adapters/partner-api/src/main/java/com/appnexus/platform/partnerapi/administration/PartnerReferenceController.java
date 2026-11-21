package com.appnexus.platform.partnerapi.administration;
import com.appnexus.platform.security.*;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.configuration.PartnerReferences;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.annotation.*;
/** 合作方引用 HTTP 映射；只调用公开用例，不直接调用 Mapper 或编排业务事务。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
@RequestMapping("/v1/dictionary-item-references")
public class PartnerReferenceController {
 private final PartnerReferences references;
 /** @param references 同事务完成引用与幂等登记的公开用例 */
 public PartnerReferenceController(PartnerReferences references){this.references=references;}
 /** @param key 16–128位幂等键 @param input 受控字典业务值 @param auth 权威机密客户端身份 @return 201及稳定引用标识 */
 @OperationBinding(id="DictionaryItemReference.create", reference=true, eligibility=OperationBinding.Eligibility.OBJECT)
 @PostMapping public ResponseEntity<PartnerReferences.Reference> create(@RequestHeader("Idempotency-Key") String key,@RequestBody PartnerReferences.Creation input,BearerTokenAuthentication auth){try{var result=references.create(context(auth),key,input);return ResponseEntity.created(java.net.URI.create("/v1/dictionary-item-references/"+result.id())).cacheControl(CacheControl.noStore()).body(result);}catch(PartnerReferences.Rejection rejected){throw problem(rejected);}}
 /** @param id 对外引用UUID @param auth 权威客户端身份 @return 当前客户端与租户自己的引用 */
 @OperationBinding(id="DictionaryItemReference.read", reference=true, eligibility=OperationBinding.Eligibility.OBJECT)
 @GetMapping("/{id}") public ResponseEntity<PartnerReferences.Reference> get(@PathVariable UUID id,BearerTokenAuthentication auth){try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(references.get(context(auth),id));}catch(PartnerReferences.Rejection rejected){throw problem(rejected);}}
 private PartnerReferences.Context context(BearerTokenAuthentication auth){if(auth.getAuthorities().stream().noneMatch(value->value.getAuthority().equals(DictionaryItemReferenceDeclaration.REQUIRED_AUTHORITY)))throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PARTNER_SCOPE_REQUIRED","客户端未获得此操作授权");return new PartnerReferences.Context((String)auth.getTokenAttributes().get("clientId"),(String)auth.getTokenAttributes().get("tenantId"),auth.getTokenAttributes().get("authorityVersion"));}
 private org.springframework.web.ErrorResponseException problem(PartnerReferences.Rejection rejected){String text=switch(rejected.getMessage()){case "INVALID_PARTNER_INPUT"->"引用登记参数不正确";case "INVALID_CLIENT_AUTHORITY"->"客户端授权已失效";case "IDEMPOTENCY_CONFLICT"->"相同幂等键不能用于不同请求";case "DICTIONARY_ITEM_UNAVAILABLE"->"字典项不存在或不可用";default->"引用登记不存在";};return AccountSettingsController.problem(HttpStatus.valueOf(rejected.status()),rejected.getMessage(),text);}
}
