package com.appnexus.platform.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.filter.OncePerRequestFilter;

/** 外部机密客户端统一调用门禁：共享每分钟 60 次、追踪与审计，不保存请求正文或原始 URL。 */
public final class PartnerCallFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> COUNT=new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",Long.class);
    public PartnerCallFilter(JdbcTemplate jdbc,StringRedisTemplate redis){this.jdbc=jdbc;this.redis=redis;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(!(auth instanceof BearerTokenAuthentication bearer)||auth.getAuthorities().stream().noneMatch(value->value.getAuthority().equals("PARTNER_CLIENT"))){chain.doFilter(request,response);return;}
        String client=(String)bearer.getTokenAttributes().get("clientId"),tenant=(String)bearer.getTokenAttributes().get("tenantId");
        String trace=UUID.randomUUID().toString().replace("-","");response.setHeader("X-Trace-Id",trace);org.slf4j.MDC.put("traceId",trace);
        String path=request.getRequestURI().substring(request.getContextPath().length());
        String operation=path.startsWith("/v1/dictionaries/")?"dictionaries.items:read":path.startsWith("/v1/dictionary-item-references")?"dictionary.references":"partner.unknown";
        Long auditId;
        try{
            // 先持久化开始记录，再进入业务；崩溃时 status=0 表示未确认结果，不能伪造成功。
            var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO integration_call_audit(tenant_id,client_id,operation,trace_id,status) VALUES(UUID_TO_BIN(?),?,?,?,0)",java.sql.Statement.RETURN_GENERATED_KEYS);statement.setString(1,tenant);statement.setString(2,client);statement.setString(3,operation);statement.setString(4,trace);return statement;},holder);
            auditId=Objects.requireNonNull(holder.getKey()).longValue();
            Long count=redis.execute(COUNT,List.of("appnexus:partner:rate:"+client));
            if(count==null||count>60){response.setStatus(count==null?503:429);response.setHeader("Retry-After","60");}
            else chain.doFilter(request,response);
            jdbc.update("UPDATE integration_call_audit SET status=? WHERE id=?",response.getStatus(),auditId);
            org.slf4j.LoggerFactory.getLogger(getClass()).info("partner call completed client={} operation={} status={}",client,operation,response.getStatus());
        }catch(org.springframework.dao.DataAccessException unavailable){if(response.isCommitted())throw unavailable;response.resetBuffer();response.setStatus(503);}
        finally{org.slf4j.MDC.remove("traceId");}
    }
}
