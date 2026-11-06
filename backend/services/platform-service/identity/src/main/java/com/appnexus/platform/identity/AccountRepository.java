package com.appnexus.platform.identity;
import java.util.Optional;
import java.util.UUID;
/** Persistence port. Implementations must preserve unique login names and immutable IDs. */
public interface AccountRepository {
    Optional<AccountCredentials> findByUsername(String username);
    Optional<AccountCredentials> findById(UUID id);
    /**
     * 按预期凭据版本修改密码并递增版本，事务内撤销该账号的 OAuth 授权记录。
     * @param id 不可变账号标识
     * @param expectedVersion 发起修改时读取的凭据版本，用于拒绝并发覆盖
     * @param passwordHash 新密码的 Argon2id 哈希，禁止传入明文
     * @return 是否成功更新；版本冲突或账号停用时为 false
     */
    boolean changePassword(UUID id, long expectedVersion, String passwordHash);
    /**
     * 修改当前账号的显示名称，不改变用户名、凭据版本或授权。
     * @param id 不可变账号标识
     * @param displayName 已校验的显示名称，长度 1 至 100 个字符
     * @return 账号启用且更新成功时为 true
     */
    boolean updateDisplayName(UUID id, String displayName);
    boolean createInitialAdministrator(AccountCredentials account);
}
