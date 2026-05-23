package db.migration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V41__normalize_stale_food_log_amounts extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        normalizeStaleFoodLogAmounts(context.getConnection());
    }

    private void normalizeStaleFoodLogAmounts(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update meal_log_item i
                set amount_g = (
                    select coalesce(f.total_weight_g, f.nutrition_basis_amount_g, f.default_serving_g, 100.00)
                    from food f
                    where f.food_id = i.food_id
                )
                where i.source_menu_option_id is null
                  and i.amount_g in (?, ?, ?)
                  and exists (
                      select 1
                      from food f
                      where f.food_id = i.food_id
                        and f.source_name = 'NATIONAL_INTEGRATED'
                        and coalesce(f.total_weight_g, f.nutrition_basis_amount_g, f.default_serving_g, 100.00) <= ?
                  )
                """)) {
            statement.setBigDecimal(1, BigDecimal.valueOf(500));
            statement.setBigDecimal(2, BigDecimal.valueOf(550));
            statement.setBigDecimal(3, BigDecimal.valueOf(600));
            statement.setBigDecimal(4, BigDecimal.valueOf(200));
            statement.executeUpdate();
        }
    }
}
