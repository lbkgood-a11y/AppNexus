package com.appnexus.platform.authorization;

import java.util.List;

/** 统一字段目录；实现由服务端扫描资源声明生成，保存策略与响应执行使用同一份目录。 */
public interface FieldCatalog {
    /** 返回指定管理域内已声明且已通过启动校验的字段。 */
    List<FieldSecurity.Descriptor> list(PermissionCatalog.ScopeType scope);
}
