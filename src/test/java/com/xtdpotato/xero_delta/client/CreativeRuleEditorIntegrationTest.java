package com.xtdpotato.xero_delta.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Wiring/resource checks run without the NeoForge transforming test classloader. */
class CreativeRuleEditorIntegrationTest {
    private static final Path SOURCE = Path.of("src/main/java/com/xtdpotato/xero_delta");
    private static final Path ASSETS = Path.of("src/main/resources/assets/xero_delta");

    private static String editor() throws Exception {
        return Files.readString(SOURCE.resolve("client/CreativeItemRuleEditor.java"));
    }

    @Test
    void statusDeduplicationIncludesEveryLayoutFeatureFlag() throws Exception {
        String server = Files.readString(SOURCE.resolve("ServerEvents.java"));
        String snapshot = server.substring(server.indexOf("private record StatusSnapshot"),
            server.indexOf("public static void onContainerOpen"));
        for (String flag : List.of("layoutEnabled", "allowChangeBc", "layoutClick")) {
            assertTrue(snapshot.contains("boolean " + flag), flag);
            assertTrue(snapshot.contains(flag + " == other." + flag), flag);
        }
        String sync = server.substring(server.indexOf("public static void syncPlayerStatus"),
            server.indexOf("private static void applyEncumbrance"));
        assertTrue(sync.indexOf("PlayerLayoutSlotRules.enabled") < sync.indexOf("current.nearlyEquals"));
        assertTrue(sync.contains("healthPenalty, injury, layoutEnabled, allowChangeBc, layoutClick"));
    }

    @Test
    void firstRenderInitializesOwnerBeforeMarkingThePanelInteractive() throws Exception {
        String source = editor();
        String render = source.substring(source.indexOf("public static void render("),
            source.indexOf("private static void renderQuality"));
        assertTrue(render.indexOf("ensureOwner(screen)") < render.indexOf("panelRendered = true"));
        assertFalse(source.contains("PlayerStatusClientState.INSTANCE.layoutEnabled()"));
        String client = Files.readString(SOURCE.resolve("client/XeroDeltaClient.java"));
        String independentPanel = client.substring(client.indexOf("private static void renderIndependentMailButton"),
            client.indexOf("private static void renderGridSlots"));
        assertTrue(independentPanel.contains("&& !(screen instanceof CreativeModeInventoryScreen)"));
        assertTrue(independentPanel.contains("renderCompact("));
    }

    @Test
    void scrollingNeverChangesQualityAndDrawsOnlyTheSelectedPage() throws Exception {
        String source = editor();
        String scroll = source.substring(source.indexOf("public static boolean mouseScrolled"),
            source.indexOf("public static boolean keyPressed"));
        assertFalse(scroll.contains("selectedQuality ="));
        assertFalse(scroll.contains("rememberUndo"));
        assertTrue(scroll.contains("CreativeRuleEditorLayout.clampScroll"));
        assertTrue(source.contains("case QUALITY -> renderQuality"));
        assertTrue(source.contains("case SIZE -> renderSize"));
        assertTrue(source.contains("case PRICE -> renderPrice"));
        assertFalse(source.contains("Component.literal(\"this_type\")"));
        assertFalse(source.contains("Component.literal(\"all_type\")"));
    }

    @Test
    void eachTabSupportsSelectionAndUpdatesExistingDrafts() throws Exception {
        String source = editor();
        assertTrue(source.contains("if (boxSelecting) selectBox"));
        assertTrue(source.contains("pendingQualities.replaceAll"));
        assertTrue(source.contains("pendingSizes.replaceAll"));
        assertTrue(source.contains("if (!additive) clearActiveDraft()"));
        assertTrue(source.contains("if (!seen.add(priceKey(stack))) continue"));
        assertTrue(source.contains("if (screen == owner && panelButton == button)"));
        assertTrue(source.contains("if (!qualityEnabled && !sizeEnabled && !priceEnabled) return false"));
    }

