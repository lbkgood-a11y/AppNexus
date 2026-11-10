package com.appnexus.printing.core;

import java.util.Set;
import java.util.regex.Pattern;

/** 打印领域共享约束；服务端保存与发布均执行，避免只依赖设计器前端校验。 */
public final class PrintRules {
  private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
  private static final Set<String> OUTPUTS = Set.of("PDF", "PRINTER");

  private PrintRules() {}

  /** @param value 模板、契约或设备稳定编码，长度 1—64 @return 是否符合受控标识格式 */
  public static boolean validCode(String value) {
    return value != null && CODE.matcher(value).matches();
  }

  /** @param value 输出渠道；当前正式支持 PDF 与 PRINTER @return 是否为受支持渠道 */
  public static boolean validOutput(String value) {
    return OUTPUTS.contains(value);
  }
}
