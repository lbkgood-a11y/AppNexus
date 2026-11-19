package com.appnexus.masterdata.domain.product;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.masterdata.MasterData.Rejection;
import org.junit.jupiter.api.Test;

/** 在无数据库和 Spring 的条件下验证生命周期约束，阻止分层迁移放宽归档及状态限制。 */
class ProductLifecycleTest {
  @Test void draftCannotBeDisabledAndRepeatedTransitionsAreRejected() {
    assertThatThrownBy(() -> ProductLifecycle.requireTransition("DRAFT", "DISABLED", false))
        .isInstanceOf(Rejection.class).hasMessage("PRODUCT_V1_CHANGED");
    assertThatThrownBy(() -> ProductLifecycle.requireTransition("ENABLED", "ENABLED", false))
        .hasMessage("PRODUCT_V1_CHANGED");
    assertThatCode(() -> ProductLifecycle.requireTransition("DRAFT", "ENABLED", false)).doesNotThrowAnyException();
    assertThatCode(() -> ProductLifecycle.requireTransition("ENABLED", "DISABLED", false)).doesNotThrowAnyException();
  }

  @Test void archiveAndLegacyStatePreventOrdinaryWrites() {
    assertThatThrownBy(() -> ProductLifecycle.requireMutable(true, "COMPLETE")).hasMessage("PRODUCT_V1_CHANGED");
    assertThatThrownBy(() -> ProductLifecycle.requireMutable(false, "LEGACY_PENDING")).hasMessage("PRODUCT_V1_CHANGED");
    assertThatThrownBy(() -> ProductLifecycle.requireTransition("DISABLED", "ENABLED", true)).hasMessage("PRODUCT_V1_CHANGED");
    assertThatCode(() -> ProductLifecycle.requireMutable(false, "COMPLETE")).doesNotThrowAnyException();
  }

  @Test void onlyDraftCanArchiveAndRestoreWithoutRepeatedEvents() {
    assertThatCode(() -> ProductLifecycle.requireArchive("DRAFT", false, true)).doesNotThrowAnyException();
    assertThatCode(() -> ProductLifecycle.requireArchive("DRAFT", true, false)).doesNotThrowAnyException();
    assertThatThrownBy(() -> ProductLifecycle.requireArchive("ENABLED", false, true)).hasMessage("PRODUCT_V1_CHANGED");
    assertThatThrownBy(() -> ProductLifecycle.requireArchive("DRAFT", true, true)).hasMessage("PRODUCT_V1_CHANGED");
  }
}
