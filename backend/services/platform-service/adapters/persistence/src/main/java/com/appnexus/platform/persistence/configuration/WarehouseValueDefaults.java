package com.appnexus.platform.persistence.configuration;

import java.util.*;
import java.nio.charset.StandardCharsets;
/** 仓库分类独立版本资源；迁移与新租户开通只补缺失项，不覆盖自定义名称和停用状态。 */
public final class WarehouseValueDefaults {
 private WarehouseValueDefaults(){}
 /** @return V91不可变资源字节，后续调整另建迁移与资源版本 */
 public static byte[] resource(){try(var stream=WarehouseValueDefaults.class.getResourceAsStream("/warehouse-value-defaults-v91.tsv")){return Objects.requireNonNull(stream).readAllBytes();}catch(java.io.IOException e){throw new IllegalStateException("WAREHOUSE_DEFAULTS_UNAVAILABLE",e);}}
 /** @param mapper 宿主写事务或迁移连接 @param tenant 可信租户 @param actor 开通账号；迁移为空，不伪造账号 */
 public static void seed(HistoricalDictionaryMapper mapper,UUID tenant,UUID actor){int order=0;for(String line:new String(resource(),StandardCharsets.UTF_8).split("\n")){if(line.isBlank()||line.startsWith("#"))continue;var row=line.stripTrailing().split("\t",-1);BusinessValueDefaults.add(mapper,tenant,actor,row[0],row[1],row[2],row[3],++order);}}
}
