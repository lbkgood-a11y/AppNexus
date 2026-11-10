package com.appnexus.platform.security;

import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    public PartnerDictionaryController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    /** 对外字典项，只发布稳定业务值和名称；不返回内部主键、租户、操作者或权限字段。 */
    public record Item(String code,String value,String name){}
    /** 标准分页结果；page 从 1 开始，pageSize 最大 100，总数受相同租户/状态谓词保护。 */
    public record Page(List<Item> items,long total,int page,int pageSize){}
    /**
     * 读取当前客户端固定租户下启用的字典项；查询与计数使用相同双表租户谓词。
     * @param code 2–64 位小写字母、数字或下划线，不接受 SQL 表达式
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
        if(!code.matches("[a-z][a-z0-9_]{1,63}")||page<1||page>100000||pageSize<1||pageSize>100)
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_PARTNER_QUERY","查询参数不正确");
        String tenant=(String)auth.getTokenAttributes().get("tenantId");
        try{
            String condition=" FROM tenant_dictionary d JOIN tenant_dictionary_item i ON i.dictionary_id=d.id AND i.tenant_id=d.tenant_id WHERE d.tenant_id=UUID_TO_BIN(?) AND i.tenant_id=UUID_TO_BIN(?) AND d.code=? AND d.status='ENABLED' AND d.deleted_at IS NULL AND i.status='ENABLED' AND i.deleted_at IS NULL";
            var items=jdbc.query("SELECT i.code,i.item_value,i.name"+condition+" ORDER BY i.sort_order,i.code LIMIT ? OFFSET ?",
                (row,index)->new Item(row.getString("code"),row.getString("item_value"),row.getString("name")),tenant,tenant,code,pageSize,(long)(page-1)*pageSize);
            long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*)"+condition,Long.class,tenant,tenant,code));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Page(items,total,page,pageSize));
        }catch(org.springframework.dao.DataAccessException unavailable){
            throw AccountSettingsController.problem(HttpStatus.SERVICE_UNAVAILABLE,"PARTNER_UNAVAILABLE","集成服务暂不可用");
        }
    }
}
