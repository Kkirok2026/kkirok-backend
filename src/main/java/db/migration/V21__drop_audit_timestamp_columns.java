package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V21__drop_audit_timestamp_columns extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        dropColumnIfExists(connection, "universities", "created_at");
        dropColumnIfExists(connection, "university_email_domains", "created_at");
        dropColumnIfExists(connection, "user_account", "created_at");
        dropColumnIfExists(connection, "user_account", "last_login_at");
        dropColumnIfExists(connection, "user_account", "student_verified_at");
        dropColumnIfExists(connection, "user_health_profile", "updated_at");
        dropColumnIfExists(connection, "auth_token_revocation", "revoked_at");
        dropColumnIfExists(connection, "dining_place", "created_at");
        dropColumnIfExists(connection, "cafeteria_menu", "crawled_at");
        dropColumnIfExists(connection, "food", "created_at");
        dropColumnIfExists(connection, "ingredient", "created_at");
        dropColumnIfExists(connection, "ingredient", "updated_at");
        dropColumnIfExists(connection, "ingredient_alias", "created_at");
        dropColumnIfExists(connection, "food_ingredient", "created_at");
        dropColumnIfExists(connection, "user_custom_food", "created_at");
        dropColumnIfExists(connection, "user_custom_food", "updated_at");
        dropColumnIfExists(connection, "meal_log", "created_at");
        dropColumnIfExists(connection, "meal_log", "updated_at");
        dropColumnIfExists(connection, "meal_log_item", "created_at");
        dropColumnIfExists(connection, "school_email_verification_code", "created_at");
        dropColumnIfExists(connection, "user_allergy", "created_at");
    }

    private void dropColumnIfExists(Connection connection, String tableName, String columnName) throws SQLException {
        if (tableExists(connection, tableName) && columnExists(connection, tableName, columnName)) {
            execute(connection, "alter table " + tableName + " drop column " + columnName);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from information_schema.tables
                where lower(table_name) = ?
                """)) {
            statement.setString(1, tableName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from information_schema.columns
                where lower(table_name) = ?
                  and lower(column_name) = ?
                """)) {
            statement.setString(1, tableName.toLowerCase(Locale.ROOT));
            statement.setString(2, columnName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
