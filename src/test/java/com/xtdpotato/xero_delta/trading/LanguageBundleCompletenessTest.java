package com.xtdpotato.xero_delta.trading;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageBundleCompletenessTest {
    @Test
    void chineseAndEnglishBundlesContainTheTradingUiKeys() throws Exception {
        JsonObject zh = load("/assets/xero_delta/lang/zh_cn.json");
        JsonObject en = load("/assets/xero_delta/lang/en_us.json");
        assertEquals(zh.keySet(), en.keySet());
        Set<String> required = Set.of(
            "trading_op.xero_delta.duration",
            "trading_op.xero_delta.owned_search",
            "trading_op.xero_delta.choose_date",
            "trading_op.xero_delta.calendar_month",
            "trading_op.xero_delta.calendar_year",
            "trading_op.xero_delta.calendar_month_value",
            "trading_op.xero_delta.calendar_separator",
            "trading_op.xero_delta.calendar_year_input",
            "trading_op.xero_delta.calendar_month_input",
            "trading_op.xero_delta.selected_days",
            "trading_op.xero_delta.week.mon",
            "trading_op.xero_delta.week.sun",
            "market.xero_delta.delete_history",
            "market.xero_delta.delete_all_history",
            "market.xero_delta.confirm_delete_history",
            "market.xero_delta.confirm_delete_all_history",
            "market.xero_delta.success.history_deleted",
            "market.xero_delta.success.history_all_deleted",
            "market.xero_delta.copied_id",
            "market.xero_delta.copied_value",
            "market.xero_delta.clear_search_history",
            "market.xero_delta.category_section.combat",
            "market.xero_delta.category_section.supplies",
            "market.xero_delta.category_section.building",
            "market.xero_delta.refresh_countdown",
            "market.xero_delta.source",
            "market.xero_delta.uses",
            "command.xero_trading.config.max_currency",
            "command.xero_trading.error.price_format",
            "trading_op.xero_delta.batch_relist_title",
            "trading_op.xero_delta.batch_submit"
        );
        assertTrue(zh.keySet().containsAll(required));
    }

    private static JsonObject load(String resource) throws Exception {
        var stream = LanguageBundleCompletenessTest.class.getResourceAsStream(resource);
        assertNotNull(stream, resource);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
