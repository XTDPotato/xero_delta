package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.RuleFormulaConfig;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.screen.material.Material3PageScreen;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Minecraft-rendered Material 3 settings; no external UI runtime is required. */
public class XeroDeltaMaterialSettingsScreen extends Material3PageScreen {
    private static final List<String> PAGES = List.of("about", "safety", "grid", "tooltip",
        "quality", "delta", "wheel", "corpse");
    private static final List<String> QUALITIES = List.of("red", "gold", "purple", "blue", "green", "gray");
    private final MaterialSettingsState state = new MaterialSettingsState();
    private String page;
    private final java.util.Set<String> invalidFields = new java.util.HashSet<>();
    private final java.util.Map<String, String> numberDrafts = new java.util.HashMap<>();

    public XeroDeltaMaterialSettingsScreen(Screen parent) { this(parent, "about"); }

    public XeroDeltaMaterialSettingsScreen(Screen parent, String initialPage) {
        super(Component.translatable("screen.xero_delta.config.title"), parent);
        page = PAGES.contains(initialPage) ? initialPage : "about";
    }

    private String tr(String key) { return Component.translatable("settings.xero_delta." + key).getString(); }

    @Override
    protected void buildPage() {
        applyDraftTheme();
        selectedPage(page);
        Material2Icon[] icons = {Material2Icon.PALETTE, Material2Icon.SECURITY, Material2Icon.GRID_VIEW,
            Material2Icon.TOOLTIP, Material2Icon.AUTO_AWESOME, Material2Icon.DASHBOARD,
            Material2Icon.TUNE, Material2Icon.RULE};
        for (int i = 0; i < PAGES.size(); i++) {
            String id = PAGES.get(i);
            navigation(id, tr(id), icons[i], () -> navigate(id, () -> page = id));
        }
        switch (page) {
            case "safety" -> safety();
            case "grid" -> grid();
            case "tooltip" -> tooltip();
            case "quality" -> quality();
            case "delta" -> hud();
            case "wheel" -> wheel();
            case "corpse" -> {
                section(tr("corpse"));
                action(tr("corpse_open"), () -> minecraft.setScreen(new CorpseRulesScreen(this)));
            }
            default -> appearance();
        }
        section(tr("reset"));
        action(tr("reset_page"), () -> confirm(tr("reset_confirm"), () -> {
            state.reset(page);
            if ("grid".equals(page)) state.reset("inventory");
            invalidFields.removeIf(key -> key.startsWith(page + ":"));
            numberDrafts.keySet().removeIf(key -> key.startsWith(page + ":"));
            rebuildPage();
        }));
        action(tr("reset_all"), () -> confirm(tr("reset_all_confirm"), () -> {
            state.resetAll();
            invalidFields.clear();
            numberDrafts.clear();
            rebuildPage();
        }));
        footer(tr("save"), this::save);
    }

    private void appearance() {
        section(tr("appearance"));
        choice(tr("theme"), List.of("mint", "graphite", "amber", "ocean", "violet", "custom"),
            state.theme, id -> tr("theme_" + id), next -> state.theme = next);
        boolean custom = ConfigThemeCatalog.isCustom(state.theme);
        color(tr("highlight"), state.themeHighlight, next -> {
            state.themeHighlight = next; applyDraftTheme();
        }).active = custom;
        color(tr("background"), state.themePrimary, next -> {
            state.themePrimary = next; applyDraftTheme();
        }).active = custom;
        color(tr("surface"), state.themeSecondary, next -> {
            state.themeSecondary = next; applyDraftTheme();
        }).active = custom;
        section(tr("general"));
        toggle(tr("quick_move"), state.quickMoveEnabled, next -> state.quickMoveEnabled = next);
        number("quick_threshold", state.quickMoveThreshold, 0, Integer.MAX_VALUE,
            next -> state.quickMoveThreshold = next.intValue());
    }

    private void safety() {
        section(tr("filters"));
        field(tr("allowlist"), String.join(", ", state.safetyAllowlist), 4096, next -> true,
            next -> state.safetyAllowlist = parseList(next));
        field(tr("blacklist"), String.join(", ", state.safetyBlacklist), 4096, next -> true,
            next -> state.safetyBlacklist = parseList(next));
        action(tr("layout_open"), () -> minecraft.setScreen(new SafetyBoxLayoutScreen(this)));
    }

