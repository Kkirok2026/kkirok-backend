package db.migration;

import com.database2026.backend.menu.MenuItemMatchSupport;
import com.database2026.backend.menu.MenuItemMatchSupport.FoodCandidate;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V40__fix_menu_item_servings_and_split_dormitory_items extends BaseJavaMigration {

    private static final BigDecimal DEFAULT_MENU_ITEM_AMOUNT_G = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_REASONABLE_MENU_ITEM_AMOUNT_G = BigDecimal.valueOf(1000);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        sanitizeFoodBasisAmounts(connection);

        List<FoodCandidate> foodCandidates = foodCandidates(connection);
        Map<Long, BigDecimal> defaultServings = defaultServings(connection);

        splitSingleRowCompositeItems(connection, foodCandidates, defaultServings);
        splitCompositeMealLogItems(connection, foodCandidates, defaultServings);
        rematchMealLogItems(connection, foodCandidates, defaultServings);
        sanitizeLargeStoredAmounts(connection);
    }

    private void sanitizeFoodBasisAmounts(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update food
                set default_serving_g = ?,
                    nutrition_basis_amount_g = ?
                where source_name = 'NATIONAL_INTEGRATED'
                  and nutrition_basis_amount_g > ?
                """)) {
            statement.setBigDecimal(1, DEFAULT_MENU_ITEM_AMOUNT_G);
            statement.setBigDecimal(2, DEFAULT_MENU_ITEM_AMOUNT_G);
            statement.setBigDecimal(3, MAX_REASONABLE_MENU_ITEM_AMOUNT_G);
            statement.executeUpdate();
        }
    }

    private void splitSingleRowCompositeItems(
            Connection connection,
            List<FoodCandidate> foodCandidates,
            Map<Long, BigDecimal> defaultServings
    ) throws SQLException {
        for (SingleRowMenuItem item : singleRowMenuItems(connection)) {
            List<String> splitItems = MenuItemMatchSupport.splitMenuItems(item.rawItemName());
            if (splitItems.size() <= 1) {
                continue;
            }

            deleteMenuItem(connection, item.menuItemId());
            for (String rawItemName : splitItems) {
                Long foodId = MenuItemMatchSupport.matchFoodId(rawItemName, foodCandidates);
                insertMenuItem(connection, item.optionId(), foodId, rawItemName, servingAmount(foodId, defaultServings));
            }
        }
    }

    private void rematchMealLogItems(
            Connection connection,
            List<FoodCandidate> foodCandidates,
            Map<Long, BigDecimal> defaultServings
    ) throws SQLException {
        for (MealLogItem item : mealLogItemsNeedingCleanup(connection)) {
            Long foodId = item.foodId();
            if (foodId == null) {
                foodId = MenuItemMatchSupport.matchFoodId(item.itemName(), foodCandidates);
            }
            BigDecimal amountG = safeAmount(item.amountG(), foodId, defaultServings);
            updateMealLogItem(connection, item.mealLogItemId(), foodId, amountG);
        }
    }

    private void splitCompositeMealLogItems(
            Connection connection,
            List<FoodCandidate> foodCandidates,
            Map<Long, BigDecimal> defaultServings
    ) throws SQLException {
        for (MealLogItem item : compositeMealLogItems(connection)) {
            List<String> splitItems = MenuItemMatchSupport.splitMenuItems(item.itemName());
            if (splitItems.size() <= 1) {
                continue;
            }

            deleteMealLogItem(connection, item.mealLogItemId());
            for (String itemName : splitItems) {
                Long foodId = MenuItemMatchSupport.matchFoodId(itemName, foodCandidates);
                Long sourceMenuOptionId = item.optionHasCalories() && foodId == null ? null : item.sourceMenuOptionId();
                insertMealLogItem(connection, item.mealLogId(), foodId, sourceMenuOptionId, itemName, servingAmount(foodId, defaultServings));
            }
        }
    }

    private List<SingleRowMenuItem> singleRowMenuItems(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select mi.menu_item_id, mi.option_id, mi.raw_item_name
                from cafeteria_menu_item mi
                join (
                    select option_id
                    from cafeteria_menu_item
                    group by option_id
                    having count(*) = 1
                ) single_item on single_item.option_id = mi.option_id
                order by mi.option_id
                """);
             ResultSet resultSet = statement.executeQuery()) {
            List<SingleRowMenuItem> items = new ArrayList<>();
            while (resultSet.next()) {
                items.add(new SingleRowMenuItem(
                        resultSet.getLong("menu_item_id"),
                        resultSet.getLong("option_id"),
                        resultSet.getString("raw_item_name")
                ));
            }
            return items;
        }
    }

    private List<MealLogItem> mealLogItemsNeedingCleanup(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select meal_log_item_id, meal_log_id, food_id, source_menu_option_id, item_name_snapshot, amount_g
                from meal_log_item
                where food_id is null
                   or amount_g > ?
                order by meal_log_item_id
                """)) {
            statement.setBigDecimal(1, MAX_REASONABLE_MENU_ITEM_AMOUNT_G);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MealLogItem> items = new ArrayList<>();
                while (resultSet.next()) {
                    items.add(new MealLogItem(
                            resultSet.getLong("meal_log_item_id"),
                            resultSet.getLong("meal_log_id"),
                            resultSet.getObject("food_id", Long.class),
                            resultSet.getObject("source_menu_option_id", Long.class),
                            resultSet.getString("item_name_snapshot"),
                            resultSet.getBigDecimal("amount_g"),
                            false
                    ));
                }
                return items;
            }
        }
    }

    private List<MealLogItem> compositeMealLogItems(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select i.meal_log_item_id,
                       i.meal_log_id,
                       i.food_id,
                       i.source_menu_option_id,
                       i.item_name_snapshot,
                       i.amount_g,
                       case when o.calories_kcal is not null then true else false end as option_has_calories
                from meal_log_item i
                left join cafeteria_menu_option o on o.option_id = i.source_menu_option_id
                where i.item_name_snapshot like '%/%'
                   or i.item_name_snapshot like '%,%'
                   or i.item_name_snapshot like '%，%'
                   or i.item_name_snapshot like '%、%'
                order by i.meal_log_item_id
                """);
             ResultSet resultSet = statement.executeQuery()) {
            List<MealLogItem> items = new ArrayList<>();
            while (resultSet.next()) {
                items.add(new MealLogItem(
                        resultSet.getLong("meal_log_item_id"),
                        resultSet.getLong("meal_log_id"),
                        resultSet.getObject("food_id", Long.class),
                        resultSet.getObject("source_menu_option_id", Long.class),
                        resultSet.getString("item_name_snapshot"),
                        resultSet.getBigDecimal("amount_g"),
                        resultSet.getBoolean("option_has_calories")
                ));
            }
            return items;
        }
    }

    private List<FoodCandidate> foodCandidates(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select f.food_id, f.food_name as label, 100 as priority
                from food f
                where not exists (
                    select 1
                    from user_custom_food ucf
                    where ucf.food_id = f.food_id
                )
                union all
                select a.food_id, a.alias_name as label, coalesce(a.priority, 0) + 200 as priority
                from food_alias a
                join food f on f.food_id = a.food_id
                where not exists (
                    select 1
                    from user_custom_food ucf
                    where ucf.food_id = f.food_id
                )
                """);
             ResultSet resultSet = statement.executeQuery()) {
            List<FoodCandidate> candidates = new ArrayList<>();
            while (resultSet.next()) {
                String label = resultSet.getString("label");
                candidates.add(new FoodCandidate(
                        resultSet.getLong("food_id"),
                        label,
                        MenuItemMatchSupport.normalize(label),
                        resultSet.getInt("priority")
                ));
            }
            return candidates;
        }
    }

    private Map<Long, BigDecimal> defaultServings(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select f.food_id, f.nutrition_basis_amount_g
                from food f
                where not exists (
                    select 1
                    from user_custom_food ucf
                    where ucf.food_id = f.food_id
                )
                """);
             ResultSet resultSet = statement.executeQuery()) {
            Map<Long, BigDecimal> servings = new HashMap<>();
            while (resultSet.next()) {
                servings.put(resultSet.getLong("food_id"), resultSet.getBigDecimal("nutrition_basis_amount_g"));
            }
            return servings;
        }
    }

    private void deleteMenuItem(Connection connection, long menuItemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from cafeteria_menu_item where menu_item_id = ?")) {
            statement.setLong(1, menuItemId);
            statement.executeUpdate();
        }
    }

    private void insertMenuItem(Connection connection, long optionId, Long foodId, String rawItemName, BigDecimal amountG) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
                values (?, ?, ?, ?)
                """)) {
            statement.setLong(1, optionId);
            if (foodId == null) {
                statement.setObject(2, null);
            } else {
                statement.setLong(2, foodId);
            }
            statement.setString(3, rawItemName);
            statement.setBigDecimal(4, amountG);
            statement.executeUpdate();
        }
    }

    private void updateMealLogItem(Connection connection, long mealLogItemId, Long foodId, BigDecimal amountG) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update meal_log_item
                set food_id = ?,
                    amount_g = ?
                where meal_log_item_id = ?
                """)) {
            if (foodId == null) {
                statement.setObject(1, null);
            } else {
                statement.setLong(1, foodId);
            }
            statement.setBigDecimal(2, amountG);
            statement.setLong(3, mealLogItemId);
            statement.executeUpdate();
        }
    }

    private void deleteMealLogItem(Connection connection, long mealLogItemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from meal_log_item where meal_log_item_id = ?")) {
            statement.setLong(1, mealLogItemId);
            statement.executeUpdate();
        }
    }

    private void insertMealLogItem(
            Connection connection,
            long mealLogId,
            Long foodId,
            Long sourceMenuOptionId,
            String itemName,
            BigDecimal amountG
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into meal_log_item (meal_log_id, food_id, source_menu_option_id, item_name_snapshot, amount_g)
                values (?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, mealLogId);
            if (foodId == null) {
                statement.setObject(2, null);
            } else {
                statement.setLong(2, foodId);
            }
            if (sourceMenuOptionId == null) {
                statement.setObject(3, null);
            } else {
                statement.setLong(3, sourceMenuOptionId);
            }
            statement.setString(4, itemName);
            statement.setBigDecimal(5, amountG);
            statement.executeUpdate();
        }
    }

    private void sanitizeLargeStoredAmounts(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update cafeteria_menu_item
                set amount_g = ?
                where amount_g > ?
                """)) {
            statement.setBigDecimal(1, DEFAULT_MENU_ITEM_AMOUNT_G);
            statement.setBigDecimal(2, MAX_REASONABLE_MENU_ITEM_AMOUNT_G);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                update meal_log_item
                set amount_g = ?
                where amount_g > ?
                """)) {
            statement.setBigDecimal(1, DEFAULT_MENU_ITEM_AMOUNT_G);
            statement.setBigDecimal(2, MAX_REASONABLE_MENU_ITEM_AMOUNT_G);
            statement.executeUpdate();
        }
    }

    private BigDecimal servingAmount(Long foodId, Map<Long, BigDecimal> defaultServings) {
        return safeAmount(defaultServings.get(foodId), foodId, defaultServings);
    }

    private BigDecimal safeAmount(BigDecimal amountG, Long foodId, Map<Long, BigDecimal> defaultServings) {
        if (amountG == null
                || amountG.compareTo(BigDecimal.ZERO) <= 0
                || amountG.compareTo(MAX_REASONABLE_MENU_ITEM_AMOUNT_G) > 0) {
            if (foodId != null && defaultServings.containsKey(foodId)) {
                BigDecimal defaultServing = defaultServings.get(foodId);
                if (defaultServing != null
                        && defaultServing.compareTo(BigDecimal.ZERO) > 0
                        && defaultServing.compareTo(MAX_REASONABLE_MENU_ITEM_AMOUNT_G) <= 0) {
                    return defaultServing;
                }
            }
            return DEFAULT_MENU_ITEM_AMOUNT_G;
        }
        return amountG;
    }

    private record SingleRowMenuItem(long menuItemId, long optionId, String rawItemName) {
    }

    private record MealLogItem(
            long mealLogItemId,
            long mealLogId,
            Long foodId,
            Long sourceMenuOptionId,
            String itemName,
            BigDecimal amountG,
            boolean optionHasCalories
    ) {
    }
}
