package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.authorization.DataScopeAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import static com.appnexus.bulkwarehousing.application.inboundexecution.ReceiptExecutionRepository.*;

/** 内部过账控制，固定授权、锁、回执、版本及库存效果的执行顺序。 */
public final class ExecuteReceiptService {
    private static final String RESOURCE = "warehousing.receipt";

    /**
     * 操作输入。过账与冲红只以同一作业的已保存事实为准。
     * @param taskId 作业 UUID
     * @param receiptId 同作业的目标入库单 UUID
     * @param taskVersion 客户端最后读取的作业版本，非负；没有缺省值
     * @param receiptVersion 客户端最后读取的入库单版本，非负；没有缺省值
     * @param idempotencyKey 调用方在原请求的各次重试间复用的键，1 至 96 个允许的 ASCII 字符
     */
    public record Command(UUID taskId, UUID receiptId, long taskVersion, long receiptVersion, String idempotencyKey) {}

    /** 过账或冲红的稳定结果，重试返回首次产生的版本。 */
    public record Result(UUID receiptId, long receiptVersion) {}

    /** 在同一事务内执行单据、库存、通知额度与 Outbox 写入。 */
    public interface Effects {
        /**
         * @param plan 锁内已复核范围的权限计划
         * @param context 可信租户和执行账号
         * @param action 过账或冲红
         * @param command 已校验归属和版本的命令
         * @param nextVersion 应原子推进到的入库单版本，写入仍须带原版本谓词
         */
        void execute(AccessPlan plan, VerifiedTenantContext context, Action action, Command command, long nextVersion);
    }

    private final ReceiptExecutionRepository repository;
    private final AccessPlanning authorization;
    private final Effects effects;

    /**
     * @param repository 绑定组合应用数据库事务及作业锁的仓储
     * @param authorization 从当前主体和固定操作解析统一权限计划
     * @param effects 同一事务内真实更新单据、库存、额度及 Outbox
     */
    public ExecuteReceiptService(ReceiptExecutionRepository repository, AccessPlanning authorization, Effects effects) {
        this.repository = Objects.requireNonNull(repository);
        this.authorization = Objects.requireNonNull(authorization);
        this.effects = Objects.requireNonNull(effects);
    }

    /**
     * @param context 服务端已认证主体
     * @param action 从专用过账或冲红操作指定的动作
     * @param command 作业和入库单 UUID、两个非负版本及幂等键
     * @return 首次或已提交的同请求结果
     * @throws Rejection 参数、范围、状态、版本或重放冲突
     */
    public Result execute(VerifiedTenantContext context, Action action, Command command) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(action);
        validate(command);
        return repository.transaction(() -> {
            String permission = action == Action.POST ? RESOURCE + ":post" : RESOURCE + ":reverse";
            var subject = new AccessAuthorization.Subject(context.accountId(), context.tenantId(),
                    context.memberId(), context.personId(), context.organizationId());
            var plan = authorization.plan(subject, permission, RESOURCE);
            if (!subject.equals(plan.subject()) || !permission.equals(plan.permissionCode())
                    || !RESOURCE.equals(plan.resource())
                    || plan.rows().type() == DataScopeAuthorization.ScopeType.DENY) {
                throw new Rejection(403, "INBOUND_EXECUTION_ACCESS_DENIED");
            }
            var target = repository.lock(plan, command.taskId(), command.receiptId())
                    .orElseThrow(() -> new Rejection(403, "INBOUND_EXECUTION_ACCESS_DENIED"));
            var digest = digest(action, command);
            var previous = repository.findExecution(context, command.taskId(), action, command.idempotencyKey());
            if (previous.isPresent()) {
                var execution = previous.get();
                if (!execution.receiptId().equals(command.receiptId()) || !execution.digest().equals(digest)) {
                    throw new Rejection(409, "INBOUND_EXECUTION_IDEMPOTENCY_CONFLICT");
                }
                return new Result(execution.receiptId(), execution.resultingVersion());
            }

            if (target.taskVersion() != command.taskVersion() || target.receiptVersion() != command.receiptVersion()) {
                throw new Rejection(409, "INBOUND_EXECUTION_VERSION_CONFLICT");
            }
            State expected = action == Action.POST ? State.DRAFT : State.POSTED;
            if (target.state() != expected) {
                throw new Rejection(409, "INBOUND_EXECUTION_STATE_CONFLICT");
            }

            long nextVersion = Math.incrementExact(target.receiptVersion());
            effects.execute(plan, context, action, command, nextVersion);
            repository.insertExecution(context, command.taskId(), action, command.idempotencyKey(),
                    new Execution(command.receiptId(), digest, nextVersion));
            return new Result(command.receiptId(), nextVersion);
        });
    }

    private static void validate(Command command) {
        if (command == null || command.taskId() == null || command.receiptId() == null
                || command.taskVersion() < 0 || command.receiptVersion() < 0 || command.idempotencyKey() == null
                || !command.idempotencyKey().matches("[A-Za-z0-9._:-]{1,96}")) {
            throw new Rejection(400, "INBOUND_EXECUTION_INPUT_INVALID");
        }
    }

    private static String digest(Action action, Command command) {
        String content = String.join("|", "receipt-execution-v1", action.name(), command.taskId().toString(),
                command.receiptId().toString(), Long.toString(command.taskVersion()), Long.toString(command.receiptVersion()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境缺少 SHA-256", e);
        }
    }
}
