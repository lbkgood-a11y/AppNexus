package com.appnexus.printing.core;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** 打印回执状态约束，未知结果不能隐式退回可重印失败。 */
class PrintJobLifecycleTest {
  /** 领取租约过期拒绝，确认送入队列后可完成；不得跨越送入阶段。 */
  @Test void enforcesPhysicalSubmissionAndLeaseBoundary() {
    assertThat(PrintJobLifecycle.permitsReceipt("CLAIMED", "SUCCEEDED", false)).isFalse();
    assertThat(PrintJobLifecycle.permitsReceipt("CLAIMED", "SUBMITTED", true)).isFalse();
    assertThat(PrintJobLifecycle.permitsReceipt("CLAIMED", "SUBMITTED", false)).isTrue();
    assertThat(PrintJobLifecycle.permitsReceipt("SUBMITTED", "SUCCEEDED", true)).isTrue();
    assertThat(PrintJobLifecycle.permitsReceipt("SUBMITTED", "FAILED", false)).isFalse();
    assertThat(PrintJobLifecycle.permitsReceipt("UNKNOWN", "SUCCEEDED", false)).isFalse();
    assertThat(PrintJobLifecycle.validReceipt(null)).isFalse();
    assertThat(PrintJobLifecycle.validReceipt("QUEUED")).isFalse();
  }
}
