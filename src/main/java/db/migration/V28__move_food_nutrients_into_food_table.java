package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V28__move_food_nutrients_into_food_table extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addFoodNutritionColumns(connection);
        copyNutritionValues(connection);
        dropTableIfExists(connection, "nutrition_standard_value");
        dropTableIfExists(connection, "food_nutrient_value");
        dropTableIfExists(connection, "nutrient");
    }

    private void addFoodNutritionColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "food", "calories_kcal", "calories_kcal decimal(12,4) not null default 0");
        addColumnIfMissing(connection, "food", "carb_g", "carb_g decimal(12,4) not null default 0");
        addColumnIfMissing(connection, "food", "protein_g", "protein_g decimal(12,4) not null default 0");
        addColumnIfMissing(connection, "food", "fat_g", "fat_g decimal(12,4) not null default 0");
        addColumnIfMissing(connection, "food", "sugar_g", "sugar_g decimal(12,4) not null default 0");
        addColumnIfMissing(connection, "food", "sodium_mg", "sodium_mg decimal(12,4) not null default 0");
    }

    private void copyNutritionValues(Connection connection) throws SQLException {
        if (!tableExists(connection, "food_nutrient_value") || !tableExists(connection, "nutrient")) {
            return;
        }
        execute(connection, """
                update food f
                set calories_kcal = coalesce((
                        select v.amount_per_100g
                        from food_nutrient_value v
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where v.food_id = f.food_id
                          and n.nutrient_code = 'CALORIES_KCAL'
                    ), f.calories_kcal),
                    carb_g = coalesce((
                        select v.amount_per_100g
                        from food_nutrient_value v
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where v.food_id = f.food_id
                          and n.nutrient_code = 'CARB_G'
                    ), f.carb_g),
                    protein_g = coalesce((
                        select v.amount_per_100g
                        from food_nutrient_value v
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where v.food_id = f.food_id
                          and n.nutrient_code = 'PROTEIN_G'
                    ), f.protein_g),
                    fat_g = coalesce((
                        select v.amount_per_100g
                        from food_nutrient_value v
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where v.food_id = f.food_id
                          and n.nutrient_code = 'FAT_G'
                    ), f.fat_g),
                    sugar_g = coalesce((
                        select v.amount_per_100g
                        from food_nutrient_value v
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where v.food_id = f.food_id
                          and n.nutrient_code = 'SUGAR_G'
                    ), f.sugar_g),
                    sodium_mg = coalesce((
                        select v.amount_per_100g
                        from food_nutrient_value v
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where v.food_id = f.food_id
                          and n.nutrient_code = 'SODIUM_MG'
                    ), f.sodium_mg)
                """);
    }

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String definition) throws SQLException {
        if (!columnExists(connection, tableName, columnName)) {
            execute(connection, "alter table " + tableName + " add column " + definition);
        }
    }

    private void dropTableIfExists(Connection connection, String tableName) throws SQLException {
        if (tableExists(connection, tableName)) {
            execute(connection, "drop table " + tableName);
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
