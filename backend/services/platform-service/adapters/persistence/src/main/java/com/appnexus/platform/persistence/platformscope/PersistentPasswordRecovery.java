package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.identity.*;
import com.appnexus.platform.audit.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 短时重置凭证使用数据库 CAS 消费；凭证删除、改密和撤销处于同一事务。 */
@Service
public class PersistentPasswordRecovery implements PasswordRecovery {
    private final PasswordRecoveryMapper mapper;
    private final AdministrationMapper administration;
    private final MultiFactorAuthentication mfa;
    private final AccountRepository accounts;
    private final SecurityLogRepository logs;
    private final SecureRandom random=new SecureRandom();
    public PersistentPasswordRecovery(PasswordRecoveryMapper mapper,AdministrationMapper administration,
            MultiFactorAuthentication mfa,AccountRepository accounts,SecurityLogRepository logs){
        this.mapper=mapper;this.administration=administration;this.mfa=mfa;this.accounts=accounts;this.logs=logs;
    }
    @Transactional
    public String recover(String username,String code){
        if(username==null||username.length()>100||code==null||!code.matches("[a-f0-9]{32}"))throw invalid();
        if(!mfa.available())throw new IllegalStateException("MFA recovery unavailable");
        var row=mapper.lock(username);
        if(row==null||!mfa.verify(UUID.fromString((String)row.get("id")),code))throw invalid();
        return create(UUID.fromString((String)row.get("id")),((Number)row.get("version")).longValue());
    }
    @Transactional
    public String issue(UUID actor,UUID account){
        if(administration.lockAdministrator(actor.toString())==null)throw new SecurityException("Platform authority required");
        if(actor.equals(account)||administration.protectedAccount(account.toString()))throw invalid();
        Long version=mapper.version(account.toString());if(version==null)throw invalid();
        String result=create(account,version);
        administration.audit(actor.toString(),"PASSWORD_RECOVERY_ISSUED",account.toString(),null);return result;
    }
    @Transactional
    public boolean complete(String username,String token,String hash){
        if(username==null||username.length()>100||token==null||!token.matches("[a-f0-9]{64}")||hash==null||!hash.startsWith("$argon2"))return false;
        var row=mapper.lock(username);if(row==null)return false;
        String account=(String)row.get("id");long version=((Number)row.get("version")).longValue();
        if(mapper.consume(account,digest(token),version)!=1)return false;
        // 删除凭证后若改密失败必须回滚，防止凭证被无效请求静默消耗。
        if(!accounts.changePassword(UUID.fromString(account),version,hash))throw new IllegalStateException("Account changed during recovery");
        logs.append(UUID.fromString(account),SecurityLog.Action.PASSWORD_RECOVERED);return true;
    }
    private String create(UUID account,long version){
        byte[] bytes=new byte[32];random.nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
        mapper.issue(account.toString(),digest(token),version,Instant.now().plusSeconds(600));
        logs.append(account,SecurityLog.Action.PASSWORD_RECOVERY_ISSUED);return token;
    }
    private byte[] digest(String value){
        try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private IllegalArgumentException invalid(){return new IllegalArgumentException("Password recovery unavailable");}
}
