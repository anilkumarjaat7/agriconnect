package com.agriconnect.config;

import com.agriconnect.entity.MarketPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds a database-level duplicate guard to the existing {@code market_prices} table.
 *
 * <p>Hibernate's {@code ddl-auto=update} adds new columns but does not reliably add multi-column
 * unique indexes to a table that already exists, so this idempotent runner does it explicitly.
 * Any duplicate imported rows left by an interrupted earlier run are collapsed first. A failure
 * is logged and ignored: the importer still deduplicates in Java, so a missing index degrades
 * resilience rather than breaking startup.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MandiPriceSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MandiPriceSchemaInitializer.class);
    private static final String INDEX_NAME = "uk_market_price_record";

    private final JdbcTemplate jdbcTemplate;

    public MandiPriceSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer existing = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'market_prices'
                      AND index_name = ?
                    """, Integer.class, INDEX_NAME);

            if (existing != null && existing > 0) {
                return;
            }

            jdbcTemplate.update("""
                    DELETE p FROM market_prices p
                    JOIN (
                        SELECT MIN(id) AS keep_id, state, district, market_name,
                               commodity, variety, grade, reporting_date
                        FROM market_prices
                        WHERE source = ?
                        GROUP BY state, district, market_name, commodity,
                                 variety, grade, reporting_date
                        HAVING COUNT(*) > 1
                    ) d
                      ON p.state <=> d.state
                     AND p.district <=> d.district
                     AND p.market_name <=> d.market_name
                     AND p.commodity <=> d.commodity
                     AND p.variety <=> d.variety
                     AND p.grade <=> d.grade
                     AND p.reporting_date <=> d.reporting_date
                    WHERE p.source = ? AND p.id > d.keep_id
                    """, MarketPrice.API_SOURCE, MarketPrice.API_SOURCE);

            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX uk_market_price_record
                    ON market_prices (state, district, market_name, commodity,
                                      variety, grade, reporting_date)
                    """);
            log.info("Created unique index {} on market_prices", INDEX_NAME);
        } catch (Exception e) {
            log.warn("Could not create market price duplicate guard: {}", e.getMessage());
        }
    }
}
