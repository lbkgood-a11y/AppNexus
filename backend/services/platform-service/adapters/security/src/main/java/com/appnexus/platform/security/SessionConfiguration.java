package com.appnexus.platform.security;
import org.springframework.context.annotation.*;
import org.springframework.session.web.http.*;
import org.springframework.beans.factory.annotation.Value;
@Configuration(proxyBeanMethods = false)
public class SessionConfiguration {
    @Bean
    CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
