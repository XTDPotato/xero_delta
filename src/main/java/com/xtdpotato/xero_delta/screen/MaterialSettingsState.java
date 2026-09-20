package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.StatusEffectHudState;
import com.xtdpotato.xero_delta.compat.DeltaSpotWheelLayoutCompat;
import com.xtdpotato.xero_delta.data.RuleFormulaConfig;
import com.xtdpotato.xero_delta.network.ItemGridConfigPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MaterialSettingsState {
    private static final List<String> QUALITY_NAMES = List.of("red", "gold", "purple", "blue", "green", "gray");
    private static boolean validColor(String value) {
        return value != null && value.trim().matches("(?i)(?:0x|#)?(?:[0-9a-f]{6}|[0-9a-f]{8})");
    }

    private static String normalizeColor(String value, String fallback) {
        if (!validColor(value)) return fallback;
        String text = value.trim().replaceFirst("(?i)^(?:0x|#)", "");
        if (text.length() == 6) text = "FF" + text;
        return "0x" + text.toUpperCase(Locale.ROOT);
    }

    private static boolean validSound(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value == null ? "" : value.trim());
        return id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id);
    }

    private static boolean connected() {
        return Minecraft.getInstance().getConnection() != null;
    }


        boolean quickMoveEnabled;
        int quickMoveThreshold;
        boolean itemGridEnabled;
        boolean originalItemGridEnabled;
        double gridBorderThickness;
        Config.TooltipSizeMode tooltipMode;
        double tooltipScale;
        int tooltipPaddingTop;
        int tooltipPaddingBottom;
        int tooltipPaddingLeft;
        int tooltipPaddingRight;
        boolean tooltipTitleAutoOffset;
        boolean inventoryShowEquipment;
        String inventoryModelControl;
        int tooltipTitleOffsetX;
        int tooltipTitleOffsetY;
        String theme;
        String themeHighlight;
        String themePrimary;
        String themeSecondary;
        Map<String, String> qualityColors;
        List<String> safetyAllowlist;
        List<String> safetyBlacklist;
        RuleFormulaConfig.FormulaData formula;
        boolean hudEffectsManual;
        int hudEffectsScale;
        int hudEffectsOpacity;
        boolean inventoryEffectsManual;
        int inventoryEffectsScale;
        int inventoryEffectsOpacity;
        boolean hudSnapX;
        boolean hudSnapY;
        int wheelSlots;
        int wheelScalePercent;
        int wheelCenterXPercent;
        int wheelCenterYPercent;
        double markerWheelHoldSeconds;
        double markerDoubleClickSeconds;
        double markerLifetimeSeconds;
        double enemyMarkerLifetimeSeconds;
        double medicalWheelHoldSeconds;
        List<String> wheelSounds;
        String quickMarkerSound;
        String rescueRequestSound;

        MaterialSettingsState() {
            load();
        }

        void load() {
            quickMoveEnabled = Config.INSTANCE.quickMoveEnabled.get();
            quickMoveThreshold = Config.INSTANCE.quickMoveValueThreshold.get();
            itemGridEnabled = Config.INSTANCE.itemGridEnabled.get();
            originalItemGridEnabled = itemGridEnabled;
            gridBorderThickness = Config.INSTANCE.gridBorderThickness.get();
            tooltipMode = Config.INSTANCE.getTooltipSizeMode();
            tooltipScale = Config.INSTANCE.tooltipSizeScale.get();
            tooltipPaddingTop = Config.INSTANCE.tooltipSizePaddingTop.get();
            tooltipPaddingBottom = Config.INSTANCE.tooltipSizePaddingBottom.get();
            tooltipPaddingLeft = Config.INSTANCE.tooltipSizePaddingLeft.get();
            tooltipPaddingRight = Config.INSTANCE.tooltipSizePaddingRight.get();
            tooltipTitleAutoOffset = Config.INSTANCE.tooltipTitleAutoOffset.get();
            inventoryShowEquipment = Config.INSTANCE.inventoryShowEquipment.get();
            inventoryModelControl = Config.INSTANCE.inventoryModelControl.get();
            tooltipTitleOffsetX = Config.INSTANCE.tooltipTitleOffsetX.get();
            tooltipTitleOffsetY = Config.INSTANCE.tooltipTitleOffsetY.get();
            theme = ConfigThemeCatalog.normalize(Config.INSTANCE.configTheme.get());
            themeHighlight = Config.INSTANCE.configThemeHighlight.get();
            themePrimary = Config.INSTANCE.configThemePrimary.get();
            themeSecondary = Config.INSTANCE.configThemeSecondary.get();
            qualityColors = new LinkedHashMap<>();
            for (String quality : QUALITY_NAMES) {
                qualityColors.put(quality, switch (quality) {
                    case "red" -> Config.INSTANCE.qualityRedColor.get();
                    case "gold" -> Config.INSTANCE.qualityGoldColor.get();
                    case "purple" -> Config.INSTANCE.qualityPurpleColor.get();
                    case "blue" -> Config.INSTANCE.qualityBlueColor.get();
                    case "green" -> Config.INSTANCE.qualityGreenColor.get();
                    default -> Config.INSTANCE.qualityGrayColor.get();
                });
            }
            safetyAllowlist = new ArrayList<>(Config.INSTANCE.safetyBoxAllowlist.get());
            safetyBlacklist = new ArrayList<>(Config.INSTANCE.safetyBoxBlacklist.get());
            formula = RuleFormulaConfig.get();
            hudEffectsManual = StatusEffectHudState.effectsManualPosition();
            hudEffectsScale = StatusEffectHudState.scalePercent();
            hudEffectsOpacity = StatusEffectHudState.opacityPercent();
            inventoryEffectsManual = StatusEffectHudState.inventoryEffectsManualPosition();
            inventoryEffectsScale = StatusEffectHudState.inventoryEffectScalePercent();
            inventoryEffectsOpacity = StatusEffectHudState.inventoryEffectOpacityPercent();
            hudSnapX = StatusEffectHudState.snapX();
            hudSnapY = StatusEffectHudState.snapY();
            wheelSlots = DeltaSpotWheelLayoutCompat.slots();
            wheelScalePercent = DeltaSpotWheelLayoutCompat.scalePercent();
            wheelCenterXPercent = DeltaSpotWheelLayoutCompat.centerXPercent();
            wheelCenterYPercent = DeltaSpotWheelLayoutCompat.centerYPercent();
            markerWheelHoldSeconds = DeltaSpotWheelLayoutCompat.holdSeconds();
            markerDoubleClickSeconds = DeltaSpotWheelLayoutCompat.doubleClickSeconds();
            markerLifetimeSeconds = DeltaSpotWheelLayoutCompat.markerLifetimeSeconds();
            enemyMarkerLifetimeSeconds = DeltaSpotWheelLayoutCompat.enemyMarkerLifetimeSeconds();
            medicalWheelHoldSeconds = Config.INSTANCE.medicalWheelHoldSeconds.get();
            wheelSounds = new ArrayList<>(DeltaSpotWheelLayoutCompat.wheelSounds());
            while (wheelSounds.size() < 8) wheelSounds.add("minecraft:block.note_block.bell");
            quickMarkerSound = DeltaSpotWheelLayoutCompat.quickMarkerSound();
            rescueRequestSound = Config.INSTANCE.rescueRequestSound.get();
        }

        void reset(String page) {
            switch (page) {
                case "about" -> {
                    theme = ConfigThemeCatalog.DEFAULT_ID;
                    themeHighlight = Config.INSTANCE.configThemeHighlight.getDefault();
                    themePrimary = Config.INSTANCE.configThemePrimary.getDefault();
                    themeSecondary = Config.INSTANCE.configThemeSecondary.getDefault();
                    quickMoveEnabled = Config.INSTANCE.quickMoveEnabled.getDefault();
                    quickMoveThreshold = Config.INSTANCE.quickMoveValueThreshold.getDefault();
                }
                case "safety" -> {
                    safetyAllowlist = new ArrayList<>(Config.INSTANCE.safetyBoxAllowlist.getDefault());
                    safetyBlacklist = new ArrayList<>(Config.INSTANCE.safetyBoxBlacklist.getDefault());
                }
                case "grid" -> {
                    itemGridEnabled = Config.INSTANCE.itemGridEnabled.getDefault();
                    gridBorderThickness = Config.INSTANCE.gridBorderThickness.getDefault();
                }
                case "tooltip" -> {
                    tooltipMode = Config.TooltipSizeMode.valueOf(Config.INSTANCE.tooltipSizeMode.getDefault());
                    tooltipScale = Config.INSTANCE.tooltipSizeScale.getDefault();
                    tooltipPaddingTop = Config.INSTANCE.tooltipSizePaddingTop.getDefault();
                    tooltipPaddingBottom = Config.INSTANCE.tooltipSizePaddingBottom.getDefault();
                    tooltipPaddingLeft = Config.INSTANCE.tooltipSizePaddingLeft.getDefault();
                    tooltipPaddingRight = Config.INSTANCE.tooltipSizePaddingRight.getDefault();
                    tooltipTitleAutoOffset = Config.INSTANCE.tooltipTitleAutoOffset.getDefault();
                    tooltipTitleOffsetX = Config.INSTANCE.tooltipTitleOffsetX.getDefault();
                    tooltipTitleOffsetY = Config.INSTANCE.tooltipTitleOffsetY.getDefault();
                }
                case "inventory" -> {
                    inventoryShowEquipment = Config.INSTANCE.inventoryShowEquipment.getDefault();
                    inventoryModelControl = Config.INSTANCE.inventoryModelControl.getDefault();
                }
                case "quality" -> {
                    qualityColors.put("red", Config.INSTANCE.qualityRedColor.getDefault());
                    qualityColors.put("gold", Config.INSTANCE.qualityGoldColor.getDefault());
                    qualityColors.put("purple", Config.INSTANCE.qualityPurpleColor.getDefault());
                    qualityColors.put("blue", Config.INSTANCE.qualityBlueColor.getDefault());
                    qualityColors.put("green", Config.INSTANCE.qualityGreenColor.getDefault());
                    qualityColors.put("gray", Config.INSTANCE.qualityGrayColor.getDefault());
                    formula = new RuleFormulaConfig.FormulaData();
                }
                case "delta" -> {
                    hudEffectsManual = false;
                    hudEffectsScale = 100;
                    hudEffectsOpacity = 100;
                    inventoryEffectsManual = false;
                    inventoryEffectsScale = 100;
                    inventoryEffectsOpacity = 100;
                    hudSnapX = true;
                    hudSnapY = true;
                }
                case "wheel" -> {
                    wheelSlots = 8;
                    wheelScalePercent = 100;
                    wheelCenterXPercent = 50;
                    wheelCenterYPercent = 50;
                    markerWheelHoldSeconds = 0.75D;
                    markerDoubleClickSeconds = 1.0D;
                    markerLifetimeSeconds = 20.0D;
                    enemyMarkerLifetimeSeconds = 10.0D;
                    medicalWheelHoldSeconds = 0.75D;
                    wheelSounds = new ArrayList<>(List.of(
                        "minecraft:block.note_block.bell", "minecraft:block.note_block.didgeridoo",
                        "minecraft:block.note_block.chime", "minecraft:block.note_block.pling",
                        "minecraft:block.amethyst_block.chime", "minecraft:block.beacon.activate",
                        "minecraft:block.note_block.bit", "minecraft:block.note_block.harp"));
                    quickMarkerSound = "minecraft:block.note_block.pling";
                    rescueRequestSound = "minecraft:block.note_block.bell";
                }
                default -> {
                }
            }
        }

        void resetAll() {
            for (String page : List.of("about", "safety", "grid", "tooltip", "inventory", "quality", "delta", "wheel")) {
                reset(page);
            }
        }

        boolean valid() {
            if (!validColor(themeHighlight) || !validColor(themePrimary) || !validColor(themeSecondary)) return false;
            for (String quality : QUALITY_NAMES) if (!validColor(qualityColors.get(quality))) return false;
            for (String sound : wheelSounds) if (!validSound(sound)) return false;
            return validSound(quickMarkerSound) && validSound(rescueRequestSound);
        }

        void save() {
            Config.INSTANCE.quickMoveEnabled.set(quickMoveEnabled);
            Config.INSTANCE.quickMoveValueThreshold.set(quickMoveThreshold);
            Config.INSTANCE.itemGridEnabled.set(itemGridEnabled);
            Config.INSTANCE.gridBorderThickness.set(gridBorderThickness);
            Config.INSTANCE.tooltipSizeMode.set(tooltipMode.name());
            Config.INSTANCE.tooltipSizeScale.set(tooltipScale);
            Config.INSTANCE.tooltipSizePaddingTop.set(tooltipPaddingTop);
            Config.INSTANCE.tooltipSizePaddingBottom.set(tooltipPaddingBottom);
            Config.INSTANCE.tooltipSizePaddingLeft.set(tooltipPaddingLeft);
            Config.INSTANCE.tooltipSizePaddingRight.set(tooltipPaddingRight);
            Config.INSTANCE.tooltipTitleAutoOffset.set(tooltipTitleAutoOffset);
            Config.INSTANCE.tooltipTitleOffsetX.set(tooltipTitleOffsetX);
            Config.INSTANCE.tooltipTitleOffsetY.set(tooltipTitleOffsetY);
            Config.INSTANCE.inventoryShowEquipment.set(inventoryShowEquipment);
            Config.INSTANCE.inventoryModelControl.set(inventoryModelControl);
            Config.INSTANCE.configTheme.set(ConfigThemeCatalog.normalize(theme));
            Config.INSTANCE.configThemeHighlight.set(normalizeColor(themeHighlight, "0xFF55D6B0"));
            Config.INSTANCE.configThemePrimary.set(normalizeColor(themePrimary, "0xF20B1418"));
            Config.INSTANCE.configThemeSecondary.set(normalizeColor(themeSecondary, "0xF2223238"));
            for (String quality : QUALITY_NAMES) Config.INSTANCE.setQualityColor(quality,
                normalizeColor(qualityColors.get(quality), "0x66666666"));
            Config.INSTANCE.safetyBoxAllowlist.set(List.copyOf(safetyAllowlist));
            Config.INSTANCE.safetyBoxBlacklist.set(List.copyOf(safetyBlacklist));
            Config.INSTANCE.medicalWheelHoldSeconds.set(medicalWheelHoldSeconds);
            Config.INSTANCE.rescueRequestSound.set(rescueRequestSound);
            RuleFormulaConfig.save(formula);
            if (hudEffectsManual) {
                StatusEffectHudState.setPosition(StatusEffectHudState.effectsX(), StatusEffectHudState.effectsY());
            } else {
                StatusEffectHudState.resetPosition();
            }
            StatusEffectHudState.setScalePercent(hudEffectsScale);
            StatusEffectHudState.setOpacityPercent(hudEffectsOpacity);
            if (inventoryEffectsManual) {
                StatusEffectHudState.setInventoryEffectPosition(StatusEffectHudState.inventoryEffectsX(),
                    StatusEffectHudState.inventoryEffectsY());
            } else {
                StatusEffectHudState.resetInventoryEffectPosition();
            }
            StatusEffectHudState.setInventoryEffectScalePercent(inventoryEffectsScale);
            StatusEffectHudState.setInventoryEffectOpacityPercent(inventoryEffectsOpacity);
            StatusEffectHudState.setSnapX(hudSnapX);
            StatusEffectHudState.setSnapY(hudSnapY);
            StatusEffectHudState.save();
            DeltaSpotWheelLayoutCompat.save(wheelSlots, wheelScalePercent, wheelCenterXPercent,
                wheelCenterYPercent, markerWheelHoldSeconds, markerDoubleClickSeconds,
                markerLifetimeSeconds, enemyMarkerLifetimeSeconds, wheelSounds, quickMarkerSound);
            ClientDataCache.INSTANCE.setLocalItemGridEnabled(itemGridEnabled);
            if (originalItemGridEnabled != itemGridEnabled && connected()) {
                ModNetwork.sendToServer(new ItemGridConfigPacket(ItemGridConfigPacket.SET_GLOBAL, "", itemGridEnabled));
            }
            Config.SPEC.save();
            originalItemGridEnabled = itemGridEnabled;
        }
    }
