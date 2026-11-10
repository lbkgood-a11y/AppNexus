package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.identity.*;
import com.appnexus.platform.audit.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TOTP 密钥使用 AES-GCM 加密，账号 UUID 作为 AAD，防止交换不同账号的密文。 */
@Service
public class PersistentMfa implements MultiFactorAuthentication {
    private final MfaMapper mapper;
    private final SecurityLogRepository logs;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param mapper 只访问认证资料的专用仓储
     * @param logs 安全事件仓储；绑定及关闭事件与因子变更同事务
     * @param encodedKey Base64 编码的 32 字节独立密钥；缺省空表示不开放新绑定，不能使用 BFF 密钥替代
     */
    public PersistentMfa(MfaMapper mapper,SecurityLogRepository logs,
            @Value("${app.mfa.encryption-key:}") String encodedKey) {
        this.mapper=mapper;this.logs=logs;
        try { key=encodedKey.isBlank()?null:Base64.getDecoder().decode(encodedKey); }
        catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("MFA encryption key must be Base64"); }
        if(key!=null&&key.length!=32)throw new IllegalArgumentException("MFA encryption key must contain 32 bytes");
    }
    public boolean available(){return key!=null;}
    public boolean enabled(UUID account){return Boolean.TRUE.equals(mapper.enabled(account.toString()));}

    @Transactional
    public String begin(UUID account){
        requireKey();lockAccount(account);
        var previous=mapper.lock(account.toString());
        if(previous!=null&&flag(previous))throw new IllegalArgumentException("MFA already enabled");
        byte[] secret=new byte[20];random.nextBytes(secret);
        mapper.pending(account.toString(),encrypt(account,secret),Instant.now().plusSeconds(600));
        return Totp.base32(secret);
    }

    @Transactional
    public List<String> activate(UUID account,String code){
        requireKey();lockAccount(account);
        var row=mapper.lock(account.toString());
        if(row==null||flag(row)||!timestamp(row.get("pending_until")).isAfter(Instant.now()))
            throw new IllegalArgumentException("MFA enrollment unavailable");
        long matched=Totp.match(decrypt(account,(byte[])row.get("encrypted_secret")),code,Instant.now().getEpochSecond()/30,-1);
        if(matched<0)throw new IllegalArgumentException("Invalid second factor");
        mapper.advance(account.toString(),matched);
        List<String> result=new ArrayList<>();
        for(int index=0;index<10;index++){
            byte[] bytes=new byte[16];random.nextBytes(bytes);
            String recovery=HexFormat.of().formatHex(bytes);result.add(recovery);
            mapper.recovery(account.toString(),hash(recovery));
        }
        invalidate(account);logs.append(account,SecurityLog.Action.MFA_ENABLED);
        return List.copyOf(result);
    }

    @Transactional
    public boolean verify(UUID account,String code){
        requireKey();lockAccount(account);
        return consume(account,code);
    }

    @Transactional
    public boolean disable(UUID account,String code){
        requireKey();lockAccount(account);
        if(!consume(account,code))return false;
        mapper.remove(account.toString());invalidate(account);
        logs.append(account,SecurityLog.Action.MFA_DISABLED);return true;
    }

    /** 固定账号锁将恢复码消费、TOTP 计数器和因子关闭串行化；不使用进程本地锁。 */
    private boolean consume(UUID account,String code){
        var row=mapper.lock(account.toString());
        if(row==null||!flag(row)||code==null)return false;
        if(code.matches("[a-f0-9]{32}")){
            boolean consumed=mapper.consume(account.toString(),hash(code))==1;
            if(consumed)logs.append(account,SecurityLog.Action.MFA_RECOVERY_USED);
            return consumed;
        }
        long matched=Totp.match(decrypt(account,(byte[])row.get("encrypted_secret")),code,
            Instant.now().getEpochSecond()/30,((Number)row.get("last_counter")).longValue());
        if(matched<0)return false;
        mapper.advance(account.toString(),matched);return true;
    }
    private void invalidate(UUID account){mapper.invalidateSessions(account.toString());mapper.invalidateTokens(account.toString());}
    private void lockAccount(UUID account){if(mapper.lockAccount(account.toString())==null)throw new SecurityException("Account unavailable");}
    private void requireKey(){if(key==null)throw new IllegalStateException("MFA encryption key unavailable");}
    private boolean flag(Map<String,Object> row){Object value=row.get("enabled");return Boolean.TRUE.equals(value)||value instanceof Number number&&number.intValue()!=0;}
    private Instant timestamp(Object value){return value instanceof java.sql.Timestamp stamp?stamp.toInstant():((LocalDateTime)value).toInstant(ZoneOffset.UTC);}
    private byte[] hash(String value){
        try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private byte[] encrypt(UUID account,byte[] secret){
        byte[] nonce=new byte[12];random.nextBytes(nonce);
        byte[] encrypted=crypt(Cipher.ENCRYPT_MODE,account,nonce,secret);
        return ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array();
    }
    private byte[] decrypt(UUID account,byte[] encrypted){
        if(encrypted.length<28)throw new IllegalStateException("Invalid MFA ciphertext");
        return crypt(Cipher.DECRYPT_MODE,account,Arrays.copyOfRange(encrypted,0,12),Arrays.copyOfRange(encrypted,12,encrypted.length));
    }
    private byte[] crypt(int mode,UUID account,byte[] nonce,byte[] value){
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(account.toString().getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(value);
        }catch(GeneralSecurityException failure){throw new IllegalStateException("MFA key operation failed");}
    }
}
