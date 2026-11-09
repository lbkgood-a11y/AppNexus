package com.appnexus.platform.authorization;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 业务范围条件模型：分支内维度取交集，完整分支之间取并集，禁止分别合并各维度后组合。
 * 本模型不读取授权表，也不替代身份验证、角色有效性和业务状态检查。
 * 仓储适配器应将条件编译为受控查询与条件写入，禁止查询全量后逐行调用本模型过滤。
 */
public final class BusinessScope {
    private BusinessScope() {}

    /**
     * 已注册的资源范围契约；标识由领域模块声明，不接受客户端 SQL、表名或脚本。
     * @param resource 稳定资源标识，例如 inventory.stock
     * @param dimensions 非空必需维度集合，例如 warehouse、owner；各维度值使用领域对象 UUID
     */
    public record Definition(String resource, Set<String> dimensions) {
        /** 校验领域声明并复制集合，避免调用方修改授权契约。 */
        public Definition {
            identifier(resource);
            dimensions = Set.copyOf(dimensions);
            if (dimensions.isEmpty()) throw new IllegalArgumentException("业务范围必须声明维度");
            dimensions.forEach(BusinessScope::identifier);
        }
    }

    /**
     * 独立授权分支；同一角色可以产生多个来源、范围或期限不同的分支。
     * @param grantId 授权记录 UUID，必填，用于独立撤销和追踪
     * @param tenantId 可信租户 UUID，必填
     * @param permissionCode 当前分支操作权限码，必填
     * @param values 维度与非空对象 UUID 集合；必须完整覆盖资源契约，不支持缺省全部或通配符
     * @param expiresAt 到期 UTC 时间；null 表示长期，到期瞬间开始失效
     */
    public record Branch(UUID grantId, UUID tenantId, String permissionCode,
                         Map<String, Set<UUID>> values, Instant expiresAt) {
        /** 深复制范围值；空维度不能隐式扩大为全部。 */
        public Branch {
            Objects.requireNonNull(grantId);
            Objects.requireNonNull(tenantId);
            if (permissionCode == null || !permissionCode.matches("[a-z][a-z0-9.]*:[a-z][a-z0-9._-]*")) {
                throw new IllegalArgumentException("无效权限码");
            }
            Objects.requireNonNull(values);
            var copy = new java.util.HashMap<String, Set<UUID>>();
            values.forEach((dimension, ids) -> {
                identifier(dimension);
                var immutable = Set.copyOf(ids);
                if (immutable.isEmpty()) throw new IllegalArgumentException("范围值不能为空");
                copy.put(dimension, immutable);
            });
            values = Map.copyOf(copy);
        }
    }

    /**
     * 为一次操作生成不可变范围计划；空分支表示拒绝。
     * @param definition 服务端注册的资源范围契约
     * @param tenantId 当前可信租户 UUID
     * @param permissionCode 本次请求的操作码
     * @param now 服务端判定时刻，UTC，不能由请求参数提供
     * @param branches 已完成主体、角色、当前权限上限和撤销校验的分支
     * @return 仅包含当前租户、操作、期限和完整维度匹配的分支；未知或缺少维度的分支被拒绝
     */
    public static List<Branch> resolve(Definition definition, UUID tenantId, String permissionCode,
                                       Instant now, List<Branch> branches) {
        Objects.requireNonNull(definition);
        Objects.requireNonNull(now);
        if (tenantId == null || permissionCode == null || branches == null
                || !permissionCode.startsWith(definition.resource() + ":")) return List.of();
        return branches.stream().filter(Objects::nonNull)
                .filter(branch -> branch.tenantId().equals(tenantId))
                .filter(branch -> branch.permissionCode().equals(permissionCode))
                .filter(branch -> branch.expiresAt() == null || branch.expiresAt().isAfter(now))
                .filter(branch -> branch.values().keySet().equals(definition.dimensions()))
                .toList();
    }

    /**
     * 对单个业务目标检查范围，适用于领域判定及仓储编译结果的契约测试。
     * @param definition 服务端注册的资源契约
     * @param tenantId 可信租户 UUID
     * @param permissionCode 当前操作权限码
     * @param now 服务端当前时刻，每次执行须重新检查到期状态
     * @param branches 已复核身份及授权状态的分支
     * @param target 从可信业务对象读取的完整维度，不能相信客户端声称的归属
     * @return 至少一个完整分支同时匹配全部维度时为 true；不得用于列表查询后过滤
     */
    public static boolean allows(Definition definition, UUID tenantId, String permissionCode,
                                 Instant now, List<Branch> branches, Map<String, UUID> target) {
        if (target == null || !target.keySet().equals(definition.dimensions())
                || target.values().stream().anyMatch(Objects::isNull)) return false;
        return resolve(definition, tenantId, permissionCode, now, branches).stream()
                .anyMatch(branch -> target.entrySet().stream()
                        .allMatch(entry -> branch.values().get(entry.getKey()).contains(entry.getValue())));
    }

    /** 只接受领域标识，拒绝 SQL 片段与任意表达式。 */
    private static void identifier(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9.]*")) {
            throw new IllegalArgumentException("无效业务维度或资源标识");
        }
    }
}
