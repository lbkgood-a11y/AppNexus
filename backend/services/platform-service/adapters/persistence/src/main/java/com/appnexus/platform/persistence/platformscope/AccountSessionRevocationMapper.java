package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.persistence.PlatformScoped;
import org.apache.ibatis.annotations.*;

/** 平台认证撤销专用仓储；只能在已锁定操作者和目标账号的管理事务中调用。 */
@Mapper
@PlatformScoped(tables={"identity_account", "oauth2_authorization"})
public interface AccountSessionRevocationMapper {
    /**
     * 推进目标凭据版本，使 Cookie 会话在下一请求失效；不更改密码及业务权限。
     * @param account 已校验且锁定的目标账号 UUID 字符串
     * @return 更新条数，必须为 1
     */
    @Update("UPDATE identity_account SET version=version+1 WHERE id=UUID_TO_BIN(#{account}) AND enabled=TRUE")
    int advanceVersion(String account);

    /**
     * 删除目标账号全部 OAuth 授权，旧 Access Token 及授权码不得继续使用。
     * @param account 不可变主体标识，必须与推进版本的目标一致
     * @return 删除条数；没有 OAuth 授权时合法为零
     */
    @Delete("DELETE FROM oauth2_authorization WHERE principal_name=#{account}")
    int revokeAuthorizations(String account);
}
