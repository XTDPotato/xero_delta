package com.xtdpotato.xero_delta.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Material3NativeSettingsTest {
    private static final Path PAGE = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/screen/material/Material3PageScreen.java");

    @Test
    void noProductionCodeLinksTheSuperResolutionRuntime() throws IOException {
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("io.homo.superresolution"), file.toString());
                assertFalse(source.contains("MinecraftScaledNanoVGScreen"), file.toString());
            }
        }
        assertFalse(Files.readString(Path.of("build.gradle")).contains("superResolutionJar"));
        assertFalse(Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"))
            .contains("modId=\"super_resolution\""));
    }

    @Test
    void screensUseSharedMinecraftMaterialControls() throws IOException {
        for (Path file : java.util.List.of(SETTINGS, CORPSE, CORPSE_EDITOR))
            assertTrue(Files.readString(file).contains("extends Material3PageScreen"), file.toString());
        String picker = Files.readString(MAIL_ITEM_PICKER);
        assertTrue(picker.contains("extends Screen"));
        assertTrue(picker.contains("Material3CompactEditBox"));
        assertTrue(picker.contains("Material3Button.builder"));
        assertTrue(picker.contains("graphics.renderItem"));
    }

    @Test
    void dialogsConsumeReleaseKeyboardAndTypingWithoutClickThrough() throws IOException {
        String source = Files.readString(PAGE);
        assertTrue(source.contains("List.copyOf(dialogWidgets)"));
        assertTrue(source.contains("widget.mouseReleased(x, y, button)"));
        assertTrue(source.contains("dialogTitle != null || super.charTyped"));
        assertTrue(source.contains("if (key == 256) dismissDialog()"));
        assertTrue(source.contains("if (body && (y < bodyTop || y >= bodyBottom)) continue"));
    }

    @Test
    void formScrollingKeepsFooterOutsideClipAndPreservesInputDrafts() throws IOException {
        String page = Files.readString(PAGE);
        assertTrue(page.contains("enableScissor(contentX, bodyTop"));
        assertTrue(page.indexOf("graphics.disableScissor()") < page.indexOf("for (var child : children())"));
        assertTrue(page.contains("if (key == 258 && getFocused()"));
        String settings = Files.readString(SETTINGS);
        assertTrue(settings.contains("numberDrafts.getOrDefault(draftKey"));
        assertTrue(settings.contains("invalidFields.removeIf(key -> key.startsWith(page"));
        assertTrue(settings.contains("state.formula.randomOffsetMinimum > state.formula.randomOffsetMaximum"));
        assertTrue(settings.contains("range.minimumPerSlot > range.maximumPerSlot"));
    }

    @Test
    void settingsKeepExistingPersistenceAndPreviewSelectedTheme() throws IOException {
        String source = Files.readString(SETTINGS);
        assertTrue(source.contains("Material3Theme.apply(ConfigThemeCatalog.customTheme("));
        assertTrue(source.contains("Material3Theme.apply(ConfigThemeCatalog.theme(state.theme))"));
        assertTrue(source.contains("}).active = custom"));
        String state = Files.readString(Path.of(
            "src/main/java/com/xtdpotato/xero_delta/screen/MaterialSettingsState.java"));
        for (String contract : java.util.List.of("RuleFormulaConfig.save(formula)",
            "DeltaSpotWheelLayoutCompat.save(", "StatusEffectHudState.save()", "Config.SPEC.save()",
            "originalItemGridEnabled != itemGridEnabled && connected()")) assertTrue(state.contains(contract), contract);
    }

    @Test
    void corpseEditorsRetainValidationAndCanClearAllCandidates() throws IOException {
        String rules = Files.readString(CORPSE);
        assertTrue(rules.contains("if (!editable())"));
        assertTrue(rules.contains("CorpseRulesUpdatePacket(settings)"));
        String editor = Files.readString(CORPSE_EDITOR);
        assertTrue(editor.contains("BuiltInRegistries.ITEM.containsKey(id)"));
        assertTrue(editor.contains("CorpseEntity.isCarrierStackForSlot"));
        assertTrue(editor.contains("value <= CorpseRules.MAX_WEIGHT"));
        assertTrue(editor.contains("old.getOrDefault(id, 1)"));
        assertTrue(editor.contains("!invalidFields.isEmpty()"));
        assertTrue(editor.contains("new CorpseEntityPreviewScreen"));
        String picker = Files.readString(MAIL_ITEM_PICKER);
        assertTrue(picker.contains("if (selected.isEmpty() && !toggleSelection) return"));
        assertTrue(picker.contains("Screen.hasShiftDown()"));
        assertTrue(picker.contains("Screen.hasControlDown()"));
    }

    @Test
    void allNewLabelsAreLocalizedInBothLanguages() throws IOException {
        var root = Path.of("src/main/resources/assets/xero_delta/lang");
        var english = com.google.gson.JsonParser.parseString(Files.readString(root.resolve("en_us.json"))).getAsJsonObject();
        var chinese = com.google.gson.JsonParser.parseString(Files.readString(root.resolve("zh_cn.json"))).getAsJsonObject();
        for (String key : english.keySet()) if (key.startsWith("settings.xero_delta.")) {
            assertTrue(chinese.has(key), key);
            assertFalse(chinese.get(key).getAsString().isBlank(), key);
        }
        for (Path path : java.util.List.of(SETTINGS, CORPSE, CORPSE_EDITOR)) {
            var matcher = java.util.regex.Pattern.compile("tr\\(\"([a-z_]+)\"\\)").matcher(Files.readString(path));
            while (matcher.find()) assertTrue(english.has("settings.xero_delta." + matcher.group(1)), matcher.group(1));
        }
    }

    @Test
    void geometryFitsMinecraftGuiScalesAndClampsBothScrollEnds() {
        for (int[] viewport : java.util.List.of(new int[]{320, 240}, new int[]{426, 240},
            new int[]{480, 270}, new int[]{854, 480}, new int[]{1920, 1080})) {
            var layout = com.xtdpotato.xero_delta.screen.material.MaterialPageLayout.of(viewport[0], viewport[1], true);
            assertTrue(layout.contentX() + layout.contentWidth() < viewport[0]);
            assertTrue(layout.bodyTop() >= 28);
            assertTrue(layout.bodyBottom() < viewport[1] - 28);
            org.junit.jupiter.api.Assertions.assertEquals(0, layout.maxScroll(100));
            org.junit.jupiter.api.Assertions.assertEquals(0, layout.clampScroll(-200, 2000));
            org.junit.jupiter.api.Assertions.assertEquals(layout.maxScroll(2000), layout.clampScroll(99999, 2000));
        }
    }
    private static final Path SETTINGS = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/screen/XeroDeltaMaterialSettingsScreen.java");
    private static final Path CORPSE = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/screen/CorpseRulesScreen.java");
    private static final Path CORPSE_EDITOR = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/screen/CorpseEntityEditorScreen.java");
    private static final Path MATERIAL_THEME = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/screen/material/Material3Theme.java");
    private static final Path SAFETY_BOX_LAYOUT = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/screen/SafetyBoxLayoutScreen.java");
    private static final Path TRADING_UI_PREFERENCES = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/client/TradingUiPreferences.java");
    private static final Path MAIL_ITEM_PICKER = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/screen/MailItemPickerScreen.java");

    @Test
    void sharedMaterialThemeRefreshesFromTheSelectedConfigTheme() throws IOException {
        String source = Files.readString(MATERIAL_THEME);

        assertTrue(source.contains("refreshFromConfig()"));
        assertTrue(source.contains("ConfigThemeCatalog.theme(Config.INSTANCE.configTheme.get())"));
        assertTrue(source.contains("public static void apply(TradingHtmlThemeParser.Theme theme)"));
    }

    @Test
    void safetyBoxLayoutUsesResponsiveMaterialItemRows() throws IOException {
        String source = Files.readString(SAFETY_BOX_LAYOUT);

        assertTrue(source.contains("if (!(parent instanceof XeroDeltaMaterialSettingsScreen))"));
        assertFalse(source.contains("renderBackground("));
        assertTrue(source.contains("renderBlurredBackground(float partialTick)"));
        assertTrue(source.indexOf("Material3Theme.refreshFromConfig()")
            == source.lastIndexOf("Material3Theme.refreshFromConfig()"));
        assertTrue(source.contains("preferredPanelWidth"));
        assertTrue(source.contains("drawControlItem(g, ry"));
        assertTrue(source.contains("controlValueX()"));
        assertTrue(source.contains("toolbarIndexAt(mx, buttonWidth, toolbarGap)"));
        assertFalse(source.contains("selected ? 0xFFFFFFFF : 0xFFCCCCCC"));
        assertFalse(source.contains("0x604A90E2"));
        assertFalse(source.contains("0xB0101010"));
    }

    @Test
    void safetyBoxScrollableRegionsDoNotOverlapTheControlsAboveThem() throws IOException {
        String source = Files.readString(SAFETY_BOX_LAYOUT);

        assertTrue(source.contains(
            "BOX_LIST_TOP = SEARCH_Y + SEARCH_HEIGHT + SECTION_SPACING"));
        assertTrue(source.contains(
            "BOX_LIST_BOTTOM = BOX_LIST_TOP + BOX_LIST_VISIBLE * BOX_ROW_H"));
        assertTrue(source.contains(
            "CTRL_TOP = BOX_LIST_BOTTOM + SECTION_SPACING + CTRL_HEADER_OFFSET"));
        assertTrue(source.contains("PANEL_X, SEARCH_Y, PANEL_W, SEARCH_HEIGHT"));
    }

    @Test
    void safetyBoxLayoutHasIndependentHighResolutionInterfaceZoom() throws IOException {
        String source = Files.readString(SAFETY_BOX_LAYOUT);
        String preferences = Files.readString(TRADING_UI_PREFERENCES);

        assertTrue(preferences.contains("get(\"safety_box_editor_scale_level\", -1,"));
        assertTrue(preferences.contains("setSafetyBoxEditorScaleLevel(int value)"));
        assertTrue(source.contains("TradingUiPreferences.safetyBoxEditorScaleLevel()"));
        assertTrue(source.contains("width = uiViewport.logicalWidth()"));
        assertTrue(source.contains("height = uiViewport.logicalHeight()"));
        assertTrue(source.contains("button -> adjustEditorScale(-1)"));
        assertTrue(source.contains("button -> adjustEditorScale(1)"));
        assertTrue(source.contains("uiViewport.apply(g)"));
        assertTrue(source.contains("uiViewport.mouseXDouble(mx)"));
        assertTrue(source.contains("uiViewport.deltaX(dx)"));
        assertTrue(source.contains("uiViewport.enableScissor"));
        assertTrue(source.contains("private void adjustEditorScale(int direction)"));
    }

}
