import {describe,expect,it} from "vitest";
import {personnelAccess,personnelEditOperations} from "./access";

/** 人员入口与独立操作权限回归；查看态不能写，明细权限不得扩大为主档或账号权限。 */
describe("personnel page access",()=>{
  const all=["Person.read","Person.create",...personnelEditOperations];
  it("keeps a fully authorized viewer read-only",()=>{
    const access=personnelAccess(all,"view");
    expect(access.canRead).toBe(true);
    for(const operation of ["Person.create",...personnelEditOperations])expect(access.allows(operation)).toBe(false);
  });
  it("does not make read permission an edit permission",()=>{
    const access=personnelAccess(["Person.read"],"edit");
    expect(access.canRead).toBe(true);
    expect(access.canOpenEdit).toBe(false);
    expect(access.allows("Person.update")).toBe(false);
  });
  it.each(personnelEditOperations)("honors %s without granting other writes",operation=>{
    const access=personnelAccess(["Person.read",operation],"edit");
    expect(access.canOpenEdit).toBe(true);
    expect(access.allows(operation)).toBe(true);
    for(const other of ["Person.create",...personnelEditOperations].filter(value=>value!==operation))expect(access.allows(other)).toBe(false);
  });
  it("requires profile read access even for write grants",()=>{
    const access=personnelAccess(["Person.update","Person.assign"],"edit");
    expect(access.canRead).toBe(false);
    expect(access.canOpenEdit).toBe(false);
    expect(access.allows("Person.update")).toBe(false);
    expect(access.allows("Person.assign")).toBe(false);
  });
  it("fails closed after revocation and for unknown operation identifiers",()=>{
    expect(personnelAccess([],"edit").allows("Person.update")).toBe(false);
    expect(personnelAccess([...all,"Person.unknown"],"edit").allows("Person.unknown")).toBe(false);
    expect(personnelAccess(["Person.read","Person.create"],"edit").allows("Person.create")).toBe(true);
  });
});
