package ru.neoflex.meta.utils;

import com.mchange.v2.c3p0.impl.NewProxyConnection;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.postgresql.core.Utils;
import ru.neoflex.meta.svc.GitflowSvc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DgConnectionProvider extends C3P0ConnectionProvider {
    public Connection getConnection() throws SQLException {
        final Connection c = super.getConnection();
        String schema = GitflowSvc.getSchema(GitflowSvc.getCurrentBranch());
        //((NewProxyConnection) c).inner.setSchema(schema);
        setSchema(c, schema);
        return c;
    }

    private void setSchema(Connection c, String schema) throws SQLException {
        Statement stmt = c.createStatement();
        try {
            if (schema == null) {
                stmt.executeUpdate("SET SESSION search_path TO DEFAULT");
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("SET SESSION search_path TO '");
                Utils.escapeLiteral(sb, schema, true);
                sb.append("'");
                stmt.executeUpdate(sb.toString());
            }
        } finally {
            stmt.close();
        }
    }
}
