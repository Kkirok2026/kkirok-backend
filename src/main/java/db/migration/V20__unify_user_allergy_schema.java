package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V20__unify_user_allergy_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean h2 = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");

        createUserAllergyTable(connection);
        migrateFoodAllergies(connection);
        migrateIngredientAllergies(connection);
        seedLegalAllergenIngredients(connection);
        dropOldAllergyTables(connection);
        createIndexIfMissing(
                connection,
                "user_allergy",
                "idx_user_allergy_user",
                "create index idx_user_allergy_user on user_allergy(user_id)",
                h2
        );
    }

    private void createUserAllergyTable(Connection connection) throws SQLException {
        if (tableExists(connection, "user_allergy")) {
            return;
        }
        execute(connection, """
                create table user_allergy (
                    allergy_id bigint auto_increment primary key,
                    user_id bigint not null,
                    allergy_type varchar(30) not null,
                    food_id bigint,
                    ingredient_id bigint,
                    allergy_name varchar(255) not null,
                    normalized_allergy_name varchar(255) not null,
                    reaction_note varchar(255),
                    created_at timestamp not null default current_timestamp,
                    unique (user_id, allergy_type, normalized_allergy_name),
                    constraint fk_user_allergy_user
                        foreign key (user_id) references user_account(user_id) on delete cascade,
                    constraint fk_user_allergy_food
                        foreign key (food_id) references food(food_id),
                    constraint fk_user_allergy_ingredient
                        foreign key (ingredient_id) references ingredient(ingredient_id)
                )
                """);
    }

    private void migrateFoodAllergies(Connection connection) throws SQLException {
        if (!tableExists(connection, "user_food_allergy")) {
            return;
        }
        try (PreparedStatement select = connection.prepareStatement("""
                select ufa.user_id, ufa.food_id, f.food_name, ufa.reaction_note
                from user_food_allergy ufa
                join food f on f.food_id = ufa.food_id
                """);
             ResultSet resultSet = select.executeQuery()) {
            while (resultSet.next()) {
                insertUserAllergy(
                        connection,
                        resultSet.getLong("user_id"),
                        "FOOD",
                        resultSet.getLong("food_id"),
                        null,
                        resultSet.getString("food_name"),
                        resultSet.getString("reaction_note")
                );
            }
        }
    }

    private void migrateIngredientAllergies(Connection connection) throws SQLException {
        if (!tableExists(connection, "user_ingredient_allergy")) {
            return;
        }
        try (PreparedStatement select = connection.prepareStatement("""
                select user_id, ingredient_id, allergy_name, reaction_note
                from user_ingredient_allergy
                """);
             ResultSet resultSet = select.executeQuery()) {
            while (resultSet.next()) {
                Long ingredientId = resultSet.getObject("ingredient_id") == null
                        ? null
                        : resultSet.getLong("ingredient_id");
                insertUserAllergy(
                        connection,
                        resultSet.getLong("user_id"),
                        "INGREDIENT",
                        null,
                        ingredientId,
                        resultSet.getString("allergy_name"),
                        resultSet.getString("reaction_note")
                );
            }
        }
    }

    private void insertUserAllergy(
            Connection connection,
            long userId,
            String allergyType,
            Long foodId,
            Long ingredientId,
            String allergyName,
            String reactionNote
    ) throws SQLException {
        if (allergyName == null || allergyName.isBlank()) {
            return;
        }
        if (userAllergyExists(connection, userId, allergyType, normalize(allergyName))) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                insert into user_allergy (
                    user_id, allergy_type, food_id, ingredient_id, allergy_name, normalized_allergy_name, reaction_note
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setLong(1, userId);
            insert.setString(2, allergyType);
            if (foodId == null) {
                insert.setObject(3, null);
            } else {
                insert.setLong(3, foodId);
            }
            if (ingredientId == null) {
                insert.setObject(4, null);
            } else {
                insert.setLong(4, ingredientId);
            }
            insert.setString(5, allergyName);
            insert.setString(6, normalize(allergyName));
            insert.setString(7, reactionNote);
            insert.executeUpdate();
        }
    }

    private boolean userAllergyExists(Connection connection, long userId, String allergyType, String normalizedName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from user_allergy
                where user_id = ?
                  and allergy_type = ?
                  and normalized_allergy_name = ?
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, allergyType);
            statement.setString(3, normalizedName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private void seedLegalAllergenIngredients(Connection connection) throws SQLException {
        seedIngredientWithAliases(connection, "알류", "계란", "달걀", "난류");
        seedIngredientWithAliases(connection, "우유", "유청", "분유", "치즈", "버터");
        seedIngredientWithAliases(connection, "메밀");
        seedIngredientWithAliases(connection, "땅콩");
        seedIngredientWithAliases(connection, "대두", "콩", "두부", "두유", "간장", "된장");
        seedIngredientWithAliases(connection, "밀", "소맥", "밀가루", "소맥분");
        seedIngredientWithAliases(connection, "고등어");
        seedIngredientWithAliases(connection, "게", "꽃게");
        seedIngredientWithAliases(connection, "새우", "쉬림프");
        seedIngredientWithAliases(connection, "돼지고기", "돈육", "돼지");
        seedIngredientWithAliases(connection, "복숭아");
        seedIngredientWithAliases(connection, "토마토");
        seedIngredientWithAliases(connection, "아황산류", "아황산", "이산화황", "메타중아황산");
        seedIngredientWithAliases(connection, "호두");
        seedIngredientWithAliases(connection, "닭고기", "닭", "계육");
        seedIngredientWithAliases(connection, "쇠고기", "소고기", "우육");
        seedIngredientWithAliases(connection, "오징어");
        seedIngredientWithAliases(connection, "조개류", "조개", "굴", "전복", "홍합", "바지락");
        seedIngredientWithAliases(connection, "잣");
    }

    private void seedIngredientWithAliases(Connection connection, String ingredientName, String... aliases) throws SQLException {
        long ingredientId = ingredientIdByNormalized(connection, normalize(ingredientName));
        if (ingredientId == 0) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert into ingredient (source_name, source_code, ingredient_name, normalized_name)
                    values ('LEGAL_ALLERGEN', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "LEGAL_ALLERGEN_" + normalize(ingredientName));
                insert.setString(2, ingredientName);
                insert.setString(3, normalize(ingredientName));
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (keys.next()) {
                        ingredientId = keys.getLong(1);
                    }
                }
            }
        }
        insertIngredientAlias(connection, ingredientId, ingredientName, "LEGAL_ALLERGEN");
        for (String alias : aliases) {
            insertIngredientAlias(connection, ingredientId, alias, "LEGAL_ALLERGEN_ALIAS");
        }
    }

    private long ingredientIdByNormalized(Connection connection, String normalizedName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select ingredient_id
                from ingredient
                where normalized_name = ?
                """)) {
            statement.setString(1, normalizedName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("ingredient_id") : 0;
            }
        }
    }

    private void insertIngredientAlias(Connection connection, long ingredientId, String aliasName, String aliasType) throws SQLException {
        if (ingredientId == 0 || aliasName == null || aliasName.isBlank()) {
            return;
        }
        if (ingredientAliasExists(connection, ingredientId, normalize(aliasName))) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                insert into ingredient_alias (ingredient_id, alias_name, normalized_alias, alias_type)
                values (?, ?, ?, ?)
                """)) {
            insert.setLong(1, ingredientId);
            insert.setString(2, aliasName);
            insert.setString(3, normalize(aliasName));
            insert.setString(4, aliasType);
            insert.executeUpdate();
        }
    }

    private boolean ingredientAliasExists(Connection connection, long ingredientId, String normalizedAlias) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from ingredient_alias
                where ingredient_id = ?
                  and normalized_alias = ?
                """)) {
            statement.setLong(1, ingredientId);
            statement.setString(2, normalizedAlias);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private void dropOldAllergyTables(Connection connection) throws SQLException {
        dropTableIfExists(connection, "user_food_allergy");
        dropTableIfExists(connection, "user_ingredient_allergy");
        dropTableIfExists(connection, "ingredient_allergen");
        dropTableIfExists(connection, "allergen_keyword");
        dropTableIfExists(connection, "allergen");
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
                where lower(table_name) = ?
                """)) {
            statement.setString(1, tableName.toLowerCase(Locale.ROOT));
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
                  where lower(table_name) = ?
                    and lower(index_name) = ?
                  """
                : """
                  select count(*)
                  from information_schema.statistics
                  where lower(table_name) = ?
                    and lower(index_name) = ?
                  """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName.toLowerCase(Locale.ROOT));
            statement.setString(2, indexName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-()\\[\\]{}]", "");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
