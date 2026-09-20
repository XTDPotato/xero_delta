package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.grid.GridBackingStore;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PersonalWarehouseData extends SavedData {
    public static final int COLUMNS = 9;
    public static final int DEFAULT_ROWS = WarehouseCategory.MAIN.defaultRows();
    public static final int MAX_ROWS = WarehouseCategory.MAIN.maximumRows();
    public static final int MAX_NAME_LENGTH = 24;

    private static final String DATA_NAME = "xero_delta_personal_warehouse";
    private static final String DEFAULT_NAME = "";

    private final Map<UUID, Warehouse> warehouses = new HashMap<>();

    public static PersonalWarehouseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(PersonalWarehouseData::new, PersonalWarehouseData::load), DATA_NAME);
    }

    public synchronized Warehouse warehouse(UUID id) {
        return warehouses.computeIfAbsent(id, ignored -> Warehouse.createDefault());
    }

    public synchronized boolean upgrade(UUID id) {
        Bin current = warehouse(id).bin(WarehouseCategory.MAIN);
        if (current.rows() >= WarehouseCategory.MAIN.maximumRows()) return false;
        int rows = Math.min(WarehouseCategory.MAIN.maximumRows(), current.rows() + 5);
        current.resize(rows);
        setDirty();
        return true;
    }

    public synchronized String rename(UUID id, String requestedName) {
        Warehouse warehouse = warehouse(id);
        warehouse.setName(sanitizeName(requestedName));
        setDirty();
        return warehouse.name();
    }

    public synchronized void changed() {
        setDirty();
    }

    public static String sanitizeName(String input) {
        return WarehouseNameRules.sanitize(input, MAX_NAME_LENGTH);
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                    HolderLookup.@NotNull Provider registries) {
        ListTag values = new ListTag();
        warehouses.forEach((id, warehouse) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", id);
            entry.putString("name", warehouse.name());
            ListTag bins = new ListTag();
            for (WarehouseCategory category : WarehouseCategory.values()) {
                Bin bin = warehouse.bin(category);
                CompoundTag binTag = new CompoundTag();
                binTag.putString("category", category.id());
                binTag.putInt("rows", bin.rows());
                binTag.put("items", saveItems(bin.items(), registries));
                bins.add(binTag);
            }
            entry.put("bins", bins);
            values.add(entry);
        });
        tag.put("warehouses", values);
        return tag;
    }

    private static ListTag saveItems(NonNullList<ItemStack> stacks,
                                     HolderLookup.Provider registries) {
        ListTag items = new ListTag();
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty()) continue;
            CompoundTag item = new CompoundTag();
            item.putInt("slot", slot);
            item.put("stack", stack.save(registries));
            items.add(item);
        }
        return items;
    }

    public static PersonalWarehouseData load(CompoundTag tag,
                                             HolderLookup.Provider registries) {
        PersonalWarehouseData data = new PersonalWarehouseData();
        for (var raw : tag.getList("warehouses", 10)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("player")) continue;
            Warehouse warehouse = Warehouse.createDefault();
            warehouse.setName(sanitizeName(entry.getString("name")));
            if (entry.contains("bins", 9)) {
                for (var binRaw : entry.getList("bins", 10)) {
                    CompoundTag binTag = (CompoundTag) binRaw;
                    WarehouseCategory category = WarehouseCategory.byId(
                        binTag.getString("category"));
                    int rows = clampRows(category, binTag.getInt("rows"));
                    warehouse.put(category, new Bin(rows,
                        loadItems(binTag.getList("items", 10), rows, registries)));
                }
            } else {
                // 0.3.5 and earlier stored one rows/items pair. Preserve it as MAIN.
                int rows = clampRows(WarehouseCategory.MAIN, entry.getInt("rows"));
                warehouse.put(WarehouseCategory.MAIN, new Bin(rows,
                    loadItems(entry.getList("items", 10), rows, registries)));
            }
            data.warehouses.put(entry.getUUID("player"), warehouse);
        }
        return data;
    }

    private static NonNullList<ItemStack> loadItems(ListTag items, int rows,
                                                     HolderLookup.Provider registries) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(COLUMNS * rows, ItemStack.EMPTY);
        for (var raw : items) {
            CompoundTag item = (CompoundTag) raw;
            int slot = item.getInt("slot");
            if (slot >= 0 && slot < stacks.size()) {
                stacks.set(slot, ItemStack.parseOptional(registries,
                    item.getCompound("stack")));
            }
        }
        return stacks;
    }

    private static int clampRows(WarehouseCategory category, int rows) {
        return Math.max(category.defaultRows(),
            Math.min(category.maximumRows(), rows));
    }

    public static final class Warehouse {
        private final EnumMap<WarehouseCategory, Bin> bins =
            new EnumMap<>(WarehouseCategory.class);
        private String name = DEFAULT_NAME;

        private static Warehouse createDefault() {
            Warehouse warehouse = new Warehouse();
            for (WarehouseCategory category : WarehouseCategory.values()) {
                warehouse.put(category, Bin.empty(category.defaultRows()));
            }
            return warehouse;
        }

        public String name() {
            return name;
        }

        private void setName(String name) {
            this.name = name == null ? DEFAULT_NAME : name;
        }

        public Bin bin(WarehouseCategory category) {
            WarehouseCategory resolved = category == null ? WarehouseCategory.MAIN : category;
            return bins.computeIfAbsent(resolved,
                ignored -> Bin.empty(resolved.defaultRows()));
        }

        private void put(WarehouseCategory category, Bin bin) {
            bins.put(category, bin);
        }

        public int usedCells(WarehouseCategory category) {
            int used = 0;
            for (ItemStack stack : bin(category).items()) {
                if (stack.isEmpty()) continue;
                ItemSize size = ModDataStorage.getCachedSizeFor(stack);
                boolean rotated = GridBackingStore.isRotated(stack);
                int width = rotated ? size.height() : size.width();
                int height = rotated ? size.width() : size.height();
                used += Math.max(1, width) * Math.max(1, height);
            }
            return Math.min(bin(category).capacity(), used);
        }
    }

    public static final class Bin {
        private int rows;
        private NonNullList<ItemStack> items;

        private Bin(int rows, NonNullList<ItemStack> items) {
            this.rows = rows;
            this.items = items;
        }

        private static Bin empty(int rows) {
            return new Bin(rows, NonNullList.withSize(COLUMNS * rows, ItemStack.EMPTY));
        }

        public int rows() {
            return rows;
        }

        public int capacity() {
            return rows * COLUMNS;
        }

        public NonNullList<ItemStack> items() {
            return items;
        }

        private void resize(int rows) {
            NonNullList<ItemStack> resized = NonNullList.withSize(COLUMNS * rows, ItemStack.EMPTY);
            for (int slot = 0; slot < Math.min(items.size(), resized.size()); slot++) {
                resized.set(slot, items.get(slot));
            }
            this.rows = rows;
            this.items = resized;
        }
    }
}
