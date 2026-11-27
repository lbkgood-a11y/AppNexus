import {describe,it,expect} from "vitest";
import {geographyPath,geographyTree,type GeographyNode} from "../../../../infra/ui/src/geography";
/** 验证真实层级约束，避免跨国、环和停用祖先成为新选项。 */
describe("geography hierarchy",()=>{
 const nodes:GeographyNode[]=[{code:"CN",name:"中国",countryCode:"CN",parentCode:"",enabled:true},{code:"CN-FJ",name:"福建",countryCode:"CN",parentCode:"CN",enabled:true},{code:"CN-MCA-350100",name:"福州",countryCode:"CN",parentCode:"CN-FJ",enabled:true},{code:"CN-MCA-350102",name:"鼓楼",countryCode:"CN",parentCode:"CN-MCA-350100",enabled:true}];
 it("resolves real paths and limits province/city fields",()=>{
  expect(geographyPath(nodes,"CN-MCA-350102").map(n=>n.code)).toEqual(nodes.map(n=>n.code));
  expect(geographyTree(nodes,"CN","","province")[0].children[0].children).toEqual([]);
  expect(geographyTree(nodes,"CN","CN-FJ","city")[0].children[0].children).toEqual([]);
  expect(geographyTree(nodes,"US")).toEqual([]);
 });
 it("rejects orphan, cycle and mismatched country paths",()=>{
  expect(geographyPath(nodes,"历史文本")).toEqual([]);
  expect(geographyPath(nodes.map(n=>n.code==="CN"?{...n,parentCode:"CN-FJ"}:n),"CN-FJ")).toEqual([]);
  expect(geographyPath(nodes.map(n=>n.code==="CN-FJ"?{...n,countryCode:"US"}:n),"CN-FJ")).toEqual([]);
 });
 it("disables descendants of a stopped region",()=>{
  const tree=geographyTree(nodes.map(n=>n.code==="CN-FJ"?{...n,enabled:false}:n));
  expect(tree[0].children[0].children[0].disabled).toBe(true);
 });
});
