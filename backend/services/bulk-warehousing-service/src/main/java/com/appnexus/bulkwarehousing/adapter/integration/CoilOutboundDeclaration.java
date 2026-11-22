package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.bulkwarehousing.api.CoilOperations;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.DedicatedOperations;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 整卷出仓通知、授权、配额及配货聚合的唯一平台声明。 */
public final class CoilOutboundDeclaration implements Provider {
  /** {@inheritDoc} */
  @Override public Descriptor descriptor() {
    var operations=new java.util.ArrayList<>(Arrays.stream(CoilOperations.values()).map(CoilOutboundDeclaration::operation).toList());
    var path="/api/bulk-coil-return-transits/eligible-coils/{handoverId}";
    operations.add(DedicatedOperations.operation(CoilOperations.OBJECT,"prepare_return","从原交接发起返仓",
        "Start return from handover",CoilOperations.DISPATCH_RETURN.permission(),
        WarehousingPage.flowPage("从原交接发起返仓","Start return from handover","GET",path),
        WarehousingPage.transitPage("从原交接发起返仓","Start return from handover","GET",path)));
    var recovery="/api/bulk-pickup-authorizations/{id}/coil-recovery";
    operations.add(DedicatedOperations.operation(CoilOperations.OBJECT,"recover_authorization","查看提货授权续办",
        "Resume pickup authorization",CoilOperations.READ_AUTHORIZATION_BALANCES.permission(),
        WarehousingPage.flowPage("查看提货授权续办","Resume pickup authorization","GET",recovery)));
    var entries="/api/bulk-pickup-authorizations/{id}/recovery-entries";
    operations.add(DedicatedOperations.operation(CoilOperations.OBJECT,"list_recovery_entries","查找授权续办卷",
        "Find coils for authorization recovery",CoilOperations.READ_AUTHORIZATION_BALANCES.permission(),
        new PageOperation("warehousing/coil-title","/warehousing/coil-title","查找授权续办卷","Find coils for authorization recovery","GET",entries)));
    var scopes=Arrays.stream(CoilOperations.values()).collect(Collectors.toMap(CoilOperations::code,op -> Set.of("ALL")));
    scopes.put("prepare_return",Set.of("ALL"));
    scopes.put("recover_authorization",Set.of("ALL"));
    scopes.put("list_recovery_entries",Set.of("ALL"));
    return new Descriptor(CoilOperations.OBJECT,"整卷出仓履约","Coil outbound fulfillment","bulk-warehousing",1,
        Category.TRANSACTION,List.of(),operations,new DataAccess("TENANT",List.of(
            new DataResource(CoilOperations.RESOURCE,"","整卷出仓履约","Coil outbound fulfillment",List.of())),scopes));
  }
  private static Operation operation(CoilOperations op) {
    if (op==CoilOperations.READ_AUTHORIZATION_BALANCES) {
      var path="/api/bulk-pickup-authorization-balances";
      return DedicatedOperations.operation(CoilOperations.OBJECT,op.code(),op.zh(),op.en(),op.permission(),
          new PageOperation("warehousing/coil-title","/warehousing/coil-title",op.zh(),op.en(),"GET",path),
          WarehousingPage.flowPage(op.zh(),op.en(),"GET",path));
    }
    if (op==CoilOperations.ISSUE || op==CoilOperations.READ_RETURN_TRANSIT) {
      String path=op==CoilOperations.ISSUE ? "/api/bulk-outbound-notices" : "/api/bulk-coil-return-transits/{id}";
      String method=op==CoilOperations.ISSUE ? "POST" : "GET";
      return DedicatedOperations.operation(CoilOperations.OBJECT,op.code(),op.zh(),op.en(),op.permission(),
          WarehousingPage.flowPage(op.zh(),op.en(),method,path),WarehousingPage.transitPage(op.zh(),op.en(),method,path));
    }
    String transitPath=switch(op) {
      case DISPATCH_RETURN -> "/api/bulk-coil-return-transits";
      case RELEASE_RETURNED_QUALITY -> "/api/bulk-coil-returns:release-quality";
      case SETTLE_TRANSIT_LOSS -> "/api/bulk-coil-return-transits:settle-loss";
      case REOPEN_TRANSIT_LOSS -> "/api/bulk-coil-return-transits:reopen-loss";
      case REROUTE_TRANSIT_COIL -> "/api/bulk-coil-return-transits:reroute-coil";
      default -> null;
    };
    if (transitPath!=null) {
      return DedicatedOperations.operation(CoilOperations.OBJECT,op.code(),op.zh(),op.en(),op.permission(),
          WarehousingPage.transitPage(op.zh(),op.en(),"POST",transitPath));
    }
    if (op==CoilOperations.RETURN_COILS) {
      String path="/api/bulk-coil-returns:post-coils";
      return DedicatedOperations.operation(CoilOperations.OBJECT,op.code(),op.zh(),op.en(),op.permission(),
          WarehousingPage.flowPage(op.zh(),op.en(),"POST",path),WarehousingPage.transitPage(op.zh(),op.en(),"POST",path));
    }
    String path=switch(op) {
      case IDENTIFY -> "/api/bulk-stock-coils";
      case AUTHORIZE -> "/api/bulk-pickup-authorizations";
      case PLAN -> "/api/bulk-outbound-trips";
      case CANCEL -> "/api/bulk-coil-allocations:cancel";
      case ALLOCATE -> "/api/bulk-coil-allocations";
      case VEHICLE_LIMITS -> "/api/bulk-outbound-trips:verify-vehicle-limits";
      case REVIEW_WEIGHING -> "/api/bulk-outbound-trips:review-weighing";
      case HANDOVER -> "/api/bulk-outbound-trips:handover";
      default -> null;
    };
    return path==null ? DedicatedOperations.operation(CoilOperations.OBJECT,op.code(),op.zh(),op.en(),op.permission())
        : WarehousingPage.flow(CoilOperations.OBJECT,op.code(),op.zh(),op.en(),op.permission(),"POST",path);
  }
}
