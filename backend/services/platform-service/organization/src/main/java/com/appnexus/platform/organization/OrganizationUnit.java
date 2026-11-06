package com.appnexus.platform.organization;
import java.util.*;
/** 租户内组织单元的最小读写模型；租户归属由执行上下文提供，不接受客户端设置。 */
public record OrganizationUnit(UUID id, String name) {
    public OrganizationUnit {
        Objects.requireNonNull(id); Objects.requireNonNull(name);
        if(name.isBlank() || name.length()>100) throw new IllegalArgumentException("Invalid organization unit name");
    }
}
