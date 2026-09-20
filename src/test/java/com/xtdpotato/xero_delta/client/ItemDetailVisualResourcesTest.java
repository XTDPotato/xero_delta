package com.xtdpotato.xero_delta.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ItemDetailVisualResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/xero_delta");

    @Test
    void favoriteIsFilledWhileUnselectedStarStaysHollow() throws Exception {
        var on = ImageIO.read(ASSETS.resolve("textures/gui/action/favorite.png").toFile());
        var off = ImageIO.read(ASSETS.resolve("textures/gui/action/favorite_off.png").toFile());
        assertEquals(off.getWidth(), on.getWidth());
        assertEquals(off.getHeight(), on.getHeight());
        int x = on.getWidth() / 2, y = on.getHeight() / 2;
        assertEquals(255, on.getRGB(x, y) >>> 24);
        assertEquals(0xE2B94F, on.getRGB(x, y) & 0xFFFFFF);
        assertEquals(0, off.getRGB(x, y) >>> 24);
        int filledPixels = 0;
        for (y = 0; y < on.getHeight(); y++) {
            for (x = 0; x < on.getWidth(); x++) {
                int before = off.getRGB(x, y) >>> 24;
                int after = on.getRGB(x, y) >>> 24;
                assertTrue(after >= before);
                if (after > 0) assertEquals(0xE2B94F, on.getRGB(x, y) & 0xFFFFFF);
                if (after > before) filledPixels++;
                if (x == 0 || y == 0 || x == on.getWidth() - 1 || y == on.getHeight() - 1) {
                    assertEquals(0, after);
                }
            }
        }
        assertTrue(filledPixels > 100);
    }

    @Test
    void warehouseItemUsesThreeDimensionalBlockTransformsAndLighting() throws Exception {
        var item = JsonParser.parseString(Files.readString(
            ASSETS.resolve("models/item/personal_warehouse.json"))).getAsJsonObject();
        var block = JsonParser.parseString(Files.readString(
            ASSETS.resolve("models/block/personal_warehouse.json"))).getAsJsonObject();
        assertEquals("xero_delta:block/personal_warehouse", item.get("parent").getAsString());
        assertEquals("minecraft:block/block", block.get("parent").getAsString());
        assertEquals("side", block.get("gui_light").getAsString());
        assertFalse(block.getAsJsonArray("elements").isEmpty());
        assertTrue(Files.exists(ASSETS.resolve("textures/block/personal_warehouse.png")));
    }
}