    @Test
    void savingAllIncludesDraftsFromInactiveTabs() throws Exception {
        String source = editor();
        String save = source.substring(source.indexOf("private static void saveAll"),
            source.indexOf("private static boolean activeSelectionContains"));
        assertFalse(save.contains("qualityEnabled"));
        assertFalse(save.contains("sizeEnabled"));
        assertFalse(save.contains("priceEnabled"));
        assertTrue(save.contains("Map.copyOf(pendingQualities)"));
        assertTrue(save.contains("Map.copyOf(pendingSizes)"));
        assertTrue(save.contains("canSavePrice()"));
        assertTrue(save.contains("discardCommittedHistory"));
    }

    @Test
    void creativeLabelsExistInBothLanguages() throws Exception {
        for (String language : List.of("zh_cn", "en_us")) {
            var json = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/" + language + ".json")))
                .getAsJsonObject();
            for (String key : List.of("title", "tab_quality", "tab_size", "tab_price",
                "scope_exact", "scope_type", "edit_mode", "clear_draft", "save", "shortcuts_tooltip")) {
                assertTrue(json.has("creative_rules.xero_delta." + key), language + ": " + key);
                assertFalse(json.get("creative_rules.xero_delta." + key).getAsString().isBlank());
            }
        }
    }

    @Test
    void creativeStatusEntryPreservesThePickerInsteadOfClosingItsContainer() throws Exception {
        String client = Files.readString(SOURCE.resolve("client/XeroDeltaClient.java"));
        String entry = client.substring(client.indexOf("Screen parent = event.getScreen();"),
            client.indexOf("mc.setScreen(new PlayerStatusScreen(parent));"));
        assertTrue(entry.contains("if (parent instanceof CreativeModeInventoryScreen)"));
        assertTrue(entry.contains("mc.player.containerMenu = mc.player.inventoryMenu;"));
        assertTrue(entry.contains("} else if (mc.player.containerMenu != mc.player.inventoryMenu)"));
        assertTrue(entry.indexOf("parent = null;") > entry.indexOf("} else if"));
    }

    @Test
    void bothStatusLayoutsReturnToTheParentAndRestoreTheCreativeMenu() throws Exception {
        String status = Files.readString(SOURCE.resolve("screen/PlayerStatusScreen.java"));
        String close = status.substring(status.indexOf("public void onClose()"),
            status.indexOf("public void removed()"));
        assertTrue(close.contains("closeWithTransition(this, this::returnToParent)"));
        assertTrue(close.contains("transition.beginClose(this::returnToParent)"));
        assertTrue(close.contains("minecraft.player.containerMenu = creative.getMenu();"));
        assertTrue(close.contains("minecraft.setScreen(parent);"));
        assertFalse(close.contains("minecraft.setScreen(null)"));
        assertTrue(status.contains("new PlayerStatusScreen(parent, isHealthTabOpen())"));
    }

    @Test
    void inventoryReplacementClosesNormallyWithoutReopeningTheStatusScreen() throws Exception {
        String client = Files.readString(SOURCE.resolve("client/XeroDeltaClient.java"));
        String navigation = client.substring(client.indexOf("public static void onScreenOpening("),
            client.indexOf("private static void completePendingSafetyBoxInventorySwap"));
        assertTrue(navigation.contains("event.setNewScreen(new PlayerStatusScreen(null))"));
        assertTrue(navigation.contains("mc.setScreen(new PlayerStatusScreen(null))"));
        assertTrue(navigation.contains("return !PlayerStatusScreenState.isInventoryBypassed();"));
        String status = Files.readString(SOURCE.resolve("screen/PlayerStatusScreen.java"));
        assertTrue(status.contains("PlayerStatusScreenState.bypassNextInventoryReplacement();"));
    }

    @Test
    void editorIconsArePackagedRasterAssets() throws Exception {
        for (String icon : List.of("tune", "mail", "check", "restart_alt")) {
            assertNotNull(ImageIO.read(ASSETS.resolve("textures/gui/material3/" + icon + ".png").toFile()), icon);
        }
        for (String icon : List.of("return", "info", "close")) {
            assertNotNull(ImageIO.read(ASSETS.resolve("textures/gui/action/" + icon + ".png").toFile()));
        }
    }
}
