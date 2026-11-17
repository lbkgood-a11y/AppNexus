package db.migration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 为已存在租户补齐 ISO 3166 标准主数据；迁移可重复执行且不会覆盖租户已有定制。
 * 账号和时间由数据库生成或保留 NULL，避免伪造业务操作者和客户端时间。
 */
public final class V95__geography_value_defaults extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection c = context.getConnection();
    seedCountries(c);
    seedSubdivisions(c);
  }

  /** 写入 ISO 3166-1 国别地区；同租户同编码已存在时保持原记录。 */
  private void seedCountries(Connection c) throws Exception {
    String sql = "INSERT IGNORE INTO md_country_region "
        + "(id,tenant_id,code,name,english_name,alpha3_code,numeric_code,enabled,version,created_at,updated_at) "
        + "VALUES(UUID_TO_BIN(UUID()),UUID_TO_BIN(?),?,?,?,?,?,TRUE,0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))";
    try (var tenants = c.prepareStatement("SELECT BIN_TO_UUID(id) FROM platform_tenant");
         var rs = tenants.executeQuery();
         var input = GeographyInput.open("/geography/iso3166-1.tsv");
         var statement = c.prepareStatement(sql)) {
      while (rs.next()) {
        String tenant = rs.getString(1);
        input.reset();
        String line;
        while ((line = input.reader().readLine()) != null) {
          String[] p = line.split("\\t", -1);
          if (p.length < 4) throw new IllegalStateException("GEOGRAPHY_SNAPSHOT_INVALID");
          statement.setString(1, tenant); statement.setString(2, p[0]); statement.setString(3, p[3]);
          statement.setString(4, p[3]); statement.setString(5, p[1]); statement.setString(6, p[2]);
          statement.addBatch();
        }
        statement.executeBatch();
      }
    }
  }

  /** 写入 ISO 3166-2 行政区划；同租户同编码已存在时保持原记录。 */
  private void seedSubdivisions(Connection c) throws Exception {
    String sql = "INSERT IGNORE INTO md_subdivision "
        + "(id,tenant_id,code,name,country_code,subdivision_type,parent_code,enabled,version,created_at,updated_at) "
        + "VALUES(UUID_TO_BIN(UUID()),UUID_TO_BIN(?),?,?,?,?,?,?,0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))";
    try (var tenants = c.prepareStatement("SELECT BIN_TO_UUID(id) FROM platform_tenant");
         var rs = tenants.executeQuery();
         var input = GeographyInput.open("/geography/iso3166-2.tsv");
         var statement = c.prepareStatement(sql)) {
      while (rs.next()) {
        String tenant = rs.getString(1);
        input.reset();
        String line;
        while ((line = input.reader().readLine()) != null) {
          String[] p = line.split("\\t", -1);
          if (p.length < 4) throw new IllegalStateException("GEOGRAPHY_SNAPSHOT_INVALID");
          statement.setString(1, tenant); statement.setString(2, p[0]); statement.setString(3, p[1]);
          statement.setString(4, p[0].substring(0, 2)); statement.setString(5, p[2]); statement.setString(6, p[3]);
          statement.setBoolean(7, true); statement.addBatch();
        }
        statement.executeBatch();
      }
    }
  }

  private static final class GeographyInput implements AutoCloseable {
    private final String resource; private BufferedReader reader;
    private GeographyInput(String resource) { this.resource = resource; reset(); }
    static GeographyInput open(String resource) { return new GeographyInput(resource); }
    void reset() {
      try {
        if (reader != null) reader.close();
        var stream = V95__geography_value_defaults.class.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("GEOGRAPHY_SNAPSHOT_MISSING");
        reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      } catch (Exception e) { throw new IllegalStateException("GEOGRAPHY_SNAPSHOT_UNREADABLE", e); }
    }
    BufferedReader reader() { return reader; }
    @Override public void close() throws Exception { reader.close(); }
  }
}
