package com.appnexus.platform.appapi.organization;
import com.appnexus.contracts.operation.ResponseReferenceNames;
import com.appnexus.contracts.operation.ResponseReferenceNames.Binding;
import com.appnexus.platform.security.*;
import com.appnexus.platform.organization.OrganizationStructure; import java.time.Instant; import java.util.*;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
/** 组织领域 HTTP 入口；权限由权限中心能力校验，组织域只维护结构和任职约束。 */
@RestController @EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS) @RequestMapping("/api/organization/domain") public class OrganizationDomainController {
 private final OrganizationStructure domain; public OrganizationDomainController(OrganizationStructure domain){this.domain=domain;}
 public record TypeInput(String code,String name,Boolean enabled,Long version) {} public record UnitInput(String code,UUID typeId,UUID parentId,String name,Boolean enabled,Long version) {}
 public record MoveInput(UUID parentId,long version) {} public record RelationInput(String relationType,UUID sourceId,UUID targetId) {} public record PositionInput(UUID organizationId,String code,String name,Boolean enabled,Long version) {}
 public record AssignmentInput(UUID personId,UUID positionId,Instant startsAt,Instant endsAt,Boolean enabled,Long version) {} public record LeaderInput(UUID organizationId,UUID assignmentId,boolean selected) {}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="OrganizationType.list", reference=true)
 @EndpointOperation("organization.unit:read") @com.appnexus.contracts.operation.RecordAuditSource(table="tenant_organization_type")
 @GetMapping("/types") List<?> types(){return domain.types();} @com.appnexus.contracts.operation.OperationBinding(id="OrganizationType.create", reference=true)
 @EndpointOperation("organization.unit:create") @PostMapping("/types") ResponseEntity<?> createType(@RequestBody TypeInput i){return ResponseEntity.status(201).body(domain.createType(code(i.code()),name(i.name())));} @com.appnexus.contracts.operation.OperationBinding(id="OrganizationType.update", reference=true)
 @EndpointOperation("organization.unit:update") @PutMapping("/types/{id}") Object updateType(@PathVariable UUID id,@RequestBody TypeInput i){return domain.updateType(id,name(i.name()),required(i.enabled()),required(i.version()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="OrganizationUnit.list",reference=true)
 @EndpointOperation("organization.unit:read") @com.appnexus.contracts.operation.RecordAuditSource(table="tenant_organization_unit")
 @ResponseReferenceNames({
     @Binding(path="",type="organization",id="parentId",name="parentName")})
 @GetMapping("/units") List<?> units(){return domain.units();} @com.appnexus.contracts.operation.OperationBinding(id="OrganizationUnit.create",reference=true)
 @EndpointOperation("organization.unit:create") @PostMapping("/units") ResponseEntity<?> createUnit(@RequestBody UnitInput i){return ResponseEntity.status(201).body(domain.createUnit(code(i.code()),Objects.requireNonNull(i.typeId()),i.parentId(),name(i.name())));} @com.appnexus.contracts.operation.OperationBinding(id="OrganizationUnit.update",reference=true)
 @EndpointOperation("organization.unit:update") @PutMapping("/units/{id}") Object updateUnit(@PathVariable UUID id,@RequestBody UnitInput i){return domain.updateUnit(id,name(i.name()),required(i.enabled()),required(i.version()));} @com.appnexus.contracts.operation.OperationBinding(id="OrganizationUnit.move",reference=true)
 @EndpointOperation("organization.unit:update") @PostMapping("/units/{id}/move") Object move(@PathVariable UUID id,@RequestBody MoveInput i){return domain.moveUnit(id,i.parentId(),i.version());} @com.appnexus.contracts.operation.OperationBinding(id="OrganizationUnit.inspectImpact",reference=true)
 @EndpointOperation("organization.unit:read") @GetMapping("/units/{id}/impact") Object impact(@PathVariable UUID id){return domain.impact(id);}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="OrganizationRelation.list", reference=true)
 @EndpointOperation("organization.unit:read") @com.appnexus.contracts.operation.RecordAuditSource(table="tenant_organization_relation")
 @ResponseReferenceNames({
     @Binding(path="",type="organization",id="sourceId",name="sourceName"),
     @Binding(path="",type="organization",id="targetId",name="targetName"),
     @Binding(type="dictionary:organization_relation_type",id="relationType",name="relationTypeName",uuid=false)})
 @GetMapping("/relations") List<?> relations(){return domain.relations();} @com.appnexus.contracts.operation.OperationBinding(id="OrganizationRelation.create", reference=true)
 @EndpointOperation("organization.unit:create") @PostMapping("/relations") ResponseEntity<?> createRelation(@RequestBody RelationInput i){return ResponseEntity.status(201).body(domain.createRelation(code(i.relationType()),Objects.requireNonNull(i.sourceId()),Objects.requireNonNull(i.targetId())));} @com.appnexus.contracts.operation.OperationBinding(id="OrganizationRelation.delete", reference=true)
 @EndpointOperation("organization.unit:delete") @DeleteMapping("/relations/{id}") void deleteRelation(@PathVariable UUID id,@RequestParam long version){domain.deleteRelation(id,version);}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="Position.list", reference=true)
 @EndpointOperation("organization.unit:read") @com.appnexus.contracts.operation.RecordAuditSource(table="tenant_organization_position")
 @ResponseReferenceNames({
     @Binding(path="",type="organization",id="organizationId",name="organizationName")})
 @GetMapping("/positions") List<?> positions(){return domain.positions();} @com.appnexus.contracts.operation.OperationBinding(id="Position.create", reference=true)
 @EndpointOperation("organization.unit:create") @PostMapping("/positions") ResponseEntity<?> createPosition(@RequestBody PositionInput i){return ResponseEntity.status(201).body(domain.createPosition(Objects.requireNonNull(i.organizationId()),code(i.code()),name(i.name())));} @com.appnexus.contracts.operation.OperationBinding(id="Position.update", reference=true)
 @EndpointOperation("organization.unit:update") @PutMapping("/positions/{id}") Object updatePosition(@PathVariable UUID id,@RequestBody PositionInput i){return domain.updatePosition(id,name(i.name()),required(i.enabled()),required(i.version()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="PositionAssignment.list", reference=true)
 @EndpointOperation("organization.unit:read") @com.appnexus.contracts.operation.RecordAuditSource(table="tenant_person_position")
 @ResponseReferenceNames({
     @Binding(path="",type="position",id="positionId",name="positionName"),
     @Binding(type="person",id="personId",name="personName")})
 @GetMapping("/assignments") List<?> assignments(){return domain.assignments();} @com.appnexus.contracts.operation.OperationBinding(id="PositionAssignment.create", reference=true)
 @EndpointOperation("organization.unit:create") @PostMapping("/assignments") ResponseEntity<?> createAssignment(@RequestBody AssignmentInput i){return ResponseEntity.status(201).body(domain.createAssignment(Objects.requireNonNull(i.personId()),Objects.requireNonNull(i.positionId()),Objects.requireNonNull(i.startsAt()),i.endsAt()));} @com.appnexus.contracts.operation.OperationBinding(id="PositionAssignment.update", reference=true)
 @EndpointOperation("organization.unit:update") @PutMapping("/assignments/{id}") Object updateAssignment(@PathVariable UUID id,@RequestBody AssignmentInput i){return domain.updateAssignment(id,i.endsAt(),required(i.enabled()),required(i.version()));}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="OrganizationUnit.listLeaders", reference=true)
 @EndpointOperation("organization.unit:read") @ResponseReferenceNames({
     @Binding(path="",type="organization",id="organizationId",name="organizationName")})
 @GetMapping("/leaders") List<?> leaders(){return domain.leaders();} @com.appnexus.contracts.operation.OperationBinding(id="OrganizationUnit.setLeader", reference=true)
 @EndpointOperation("organization.unit:update") @PutMapping("/leaders") void leader(@RequestBody LeaderInput i){domain.setLeader(Objects.requireNonNull(i.organizationId()),Objects.requireNonNull(i.assignmentId()),i.selected());}
 /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
 @com.appnexus.contracts.operation.OperationBinding(id="PersonDirectory.list",reference=true)
 @EndpointOperation("organization.unit:read") @GetMapping("/persons") List<?> persons(){return domain.persons();}
 private String code(String value){if(value==null||!value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,49}"))throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_CODE","编码须为 1–50 位字母、数字、点、横线或下划线");return value;} private String name(String value){if(value==null||value.isBlank()||value.length()>100)throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_NAME","名称须为 1–100 个字符");return value.trim();} private boolean required(Boolean value){return Objects.requireNonNull(value,"enabled");} private long required(Long value){return Objects.requireNonNull(value,"version");}
}
