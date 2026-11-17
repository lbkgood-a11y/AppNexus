package com.appnexus.platform.security;

import com.appnexus.contracts.operation.OperationPreviewProof;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.*;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 复用宿主持久RSA密钥签发预览凭据，不新增环境密钥或临时进程密钥。
 * 协议域与JWT区分；凭据仅绑定不可逆摘要，真实业务授权与并发校验留在原执行器。
 */
@Component
public final class SignedOperationPreviewProof implements OperationPreviewProof {
    private final JWKSource<SecurityContext> keys;
    private final Clock clock;
    /** @param keys 宿主PKCS12加载的RSA密钥源，多实例需使用同一受控密钥库 */
    @org.springframework.beans.factory.annotation.Autowired
    public SignedOperationPreviewProof(JWKSource<SecurityContext> keys){this(keys,Clock.systemUTC());}
    /** 独立时钟入口仅用于到期边界测试；生产使用UTC。 */
    SignedOperationPreviewProof(JWKSource<SecurityContext> keys,Clock clock){this.keys=keys;this.clock=clock;}
    @Override public OperationPreviewProof.Proof issue(String binding){
        validate(binding);
        try{
            RSAKey key=(RSAKey)keys.get(new JWKSelector(new JWKMatcher.Builder().keyType(KeyType.RSA).privateOnly(true).build()),null).getFirst();
            String kid=Base64.getUrlEncoder().withoutPadding().encodeToString(key.getKeyID().getBytes(StandardCharsets.UTF_8));
            Instant expires=clock.instant().plusSeconds(900);
            String head="p1."+kid+"."+expires.getEpochSecond();
            Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(key.toRSAPrivateKey());signer.update(message(head,binding));
            return new OperationPreviewProof.Proof(head+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()),expires);
        }catch(Exception e){throw new IllegalStateException("PREVIEW_SIGNING_UNAVAILABLE",e);}
    }
    @Override public boolean verify(String token,String binding){
        if(token==null||token.length()>2048||binding==null||!binding.matches("[0-9a-f]{64}"))return false;
        try{
            String[] parts=token.split("\\.",-1);if(parts.length!=4||!parts[0].equals("p1"))return false;
            long expiry=Long.parseLong(parts[2]);long now=clock.instant().getEpochSecond();
            if(expiry<=now||expiry>now+900)return false;
            String kid=new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8);
            var candidates=keys.get(new JWKSelector(new JWKMatcher.Builder().keyType(KeyType.RSA).keyID(kid).build()),null);
            String head=parts[0]+"."+parts[1]+"."+parts[2];
            for(var candidate:candidates){
                Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(((RSAKey)candidate).toRSAPublicKey());verifier.update(message(head,binding));
                if(verifier.verify(Base64.getUrlDecoder().decode(parts[3])))return true;
            }return false;
        }catch(Exception e){return false;}
    }
    private static byte[] message(String head,String binding){return ("appnexus:operation-preview:v1\n"+head+"\n"+binding).getBytes(StandardCharsets.UTF_8);}
    private static void validate(String binding){if(binding==null||!binding.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("PREVIEW_BINDING_INVALID");}
}
