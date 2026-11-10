package com.appnexus.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 合作方登记字典项业务引用；防止仍被使用的字典项被删除，幂等与业务副作用同事务提交。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
@RequestMapping("/v1/dictionary-item-references")
public class PartnerReferenceController {
    private final JdbcTemplate jdbc;
    private final IntegrationClients clients;
    public PartnerReferenceController(JdbcTemplate jdbc,IntegrationClients clients){this.jdbc=jdbc;this.clients=clients;}
    /** 稳定业务键不得包含密码、Token 或敏感原值；不接受内部字典项 UUID 或租户字段。 */
    public record Creation(String dictionaryCode,String itemValue,String referenceKey){}
    /** 对外登记标识，不暴露内部字典项、租户及创建人字段。 */
    public record Reference(UUID id,String referenceKey){}
    /**
     * 原子登记外部引用；重复同键同参数返回相同结果，不重复增加引用记录。
     * @param key Idempotency-Key，16–128 位字母数字及 ._:-，作用域为当前客户端和本操作
     * @param input 字典编码 2–64 字符、项业务值 1–100 字符、引用键 1–200 字符
     * @param auth 权威机密客户端身份，必须具备 dictionary-references:write scope
     * @return 201 和稳定对外引用标识；相同幂等请求返回相同标识
     */
    @PostMapping
    @Transactional
    ResponseEntity<Reference> create(@RequestHeader("Idempotency-Key") String key,@RequestBody Creation input,BearerTokenAuthentication auth){
        require(auth);
        if(!key.matches("[A-Za-z0-9._:-]{16,128}")||input.dictionaryCode()==null||!input.dictionaryCode().matches("[a-z][a-z0-9_]{1,63}")||
                !valid(input.itemValue(),100)||!valid(input.referenceKey(),200))throw problem(HttpStatus.BAD_REQUEST,"INVALID_PARTNER_INPUT","引用登记参数不正确");
        String client=(String)auth.getTokenAttributes().get("clientId"),tenant=(String)auth.getTokenAttributes().get("tenantId");
        // 与客户端授权更改共享权威行锁，避免 scope 校验后撤权与写入并发交错。
        var locked=jdbc.queryForList("SELECT id FROM integration_client WHERE id=? AND tenant_id=UUID_TO_BIN(?) AND enabled=TRUE AND authority_version=? FOR UPDATE",client,tenant,auth.getTokenAttributes().get("authorityVersion"));
        var policy=clients.policy(client);if(locked.isEmpty()||policy==null||!policy.enabled())throw problem(HttpStatus.UNAUTHORIZED,"INVALID_CLIENT_AUTHORITY","客户端授权已失效");
        byte[] hash=hash(input);var existing=jdbc.queryForList("SELECT BIN_TO_UUID(reference_id) id,request_hash,reference_key FROM integration_reference_request WHERE client_id=? AND idempotency_key=? FOR UPDATE",client,key);
        if(!existing.isEmpty()){
            var row=existing.getFirst();if(!MessageDigest.isEqual(hash,(byte[])row.get("request_hash")))throw problem(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","相同幂等键不能用于不同请求");
            return result(new Reference(UUID.fromString((String)row.get("id")),(String)row.get("reference_key")));
        }
        // 双表租户谓词及字典项行锁，与字典删除/停用串行化，不能登记另一租户相同业务值。
        var items=jdbc.queryForList("SELECT BIN_TO_UUID(i.id) id FROM tenant_dictionary d JOIN tenant_dictionary_item i ON i.dictionary_id=d.id AND i.tenant_id=d.tenant_id WHERE d.tenant_id=UUID_TO_BIN(?) AND i.tenant_id=UUID_TO_BIN(?) AND d.code=? AND i.item_value=? AND d.status='ENABLED' AND i.status='ENABLED' AND d.deleted_at IS NULL AND i.deleted_at IS NULL FOR UPDATE",tenant,tenant,input.dictionaryCode(),input.itemValue());
        if(items.size()!=1)throw problem(HttpStatus.NOT_FOUND,"DICTIONARY_ITEM_UNAVAILABLE","字典项不存在或不可用");
        String item=(String)items.getFirst().get("id");UUID id=UUID.randomUUID();
        jdbc.update("INSERT IGNORE INTO tenant_dictionary_item_reference(tenant_id,item_id,consumer,reference_key) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",tenant,item,client,input.referenceKey());
        jdbc.update("INSERT INTO integration_reference_request(client_id,idempotency_key,request_hash,reference_id,tenant_id,item_id,reference_key) VALUES(?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?)",client,key,hash,id.toString(),tenant,item,input.referenceKey());
        return result(new Reference(id,input.referenceKey()));
    }
    /** 只查询当前客户端和固定租户自己的引用登记，跨主体与不存在均返回 404。 */
    @GetMapping("/{id}")
    ResponseEntity<Reference> get(@PathVariable UUID id,BearerTokenAuthentication auth){
        require(auth);
        var rows=jdbc.queryForList("SELECT reference_key FROM integration_reference_request WHERE reference_id=UUID_TO_BIN(?) AND client_id=? AND tenant_id=UUID_TO_BIN(?)",id.toString(),auth.getTokenAttributes().get("clientId"),auth.getTokenAttributes().get("tenantId"));
        if(rows.isEmpty())throw problem(HttpStatus.NOT_FOUND,"REFERENCE_NOT_FOUND","引用登记不存在");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Reference(id,(String)rows.getFirst().get("reference_key")));
    }
    private void require(BearerTokenAuthentication auth){if(auth.getAuthorities().stream().noneMatch(value->value.getAuthority().equals("SCOPE_dictionary-references:write")))throw problem(HttpStatus.FORBIDDEN,"PARTNER_SCOPE_REQUIRED","客户端未获得此操作授权");}
    private boolean valid(String value,int maximum){return value!=null&&!value.isBlank()&&value.length()<=maximum&&value.codePoints().noneMatch(Character::isISOControl);}
    private byte[] hash(Creation input){
        try{return MessageDigest.getInstance("SHA-256").digest((input.dictionaryCode()+"\u0000"+input.itemValue()+"\u0000"+input.referenceKey()).getBytes(StandardCharsets.UTF_8));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private ResponseEntity<Reference> result(Reference value){return ResponseEntity.created(java.net.URI.create("/v1/dictionary-item-references/"+value.id())).cacheControl(CacheControl.noStore()).body(value);}
    private org.springframework.web.ErrorResponseException problem(HttpStatus status,String code,String text){return AccountSettingsController.problem(status,code,text);}
}
