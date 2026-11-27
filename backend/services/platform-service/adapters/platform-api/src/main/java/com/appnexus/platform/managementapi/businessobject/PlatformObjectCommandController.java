package com.appnexus.platform.managementapi.businessobject;

import com.appnexus.contracts.businessobject.BusinessObjectExecution;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.EndpointPolicy;
import com.appnexus.contracts.operation.OperationBinding;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 平台命令分发入口，身份取自平台认证链，目标租户只属于业务命令。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
public final class PlatformObjectCommandController {
    private final BusinessObjectExecution commands;

    /** @param commands 核验原平台能力的公共执行器 */
    public PlatformObjectCommandController(BusinessObjectExecution commands){this.commands=commands;}

    /**
     * @param auth 当前安全链的真实账号认证
     * @param request 宿主请求，Idempotency-Key 为 UUID
     * @param input 平台固定操作的类型化请求
     * @return 禁止缓存的执行结果
     */
    @OperationBinding(id="BusinessObject.executePlatformCommand",reference=true,eligibility=OperationBinding.Eligibility.OBJECT)
    @PostMapping("/bff/platform/business-object-executions")
    public ResponseEntity<?> execute(Authentication auth,HttpServletRequest request,@RequestBody BusinessObjectExecution.Request input){
        try{
            if(auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken)
                throw new Rejection(401,"AUTHENTICATION_REQUIRED");
            UUID actor;
            try{actor=UUID.fromString(auth.getName());}
            catch(IllegalArgumentException invalid){throw new Rejection(403,"BUSINESS_OBJECT_ACCESS_DENIED");}
            String key=request.getHeader("Idempotency-Key");
            try{UUID.fromString(key);}
            catch(RuntimeException invalid){throw new Rejection(400,"BUSINESS_OBJECT_REQUEST_KEY_REQUIRED");}
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(commands.executePlatform(new Context(null,actor.toString(),key),input));
        }catch(Rejection denied){
            return ResponseEntity.status(denied.status()).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of("status",denied.status(),"code",denied.getMessage()));
        }catch(org.springframework.web.ErrorResponseException error){
            // 近期 MFA 按原服务端会话失败契约返回，禁止记录或缓存找回凭证。
            return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(error.getBody());
        }catch(SecurityException denied){
            return ResponseEntity.status(403).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of("status",403,"code","PLATFORM_DENIED"));
        }
    }
}
