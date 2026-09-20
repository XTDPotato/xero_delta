package com.xtdpotato.xero_delta.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusEffectHudMaterialEditorTest {
    private static final Path SCREEN = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/screen/StatusEffectHudConfigScreen.java");
    private static final Path LANG = Path.of(
        "src/main/resources/assets/xero_delta/lang");

    @Test
    void editorUsesMaterialSlidersAndGroupedSections() throws IOException {
        String source = Files.readString(SCREEN);

        assertTrue(source.contains("draggingAdjustmentSlider"));
        assertTrue(source.contains("record ValueRange"));
        assertTrue(source.contains("Material2Drawing.roundedRect"));
        assertTrue(source.contains("section_appearance"));
        assertTrue(source.contains("section_animation"));
        assertTrue(source.contains("section_position"));
        assertFalse(source.contains("adjustmentDirection("));
    }

    @Test
    void sectionLabelsExistInBothLanguages() throws IOException {
        for (String language : new String[]{"zh_cn.json", "en_us.json"}) {
            String json = Files.readString(LANG.resolve(language));
            assertTrue(json.contains("status_effect_hud.xero_delta.section_appearance"));
            assertTrue(json.contains("status_effect_hud.xero_delta.section_animation"));
            assertTrue(json.contains("status_effect_hud.xero_delta.section_position"));
        }
    }
}
