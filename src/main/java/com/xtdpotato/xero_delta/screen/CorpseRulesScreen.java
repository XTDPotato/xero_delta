package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.client.CorpseRulesClientState;
import com.xtdpotato.xero_delta.data.CorpseRules;
import com.xtdpotato.xero_delta.data.CorpseRulesConfig;
import com.xtdpotato.xero_delta.network.CorpseRulesUpdatePacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.screen.material.Material3PageScreen;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/** Per-entity rules with independent entity navigation and a fixed save bar. */
public final class CorpseRulesScreen extends Material3PageScreen {
    public record EditedRule(String originalId, String entityId,
                             List<CorpseRules.WeightedCarrier> chestRigs,
                             List<CorpseRules.WeightedCarrier> backpacks) {}
    private CorpseRules.Settings settings;
    private String selectedId;

    public CorpseRulesScreen(Screen parent) {
        super(Component.translatable("screen.xero_delta.corpse_rules.title"), parent);
        settings = connected() ? CorpseRulesClientState.INSTANCE.settings() : CorpseRulesConfig.load();
        if (settings == null) settings = CorpseRules.defaultSettings();
        selectedId = settings.entityIds().stream().findFirst().orElse("");
    }

    static String tr(String key) { return Component.translatable("settings.xero_delta." + key).getString(); }

    @Override protected void buildPage() {
        selectedPage(selectedId);
        for (String id : settings.entityIds())
            navigation(id, entityName(id), Material2Icon.RULE, () -> navigate(id, () -> selectedId = id));
        if (!editable()) text(tr("read_only"));
        if (!selectedId.isBlank() && settings.entityIds().contains(selectedId)) {
            section(entityName(selectedId));
            text(selectedId);
            action(tr("preview_entity"), () -> minecraft.setScreen(new CorpseEntityPreviewScreen(this, selectedId)));
            carriers(tr("chest_rigs"), settings.candidates(selectedId, true));
            carriers(tr("backpacks"), settings.candidates(selectedId, false));
            action(tr("edit_rule"), () -> minecraft.setScreen(editor(this, selectedId,
                settings.candidates(selectedId, true), settings.candidates(selectedId, false), this::applyEdit)))
                .active = editable();
            action(tr("delete_entity"), () -> confirm(tr("delete_confirm"), () -> delete(selectedId)))
                .active = editable();
        }
        section(tr("entities"));
        action(tr("add_entity"), () -> minecraft.setScreen(new CorpseEntityPickerScreen(this, "", id -> {
            if (!settings.entityIds().contains(id)) settings = settings.withCandidates(id, true,
                List.of(new CorpseRules.WeightedCarrier(CorpseRules.DEFAULT_CHEST_RIG_ID, 1)));
            selectedId = id;
        }))).active = editable();
        action(tr("reset_all"), () -> confirm(tr("reset_all_confirm"), () -> {
            settings = CorpseRules.defaultSettings();
            selectedId = settings.entityIds().stream().findFirst().orElse("");
            rebuildPage();
        })).active = editable();
        footer(tr("save"), this::save);
    }

    private void carriers(String title, List<CorpseRules.WeightedCarrier> values) {
        section(title);
        if (values.isEmpty()) { text(tr("none")); return; }
        long total = values.stream().mapToLong(CorpseRules.WeightedCarrier::weight).sum();
        for (var value : values) {
            ResourceLocation id = ResourceLocation.tryParse(value.itemId());
            String name = id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.get(id).getDescription().getString() : value.itemId();
            ItemStack stack = id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.get(id).getDefaultInstance() : ItemStack.EMPTY;
            row(new CorpseCarrierCandidateRow(font, 0, 0, contentWidth, stack, value.itemId(),
                Material3Theme.SURFACE_CONTAINER, Material3Theme.PRIMARY, () -> {},
                minecraft.keyboardHandler::setClipboard,
                () -> total <= 0 ? 0 : value.weight() / (double) total), 36);
            text(String.format(java.util.Locale.ROOT, "%s  %.1f%%", name,
                total <= 0 ? 0 : value.weight() * 100.0 / total));
            text(value.itemId());
        }
    }

    private void applyEdit(EditedRule value) {
        if (!editable()) return;
        var ids = new LinkedHashSet<>(settings.entityIds());
        var rules = new LinkedHashMap<>(settings.entityRules());
        if (!value.originalId().equals(value.entityId())) {
            ids.remove(value.originalId());
            rules.remove(value.originalId());
        }
        ids.add(value.entityId());
        rules.put(value.entityId(), new CorpseRules.EntityCarriers(value.chestRigs(), value.backpacks()));
        settings = new CorpseRules.Settings(ids, rules);
        selectedId = value.entityId();
    }

    private void delete(String id) {
        if (!editable()) return;
        var ids = new LinkedHashSet<>(settings.entityIds());
        var rules = new LinkedHashMap<>(settings.entityRules());
        ids.remove(id);
        rules.remove(id);
        settings = new CorpseRules.Settings(ids, rules);
        selectedId = ids.stream().findFirst().orElse("");
        rebuildPage();
    }

    private void save() {
        if (!editable()) { error(tr("read_only")); return; }
        CorpseRulesConfig.save(settings);
        if (connected()) ModNetwork.sendToServer(new CorpseRulesUpdatePacket(settings));
        onClose();
    }

    private boolean editable() { return !connected() || CorpseRulesClientState.INSTANCE.editable(); }
    private static boolean connected() { return Minecraft.getInstance().getConnection() != null; }

    private static String entityName(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)
            ? BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString() : raw;
    }

    public static Screen editor(Screen parent, String entityId, List<CorpseRules.WeightedCarrier> chestRigs,
                                List<CorpseRules.WeightedCarrier> backpacks, Consumer<EditedRule> callback) {
        return new CorpseEntityEditorScreen(parent, entityId, chestRigs, backpacks,
            result -> callback.accept(new EditedRule(result.originalId(), result.entityId(),
                result.chestRigs(), result.backpacks())));
    }
}
