package com.appnexus.platform;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;

/** 三个接口面独立生成契约；合作方资源只从 /v1 映射，内部认证和管理字段不得混入。 */
@Configuration(proxyBeanMethods=false)
public class ApiContracts {
    /** 第一方契约包含已实现 API 与浏览器本人/租户 BFF，兼容原生 Bearer 和 Web Cookie。 */
    @Bean GroupedOpenApi appContract(){return GroupedOpenApi.builder().group("app")
        .pathsToMatch("/api/**","/bff/account/**","/bff/tenants/**","/bff/session","/bff/csrf")
        .addOpenApiCustomizer(api->{
            configure(api,"AppNexus App API","firstParty",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer"));
            api.getComponents().addSecuritySchemes("webSession",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID"));
            api.setSecurity(new java.util.ArrayList<>(java.util.List.of(new SecurityRequirement().addList("firstParty"),new SecurityRequirement().addList("webSession"))));
        })
        .build();}
    /** 平台管理独立分组，不混入租户和合作方资源；当前运行路由是平台 BFF，由部署入口映射。 */
    @Bean GroupedOpenApi platformContract(){return GroupedOpenApi.builder().group("platform").pathsToMatch("/bff/platform/**")
        .addOpenApiCustomizer(api->configure(api,"AppNexus Platform API","platformSession",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID")))
        .build();}
    /** 合作方仅发布已实现 /v1 资源；精确 scope、错误及幂等说明以校审后的手写契约补充。 */
    @Bean GroupedOpenApi partnerContract(){return GroupedOpenApi.builder().group("partner-v1").pathsToMatch("/v1/**")
        .addOpenApiCustomizer(api->configure(api,"AppNexus Partner API","partnerOAuth",new SecurityScheme().type(SecurityScheme.Type.OAUTH2)
            .flows(new OAuthFlows().clientCredentials(new OAuthFlow().tokenUrl("/oauth2/token").scopes(new Scopes()
                .addString("dictionaries:read","读取固定租户字典项").addString("dictionary-references:write","登记字典业务引用"))))))
        .build();}
    /** 分组安全声明只描述认证协议，不替代接口内租户、scope 和功能权限验证。 */
    private void configure(OpenAPI api,String title,String scheme,SecurityScheme security){
        api.setInfo(new Info().title(title).version("1.0.0"));
        if(api.getComponents()==null)api.setComponents(new Components());
        api.getComponents().addSecuritySchemes(scheme,security);api.setSecurity(java.util.List.of(new SecurityRequirement().addList(scheme)));
        // 禁止导出的契约携带运行机器主机名或端口，实际入口由部署确定。
        api.setServers(null);
    }
}
