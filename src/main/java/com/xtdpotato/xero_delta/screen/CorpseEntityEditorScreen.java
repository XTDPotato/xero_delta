package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.data.CorpseRules;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.screen.material.Material3PageScreen;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Entity and carrier drafts are retained while navigating between native screens. */
final class CorpseEntityEditorScreen extends Material3PageScreen {
    record Result(String originalId, String entityId, List<CorpseRules.WeightedCarrier> chestRigs,
                  List<CorpseRules.WeightedCarrier> backpacks) {}
    private final Consumer<Result> callback;
    private final String originalId;
    private String entityId;
    private List<CorpseRules.WeightedCarrier> chestRigs;
    private List<CorpseRules.WeightedCarrier> backpacks;
    private String page = "entity";
    private final Map<String, String> drafts = new LinkedHashMap<>();
    private final Set<String> invalidFields = new LinkedHashSet<>();

    CorpseEntityEditorScreen(Screen parent, String entityId, List<CorpseRules.WeightedCarrier> chestRigs,
                             List<CorpseRules.WeightedCarrier> backpacks, Consumer<Result> callback) {
        super(Component.translatable("screen.xero_delta.corpse_rules.entity_editor"), parent);
        this.originalId = entityId == null ? "" : entityId;
        this.entityId = originalId;
        this.chestRigs = new ArrayList<>(chestRigs == null ? List.of() : chestRigs);
        this.backpacks = new ArrayList<>(backpacks == null ? List.of() : backpacks);
        this.callback = callback;
    }

    private static String tr(String key) { return CorpseRulesScreen.tr(key); }

    @Override protected void buildPage() {
        selectedPage(page);
        navigation("entity", tr("entity"), Material2Icon.RULE, () -> navigate("entity", () -> page = "entity"));
        navigation("chest", tr("chest_rigs"), Material2Icon.SHIELD, () -> navigate("chest", () -> page = "chest"));
        navigation("backpack", tr("backpacks"), Material2Icon.BACKPACK,
            () -> navigate("backpack", () -> page = "backpack"));
        if (page.equals("entity")) {
            section(tr("entity"));
            field(tr("entity_id"), entityId, 256, this::validEntity, next -> entityId = next.trim());
            action(tr("choose_entity"), () -> minecraft.setScreen(new CorpseEntityPickerScreen(this, entityId,
                next -> entityId = next)));
            action(tr("preview_entity"), () -> {
                if (validEntity(entityId)) minecraft.setScreen(new CorpseEntityPreviewScreen(this, entityId));
                else error(tr("invalid_entity"));
            });
        } else carriers(page.equals("chest"));
        footer(tr("save"), this::save);
    }

