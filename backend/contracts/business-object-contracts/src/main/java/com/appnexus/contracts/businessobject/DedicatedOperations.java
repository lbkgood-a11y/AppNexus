package com.appnexus.contracts.businessobject;

import com.appnexus.contracts.businessobject.BusinessObjects.Operation;
import com.appnexus.contracts.businessobject.BusinessObjects.OperationAlias;
import com.appnexus.contracts.businessobject.BusinessObjects.PageOperation;
import java.util.stream.IntStream;
import java.util.List;

/** 专用入口的统一契约组装；真实用例继续决定身份、对象归属和执行资格。 */
public final class DedicatedOperations {
    private DedicatedOperations() {}

    /**
     * 同一操作可关联多个真实页面或同一页面的多个 API 路径，不开放公共执行或专业能力。
     * @param type 唯一对象编码
     * @param code 对象内稳定操作编码
     * @param nameZh 中文名称
     * @param nameEn 英文名称
     * @param permission 权限码；本人和对象准入的专用入口可为空，由原执行器复核
     * @param pages 真实页面和 API 关联；无页面专用入口传空集合
     * @return 专用操作契约
     */
    public static Operation operation(String type, String code, String nameZh, String nameEn,
            String permission, PageOperation... pages) {
        String id = type + "." + code;
        var aliases = IntStream.range(1, pages.length)
                .mapToObj(index -> new OperationAlias(id + "@entry" + index, pages[index])).toList();
        var interaction = new InteractionContract(id, 1, type, code, nameZh, nameEn,
                InteractionContract.Trigger.USER_ACTION, List.of());
        return new Operation(code, nameZh, nameEn, permission, id,
                pages.length == 0 ? null : pages[0], aliases, interaction);
    }
}
