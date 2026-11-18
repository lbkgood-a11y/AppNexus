package db.migration;

import java.sql.Connection;
import org.flywaydb.core.api.migration.*;

/**
 * 正式单写入口切换前的强制存量导入与逐列对账。
 * 必须在受控停写窗口部署；若旧源与已暂存快照不符或编码碰撞，迁移失败，应用不得启动新写入口。
 * 不覆盖新档、不改旧表、不DROP历史列；恢复旧应用前必须核实是否已发生新模型写入。
 */
public final class V111__product_single_writer_cutover extends BaseJavaMigration {
 @Override public void migrate(Context context)throws Exception{
  Connection c=context.getConnection();boolean auto=c.getAutoCommit();c.setAutoCommit(false);
  try(var lock=c.createStatement()){
   // 锁定源记录和尾部范围以阻止停写窗口内遗留写入；发布流程仍必须先撤下旧实例。
   try(var source=lock.executeQuery("SELECT id FROM md_product ORDER BY id FOR UPDATE")){while(source.next()){}}
   lock.executeUpdate("""
    INSERT INTO md_product_v1(id,tenant_id,code,name,barcode,status,structure_state,version,legacy_enabled_snapshot,legacy_specification_original,legacy_specification_disposition,legacy_unit_original,legacy_source_version,legacy_name_original,legacy_updated_by_original,legacy_updated_at_original,created_by,created_at,updated_by,updated_at)
    SELECT o.id,o.tenant_id,o.code,o.name,o.barcode,CASE WHEN o.enabled THEN 'ENABLED' ELSE 'DISABLED' END,'LEGACY_PENDING',o.version,o.enabled,o.specification,'UNREVIEWED',o.unit,o.version,o.name,o.updated_by,o.updated_at,o.created_by,o.created_at,o.updated_by,o.updated_at
    FROM md_product o WHERE NOT EXISTS(SELECT 1 FROM md_product_v1 p WHERE p.tenant_id=o.tenant_id AND p.id=o.id)
    """);
   try(var mismatch=lock.executeQuery("""
    SELECT COUNT(*) FROM md_product o LEFT JOIN md_product_v1 p ON p.tenant_id=o.tenant_id AND p.id=o.id
    WHERE p.id IS NULL OR p.legacy_source_version IS NULL OR p.legacy_source_version<>o.version
      OR NOT(BINARY p.code<=>BINARY o.code) OR NOT(BINARY p.legacy_name_original<=>BINARY o.name)
      OR NOT(BINARY p.barcode<=>BINARY o.barcode) OR NOT(BINARY p.legacy_specification_original<=>BINARY o.specification)
      OR NOT(BINARY p.legacy_unit_original<=>BINARY o.unit) OR NOT(p.legacy_enabled_snapshot<=>o.enabled)
      OR NOT(p.created_by<=>o.created_by) OR NOT(p.created_at<=>o.created_at)
      OR NOT(p.legacy_updated_by_original<=>o.updated_by) OR NOT(p.legacy_updated_at_original<=>o.updated_at)
    """)){
    mismatch.next();if(mismatch.getLong(1)!=0)throw new IllegalStateException("PRODUCT_CUTOVER_SOURCE_CHANGED_OR_UNRECONCILED");
   }
   c.commit();
  }catch(Exception error){c.rollback();throw error;}finally{c.setAutoCommit(auto);}
 }
}
