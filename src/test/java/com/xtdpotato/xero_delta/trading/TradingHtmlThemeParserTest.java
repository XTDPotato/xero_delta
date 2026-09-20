package com.xtdpotato.xero_delta.trading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingHtmlThemeParserTest {
    @Test
    void parsesAndClampsTheSafeHtmlSubset() {
        String html = """
            <style>:root { --market-bg:#112233; --market-accent:#AA22CC; }</style>
            <xtd-market data-columns="9" data-sidebar-width="240" data-card-height="120" data-card-gap="8">
            """;
        var theme = TradingHtmlThemeParser.parse(html);
        assertEquals(0xFF112233, theme.background());
        assertEquals(0xFFAA22CC, theme.accent());
        assertEquals(9, theme.columns());
        assertEquals(240, theme.sidebarWidth());
        assertEquals(120, theme.cardHeight());
        assertEquals(8, theme.cardGap());
    }

    @Test
    void parsesInteractiveControlBindingsFromHtml() {
        String html = """
            <button data-tab="buy"></button><button data-tab="sell"></button>
            <button data-category="all"></button>
            <section data-category-section="combat" data-category-label="战斗"
                     data-category-icon="minecraft:crossbow" data-expanded="true">
              <button data-category="guns"></button><button data-category="ammo"></button>
            </section>
            <button data-group="all"></button><button data-group="modded"></button>
            <button data-action="primary"></button><button data-action="refresh"></button>
            """;
        var document = TradingHtmlThemeParser.parseDocument(html);
        assertEquals(java.util.List.of("buy", "sell"), document.tabs());
        assertEquals(java.util.List.of("all", "guns", "ammo"), document.categories());
        assertEquals(java.util.List.of("all", "modded"), document.groups());
        assertEquals(java.util.List.of("primary", "refresh"), document.actions());
        assertEquals(1, document.categorySections().size());
        assertEquals("combat", document.categorySections().getFirst().id());
        assertEquals(java.util.List.of("guns", "ammo"), document.categorySections().getFirst().categories());
        assertTrue(document.categorySections().getFirst().expanded());
        assertEquals("战斗", document.categorySections().getFirst().label());
        assertEquals("minecraft:crossbow", document.categorySections().getFirst().iconItemId());
    }

    @Test
    void preservesMultipleExpandedCustomSectionsAndSpacedLabels() {
        String html = """
            <xtd-market data-custom-category-sections="true"></xtd-market>
            <section data-category-section="building" data-category-label="Building Blocks"
                     data-category-icon="minecraft:bricks" data-expanded="true">
              <button data-category="blocks"></button>
            </section>
            <section data-category-section="combat" data-category-label="Combat Gear"
                     data-category-icon="minecraft:crossbow" data-expanded="true">
              <button data-category="guns"></button><button data-category="ammo"></button>
            </section>
            """;
        var sections = TradingHtmlThemeParser.parseDocument(html).categorySections();
        assertEquals(2, sections.size());
        assertTrue(TradingHtmlThemeParser.parseDocument(html).customCategorySections());
        assertTrue(sections.get(0).expanded());
        assertTrue(sections.get(1).expanded());
        assertEquals("Building Blocks", sections.get(0).label());
        assertEquals("Combat Gear", sections.get(1).label());
    }

    @Test
    void bundledTradingTemplateDeclaresCompleteTradingActions() throws Exception {
        try (var input = TradingHtmlThemeParserTest.class
            .getResourceAsStream("/assets/xero_delta/trading/trading.html")) {
            assertNotNull(input);
            String html = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var actions = TradingHtmlThemeParser.parseDocument(html).actions();
            assertTrue(actions.containsAll(java.util.List.of("select-listing", "toggle-favorite",
                "buy-listing", "select-source", "open-listing-screen",
                "open-recycling", "refresh-market", "decrease-columns", "increase-columns",
                "confirm-buy", "select-history-record", "view-history-details",
                "delete-history", "delete-all-history")));
            assertTrue(html.contains("listing-public-id"));
            assertTrue(html.contains("completed-time"));
            assertTrue(html.contains("trading-market-v13"));
            var sections = TradingHtmlThemeParser.parseDocument(html).categorySections();
            assertEquals(3, sections.size());
            assertEquals("combat", sections.getFirst().id());
            assertTrue(sections.getFirst().expanded());
            assertEquals(82, TradingHtmlThemeParser.parse(html).cardHeight());
            assertFalse(actions.contains("cancel-listing"));
            assertFalse(actions.contains("back-to-catalogue"));
        }
    }

    @Test
    void bundledOperatorTemplateUsesSliderOnlyAmountAndOwnedListingActions() throws Exception {
        try (var input = TradingHtmlThemeParserTest.class
            .getResourceAsStream("/assets/xero_delta/trading/trading_op.html")) {
            assertNotNull(input);
            String html = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var actions = TradingHtmlThemeParser.parseDocument(html).actions();
            assertTrue(actions.containsAll(java.util.List.of("list-current",
                "relist-listing", "view-listing", "cancel-listing", "decrease-amount",
                "increase-amount", "decrease-price", "increase-price")));
            assertTrue(html.contains("data-role=\"detail-amount-slider\""));
            assertTrue(!html.contains("detail-amount-input"));
            assertTrue(!html.contains("detail-price-slider"));
            assertTrue(html.contains("listing-public-id"));
            assertTrue(html.contains("listing-remaining-time"));
            assertFalse(actions.contains("back-to-staged"));
            assertTrue(html.contains("trading-operator-v8"));
            assertTrue(html.contains("data-role=\"expected-income\""));
            assertTrue(html.contains("data-role=\"income-info\""));
            assertFalse(html.contains("data-role=\"market-tax\""));
        }
    }
}