    private void grid() {
        section(tr("grid"));
        toggle(tr("grid_enabled"), state.itemGridEnabled, next -> state.itemGridEnabled = next);
        toggle(tr("show_equipment"), state.inventoryShowEquipment, next -> state.inventoryShowEquipment = next);
        choice(tr("model_control"), List.of("MOUSE", "DRAG"), state.inventoryModelControl,
            id -> tr("control_" + id.toLowerCase(Locale.ROOT)), next -> state.inventoryModelControl = next);
        slider(tr("border"), " px", .1, 2, .1, state.gridBorderThickness, next -> state.gridBorderThickness = next);
        action(tr("layout_open"), () -> minecraft.setScreen(new SafetyBoxLayoutScreen(this)));
    }

    private void tooltip() {
        section(tr("tooltip"));
        choice(tr("tooltip_mode"), List.of(Config.TooltipSizeMode.values()), state.tooltipMode,
            value -> tr("tooltip_" + value.name().toLowerCase(Locale.ROOT)), next -> state.tooltipMode = next);
        slider(tr("scale"), "x", .5, 3, .1, state.tooltipScale, next -> state.tooltipScale = next);
        section(tr("padding"));
        slider(tr("top"), " px", 0, 20, 1, state.tooltipPaddingTop, next -> state.tooltipPaddingTop = (int) next);
        slider(tr("bottom"), " px", 0, 20, 1, state.tooltipPaddingBottom, next -> state.tooltipPaddingBottom = (int) next);
        slider(tr("left"), " px", 0, 20, 1, state.tooltipPaddingLeft, next -> state.tooltipPaddingLeft = (int) next);
        slider(tr("right"), " px", 0, 20, 1, state.tooltipPaddingRight, next -> state.tooltipPaddingRight = (int) next);
        section(tr("title_position"));
        toggle(tr("title_auto"), state.tooltipTitleAutoOffset, next -> state.tooltipTitleAutoOffset = next);
        slider(tr("horizontal"), " px", -100, 100, 1, state.tooltipTitleOffsetX, next -> state.tooltipTitleOffsetX = (int) next);
        slider(tr("vertical"), " px", -100, 100, 1, state.tooltipTitleOffsetY, next -> state.tooltipTitleOffsetY = (int) next);
    }

    private void quality() {
        section(tr("quality_colors"));
        for (String quality : QUALITIES) color(tr("quality_" + quality), state.qualityColors.get(quality),
            next -> state.qualityColors.put(quality, next));
        section(tr("default_size"));
        slider(tr("width"), "", 1, 8, 1, state.formula.defaultWidth, next -> state.formula.defaultWidth = (int) next);
        slider(tr("height"), "", 1, 8, 1, state.formula.defaultHeight, next -> state.formula.defaultHeight = (int) next);
        toggle(tr("recipe_size"), state.formula.recipeSizeEnabled, next -> state.formula.recipeSizeEnabled = next);
        section(tr("value_formula"));
        number("random_min", state.formula.randomOffsetMinimum, 0, 9_999_999, next -> state.formula.randomOffsetMinimum = next);
        number("random_max", state.formula.randomOffsetMaximum, 0, 9_999_999, next -> state.formula.randomOffsetMaximum = next);
        for (String quality : QUALITIES) {
            section(tr("quality_" + quality));
            RuleFormulaConfig.Range range = state.formula.ranges.get(quality);
            number(quality + "_min", tr("minimum"), range.minimumPerSlot, 1, 9_999_999, next -> range.minimumPerSlot = next);
            number(quality + "_max", tr("maximum"), range.maximumPerSlot, 1, 9_999_999, next -> range.maximumPerSlot = next);
        }
    }

    private void hud() {
        section(tr("effects"));
        toggle(tr("manual_position"), state.hudEffectsManual, next -> state.hudEffectsManual = next);
        slider(tr("scale"), "%", 50, 200, 5, state.hudEffectsScale, next -> state.hudEffectsScale = (int) next);
        slider(tr("opacity"), "%", 10, 100, 5, state.hudEffectsOpacity, next -> state.hudEffectsOpacity = (int) next);
        section(tr("inventory_effects"));
        toggle(tr("manual_position"), state.inventoryEffectsManual, next -> state.inventoryEffectsManual = next);
        slider(tr("scale"), "%", 50, 200, 5, state.inventoryEffectsScale, next -> state.inventoryEffectsScale = (int) next);
        slider(tr("opacity"), "%", 10, 100, 5, state.inventoryEffectsOpacity, next -> state.inventoryEffectsOpacity = (int) next);
        section(tr("positioning"));
        toggle(tr("snap_x"), state.hudSnapX, next -> state.hudSnapX = next);
        toggle(tr("snap_y"), state.hudSnapY, next -> state.hudSnapY = next);
        action(tr("hud_open"), () -> minecraft.setScreen(new StatusEffectHudConfigScreen(this)));
    }

