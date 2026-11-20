package com.appnexus.platform.security;

import java.util.*;
import org.springframework.http.*;

import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.annotation.*;

/**
 * 外部合作方字典只读适配器：显式固定联表、双表租户谓词及公开 DTO，禁止返回内部审计/授权字段。
 * 认证和 scope、租户绑定均来自已验证的机密客户端 Token，不接受 Cookie、账号或 tenantId 参数。
 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
@RequestMapping("/v1/dictionaries")
public class PartnerDictionaryController {
    private final PartnerDictionaryMapper mapper;
    public PartnerDictionaryController(PartnerDictionaryMapper mapper){this.mapper=mapper;}
    /** 对外字典项，只发布稳定业务值和名称；不返回内部主键、租户、操作者或权限字段。 */
    public record Item(String code,String value,String name){}
    /** 标准分页结果；page 从 1 开始，pageSize 最大 100，总数受相同租户/状态谓词保护。 */
    public record Page(List<Item> items,long total,int page,int pageSize){}
    /**
     * 读取当前客户端固定租户下启用的字典项；查询与计数使用相同双表租户谓词。
     * @param code 1–64 位小写英文语义码，以字母开头、单词间单个下划线；禁止 UUID、哈希和 SQL 表达式
     * @param page 页码从 1 开始，最大 100000，缺省 1
     * @param pageSize 每页 1–100 条，缺省 20
     * @param auth 已完成不透明 Token 验证的集成身份；普通用户 Token 被接口面门禁拒绝
     * @return 只含公开业务字段的当前租户结果，无匹配时为空页
     */
    @GetMapping("/{code}/items")
    ResponseEntity<Page> items(@PathVariable String code,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize,BearerTokenAuthentication auth){
        if(auth.getAuthorities().stream().noneMatch(value->value.getAuthority().equals("SCOPE_dictionaries:read")))
            throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PARTNER_SCOPE_REQUIRED","客户端未获得此操作授权");
        if(!com.appnexus.platform.configuration.DictionaryValueStandard.validCode(code)||page<1||page>100000||pageSize<1||pageSize>100)
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_PARTNER_QUERY","查询参数不正确");
        String tenant=(String)auth.getTokenAttributes().get("tenantId");
        try{
            var items=mapper.items(tenant,code,pageSize,(long)(page-1)*pageSize).stream().map(row->new Item((String)row.get("code"),(String)row.get("value"),(String)row.get("name"))).toList();
            long total=mapper.count(tenant,code);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Page(items,total,page,pageSize));
        }catch(org.springframework.dao.DataAccessException unavailable){
            throw AccountSettingsController.problem(HttpStatus.SERVICE_UNAVAILABLE,"PARTNER_UNAVAILABLE","集成服务暂不可用");
        }
    }
}
