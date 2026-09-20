package com.xtdpotato.xero_delta.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ConfigPaths;
import com.xtdpotato.xero_delta.data.DeltaPacksConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class SafetyBoxLayoutPack {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int LAYOUT_SCHEMA_VERSION = 3;
    private static final String LAYOUT_SCHEMA_MARKER = ".layout_schema_" + LAYOUT_SCHEMA_VERSION;
    private static final List<String> DEFAULT_BOX_IDS = List.of(
        "xero_delta:safety_box_2x1",
        "xero_delta:safety_box_2x2",
        "xero_delta:safety_box_3x2",
        "xero_delta:safety_box_3x3",
        "xero_delta:safety_box_4x2"
    );

    public static class LayoutData {
        public int schemaVersion = LAYOUT_SCHEMA_VERSION;
        public String boxId;
        public WidgetNode root;
        public List<GridContainerData> containers = new ArrayList<>();
        public String layout = "TOP";
        public boolean verticalText;
        // Offsets
        public double iconScale = 0.5, textScale = 0.5;
        public int iconOffX, iconOffY, textOffX, textOffY, textPad = 4;
        public int borderPad, borderOffX, borderOffY;
        public int bgW, bgH;
        public int panelPadLeft, panelPadRight, panelPadTop, panelPadBottom;
        public int panelAlpha = 0;
        public int globalOffX, globalOffY;
        public int centerX = -1, centerY = -1;
        public int gridOffX, gridOffY;
        public double gridScale = 1.0;
        // Custom text content (null = use default item name)
        public String customText;
    }

    public static class GridContainerData {
        public String id = "container_0";
        public int columns = 3;
        public int rows = 3;
        public int depth = 1;
    }

    public static class BorderStyle {
        public boolean enabled;
        public String color = "#FFFFFFFF";
        public int size = 1;
    }

    public static class WidgetNode {
        public String id;
        public boolean legacyBridge;
        public String type = "layout";
        public String name;
        public int x;
        public int y;
        public int z;
        public int rotation;
        public int width = -2;
        public int height = -2;
        public int backgroundColor;
        public int cornerRadius;
        public String text;
        /** Text widgets own their orientation; LayoutData keeps the legacy header setting. */
        public boolean verticalText;
        public double fontSize = 1.0;
        public String textColor = "#FFFFFFFF";
        public String imagePath;
        public int gridColumns = 3;
        public int gridRows = 3;
        public int gridDepth = 1;
        public int containerIndex;
        public BorderStyle outerBorder = new BorderStyle();
        public BorderStyle innerBorder = new BorderStyle();
        public List<WidgetNode> children = new ArrayList<>();
    }

    public static class PackManifest {
        public String version = "1";
        public String name;
        public List<String> boxes = new ArrayList<>();
    }

    public static Path getPackDir() {
        DeltaPacksConfig.migrateLegacyDirectory(FMLPaths.GAMEDIR.get().resolve(DeltaPacksConfig.DIRECTORY_NAME));
        return ConfigPaths.directory();
    }

    public static Path getDefaultPackDir() {
        return getPackDir().resolve("default");
    }

    /**
     * Creates the bundled default pack only when it does not exist yet.
     * Existing packs are user-owned and must never be merged with bundled assets.
     */
    public static boolean ensureDefaultPack() {
        Path dir = getDefaultPackDir();
        boolean created = !Files.isDirectory(dir);
        try {
            if (created) {
                copyBundledAssets(dir);
                XeroDelta.LOGGER.info("Created default delta_packs pack from bundled assets");
            }
            PackManifest manifest = ensurePackMetadata(dir);
            resetLegacyLayoutsIfNeeded(dir, manifest.boxes);
            return created;
        } catch (Exception e) {
            XeroDelta.LOGGER.error("Failed to initialize default delta_packs pack: {}", e.getMessage());
            return false;
        }
    }

    private static PackManifest ensurePackMetadata(Path packDir) throws IOException {
        Files.createDirectories(packDir);
        Files.createDirectories(packDir.resolve("items"));
        Files.createDirectories(packDir.resolve("config"));
        Files.createDirectories(packDir.resolve("config_default"));
        Path manifestPath = packDir.resolve("manifest.json");
        PackManifest manifest = null;
        if (Files.exists(manifestPath)) {
            try {
                manifest = GSON.fromJson(Files.readString(manifestPath), PackManifest.class);
            } catch (Exception ignored) {
            }
        }
        if (manifest == null) manifest = new PackManifest();
        if (manifest.name == null || manifest.name.isBlank()) manifest.name = "Default Xero Delta Pack";
        if (manifest.boxes == null) manifest.boxes = new ArrayList<>();
        for (String boxId : DEFAULT_BOX_IDS) if (!manifest.boxes.contains(boxId)) manifest.boxes.add(boxId);
        manifest.version = String.valueOf(LAYOUT_SCHEMA_VERSION);
        Files.writeString(manifestPath, GSON.toJson(manifest));
        for (String boxId : manifest.boxes) {
            Path itemPath = packDir.resolve("items").resolve(boxId.substring(boxId.indexOf(':') + 1) + ".json");
            if (!Files.exists(itemPath)) createDefaultItemJson(packDir, boxId);
        }
        return manifest;
    }

    private static void resetLegacyLayoutsIfNeeded(Path packDir, List<String> boxIds) throws IOException {
        Path marker = packDir.resolve(LAYOUT_SCHEMA_MARKER);
        if (Files.exists(marker)) return;
        deleteJsonFiles(packDir.resolve("config"));
        deleteJsonFiles(packDir.resolve("config_default"));
        for (String boxId : boxIds) {
            LayoutData layout = createDefaultLayout(boxId);
            saveLayout(packDir, layout);
            saveLayoutTo(packDir.resolve("config_default"), layout);
        }
        Files.writeString(marker, String.valueOf(LAYOUT_SCHEMA_VERSION));
        XeroDelta.LOGGER.info("Removed legacy floating safety-box layouts and created schema {} defaults",
            LAYOUT_SCHEMA_VERSION);
    }

    private static void deleteJsonFiles(Path directory) throws IOException {
        Files.createDirectories(directory);
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    public static boolean refreshRuntimePack() {
        ensureDefaultPack();
        if (!Files.isDirectory(getDefaultPackDir())) return false;
        try {
            deleteTree(getPackDir().resolve(".xtd_runtime"));
            return true;
        } catch (Exception e) {
            XeroDelta.LOGGER.error("Failed to remove obsolete delta_packs runtime pack: {}", e.getMessage());
            return false;
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private static void copyBundledAssets(Path destination) throws Exception {
        URL resource = SafetyBoxLayoutPack.class.getResource("/assets/xero_delta");
        if (resource == null) throw new IOException("Bundled assets are unavailable");
        URI uri = resource.toURI();
        FileSystem fileSystem = null;
        boolean closeFileSystem = false;
        Path source;
        if ("jar".equals(uri.getScheme())) {
            try {
                fileSystem = FileSystems.newFileSystem(uri, Map.of());
                closeFileSystem = true;
            } catch (FileSystemAlreadyExistsException ignored) {
                fileSystem = FileSystems.getFileSystem(uri);
            }
            source = fileSystem.getPath("/assets/xero_delta");
        } else {
            source = Paths.get(uri);
        }

        try (Stream<Path> paths = Files.walk(source)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path asset = iterator.next();
                if (com.xtdpotato.xero_delta.data.SafetyBoxInspectResources.isBundledRig(
                    source.relativize(asset).toString().replace('\\', '/'))) continue;
                Path target = destination.resolve(source.relativize(asset).toString());
                if (Files.isDirectory(asset)) {
                    Files.createDirectories(target);
                } else if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(asset, target);
                }
            }
        } finally {
            if (closeFileSystem && fileSystem != null && fileSystem.isOpen()) fileSystem.close();
        }
    }

    public static class ItemEntry {
        public String item;        // item ID, e.g. "xero_delta:safety_box_3x3"
        public String icon;        // path to icon JSON/texture
        public String texture;     // path to UV texture
        public String model_hand;  // model with hand + safety box (first-person)
        public String model_box;   // model of just the safety box
        public String animation_firstperson; // first-person inspect animation
        public String animation_thirdperson; // third-person inspect animation
    }

    private static void createDefaultItemJson(Path packDir, String boxId) {
        try {
            String safeName = boxId.substring(boxId.indexOf(':') + 1);
            ItemEntry entry = new ItemEntry();
            entry.item = boxId;
            entry.icon = "textures/item/" + safeName + ".png";
            entry.texture = "textures/uv/" + safeName + ".png";
            entry.model_hand = "models/" + safeName + "_hand.json";
            entry.model_box = "models/" + safeName + "_box.json";
            entry.animation_firstperson = "animations/" + safeName + "_firstperson.json";
            entry.animation_thirdperson = "animations/" + safeName + "_thirdperson.json";

            Path itemPath = packDir.resolve("items").resolve(safeName + ".json");
            Files.writeString(itemPath, GSON.toJson(entry));
        } catch (Exception e) {
            XeroDelta.LOGGER.error("Failed to create item json for {}: {}", boxId, e.getMessage());
        }
    }

    public static List<LayoutData> loadLayouts(Path packDir) {
        List<LayoutData> list = new ArrayList<>();
        try {
            if (packDir.equals(getDefaultPackDir())) ensureDefaultPack();
            Path mfPath = packDir.resolve("manifest.json");
            if (!Files.exists(mfPath)) return list;
            PackManifest mf = GSON.fromJson(Files.readString(mfPath), PackManifest.class);
            if (mf.boxes == null) return list;
            for (String boxId : mf.boxes) {
                String safeName = boxId.replace(':', '_') + ".json";
                Path lPath = packDir.resolve("config").resolve(safeName);
                if (Files.exists(lPath)) {
                    LayoutData ld = GSON.fromJson(Files.readString(lPath), LayoutData.class);
                    if (ld.boxId == null) ld.boxId = boxId;
                    normalizeLayout(ld);
                    list.add(ld);
                } else {
                    LayoutData ld = createDefaultLayout(boxId);
                    list.add(ld);
                }
            }
        } catch (Exception e) {
            XeroDelta.LOGGER.error("Failed to load layouts: {}", e.getMessage());
        }
        return list;
    }

    static List<String> editorBoxIds(List<LayoutData> layouts) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (layouts != null) {
            for (LayoutData layout : layouts) {
                if (layout != null && layout.boxId != null && !layout.boxId.isBlank()) {
                    ids.add(layout.boxId);
                }
            }
        }
        if (ids.isEmpty()) ids.addAll(DEFAULT_BOX_IDS);
        return new ArrayList<>(ids);
    }

    public static void saveLayout(Path packDir, LayoutData ld) {
        try {
            Files.createDirectories(packDir.resolve("config"));
            normalizeLayout(ld);
            String safeName = ld.boxId.replace(':', '_') + ".json";
            Files.writeString(packDir.resolve("config").resolve(safeName), GSON.toJson(ld));
        } catch (Exception e) {
            XeroDelta.LOGGER.error("Failed to save layout: {}", e.getMessage());
        }
    }

    public static void applyLayoutToConfig(LayoutData ld) {
        var cfg = com.xtdpotato.xero_delta.Config.INSTANCE;
        cfg.overlayLayout.set(ld.layout);
        cfg.overlayVerticalText.set(ld.verticalText);
        cfg.overlayIconScale.set(ld.iconScale);
        cfg.overlayIconOffsetX.set(ld.iconOffX);
        cfg.overlayIconOffsetY.set(ld.iconOffY);
        cfg.overlayTextScale.set(ld.textScale);
        cfg.overlayTextOffsetX.set(ld.textOffX);
        cfg.overlayTextOffsetY.set(ld.textOffY);
        cfg.overlayTextPadding.set(ld.textPad);
        cfg.overlayBorderPadding.set(ld.borderPad);
        cfg.overlayBorderOffsetX.set(ld.borderOffX);
        cfg.overlayBorderOffsetY.set(ld.borderOffY);
        cfg.overlayBgWidth.set(ld.bgW);
        cfg.overlayBgHeight.set(ld.bgH);
        cfg.overlayGlobalOffsetX.set(ld.globalOffX);
        cfg.overlayGlobalOffsetY.set(ld.globalOffY);
        cfg.overlayCenterX.set(ld.centerX);
        cfg.overlayCenterY.set(ld.centerY);
        cfg.overlayGridOffsetX.set(ld.gridOffX);
        cfg.overlayGridOffsetY.set(ld.gridOffY);
        cfg.overlayGridScale.set(ld.gridScale);
    }

    public static LayoutData captureCurrentLayout(String boxId) {
        var cfg = com.xtdpotato.xero_delta.Config.INSTANCE;
        LayoutData ld = new LayoutData();
        ld.boxId = boxId;
        ld.layout = cfg.overlayLayout.get();
        ld.verticalText = cfg.overlayVerticalText.get();
        ld.iconScale = cfg.overlayIconScale.get();
        ld.iconOffX = cfg.overlayIconOffsetX.get();
        ld.iconOffY = cfg.overlayIconOffsetY.get();
        ld.textScale = cfg.overlayTextScale.get();
        ld.textOffX = cfg.overlayTextOffsetX.get();
        ld.textOffY = cfg.overlayTextOffsetY.get();
        ld.textPad = cfg.overlayTextPadding.get();
        ld.borderPad = cfg.overlayBorderPadding.get();
        ld.borderOffX = cfg.overlayBorderOffsetX.get();
        ld.borderOffY = cfg.overlayBorderOffsetY.get();
        ld.bgW = cfg.overlayBgWidth.get();
        ld.bgH = cfg.overlayBgHeight.get();
        ld.globalOffX = cfg.overlayGlobalOffsetX.get();
        ld.globalOffY = cfg.overlayGlobalOffsetY.get();
        ld.centerX = cfg.overlayCenterX.get();
        ld.centerY = cfg.overlayCenterY.get();
        ld.gridOffX = cfg.overlayGridOffsetX.get();
        ld.gridOffY = cfg.overlayGridOffsetY.get();
        ld.gridScale = cfg.overlayGridScale.get();
        return ld;
    }

    public static LayoutData createDefaultLayout(String boxId) {
        LayoutData ld = new LayoutData();
        ld.boxId = boxId;
        ld.layout = "TOP";
        ld.verticalText = false;
        ld.iconScale = 0.5;
        ld.textScale = 0.5;
        ld.borderPad = 0;
        ld.panelPadLeft = 0;
        ld.panelPadRight = 0;
        ld.panelPadTop = 0;
        ld.panelPadBottom = 0;
        ld.bgW = 0;
        ld.globalOffX = 0;
        ld.globalOffY = 0;
        ld.centerX = -1;
        ld.centerY = -1;
        ld.gridOffX = 0;
        ld.gridScale = 1.0;

        if (boxId.contains("safety_box_3x3")) {
            ld.iconOffX = -18; ld.iconOffY = 0;
            ld.textOffX = 6; ld.textOffY = 0;
            ld.textPad = 1;
            ld.bgH = 10; ld.gridOffY = 1;
            ld.bgW = 54;
        } else if (boxId.contains("safety_box_3x2")) {
            ld.iconOffX = -20; ld.iconOffY = -1;
            ld.textOffX = 5; ld.textOffY = -1;
            ld.textPad = 1;
            ld.bgH = 12; ld.gridOffY = 0;
            ld.bgW = 54;
        } else if (boxId.contains("safety_box_2x2")) {
            ld.iconOffX = -13; ld.iconOffY = 0;
            ld.textOffX = -2; ld.textOffY = 0;
            ld.textPad = 0;
            ld.bgH = 12; ld.gridOffY = 0;
        } else if (boxId.contains("safety_box_2x1")) {
            ld.iconOffX = -13; ld.iconOffY = 0;
            ld.textOffX = -2; ld.textOffY = 0;
            ld.textPad = 0;
            ld.bgH = 12; ld.gridOffY = 0;
        }
        normalizeLayout(ld);
        return ld;
    }


    /** Copy all default configs back to config/, resetting user changes */
    public static void resetAllLayouts(Path packDir) {
        try {
            Path defDir = packDir.resolve("config_default");
            Path cfgDir = packDir.resolve("config");
            if (Files.exists(defDir)) {
                try (var stream = Files.list(defDir)) {
                    stream.filter(p -> p.toString().endsWith(".json")).forEach(src -> {
                        try {
                            Files.copy(src, cfgDir.resolve(src.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception e) { XeroDelta.LOGGER.warn("Failed to reset layout {}: {}", src, e.getMessage()); }
                    });
                }
            }
        } catch (Exception e) {
            XeroDelta.LOGGER.error("Failed to reset layouts: {}", e.getMessage());
        }
    }

    /** Save a layout to a specific directory (used for config_default) */
    private static void saveLayoutTo(Path targetDir, LayoutData ld) {
        try {
            Files.createDirectories(targetDir);
            String safeName = ld.boxId.replace(':', '_') + ".json";
            Files.writeString(targetDir.resolve(safeName), GSON.toJson(ld));
        } catch (Exception e) {
            XeroDelta.LOGGER.error("Failed to save layout to {}: {}", targetDir, e.getMessage());
        }
    }


public static LayoutData getLayoutForBox(String boxId) {
        LayoutData def = createDefaultLayout(boxId);
        try {
            Path dir = getDefaultPackDir();
            ensureDefaultPack();
            String safeName = boxId.replace(':', '_') + ".json";
            Path lPath = dir.resolve("config").resolve(safeName);
            if (Files.exists(lPath)) {
                LayoutData ld = GSON.fromJson(Files.readString(lPath), LayoutData.class);
                if (ld.boxId == null) ld.boxId = boxId;
                normalizeLayout(ld);
                return ld;
            }
        } catch (Exception e) {
            XeroDelta.LOGGER.error("Failed to load layout for {}: {}", boxId, e.getMessage());
        }
        normalizeLayout(def);
        return def;
    }

    public static void normalizeLayout(LayoutData ld) {
        if (ld == null) return;
        ld.schemaVersion = Math.max(ld.schemaVersion, LAYOUT_SCHEMA_VERSION);
        if (ld.panelPadLeft < 0 && ld.panelPadRight >= 0) ld.panelPadLeft = ld.panelPadRight;
        if (ld.panelPadRight < 0 && ld.panelPadLeft >= 0) ld.panelPadRight = ld.panelPadLeft;
        ld.panelPadLeft = Math.max(0, ld.panelPadLeft);
        ld.panelPadRight = Math.max(0, ld.panelPadRight);
        ld.panelPadTop = Math.max(0, ld.panelPadTop);
        ld.panelPadBottom = Math.max(0, ld.panelPadBottom);

        int[] grid = gridSizeForBox(ld.boxId);
        if (ld.containers == null) ld.containers = new ArrayList<>();
        if (ld.containers.isEmpty()) {
            GridContainerData container = new GridContainerData();
            container.columns = grid[0];
            container.rows = grid[1];
            ld.containers.add(container);
        }
        for (int i = 0; i < ld.containers.size(); i++) {
            GridContainerData container = ld.containers.get(i);
            if (container == null) {
                container = new GridContainerData();
                ld.containers.set(i, container);
            }
            if (container.id == null || container.id.isBlank()) container.id = "container_" + i;
            container.columns = Math.max(1, container.columns);
            container.rows = Math.max(1, container.rows);
            container.depth = Math.max(1, container.depth);
        }

        if (ld.root == null) ld.root = legacyRoot(ld, grid[0], grid[1]);
        normalizeWidget(ld.root);
        bindGridContainers(ld);
    }

    public static List<WidgetNode> flattenWidgets(LayoutData layout) {
        List<WidgetNode> widgets = new ArrayList<>();
        if (layout != null && layout.root != null) flattenWidget(layout.root, widgets);
        return widgets;
    }

    public static WidgetNode findWidget(LayoutData layout, String id) {
        if (layout == null || id == null) return null;
        for (WidgetNode node : flattenWidgets(layout)) if (id.equals(node.id)) return node;
        return null;
    }

    public static WidgetNode findParent(LayoutData layout, WidgetNode target) {
        return layout == null || layout.root == null || target == null ? null : findParent(layout.root, target);
    }

    public static LayoutData copyLayout(LayoutData source) {
        if (source == null) return null;
        LayoutData copy = GSON.fromJson(GSON.toJson(source), LayoutData.class);
        normalizeLayout(copy);
        return copy;
    }

    public static WidgetNode copyWidget(WidgetNode source) {
        if (source == null) return null;
        WidgetNode copy = GSON.fromJson(GSON.toJson(source), WidgetNode.class);
        normalizeWidget(copy);
        return copy;
    }

    public static void copyWidgetProperties(WidgetNode source, WidgetNode target) {
        if (source == null || target == null) return;
        String originalId = target.id;
        boolean originalLegacyBridge = target.legacyBridge;
        List<WidgetNode> originalChildren = target.children;
        WidgetNode copy = copyWidget(source);
        target.type = copy.type;
        target.name = copy.name;
        target.x = copy.x;
        target.y = copy.y;
        target.z = copy.z;
        target.rotation = copy.rotation;
        target.width = copy.width;
        target.height = copy.height;
        target.backgroundColor = copy.backgroundColor;
        target.cornerRadius = copy.cornerRadius;
        target.text = copy.text;
        target.verticalText = copy.verticalText;
        target.fontSize = copy.fontSize;
        target.textColor = copy.textColor;
        target.imagePath = copy.imagePath;
        target.gridColumns = copy.gridColumns;
        target.gridRows = copy.gridRows;
        target.gridDepth = copy.gridDepth;
        target.containerIndex = copy.containerIndex;
        target.outerBorder = copy.outerBorder;
        target.innerBorder = copy.innerBorder;
        target.id = originalId;
        target.legacyBridge = originalLegacyBridge;
        target.children = originalChildren;
    }

    public static boolean moveWidget(LayoutData layout, WidgetNode target, WidgetNode newParent, int newIndex) {
        if (layout == null || target == null || target == layout.root || newParent == null
            || !"layout".equals(newParent.type) || target == newParent || isDescendant(target, newParent)) {
            return false;
        }
        WidgetNode oldParent = findParent(layout, target);
        if (oldParent == null) return false;
        int oldAbsoluteX = absoluteX(layout, target);
        int oldAbsoluteY = absoluteY(layout, target);
        int oldIndex = oldParent.children.indexOf(target);
        if (oldIndex < 0) return false;

        oldParent.children.remove(oldIndex);
        int insertion = Math.max(0, Math.min(newIndex, newParent.children.size()));
        if (oldParent == newParent && oldIndex < insertion) insertion--;
        newParent.children.add(Math.max(0, insertion), target);
        target.x = oldAbsoluteX - absoluteX(layout, newParent);
        target.y = oldAbsoluteY - absoluteY(layout, newParent);
        return true;
    }

    public static boolean moveWidgetBy(LayoutData layout, WidgetNode target, int delta) {
        WidgetNode parent = findParent(layout, target);
        if (parent == null) return false;
        int index = parent.children.indexOf(target);
        int next = index + delta;
        if (index < 0 || next < 0 || next >= parent.children.size()) return false;
        Collections.swap(parent.children, index, next);
        return true;
    }

    public static boolean isDescendant(WidgetNode ancestor, WidgetNode candidate) {
        if (ancestor == null || candidate == null) return false;
        for (WidgetNode child : ancestor.children) {
            if (child == candidate || isDescendant(child, candidate)) return true;
        }
        return false;
    }

    public static int absoluteX(LayoutData layout, WidgetNode node) {
        int result = 0;
        WidgetNode current = node;
        while (current != null && current != layout.root) {
            result += current.x;
            current = findParent(layout, current);
        }
        return result;
    }

    public static int absoluteY(LayoutData layout, WidgetNode node) {
        int result = 0;
        WidgetNode current = node;
        while (current != null && current != layout.root) {
            result += current.y;
            current = findParent(layout, current);
        }
        return result;
    }

    public static WidgetNode addWidget(LayoutData layout, WidgetNode parent, String type) {
        normalizeLayout(layout);
        WidgetNode actualParent = parent != null && "layout".equals(parent.type) ? parent : layout.root;
        WidgetNode node = new WidgetNode();
        node.type = switch (type == null ? "layout" : type.toLowerCase(Locale.ROOT)) {
            case "text", "image", "grid" -> type.toLowerCase(Locale.ROOT);
            default -> "layout";
        };
        node.name = switch (node.type) {
            case "text" -> "Text Widget";
            case "image" -> "Image Widget";
            case "grid" -> "Grid Widget";
            default -> "Layout Widget";
        };
        node.x = 0;
        node.y = 0;
        node.z = actualParent.children.size() * 10;
        node.width = "grid".equals(node.type) ? 54 : -2;
        node.height = "grid".equals(node.type) ? 54 : -2;
        if ("text".equals(node.type)) node.text = "#delta_pack_name";
        if ("image".equals(node.type)) node.imagePath = "#delta_pack_icon";
        if ("grid".equals(node.type)) {
            GridContainerData container = new GridContainerData();
            container.id = "container_" + layout.containers.size();
            layout.containers.add(container);
            node.containerIndex = layout.containers.size() - 1;
        }
        normalizeWidget(node);
        actualParent.children.add(node);
        return node;
    }

    public static boolean removeWidget(LayoutData layout, WidgetNode target) {
        if (layout == null || target == null || target == layout.root) return false;
        WidgetNode parent = findParent(layout, target);
        if (parent == null || !parent.children.remove(target)) return false;
        Set<Integer> removedContainers = new TreeSet<>(Comparator.reverseOrder());
        collectGridContainerIndexes(target, removedContainers);
        for (int removedIndex : removedContainers) {
            if (removedIndex < 0 || removedIndex >= layout.containers.size()) continue;
            layout.containers.remove(removedIndex);
            for (WidgetNode node : flattenWidgets(layout)) {
                if ("grid".equals(node.type) && node.containerIndex > removedIndex) node.containerIndex--;
            }
        }
        return true;
    }

    public static void copyWidgetStructure(LayoutData source, LayoutData target) {
        target.root = GSON.fromJson(GSON.toJson(source.root), WidgetNode.class);
        GridContainerData[] copied = GSON.fromJson(GSON.toJson(source.containers), GridContainerData[].class);
        target.containers = new ArrayList<>();
        if (copied != null) target.containers.addAll(Arrays.asList(copied));
        normalizeLayout(target);
    }

    private static void flattenWidget(WidgetNode node, List<WidgetNode> output) {
        output.add(node);
        for (WidgetNode child : node.children) flattenWidget(child, output);
    }

    private static void collectGridContainerIndexes(WidgetNode node, Set<Integer> indexes) {
        if ("grid".equals(node.type)) indexes.add(node.containerIndex);
        for (WidgetNode child : node.children) collectGridContainerIndexes(child, indexes);
    }

    private static WidgetNode findParent(WidgetNode current, WidgetNode target) {
        for (WidgetNode child : current.children) {
            if (child == target) return current;
            WidgetNode nested = findParent(child, target);
            if (nested != null) return nested;
        }
        return null;
    }

    private static void bindGridContainers(LayoutData layout) {
        for (WidgetNode node : flattenWidgets(layout)) {
            if (!"grid".equals(node.type)) continue;
            if (node.containerIndex < 0 || node.containerIndex >= layout.containers.size()) {
                GridContainerData container = new GridContainerData();
                container.id = "container_" + layout.containers.size();
                container.columns = Math.max(1, node.gridColumns);
                container.rows = Math.max(1, node.gridRows);
                container.depth = Math.max(1, node.gridDepth);
                layout.containers.add(container);
                node.containerIndex = layout.containers.size() - 1;
            }
            GridContainerData container = layout.containers.get(node.containerIndex);
            node.gridColumns = Math.max(1, node.gridColumns);
            node.gridRows = Math.max(1, node.gridRows);
            node.gridDepth = Math.max(1, node.gridDepth);
            container.columns = node.gridColumns;
            container.rows = node.gridRows;
            container.depth = node.gridDepth;
        }
    }

    private static void normalizeWidget(WidgetNode node) {
        if (node == null) return;
        if (node.id == null || node.id.isBlank()) node.id = UUID.randomUUID().toString();
        if (node.type == null || node.type.isBlank()) node.type = "layout";
        if (node.name == null || node.name.isBlank()) node.name = switch (node.type) {
            case "text" -> "Text Widget";
            case "image" -> "Image Widget";
            case "grid" -> "Grid Widget";
            default -> "Layout Widget";
        };
        if (node.outerBorder == null) node.outerBorder = new BorderStyle();
        if (node.innerBorder == null) node.innerBorder = new BorderStyle();
        if (node.textColor == null || node.textColor.isBlank()) node.textColor = "#FFFFFFFF";
        if (node.children == null) node.children = new ArrayList<>();
        for (WidgetNode child : node.children) normalizeWidget(child);
    }

    private static WidgetNode legacyRoot(LayoutData ld, int gridColumns, int gridRows) {
        WidgetNode root = new WidgetNode();
        root.type = "layout";
        root.name = "Root Layout";
        root.width = -1;
        root.height = -1;
        root.backgroundColor = ld.panelAlpha << 24;

        WidgetNode background = new WidgetNode();
        background.type = "layout";
        background.legacyBridge = true;
        background.name = "Background Layout";
        background.x = 0;
        background.y = 0;
        background.z = 0;
        background.width = -2;
        background.height = -2;
        background.backgroundColor = ld.panelAlpha << 24;

        WidgetNode icon = new WidgetNode();
        icon.type = "image";
        icon.legacyBridge = true;
        icon.name = "Icon Widget";
        icon.x = ld.iconOffX;
        icon.y = ld.iconOffY;
        icon.z = 10;
        icon.width = 16;
        icon.height = 16;
        icon.imagePath = "#delta_pack_icon";

        WidgetNode text = new WidgetNode();
        text.type = "text";
        text.legacyBridge = true;
        text.name = "Text Widget";
        text.x = ld.textOffX;
        text.y = ld.textOffY;
        text.z = 20;
        text.text = ld.customText == null || ld.customText.isBlank() ? "#delta_pack_name" : ld.customText;
        text.verticalText = ld.verticalText;
        text.fontSize = Math.max(0.2, ld.textScale * 2.0);
        text.textColor = "#FFFFFFFF";
        text.width = -2;
        text.height = -2;

        WidgetNode grid = new WidgetNode();
        grid.type = "grid";
        grid.legacyBridge = true;
        grid.name = "Grid Widget";
        grid.x = ld.gridOffX;
        grid.y = ld.gridOffY;
        grid.z = 5;
        grid.gridColumns = gridColumns;
        grid.gridRows = gridRows;
        grid.gridDepth = 1;
        grid.containerIndex = 0;
        grid.width = gridColumns * 18;
        grid.height = gridRows * 18;

        root.children.add(background);
        root.children.add(icon);
        root.children.add(text);
        root.children.add(grid);
        return root;
    }

    private static int[] gridSizeForBox(String boxId) {
        if (boxId == null || boxId.isBlank()) return new int[]{3, 3};
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(boxId));
            if (item instanceof com.xtdpotato.xero_delta.item.SafetyBoxItem safetyBoxItem) {
                return new int[]{safetyBoxItem.getGridWidth(), safetyBoxItem.getGridHeight()};
            }
        } catch (Exception ignored) {
        }
        return new int[]{3, 3};
    }
}