    private void wheel() {
        section(tr("wheel"));
        choice(tr("wheel_slots"), List.of(4, 8), state.wheelSlots, Object::toString, next -> state.wheelSlots = next);
        slider(tr("scale"), "%", 50, 150, 5, state.wheelScalePercent, next -> state.wheelScalePercent = (int) next);
        slider(tr("horizontal"), "%", 0, 100, 1, state.wheelCenterXPercent, next -> state.wheelCenterXPercent = (int) next);
        slider(tr("vertical"), "%", 0, 100, 1, state.wheelCenterYPercent, next -> state.wheelCenterYPercent = (int) next);
        section(tr("timing"));
        slider(tr("marker_hold"), " s", .1, 3, .05, state.markerWheelHoldSeconds, next -> state.markerWheelHoldSeconds = next);
        slider(tr("medical_hold"), " s", .1, 3, .05, state.medicalWheelHoldSeconds, next -> state.medicalWheelHoldSeconds = next);
        slider(tr("double_click"), " s", .1, 3, .05, state.markerDoubleClickSeconds, next -> state.markerDoubleClickSeconds = next);
        slider(tr("marker_lifetime"), " s", 1, 300, 1, state.markerLifetimeSeconds, next -> state.markerLifetimeSeconds = next);
        slider(tr("enemy_lifetime"), " s", 1, 300, 1, state.enemyMarkerLifetimeSeconds, next -> state.enemyMarkerLifetimeSeconds = next);
        section(tr("sounds"));
        for (int i = 0; i < state.wheelSounds.size(); i++) {
            int index = i;
            sound(tr("sound_slot") + " " + (i + 1), state.wheelSounds.get(i), next -> state.wheelSounds.set(index, next));
        }
        sound(tr("quick_marker"), state.quickMarkerSound, next -> state.quickMarkerSound = next);
        sound(tr("rescue"), state.rescueRequestSound, next -> state.rescueRequestSound = next);
    }

    private void sound(String name, String value, Consumer<String> change) {
        var field = field(name, value, 256, this::validSound, change);
        action(tr("preview_sound"), () -> {
            ResourceLocation id = ResourceLocation.tryParse(field.getValue().trim());
            if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) { error(tr("invalid_sound")); return; }
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(BuiltInRegistries.SOUND_EVENT.get(id), 1));
        });
    }

    private boolean validSound(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        return id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id);
    }

    private void number(String key, long value, long min, long max, Consumer<Long> change) {
        number(key, tr(key), value, min, max, change);
    }

    private void number(String key, String label, long value, long min, long max, Consumer<Long> change) {
        String draftKey = page + ":" + key;
        field(label, numberDrafts.getOrDefault(draftKey, Long.toString(value)), 12,
            raw -> validNumber(raw, min, max), raw -> {
            numberDrafts.put(draftKey, raw);
            if (validNumber(raw, min, max)) {
                invalidFields.remove(draftKey);
                change.accept(Long.parseLong(raw));
            } else invalidFields.add(draftKey);
        });
    }

    private static boolean validNumber(String raw, long min, long max) {
        try { long value = Long.parseLong(raw); return value >= min && value <= max; }
        catch (NumberFormatException ignored) { return false; }
    }

    private void save() {
        if (!invalidFields.isEmpty() || !state.valid()
            || state.formula.randomOffsetMinimum > state.formula.randomOffsetMaximum
            || state.formula.ranges.values().stream().anyMatch(range -> range.minimumPerSlot > range.maximumPerSlot)) {
            error(tr("invalid_settings"));
            return;
        }
        state.save();
        onClose();
    }

    @Override public void onClose() {
        Material3Theme.refreshFromConfig();
        super.onClose();
    }

    private void applyDraftTheme() {
        if (ConfigThemeCatalog.isCustom(state.theme)) {
            if (!validColor(state.themeHighlight) || !validColor(state.themePrimary) || !validColor(state.themeSecondary)) return;
            Material3Theme.apply(ConfigThemeCatalog.customTheme(state.themeHighlight, state.themePrimary, state.themeSecondary));
        } else Material3Theme.apply(ConfigThemeCatalog.theme(state.theme));
    }

    private static boolean validColor(String raw) {
        return raw != null && raw.trim().matches("(?i)(?:0x|#)?(?:[0-9a-f]{6}|[0-9a-f]{8})");
    }

    private static List<String> parseList(String raw) {
        return new ArrayList<>(Arrays.stream(raw.split("[,;\\n]")).map(String::trim)
            .filter(value -> !value.isBlank()).distinct().toList());
    }
}
