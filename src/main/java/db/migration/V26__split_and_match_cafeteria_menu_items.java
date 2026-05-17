package db.migration;

import com.database2026.backend.menu.MenuItemMatchSupport;
import com.database2026.backend.menu.MenuItemMatchSupport.FoodCandidate;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class V26__split_and_match_cafeteria_menu_items extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<FoodCandidate> foodCandidates = foodCandidates(connection);
        Map<Long, BigDecimal> defaultServings = defaultServings(connection);

        for (MenuOption option : menuOptions(connection)) {
            List<MenuItem> currentItems = menuItems(connection, option.optionId());
            List<String> splitItems = MenuItemMatchSupport.splitMenuItems(option.optionName());
            if (splitItems.isEmpty()) {
                continue;
            }

            if (shouldReplace(option, currentItems, splitItems)) {
                deleteMenuItems(connection, option.optionId());
                for (String rawItemName : splitItems) {
                    Long foodId = MenuItemMatchSupport.matchFoodId(rawItemName, foodCandidates);
                    insertMenuItem(connection, option.optionId(), foodId, rawItemName, servingAmount(foodId, defaultServings));
                }
            } else {
                for (MenuItem item : currentItems) {
                    if (item.foodId() != null) {
                        continue;
                    }
                    Long foodId = MenuItemMatchSupport.matchFoodId(item.rawItemName(), foodCandidates);
                    if (foodId != null) {
                        updateMenuItemFood(connection, item.menuItemId(), foodId, servingAmount(foodId, defaultServings));
                    }
                }
            }
        }
    }

    private boolean shouldReplace(MenuOption option, List<MenuItem> currentItems, List<String> splitItems) {
        if (currentItems.size() != 1 || splitItems.size() <= 1) {
            return false;
        }
        String rawItemName = currentItems.getFirst().rawItemName();
        return MenuItemMatchSupport.normalize(rawItemName).equals(MenuItemMatchSupport.normalize(option.optionName()))
                || rawItemName.contains("/")
                || rawItemName.contains("&");
    }

    private List<MenuOption> menuOptions(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select option_id, option_name
                from cafeteria_menu_option
                order by option_id
                """);
             ResultSet resultSet = statement.executeQuery()) {
            List<MenuOption> options = new ArrayList<>();
            while (resultSet.next()) {
                options.add(new MenuOption(
                        resultSet.getLong("option_id"),
                        resultSet.getString("option_name")
                ));
            }
            return options;
        }
    }

    private List<MenuItem> menuItems(Connection connection, long optionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select menu_item_id, food_id, raw_item_name
                from cafeteria_menu_item
                where option_id = ?
                order by menu_item_id
                """)) {
            statement.setLong(1, optionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MenuItem> items = new ArrayList<>();
                while (resultSet.next()) {
                    items.add(new MenuItem(
                            resultSet.getLong("menu_item_id"),
                            resultSet.getObject("food_id") == null ? null : resultSet.getLong("food_id"),
                            resultSet.getString("raw_item_name")
                    ));
                }
                return items;
            }
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
                select f.food_id, f.default_serving_g
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
                servings.put(resultSet.getLong("food_id"), resultSet.getBigDecimal("default_serving_g"));
            }
            return servings;
        }
    }

    private void deleteMenuItems(Connection connection, long optionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from cafeteria_menu_item where option_id = ?")) {
            statement.setLong(1, optionId);
            statement.executeUpdate();
        }
    }

    private void insertMenuItem(
            Connection connection,
            long optionId,
            Long foodId,
            String rawItemName,
            BigDecimal amountG
    ) throws SQLException {
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

    private void updateMenuItemFood(Connection connection, long menuItemId, long foodId, BigDecimal amountG) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update cafeteria_menu_item
                set food_id = ?,
                    amount_g = ?
                where menu_item_id = ?
                """)) {
            statement.setLong(1, foodId);
            statement.setBigDecimal(2, amountG);
            statement.setLong(3, menuItemId);
            statement.executeUpdate();
        }
    }

    private BigDecimal servingAmount(Long foodId, Map<Long, BigDecimal> defaultServings) {
        if (foodId == null) {
            return BigDecimal.valueOf(100);
        }
        return defaultServings.getOrDefault(foodId, BigDecimal.valueOf(100));
    }

    private record MenuOption(long optionId, String optionName) {
    }

    private record MenuItem(long menuItemId, Long foodId, String rawItemName) {
    }
}
