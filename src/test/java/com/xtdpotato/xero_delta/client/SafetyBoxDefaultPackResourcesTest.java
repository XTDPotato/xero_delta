package com.xtdpotato.xero_delta.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SafetyBoxDefaultPackResourcesTest {
    @TempDir Path root;

    @Test
    void oldDefaultPackCannotShadowAnyPartOfTheUpdatedPlayerRig() throws Exception {
        List<String> rig = List.of(
            "models/inspect/safety_box_3x3_firstperson.geo.json",
            "models/inspect/safety_box_3x3_thirdperson.geo.json",
            "animations/safety_box_3x3_firstperson.animation.json",
            "animations/safety_box_3x3_thirdperson.animation.json",
            "textures/uv/safety_box_3x3_box.png");
        String custom = "textures/item/safety_box_3x3.png";
        for (String path : rig) write(path, "old rig");
        write(custom, "custom icon");
        var location = new PackLocationInfo("test", Component.literal("test"), PackSource.BUILT_IN, Optional.empty());
        try (var pack = new SafetyBoxDefaultPackResources(location, root)) {
            for (String path : rig) {
                assertNull(pack.getResource(PackType.CLIENT_RESOURCES, id(path)), path);
                assertEquals("old rig", Files.readString(root.resolve(path)), "preserve files on disk");
            }
            assertNotNull(pack.getResource(PackType.CLIENT_RESOURCES, id(custom)));
            var listed = new ArrayList<ResourceLocation>();
            pack.listResources(PackType.CLIENT_RESOURCES, "xero_delta", "", (id, supplier) -> listed.add(id));
            assertEquals(List.of(id(custom)), listed);
        }
    }

    private void write(String path, String text) throws Exception {
        Files.createDirectories(root.resolve(path).getParent());
        Files.writeString(root.resolve(path), text);
    }

    private ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("xero_delta", path);
    }
}
