package com.appnexus.platform.identity;

import java.util.UUID;

/**
 * 当前账号可自助查看和维护的资料，不包含密码、Token、角色或授权信息。
 *
 * @param id 不可变账号标识，仅用于辅助识别，不能由个人中心修改
 * @param username 登录账号，第一期不支持本人修改
 * @param displayName 显示名称，长度为 1 至 100 个字符
 * @param email 归一化为小写的邮箱；缺省表示尚未设置
 * @param emailVerified 邮箱是否已通过受信验证流程；修改邮箱后必须重置为 false
 * @param phoneNumber E.164 手机号；缺省表示尚未设置
 * @param phoneVerified 手机号是否已通过受信验证流程；修改手机号后必须重置为 false
 */
public record AccountProfile(UUID id, String username, String displayName, String email,
        boolean emailVerified, String phoneNumber, boolean phoneVerified) {}
