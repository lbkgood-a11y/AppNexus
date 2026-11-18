package db.migration;
import org.flywaydb.core.api.migration.*;
import java.sql.*;
import java.util.*;
/** 只替换V1模式/形态/模板用途CHECK，不改历史迁移；约束名从MySQL元数据读取并校验，保留其他完整性约束。 */
public final class V115__product_extended_modes extends BaseJavaMigration {
 @Override public void migrate(Context context)throws Exception{
  replace(context.getConnection(),"md_product_v1","specification_mode","specification_mode IN ('NONE','STANDARD','CUSTOM')");
  replace(context.getConnection(),"md_product_v1","product_form","product_form IS NULL OR product_form IN ('PHYSICAL','SERVICE','COMPOSITE')");
  for(String table:List.of("md_product_template","md_product_category_template"))replace(context.getConnection(),table,"purpose","purpose IN ('BASIC','SPEC','QUALITY','EXTENSION')");
 }
 private void replace(Connection c,String table,String column,String expression)throws SQLException{
  var names=new ArrayList<String>();try(var q=c.prepareStatement("SELECT tc.CONSTRAINT_NAME,cc.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME=? AND tc.CONSTRAINT_TYPE='CHECK'")){q.setString(1,table);try(var r=q.executeQuery()){while(r.next()){String clause=r.getString(2);if(clause.contains(column)){String name=r.getString(1);if(!name.matches("[A-Za-z0-9_]+"))throw new SQLException("Unexpected constraint identifier");names.add(name);}}}}
  if(names.size()!=1)throw new SQLException("Expected exactly one mode constraint: "+table+"."+column);
  try(var s=c.createStatement()){s.execute("ALTER TABLE `"+table+"` DROP CHECK `"+names.getFirst()+"`, ADD CONSTRAINT `ck_ext_"+table+"_"+column+"` CHECK("+expression+")");}
 }
}
