package com.appnexus.platform.persistence.identity;
import com.appnexus.platform.identity.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
@Repository
public class MyBatisAccountRepository implements AccountRepository {
    private final AccountMapper mapper;
    private final com.appnexus.platform.audit.SecurityLogRepository logs;
    public MyBatisAccountRepository(AccountMapper mapper, com.appnexus.platform.audit.SecurityLogRepository logs) {
        this.mapper = mapper; this.logs = logs;
    }
    public Optional<AccountCredentials> findByUsername(String username) { return convert(mapper.findByUsername(username)); }
    public Optional<AccountCredentials> findById(UUID id) { return convert(mapper.findById(id.toString())); }
    public Optional<AccountProfile> findProfileById(UUID id) {
        var row = mapper.findProfileById(id.toString());
        if (row == null) return Optional.empty();
        return Optional.of(new AccountProfile(UUID.fromString((String) row.get("id")),
            (String) row.get("username"), (String) row.get("display_name"), (String) row.get("email"),
            flag(row.get("email_verified")), (String) row.get("phone_number"), flag(row.get("phone_verified"))));
    }
    @Transactional
    public boolean createInitialAdministrator(AccountCredentials account) {
        if (mapper.claimBootstrap(account.id().toString()) == 0) return false;
        mapper.insert(account.id().toString(), account.username(), account.displayName(), account.passwordHash());
        return true;
    }
    /** 密码更新与 OAuth 授权撤销在同一数据库事务中提交。 */
    @Transactional
    public boolean changePassword(UUID id, long expectedVersion, String passwordHash) {
        if (mapper.changePassword(id.toString(), expectedVersion, passwordHash) != 1) return false;
        mapper.revokeAuthorizations(id.toString());
        logs.append(id, com.appnexus.platform.audit.SecurityLog.Action.PASSWORD_CHANGED);
        return true;
    }
    /** 更新账号展示资料，不修改凭据版本。 */
    @Transactional
    public boolean updateDisplayName(UUID id, String displayName) {
        if (mapper.updateDisplayName(id.toString(), displayName) != 1) return false;
        logs.append(id, com.appnexus.platform.audit.SecurityLog.Action.PROFILE_UPDATED);
        return true;
    }
    /** 资料与联系方式在同一事务更新并记录一次安全日志。 */
    @Transactional
    public boolean updateProfile(UUID id, String displayName, String email, String phoneNumber) {
        try {
            if (mapper.updateProfile(id.toString(), displayName, email, phoneNumber) != 1) return false;
        } catch (DuplicateKeyException conflict) {
            // 不区分邮箱或手机号，避免利用错误详情探测其他账号的联系方式。
            throw new AccountContactConflictException();
        }
        logs.append(id, com.appnexus.platform.audit.SecurityLog.Action.PROFILE_UPDATED);
        return true;
    }
    private boolean flag(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number number && number.intValue() != 0);
    }
    private Optional<AccountCredentials> convert(Map<String,Object> row) {
        if (row == null) return Optional.empty();
        var enabled = row.get("enabled");
        return Optional.of(new AccountCredentials(UUID.fromString((String) row.get("id")),
            (String) row.get("username"), (String) row.get("display_name"), (String) row.get("password_hash"),
            Boolean.TRUE.equals(enabled) || (enabled instanceof Number n && n.intValue() != 0),
            ((Number) row.get("version")).longValue()));
    }
}
