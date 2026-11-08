package com.appnexus.platform.configuration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 租户业务字典管理边界；系统枚举和业务参数不属于本接口。
 * 调用方必须提供服务端已验证的租户与成员上下文，编码创建后保持不变。
 */
public interface DictionaryManagement {
    /** 字典及字典项状态；停用项保留历史含义，但不应进入新的业务选择。 */
    enum Status { ENABLED, DISABLED }

    /**
     * 可信调用上下文。
     * @param actorAccountId 已认证账号 UUID
     * @param tenantId 当前服务端会话选择的租户 UUID
     * @param actorMemberId 当前租户成员 UUID
     */
    record Context(UUID actorAccountId, UUID tenantId, UUID actorMemberId) {}

    /** @param code 租户内永久唯一且创建后不可修改的稳定编码 @param version 乐观锁版本 */
    record Dictionary(UUID id, String code, String name, String description, Status status,
                      int sortOrder, long version, Instant updatedAt) {}

    /**
     * 字典项；parentId 为空表示根项。
     * @param value 稳定业务值，创建后不可修改
     * @param referenced 是否已有业务引用；已引用项禁止删除
     */
    record Item(UUID id, UUID dictionaryId, UUID parentId, String code, String value, String name,
                String description, Status status, int sortOrder, boolean referenced,
                long version, Instant updatedAt) {}

    /** @param code 2–64 位小写字母、数字或下划线 @param sortOrder 排序值 0–9999 */
    record CreateDictionary(String code, String name, String description, int sortOrder) {}
    /** 字典编码不可修改；状态变化也通过本编辑命令显式提交。 */
    record UpdateDictionary(String name, String description, Status status, int sortOrder, long version) {}
    /** @param value 1–100 字符稳定业务值 @param parentId 可空的同字典上级项 */
    record CreateItem(UUID parentId, String code, String value, String name, String description, int sortOrder) {}
    /** 字典项编码和值不可修改；parentId 变化必须保持无环层级。 */
    record UpdateItem(UUID parentId, String name, String description, Status status, int sortOrder, long version) {}

    List<Dictionary> dictionaries(Context context);
    Dictionary createDictionary(Context context, CreateDictionary input);
    Dictionary updateDictionary(Context context, UUID dictionaryId, UpdateDictionary input);
    void deleteDictionary(Context context, UUID dictionaryId, long version);
    List<Item> items(Context context, UUID dictionaryId);
    Item createItem(Context context, UUID dictionaryId, CreateItem input);
    Item updateItem(Context context, UUID dictionaryId, UUID itemId, UpdateItem input);
    void deleteItem(Context context, UUID dictionaryId, UUID itemId, long version);
    /** @return 当前租户指定编码下可用于新业务录入的启用字典项；只要求有效成员身份 */
    List<Item> enabledItems(Context context, String dictionaryCode);
}
