package com.appnexus.platform.security;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
/** 从持久 PKCS12 密钥库加载 OIDC 签名材料，确保重启和多实例使用一致的签名身份。 */
@Configuration(proxyBeanMethods = false)
public class SigningKeys {
    /**
     * 加载 RSA 私钥及证书，公开端点仅发布其中的公钥。
     *
     * @param path 必填的 PKCS12 文件路径；运行环境必须具备读取权限
     * @param password 必填的密钥库及私钥口令；通过密钥管理设施注入，禁止打印
     * @param alias 私钥及证书的别名，默认 appnexus，同时作为公开密钥的 kid
     * @return 授权服务器使用的签名密钥源
     * @throws Exception 文件、口令、别名或密钥类型不符合要求时使启动失败
     */
    @Bean
    JWKSource<SecurityContext> jwkSource(
            @Value("${app.signing-key-store}") String path,
            @Value("${app.signing-key-password}") String password,
            @Value("${app.signing-key-alias:appnexus}") String alias) throws Exception {
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(Path.of(path))) { store.load(input, password.toCharArray()); }
        var certificate = store.getCertificate(alias);
        if (certificate == null) throw new IllegalStateException("Signing key alias missing");
        var key = new RSAKey.Builder((RSAPublicKey) certificate.getPublicKey())
            .privateKey((RSAPrivateKey) store.getKey(alias, password.toCharArray()))
            .keyID(alias).build();
        var keys = new JWKSet(key);
        return (selector, context) -> selector.select(keys);
    }
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> source) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(source);
    }
}
