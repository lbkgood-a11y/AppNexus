package com.appnexus.platform.tenant;

import java.util.*;
import com.appnexus.platform.authorization.*;

/** 平台用户与租户开通端口；每个操作独立验证平台管理员，不允许平台身份绕过租户业务隔离。 */
public interface PlatformAdministration {
    /** 平台账号摘要；联系方式与登录识别符分离，不含认证凭据。 */
    @ProtectedResource(value="identity.account",scope=PermissionCatalog.ScopeType.PLATFORM)
    record User(UUID id, String username, String displayName,
                @ProtectedField(nameZh="邮箱",nameEn="Email",kind=FieldSecurity.Kind.EMAIL) String email, boolean emailVerified,
                @ProtectedField(nameZh="手机号",nameEn="Phone number",kind=FieldSecurity.Kind.PHONE) String phoneNumber, boolean phoneVerified, boolean enabled, Set<String> restrictedFields) {
        /** 兼容不携带字段限制的内部构造；受保护响应必须由服务层明确填入限制字段。 */
        public User(UUID id,String username,String displayName,String email,boolean emailVerified,String phoneNumber,boolean phoneVerified,boolean enabled){this(id,username,displayName,email,emailVerified,phoneNumber,phoneVerified,enabled,Set.of());}
    }
    /** 租户目录，不包含租户业务内容。 */
    record Tenant(UUID id, String name, boolean enabled) {}
    /** 成员是账号与租户的关联，不等价于组织归属或业务授权。 */
    @ProtectedResource(value="identity.member",scope=PermissionCatalog.ScopeType.PLATFORM)
    record Member(UUID memberId, UUID accountId, String username,
                  @ProtectedField(nameZh="成员姓名",nameEn="Member name",kind=FieldSecurity.Kind.PERSON_NAME) String displayName,
                  boolean enabled, boolean removed) {}
    /** 带总数的目录页，页码从 1 开始，每页最多 100 条。 */
    record Page<T>(List<T> items, long total) {}
    /** @param actor 经过认证的全局账号 UUID，禁止使用客户端操作者字段 */
    boolean isAdministrator(UUID actor);
    /** @param page 从 1 开始；size 范围 1–100，query 最长 100 字符 */
    Page<User> users(UUID actor, int page, int size, String query);
    /**
     * 创建全局账号，联系方式初始为未验证，不自动开放邮箱或手机号登录。
     * @param email 可选邮箱，最长 254 字符，按小写归一化后全局唯一
     * @param phoneNumber 可选 E.164 手机号，包含前导加号且全局唯一
     * @param passwordHash 已校验至少 12 字符密码的 Argon2id 哈希，不接收明文
     */
    User createUser(UUID actor, UUID id, String username, String displayName, String email, String phoneNumber, String passwordHash);
    /** 内部初始化与现有调用的兼容入口，缺省不设置联系方式。 */
    default User createUser(UUID actor, UUID id, String username, String displayName, String passwordHash) {
        return createUser(actor,id,username,displayName,null,null,passwordHash);
    }
    /** 维护显示名和联系方式；联系方式变化会重置对应验证状态。 */
    User updateUser(UUID actor, UUID target, String displayName, String email, String phoneNumber);
    /** 停用立即阻止后续认证请求；不能停用自己或任何平台管理员。 */
    void setUserEnabled(UUID actor, UUID target, boolean enabled);
    /**
     * 管理员强制结束目标账号的全部认证，不修改密码或账号启停状态。
     * @param actor 已认证的平台管理员；不能通过该入口撤销本人
     * @param target 目标有效账号 UUID；凭据版本推进和 OAuth 撤销、审计同事务提交
     */
    void revokeUserSessions(UUID actor, UUID target);
    /** @param page 从 1 开始；size 范围 1–100，query 最长 100 字符 */
    Page<Tenant> tenants(UUID actor, int page, int size, String query);
    /** 原子创建租户、首个管理员成员与组织能力；重试必须使用相同 requestId 和参数。 */
    Tenant createTenant(UUID actor, UUID requestId, String name, UUID administratorAccountId);
    /**
     * 修改租户展示名称，不改变租户标识、成员、开通状态或授权。
     * @param actor 已认证的平台管理员账号 UUID
     * @param tenant 不可变租户 UUID
     * @param name 新名称，去除首尾空白后长度为 1 至 100 个字符
     * @return 更新后的租户目录摘要
     */
    Tenant updateTenant(UUID actor, UUID tenant, String name);
    /** 停用租户不删除成员或业务数据，恢复后原有授权仍按有效期判定。 */
    void setTenantEnabled(UUID actor, UUID tenant, boolean enabled);
    /** 分页读取指定租户成员，平台管理只暴露账号关联摘要。 */
    Page<Member> members(UUID actor, UUID tenant, int page, int size, String query);
    /** 新成员默认无组织能力；已移除成员重新加入时复用原成员 ID，且不恢复历史授权。 */
    void addMember(UUID actor, UUID tenant, UUID account);
    /** 停用时原子撤销该成员全部组织权限；恢复成员不会恢复历史授权。 */
    void setMemberEnabled(UUID actor, UUID tenant, UUID member, boolean enabled);
    /** 移除成员但保留历史标识；最后一个有效租户管理员禁止移除。 */
    void removeMember(UUID actor, UUID tenant, UUID member);
}