    private void carriers(boolean chest) {
        section(tr(chest ? "chest_rigs" : "backpacks"));
        action(tr("choose_items"), () -> openCarrierPicker(chest));
        var values = values(chest);
        long total = values.stream().mapToLong(CorpseRules.WeightedCarrier::weight).sum();
        for (int index = 0; index < values.size(); index++) {
            int slot = index;
            var value = values.get(index);
            String prefix = chest + ":" + index;
            section(String.format(java.util.Locale.ROOT, "%s %d  %.1f%%", tr("candidate"), index + 1,
                total <= 0 ? 0 : value.weight() * 100.0 / total));
            ResourceLocation itemId = ResourceLocation.tryParse(value.itemId());
            ItemStack stack = itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)
                ? BuiltInRegistries.ITEM.get(itemId).getDefaultInstance() : ItemStack.EMPTY;
            row(new CorpseCarrierCandidateRow(font, 0, 0, contentWidth, stack, value.itemId(),
                Material3Theme.SURFACE_CONTAINER, Material3Theme.PRIMARY,
                () -> openCarrierPicker(chest), minecraft.keyboardHandler::setClipboard,
                () -> {
                    long sum = values.stream().mapToLong(CorpseRules.WeightedCarrier::weight).sum();
                    return sum <= 0 ? 0 : values.get(slot).weight() / (double) sum;
                }), 36);
            field(tr("item_id"), drafts.getOrDefault(prefix + ":id", value.itemId()), 256,
                raw -> validCarrier(chest, slot, raw), raw -> {
                    drafts.put(prefix + ":id", raw);
                    if (validCarrier(chest, slot, raw)) {
                        invalidFields.remove(prefix + ":id");
                        var old = values.get(slot);
                        values.set(slot, new CorpseRules.WeightedCarrier(ResourceLocation.parse(raw.trim()).toString(), old.weight()));
                    } else invalidFields.add(prefix + ":id");
                });
            field(tr("weight"), drafts.getOrDefault(prefix + ":weight", Integer.toString(value.weight())), 10,
                CorpseEntityEditorScreen::validWeight, raw -> {
                    drafts.put(prefix + ":weight", raw);
                    if (validWeight(raw)) {
                        invalidFields.remove(prefix + ":weight");
                        var old = values.get(slot);
                        values.set(slot, new CorpseRules.WeightedCarrier(old.itemId(), Integer.parseInt(raw)));
                    } else invalidFields.add(prefix + ":weight");
                });
            action(tr("delete"), () -> {
                Runnable remove = () -> { values.remove(slot); clearDrafts(chest); rebuildPage(); };
                if (Screen.hasShiftDown()) remove.run(); else confirm(tr("delete_confirm"), remove);
            });
        }
        if (values.isEmpty()) text(tr("none"));
    }

    private void openCarrierPicker(boolean chest) {
        int slot = chest ? CorpseMenu.CHEST_RIG_SLOT : CorpseMenu.BACKPACK_SLOT;
        Set<String> selected = new LinkedHashSet<>();
        for (var value : values(chest)) selected.add(value.itemId());
        minecraft.setScreen(MailItemPickerScreen.filtered(this,
            stack -> !stack.isEmpty() && CorpseEntity.isCarrierStackForSlot(slot, stack), selected, stacks -> {
                Map<String, Integer> old = new LinkedHashMap<>();
                for (var value : values(chest)) old.put(value.itemId(), value.weight());
                Map<String, CorpseRules.WeightedCarrier> replacement = new LinkedHashMap<>();
                for (ItemStack stack : stacks) {
                    if (stack.isEmpty()) continue;
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    replacement.put(id, new CorpseRules.WeightedCarrier(id, old.getOrDefault(id, 1)));
                }
                if (chest) chestRigs = new ArrayList<>(replacement.values());
                else backpacks = new ArrayList<>(replacement.values());
                clearDrafts(chest);
            }));
    }

    private void clearDrafts(boolean chest) {
        drafts.keySet().removeIf(key -> key.startsWith(chest + ":"));
        invalidFields.removeIf(key -> key.startsWith(chest + ":"));
    }

    private boolean validCarrier(boolean chest, int index, String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        int slot = chest ? CorpseMenu.CHEST_RIG_SLOT : CorpseMenu.BACKPACK_SLOT;
        if (!CorpseEntity.isCarrierStackForSlot(slot, BuiltInRegistries.ITEM.get(id).getDefaultInstance())) return false;
        List<CorpseRules.WeightedCarrier> candidates = values(chest);
        for (int i = 0; i < candidates.size(); i++)
            if (i != index && candidates.get(i).itemId().equals(id.toString())) return false;
        return true;
    }

    private boolean validEntity(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    private static boolean validWeight(String raw) {
        try { int value = Integer.parseInt(raw); return value >= 1 && value <= CorpseRules.MAX_WEIGHT; }
        catch (NumberFormatException ignored) { return false; }
    }

    private List<CorpseRules.WeightedCarrier> values(boolean chest) { return chest ? chestRigs : backpacks; }

    private void save() {
        if (!validEntity(entityId) || !invalidFields.isEmpty()) { error(tr("invalid_settings")); return; }
        callback.accept(new Result(originalId, ResourceLocation.parse(entityId).toString(),
            List.copyOf(chestRigs), List.copyOf(backpacks)));
        onClose();
    }
}
