package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class V19__simplify_account_and_meal_log_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean h2 = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");

        simplifyUserUniversityAndVerification(connection, h2);
        simplifyUniversityEmailDomains(connection);
        simplifyCafeteriaMealType(connection, h2);
        renameDietEntryToMealLog(connection, h2);
        dropUnusedNutritionStandardTables(connection);
    }

    private void simplifyUserUniversityAndVerification(Connection connection, boolean h2) throws SQLException {
        dropForeignKeyIfExists(connection, "user_account", "fk_user_account_primary_university");
        if (columnExists(connection, "user_account", "primary_university_id")) {
            execute(connection, "alter table user_account rename column primary_university_id to university_id");
        }
        addColumnIfMissing(connection, "user_account", "student_email", "student_email varchar(255) null");
        addColumnIfMissing(connection, "user_account", "is_student_verified", "is_student_verified boolean not null default false");
        addColumnIfMissing(connection, "user_account", "student_verified_at", "student_verified_at timestamp null");

        if (tableExists(connection, "student_verifications")) {
            execute(connection, """
                update user_account u
                set student_email = (
                        select sv.student_email
                        from student_verifications sv
                        where sv.user_id = u.user_id
                          and sv.university_id = u.university_id
                          and sv.status = 'VERIFIED'
                        order by sv.verification_id desc
                        limit 1
                    ),
                    is_student_verified = exists (
                        select 1
                        from student_verifications sv
                        where sv.user_id = u.user_id
                          and sv.university_id = u.university_id
                          and sv.status = 'VERIFIED'
                    ),
                    student_verified_at = (
                        select sv.verified_at
                        from student_verifications sv
                        where sv.user_id = u.user_id
                          and sv.university_id = u.university_id
                          and sv.status = 'VERIFIED'
                        order by sv.verification_id desc
                        limit 1
                    )
                """);
        }

        addConstraintIfMissing(connection, "user_account", "fk_user_account_university", """
            alter table user_account
                add constraint fk_user_account_university
                    foreign key (university_id) references universities(university_id)
            """);
        addConstraintIfMissing(connection, "user_account", "uq_user_account_university_student_email", """
            alter table user_account
                add constraint uq_user_account_university_student_email unique (university_id, student_email)
            """);
        if (tableExists(connection, "student_verifications")) {
            execute(connection, "drop table student_verifications");
        }
    }

    private void simplifyUniversityEmailDomains(Connection connection) throws SQLException {
        dropColumnIfExists(connection, "university_email_domains", "verification_method");
        dropColumnIfExists(connection, "university_email_domains", "is_active");
    }

    private void simplifyCafeteriaMealType(Connection connection, boolean h2) throws SQLException {
        addColumnIfMissing(connection, "cafeteria_menu", "meal_type", "meal_type varchar(30) null");
        if (columnExists(connection, "cafeteria_menu", "meal_type_id") && tableExists(connection, "meal_type")) {
            execute(connection, """
                update cafeteria_menu m
                set meal_type = (
                    select mt.meal_type_code
                    from meal_type mt
                    where mt.meal_type_id = m.meal_type_id
                )
                where m.meal_type is null
                """);
        }
        if (columnExists(connection, "cafeteria_menu", "meal_type")) {
            execute(connection, "alter table cafeteria_menu modify meal_type varchar(30) not null");
        }
        dropForeignKeyIfExists(connection, "cafeteria_menu", "fk_cafeteria_menu_meal_type");
        dropIndexIfExists(connection, "cafeteria_menu", "idx_menu_daily", h2);
        if (columnExists(connection, "cafeteria_menu", "meal_type_id")) {
            createIndexIfMissing(
                    connection,
                    "cafeteria_menu",
                    "idx_cafeteria_menu_dining_place_fk",
                    "create index idx_cafeteria_menu_dining_place_fk on cafeteria_menu(dining_place_id)",
                    h2
            );
            dropUniqueConstraintsReferencing(connection, "cafeteria_menu", Set.of("meal_type_id"), h2);
            execute(connection, "alter table cafeteria_menu drop column meal_type_id");
        }
        addConstraintIfMissing(connection, "cafeteria_menu", "uq_cafeteria_menu_place_meal_date", """
            alter table cafeteria_menu
                add constraint uq_cafeteria_menu_place_meal_date unique (dining_place_id, meal_type, served_date)
            """);
        createIndexIfMissing(connection, "cafeteria_menu", "idx_menu_daily", "create index idx_menu_daily on cafeteria_menu(served_date, meal_type)", h2);
    }

    private void renameDietEntryToMealLog(Connection connection, boolean h2) throws SQLException {
        if (!tableExists(connection, "diet_entry") && tableExists(connection, "meal_log")) {
            finalizeMealLogItemMigration(connection, h2);
            dropTableIfExists(connection, "meal_type");
            return;
        }

        addColumnIfMissing(connection, "diet_entry", "meal_type", "meal_type varchar(30) null");
        if (columnExists(connection, "diet_entry", "meal_type_id") && tableExists(connection, "meal_type")) {
            execute(connection, """
                update diet_entry d
                set meal_type = (
                    select mt.meal_type_code
                    from meal_type mt
                    where mt.meal_type_id = d.meal_type_id
                )
                where d.meal_type is null
                """);
        }
        execute(connection, "alter table diet_entry modify meal_type varchar(30) not null");

        dropForeignKeyIfExists(connection, "diet_entry_item", "fk_diet_entry_item_entry");
        dropForeignKeyIfExists(connection, "diet_entry_item", "fk_diet_entry_item_food");
        dropForeignKeyIfExists(connection, "diet_entry_item", "fk_diet_entry_item_source_option");
        dropIndexIfExists(connection, "diet_entry_item", "idx_diet_entry_item_entry", h2);

        dropForeignKeyIfExists(connection, "diet_entry", "fk_diet_entry_meal_type");
        dropIndexIfExists(connection, "diet_entry", "idx_diet_entry_user_date", h2);
        if (columnExists(connection, "diet_entry", "meal_type_id")) {
            createIndexIfMissing(
                    connection,
                    "diet_entry",
                    "idx_diet_entry_user_fk",
                    "create index idx_diet_entry_user_fk on diet_entry(user_id)",
                    h2
            );
            dropUniqueConstraintsReferencing(connection, "diet_entry", Set.of("meal_type_id"), h2);
            execute(connection, "alter table diet_entry drop column meal_type_id");
        }

        execute(connection, "alter table diet_entry rename to meal_log");
        if (columnExists(connection, "meal_log", "diet_entry_id")) {
            execute(connection, "alter table meal_log rename column diet_entry_id to meal_log_id");
        }
        if (columnExists(connection, "meal_log", "consumed_date")) {
            execute(connection, "alter table meal_log rename column consumed_date to log_date");
        }
        addConstraintIfMissing(connection, "meal_log", "uq_meal_log_user_meal_date", """
            alter table meal_log
                add constraint uq_meal_log_user_meal_date unique (user_id, meal_type, log_date)
            """);
        createIndexIfMissing(connection, "meal_log", "idx_meal_log_user_date", "create index idx_meal_log_user_date on meal_log(user_id, log_date)", h2);

        if (tableExists(connection, "diet_entry_item")) {
            execute(connection, "alter table diet_entry_item rename to meal_log_item");
        }
        finalizeMealLogItemMigration(connection, h2);

        dropTableIfExists(connection, "meal_type");
    }

    private void finalizeMealLogItemMigration(Connection connection, boolean h2) throws SQLException {
        if (columnExists(connection, "meal_log_item", "diet_item_id")) {
            execute(connection, "alter table meal_log_item rename column diet_item_id to meal_log_item_id");
        }
        if (columnExists(connection, "meal_log_item", "diet_entry_id")) {
            execute(connection, "alter table meal_log_item rename column diet_entry_id to meal_log_id");
        }
        if (columnExists(connection, "meal_log_item", "source_option_id")) {
            execute(connection, "alter table meal_log_item rename column source_option_id to source_menu_option_id");
        }
        addConstraintIfMissing(connection, "meal_log_item", "fk_meal_log_item_log", """
            alter table meal_log_item
                add constraint fk_meal_log_item_log
                    foreign key (meal_log_id) references meal_log(meal_log_id) on delete cascade
            """);
        addConstraintIfMissing(connection, "meal_log_item", "fk_meal_log_item_food", """
            alter table meal_log_item
                add constraint fk_meal_log_item_food
                    foreign key (food_id) references food(food_id)
            """);
        addConstraintIfMissing(connection, "meal_log_item", "fk_meal_log_item_source_menu_option", """
            alter table meal_log_item
                add constraint fk_meal_log_item_source_menu_option
                    foreign key (source_menu_option_id) references cafeteria_menu_option(option_id)
            """);
        createIndexIfMissing(connection, "meal_log_item", "idx_meal_log_item_log", "create index idx_meal_log_item_log on meal_log_item(meal_log_id)", h2);
    }

    private void dropUnusedNutritionStandardTables(Connection connection) throws SQLException {
        dropTableIfExists(connection, "nutrition_standard_value");
        dropTableIfExists(connection, "nutrition_standard_group");
    }

    private void dropIndexIfExists(Connection connection, String tableName, String indexName, boolean h2) throws SQLException {
        if (!indexExists(connection, tableName, indexName, h2)) {
            return;
        }
        if (h2) {
            execute(connection, "drop index " + indexName);
        } else {
            execute(connection, "drop index " + indexName + " on " + tableName);
        }
    }

    private void dropUniqueConstraintsReferencing(Connection connection, String tableName, Set<String> columnNames, boolean h2) throws SQLException {
        List<String> constraintNames = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            select distinct k.constraint_name
            from information_schema.key_column_usage k
            join information_schema.table_constraints t
              on t.constraint_schema = k.constraint_schema
             and t.table_schema = k.table_schema
             and t.constraint_name = k.constraint_name
             and t.table_name = k.table_name
            where lower(k.table_schema) = ?
              and lower(k.table_name) = ?
              and lower(k.column_name) in (%s)
              and t.constraint_type = 'UNIQUE'
            """.formatted(placeholders(columnNames.size())))) {
            int index = 1;
            statement.setString(index++, informationSchemaName(connection));
            statement.setString(index++, tableName.toLowerCase(Locale.ROOT));
            for (String columnName : columnNames) {
                statement.setString(index++, columnName.toLowerCase(Locale.ROOT));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    constraintNames.add(resultSet.getString("constraint_name"));
                }
            }
        }

        for (String constraintName : constraintNames) {
            if (h2) {
                execute(connection, "alter table " + tableName + " drop constraint \"" + constraintName + "\"");
            } else {
                execute(connection, "alter table " + tableName + " drop index " + quoteMysqlIdentifier(constraintName));
            }
        }
    }

    private void dropForeignKeyIfExists(Connection connection, String tableName, String constraintName) throws SQLException {
        if (constraintExists(connection, tableName, constraintName)) {
            execute(connection, "alter table " + tableName + " drop foreign key " + constraintName);
        }
    }

    private void addConstraintIfMissing(Connection connection, String tableName, String constraintName, String sql) throws SQLException {
        if (!constraintExists(connection, tableName, constraintName)) {
            execute(connection, sql);
        }
    }

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String columnDefinition) throws SQLException {
        if (!columnExists(connection, tableName, columnName)) {
            execute(connection, "alter table " + tableName + " add column " + columnDefinition);
        }
    }

    private void dropColumnIfExists(Connection connection, String tableName, String columnName) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            execute(connection, "alter table " + tableName + " drop column " + columnName);
        }
    }

    private void dropTableIfExists(Connection connection, String tableName) throws SQLException {
        if (tableExists(connection, tableName)) {
            execute(connection, "drop table " + tableName);
        }
    }

    private void createIndexIfMissing(Connection connection, String tableName, String indexName, String sql, boolean h2) throws SQLException {
        if (!indexExists(connection, tableName, indexName, h2)) {
            execute(connection, sql);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            select count(*)
            from information_schema.tables
            where lower(table_schema) = ?
              and lower(table_name) = ?
            """)) {
            statement.setString(1, informationSchemaName(connection));
            statement.setString(2, tableName.toLowerCase(Locale.ROOT));
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
            where lower(table_schema) = ?
              and lower(table_name) = ?
              and lower(column_name) = ?
            """)) {
            statement.setString(1, informationSchemaName(connection));
            statement.setString(2, tableName.toLowerCase(Locale.ROOT));
            statement.setString(3, columnName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean constraintExists(Connection connection, String tableName, String constraintName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            select count(*)
            from information_schema.table_constraints
            where lower(table_schema) = ?
              and lower(table_name) = ?
              and lower(constraint_name) = ?
            """)) {
            statement.setString(1, informationSchemaName(connection));
            statement.setString(2, tableName.toLowerCase(Locale.ROOT));
            statement.setString(3, constraintName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean indexExists(Connection connection, String tableName, String indexName, boolean h2) throws SQLException {
        String sql = h2
                ? """
                  select count(*)
                  from information_schema.indexes
                  where lower(table_schema) = ?
                    and lower(table_name) = ?
                    and lower(index_name) = ?
                  """
                : """
                  select count(*)
                  from information_schema.statistics
                  where lower(table_schema) = ?
                    and lower(table_name) = ?
                    and lower(index_name) = ?
                  """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, informationSchemaName(connection));
            statement.setString(2, tableName.toLowerCase(Locale.ROOT));
            statement.setString(3, indexName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private String quoteMysqlIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String informationSchemaName(Connection connection) throws SQLException {
        String schema = connection.getSchema();
        if (schema != null && !schema.isBlank()) {
            return schema.toLowerCase(Locale.ROOT);
        }
        return connection.getCatalog().toLowerCase(Locale.ROOT);
    }

    private String placeholders(int size) {
        return String.join(", ", java.util.Collections.nCopies(size, "?"));
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
