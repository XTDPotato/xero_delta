package com.xtdpotato.xero_delta.entity;

import com.mojang.authlib.properties.Property;
import com.xtdpotato.xero_delta.data.CorpseRulesData;
import com.xtdpotato.xero_delta.data.DownedManager;
import com.xtdpotato.xero_delta.data.ServerItemRules;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.tag.ModTags;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Optional;
import java.util.UUID;

/** Persistent, lootable body created when a player reaches yellow-down. */
public final class CorpseEntity extends Entity implements MenuProvider {
    public static final int SLOT_COUNT = 54;
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_VALUE =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_SIGNATURE =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> HEAD_ITEM =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> CHEST_ITEM =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> LEGS_ITEM =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> FEET_ITEM =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> KNIFE_ITEM =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> CHEST_RIG_ITEM =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> BACKPACK_ITEM =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> LOOT_BOX =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> OPENED =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CARRIER_ENTITY_ID =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> MOB_TYPE =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> MOB_DATA =
        SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT);
    private final boolean lootBoxEntity;
    private long raidEarnings;
    private boolean mobCorpse;
    private int lifeTicks;
    private boolean spilled;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        this(type, level, false);
    }

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level, boolean lootBoxEntity) {
        super(type, level);
        this.lootBoxEntity = lootBoxEntity;
        entityData.set(LOOT_BOX, lootBoxEntity);
        setNoGravity(false);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(OWNER_NAME, "");
        builder.define(SKIN_VALUE, "");
        builder.define(SKIN_SIGNATURE, "");
        builder.define(HEAD_ITEM, ItemStack.EMPTY);
        builder.define(CHEST_ITEM, ItemStack.EMPTY);
        builder.define(LEGS_ITEM, ItemStack.EMPTY);
        builder.define(FEET_ITEM, ItemStack.EMPTY);
        builder.define(KNIFE_ITEM, ItemStack.EMPTY);
        builder.define(CHEST_RIG_ITEM, ItemStack.EMPTY);
        builder.define(BACKPACK_ITEM, ItemStack.EMPTY);
        builder.define(LOOT_BOX, false);
        builder.define(OPENED, false);
        builder.define(CARRIER_ENTITY_ID, -1);
        builder.define(MOB_TYPE, "");
        builder.define(MOB_DATA, new CompoundTag());
    }

    public void setOwner(ServerPlayer player) {
        entityData.set(OWNER, Optional.of(player.getUUID()));
        entityData.set(OWNER_NAME, player.getGameProfile().getName());
        Property textures = player.getGameProfile().getProperties().get("textures").stream()
            .findFirst().orElse(null);
        entityData.set(SKIN_VALUE, textures == null ? "" : textures.value());
        entityData.set(SKIN_SIGNATURE,
            textures == null || textures.signature() == null ? "" : textures.signature());
        snapshotEquipment(player);
        snapshotCarriers(player);
        setCustomName(Component.translatable("entity.xero_delta.corpse.named",
            player.getGameProfile().getName()));
    }

    public void setMobOwner(LivingEntity entity) {
        mobCorpse = true;
        entityData.set(OWNER, Optional.empty());
        entityData.set(OWNER_NAME, entity.getDisplayName().getString());
        entityData.set(SKIN_VALUE, "");
        entityData.set(SKIN_SIGNATURE, "");
        entityData.set(MOB_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        CompoundTag appearance = new CompoundTag();
        entity.saveWithoutId(appearance);
        appearance.remove("Pos");
        appearance.remove("Motion");
        appearance.remove("Rotation");
        appearance.remove("UUID");
        appearance.remove("Passengers");
        entityData.set(MOB_DATA, appearance);
        snapshotEquipment(entity);
        snapshotCarriers(entity);
        clearMobCarrier(entity, "chest_rig");
        clearMobCarrier(entity, "backpack");
        setCustomName(Component.translatable("entity.xero_delta.corpse.named",
            entity.getDisplayName()));
    }

    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public String ownerName() { return entityData.get(OWNER_NAME); }
    public String skinValue() { return entityData.get(SKIN_VALUE); }
    public String skinSignature() { return entityData.get(SKIN_SIGNATURE); }
    public String mobTypeId() { return entityData.get(MOB_TYPE); }
    public CompoundTag mobData() { return entityData.get(MOB_DATA).copy(); }
    public ItemStack equipment(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> entityData.get(HEAD_ITEM);
            case CHEST -> entityData.get(CHEST_ITEM);
            case LEGS -> entityData.get(LEGS_ITEM);
            case FEET -> entityData.get(FEET_ITEM);
            default -> ItemStack.EMPTY;
        };
    }
    public SimpleContainer inventory() { return inventory; }
    public ItemStack knifeItem() { return entityData.get(KNIFE_ITEM); }
    public ItemStack chestRigItem() { return entityData.get(CHEST_RIG_ITEM); }
    public ItemStack backpackItem() { return entityData.get(BACKPACK_ITEM); }
    public boolean isLootBox() { return entityData.get(LOOT_BOX); }
    public boolean wasOpened() { return entityData.get(OPENED); }

    /** Converts the yellow-down body in-place so its inventory and search state stay intact. */
    public void convertToLootBox() {
        entityData.set(LOOT_BOX, true);
        entityData.set(OPENED, false);
        setCustomName(Component.translatable("entity.xero_delta.loot_box.named",
            ownerName().isBlank() ? "?" : ownerName()));
    }

    public void openFromInteraction(ServerPlayer player) {
        entityData.set(OPENED, true);
        player.openMenu(this, buffer -> buffer.writeVarInt(getId()));
    }
    public synchronized long raidEarnings() { return raidEarnings; }
    public synchronized void setRaidEarnings(long value) {
        raidEarnings = Math.max(0L, value);
    }
    public synchronized long takeRaidEarnings() {
        long value = raidEarnings;
        raidEarnings = 0L;
        return value;
    }

    private void snapshotEquipment(ServerPlayer player) {
        snapshotEquipment((LivingEntity) player);
        entityData.set(KNIFE_ITEM, player.getInventory().getItem(3).copy());
    }

    private void snapshotCarriers(LivingEntity entity) {
        ItemStack chestRig = equippedCurio(entity, "chest_rig");
        ItemStack backpack = equippedCurio(entity, "backpack");
        inventory.setItem(CorpseMenu.CHEST_RIG_SLOT, chestRig.copy());
        inventory.setItem(CorpseMenu.BACKPACK_SLOT, backpack.copy());
        entityData.set(CHEST_RIG_ITEM, chestRig);
        entityData.set(BACKPACK_ITEM, backpack);
    }

    private static ItemStack equippedCurio(LivingEntity entity, String identifier) {
        try {
            return CuriosApi.getCuriosInventory(entity)
                .flatMap(curios -> curios.getStacksHandler(identifier))
                .filter(handler -> handler.getSlots() > 0)
                .map(handler -> handler.getStacks().getStackInSlot(0).copy())
                .orElse(ItemStack.EMPTY);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void clearMobCarrier(LivingEntity entity, String identifier) {
        if (!(entity instanceof net.minecraft.world.entity.Mob)) return;
        try {
            CuriosApi.getCuriosInventory(entity).ifPresent(curios ->
                curios.getStacksHandler(identifier).ifPresent(handler -> {
                    if (handler.getSlots() <= 0) return;
                    handler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                    handler.update();
                }));
        } catch (RuntimeException ignored) {
        }
    }

    public void setCarrierItem(String identifier, ItemStack stack) {
        EntityDataAccessor<ItemStack> accessor = carrierAccessor(identifier);
        if (accessor != null) {
            entityData.set(accessor, stack == null ? ItemStack.EMPTY : stack.copy());
        }
    }

    public void syncCarrierSlot(int corpseSlot) {
        if (corpseSlot != CorpseMenu.CHEST_RIG_SLOT
            && corpseSlot != CorpseMenu.BACKPACK_SLOT) return;
        ItemStack stack = inventory.getItem(corpseSlot);
        if (!isCarrierStackForSlot(corpseSlot, stack)) stack = ItemStack.EMPTY;
        setCarrierItem(corpseSlot == CorpseMenu.CHEST_RIG_SLOT ? "chest_rig" : "backpack", stack);
    }

    public static boolean isCarrierStackForSlot(int inventorySlot, ItemStack stack) {
        String expected = inventorySlot == CorpseMenu.CHEST_RIG_SLOT ? "chest_rig" : "backpack";
        if (stack.getItem() instanceof DeltaPackItem pack) {
            return expected.equals(pack.slotIdentifier());
        }
        return "chest_rig".equals(expected) ? stack.is(ModTags.CHEST_RIG)
            : stack.is(ModTags.BACKPACK);
    }

    public synchronized boolean tryEquipCarrier(int corpseSlot, ItemStack stack) {
        if (corpseSlot != CorpseMenu.CHEST_RIG_SLOT
            && corpseSlot != CorpseMenu.BACKPACK_SLOT) return false;
        if (stack == null || stack.isEmpty() || !inventory.getItem(corpseSlot).isEmpty()) {
            return false;
        }
        String expected = corpseSlot == CorpseMenu.CHEST_RIG_SLOT ? "chest_rig"
            : "backpack";
        if (!isCarrierStackForSlot(corpseSlot, stack)) return false;
        inventory.setItem(corpseSlot, stack.copyWithCount(1));
        syncCarrierSlot(corpseSlot);
        return true;
    }

    private static EntityDataAccessor<ItemStack> carrierAccessor(String identifier) {
        return switch (identifier) {
            case "chest_rig" -> CHEST_RIG_ITEM;
            case "backpack" -> BACKPACK_ITEM;
            default -> null;
        };
    }

    private void snapshotEquipment(LivingEntity entity) {
        entityData.set(HEAD_ITEM, entity.getItemBySlot(EquipmentSlot.HEAD).copy());
        entityData.set(CHEST_ITEM, entity.getItemBySlot(EquipmentSlot.CHEST).copy());
        entityData.set(LEGS_ITEM, entity.getItemBySlot(EquipmentSlot.LEGS).copy());
        entityData.set(FEET_ITEM, entity.getItemBySlot(EquipmentSlot.FEET).copy());
    }

    /** Moves a mob's visible equipment into the authoritative corpse slots. */
    public synchronized java.util.List<ItemStack> captureMobEquipment(LivingEntity entity) {
        java.util.List<ItemStack> captured = new java.util.ArrayList<>();
        captureEquipmentSlot(entity, EquipmentSlot.HEAD, CorpseMenu.HELMET_SLOT, captured);
        captureEquipmentSlot(entity, EquipmentSlot.CHEST, CorpseMenu.CHEST_SLOT, captured);
        captureWeaponSlot(entity.getMainHandItem(), CorpseMenu.PRIMARY_ONE_SLOT, captured);
        captureWeaponSlot(entity.getOffhandItem(), CorpseMenu.PRIMARY_TWO_SLOT, captured);
        return java.util.List.copyOf(captured);
    }

    private void captureEquipmentSlot(LivingEntity entity, EquipmentSlot equipmentSlot,
                                      int corpseSlot, java.util.List<ItemStack> captured) {
        ItemStack stack = entity.getItemBySlot(equipmentSlot);
        if (stack.isEmpty() || !inventory.getItem(corpseSlot).isEmpty()) return;
        inventory.setItem(corpseSlot, stack.copy());
        captured.add(stack.copy());
        entity.setItemSlot(equipmentSlot, ItemStack.EMPTY);
    }

    private void captureWeaponSlot(ItemStack stack, int preferredSlot,
                                   java.util.List<ItemStack> captured) {
        if (stack.isEmpty()) return;
        int slot = PlayerLayoutSlotRules.isTaczGun(stack)
            && PlayerLayoutSlotRules.isHandgun(stack) ? CorpseMenu.SIDEARM_SLOT : preferredSlot;
        if (!inventory.getItem(slot).isEmpty()) return;
        inventory.setItem(slot, stack.copy());
        captured.add(stack.copy());
        stack.setCount(0);
    }

    /** Transactional placement used by the custom enlarged corpse slot UI. */
    public synchronized ItemStack placeInPresentationSlot(int slot, ItemStack source) {
        if (source == null || source.isEmpty() || slot < 0
            || slot >= CorpseMenu.RESERVED_CORPSE_SLOT) return source == null
                ? ItemStack.EMPTY : source.copy();
        ItemStack existing = inventory.getItem(slot);
        if (existing.isEmpty()) {
            inventory.setItem(slot, source.copy());
            syncCarrierSlot(slot);
            return ItemStack.EMPTY;
        }
        if (ItemStack.isSameItemSameComponents(existing, source)) {
            int room = Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize())
                - existing.getCount();
            int moved = Math.min(Math.max(0, room), source.getCount());
            if (moved > 0) {
                existing.grow(moved);
                ItemStack remainder = source.copy();
                remainder.shrink(moved);
                inventory.setChanged();
                return remainder;
            }
        }
        return source.copy();
    }

    public ItemStack addStack(ItemStack stack) {
        return addStackToStorage(stack);
    }

    /** Inserts loose corpse loot into the equipment that is actually worn. */
    public synchronized ItemStack addStackToStorage(ItemStack stack) {
        ItemStack remainder = stack == null ? ItemStack.EMPTY : stack.copy();
        remainder = insertIntoCarrier(CorpseMenu.CHEST_RIG_SLOT, remainder);
        remainder = insertIntoCarrier(CorpseMenu.BACKPACK_SLOT, remainder);
        for (int slot = CorpseMenu.POCKET_START;
             slot <= CorpseMenu.POCKET_END && !remainder.isEmpty(); slot++) {
            ItemStack stored = inventory.getItem(slot);
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, remainder)) {
                int room = Math.min(stored.getMaxStackSize(), inventory.getMaxStackSize())
                    - stored.getCount();
                int moved = Math.min(Math.max(0, room), remainder.getCount());
                if (moved > 0) {
                    stored.grow(moved);
                    remainder.shrink(moved);
                    inventory.setChanged();
                }
            }
        }
        for (int slot = CorpseMenu.POCKET_START;
             slot <= CorpseMenu.POCKET_END && !remainder.isEmpty(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) continue;
            var size = ServerItemRules.getSizeFor(remainder);
            if (size.width() != 1 || size.height() != 1) break;
            inventory.setItem(slot, remainder.copy());
            remainder.setCount(0);
        }
        return remainder;
    }

    private ItemStack insertIntoCarrier(int carrierSlot, ItemStack source) {
        if (source.isEmpty()) return source;
        ItemStack carrier = inventory.getItem(carrierSlot);
        if (!(carrier.getItem() instanceof DeltaPackItem pack)) return source;
        String identifier = pack.slotIdentifier();
        GridBackingStore store = new GridBackingStore(carrier,
            pack.gridWidth(), pack.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack));
        ItemStack remainder = source.copy();
        boolean changed = false;
        for (int index = 0; index < store.getSize() && !remainder.isEmpty(); index++) {
            int x = index % store.getWidth();
            int y = index / store.getWidth();
            if (store.canStackAt(x, y, remainder)) changed |= store.stackInto(x, y, remainder);
        }
        while (!remainder.isEmpty()) {
            var placement = store.findFreePlacement(remainder);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE) break;
            int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
            ItemStack placed = remainder.copyWithCount(moved);
            if (!store.place(placement.x(), placement.y(), placed, placement.rotated())) break;
            remainder.shrink(moved);
            changed = true;
        }
        if (changed) {
            inventory.setChanged();
            syncCarrierSlot(carrierSlot);
        }
        return remainder;
    }

    /** Resolves the live grid stored by one worn corpse carrier. */
    public synchronized GridBackingStore carrierStore(int carrierSlot) {
        ItemStack carrier = inventory.getItem(carrierSlot);
        if (!(carrier.getItem() instanceof DeltaPackItem pack)) return null;
        String expected = carrierSlot == CorpseMenu.CHEST_RIG_SLOT ? "chest_rig"
            : carrierSlot == CorpseMenu.BACKPACK_SLOT ? "backpack" : "";
        if (!expected.equals(pack.slotIdentifier())) return null;
        return new GridBackingStore(carrier, pack.gridWidth(), pack.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage(expected, stack));
    }

    public synchronized void carrierContentsChanged(int carrierSlot) {
        inventory.setChanged();
        syncCarrierSlot(carrierSlot);
    }

    /** Flushes menu-side mutations before a corpse menu is closed. */
    public synchronized void commitInventoryChanges() {
        inventory.setChanged();
        syncCarrierSlot(CorpseMenu.CHEST_RIG_SLOT);
        syncCarrierSlot(CorpseMenu.BACKPACK_SLOT);
    }

    /** Migrates old overflow slots into worn storage before the corpse is opened. */
    public synchronized void migrateLegacyOverflow() {
        if (level().isClientSide) return;
        for (int slot = CorpseMenu.OVERFLOW_START;
             slot < inventory.getContainerSize(); slot++) {
            ItemStack stored = inventory.removeItemNoUpdate(slot);
            if (stored.isEmpty()) continue;
            ItemStack remainder = addStackToStorage(stored);
            if (!remainder.isEmpty()) {
                ItemEntity dropped = new ItemEntity(level(), getX(), getY() + 0.2D,
                    getZ(), remainder);
                dropped.setDefaultPickUpDelay();
                level().addFreshEntity(dropped);
            }
        }
    }

    public synchronized ItemStack addStackAt(int slot, ItemStack stack) {
        if (slot < 0 || slot >= inventory.getContainerSize() || stack.isEmpty()) {
            return stack.copy();
        }
        if (slot == CorpseMenu.CHEST_RIG_SLOT || slot == CorpseMenu.BACKPACK_SLOT) {
            if (!isCarrierStackForSlot(slot, stack)) return stack.copy();
        }
        if (!inventory.getItem(slot).isEmpty()) return addStackFrom(stack, 13);
        inventory.setItem(slot, stack.copy());
        syncCarrierSlot(slot);
        return ItemStack.EMPTY;
    }

    public synchronized ItemStack addStackFrom(ItemStack stack, int startSlot) {
        return addStackToStorage(stack);
    }
    public void restoreRemaining(ServerPlayer player) {
        restoreInventorySlot(player, CorpseMenu.PRIMARY_ONE_SLOT,
            player.getInventory().items, 0);
        restoreInventorySlot(player, CorpseMenu.PRIMARY_TWO_SLOT,
            player.getInventory().items, 1);
        restoreInventorySlot(player, CorpseMenu.SIDEARM_SLOT,
            player.getInventory().items, 2);
        for (int corpseSlot = CorpseMenu.POCKET_START;
             corpseSlot <= CorpseMenu.POCKET_END; corpseSlot++) {
            restoreInventorySlot(player, corpseSlot, player.getInventory().items,
                4 + corpseSlot - CorpseMenu.POCKET_START);
        }
        // Inventory armor order is feet, legs, chest, head.
        restoreInventorySlot(player, CorpseMenu.HELMET_SLOT,
            player.getInventory().armor, 3);
        restoreInventorySlot(player, CorpseMenu.CHEST_SLOT,
            player.getInventory().armor, 2);
        restoreCurioSlot(player, CorpseMenu.CHEST_RIG_SLOT, "chest_rig");
        restoreCurioSlot(player, CorpseMenu.BACKPACK_SLOT, "backpack");

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == CorpseMenu.RESERVED_CORPSE_SLOT) continue;
            ItemStack stored = inventory.removeItemNoUpdate(slot);
            if (stored.isEmpty()) continue;
            ItemStack stack = stored.copy();
            player.getInventory().placeItemBackInInventory(stack);
            if (!stack.isEmpty()) player.drop(stack, false);
        }
        syncCarrierSlot(CorpseMenu.CHEST_RIG_SLOT);
        syncCarrierSlot(CorpseMenu.BACKPACK_SLOT);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private void restoreInventorySlot(ServerPlayer player, int corpseSlot,
                                      net.minecraft.core.NonNullList<ItemStack> target,
                                      int targetSlot) {
        if (targetSlot < 0 || targetSlot >= target.size() || !target.get(targetSlot).isEmpty()) return;
        ItemStack stored = inventory.getItem(corpseSlot);
        if (stored.isEmpty()) return;
        target.set(targetSlot, stored.copy());
        inventory.setItem(corpseSlot, ItemStack.EMPTY);
    }

    private void restoreCurioSlot(ServerPlayer player, int corpseSlot, String identifier) {
        ItemStack stored = inventory.getItem(corpseSlot);
        if (stored.isEmpty()) return;
        CuriosApi.getCuriosInventory(player).ifPresent(curios ->
            curios.getStacksHandler(identifier).ifPresent(handler -> {
                if (handler.getSlots() <= 0 || !handler.getStacks()
                    .getStackInSlot(0).isEmpty()) return;
                curios.setEquippedCurio(identifier, 0, stored.copy());
                handler.update();
                inventory.setItem(corpseSlot, ItemStack.EMPTY);
                syncCarrierSlot(corpseSlot);
            }));
    }

    @Override
    public void tick() {
        super.tick();
        Player carrier = carryingPlayer();
        if (level().isClientSide && carrier != null) {
            // Custom entities do not always receive the vanilla passenger
            // position update on the client. Keep the rendered corpse at the
            // carrier's shoulder instead of leaving it at its pickup point.
            setPos(carrier.getX(), carrier.getY() + 0.5D, carrier.getZ());
            setYRot(carrier.getYRot());
            setXRot(0.0F);
        }
        tickGravity();
        if (level().isClientSide || spilled || isRemoved()) return;
        lifeTicks++;
        int lifetime = CorpseRulesData.get(level().getServer()).lifetimeTicks();
        if (lifeTicks >= lifetime) spillAndDiscard();
    }

    public void setCarrierEntityId(int entityId) {
        entityData.set(CARRIER_ENTITY_ID, entityId);
    }

    public Player carryingPlayer() {
        if (getVehicle() instanceof Player player) return player;
        int entityId = entityData.get(CARRIER_ENTITY_ID);
        if (entityId < 0) return null;
        Entity carrier = level().getEntity(entityId);
        return carrier instanceof Player player ? player : null;
    }

    private void tickGravity() {
        if (isPassenger() || isNoGravity() || isRemoved()) return;
        var movement = getDeltaMovement();
        if (!onGround()) movement = movement.add(0.0D, -0.04D, 0.0D);
        move(MoverType.SELF, movement);
        double drag = onGround() ? 0.72D : 0.98D;
        setDeltaMovement(movement.x * drag, onGround() ? 0.0D : movement.y * 0.98D,
            movement.z * drag);
    }

    /** Moves every remaining stack into the world exactly once before removal. */
    public synchronized void spillAndDiscard() {
        if (spilled || level().isClientSide) return;
        spilled = true;
        for (ItemStack stored : inventory.removeAllItems()) {
            if (stored.isEmpty()) continue;
            ItemEntity dropped = new ItemEntity(level(), getX(), getY() + 0.2D, getZ(), stored.copy());
            dropped.setDefaultPickUpDelay();
            level().addFreshEntity(dropped);
        }
        discard();
    }

    public boolean isMobCorpse() {
        return mobCorpse;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || carryingPlayer() != null
            || !mobCorpse || spilled || isRemoved()) return false;
        if (!CorpseRulesData.get(level().getServer()).mobCorpsesAttackable()) return false;
        spillAndDiscard();
        return true;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // Corpses and loot boxes deliberately use Xero Delta's configurable F interaction.
        return InteractionResult.PASS;
    }

    @Override
    public Component getDisplayName() {
        if (isLootBox() && ownerName().isBlank()) {
            return getCustomName() != null ? getCustomName()
                : Component.translatable("entity.xero_delta.loot_box");
        }
        return Component.translatable(isLootBox()
                ? "entity.xero_delta.loot_box.named"
                : "entity.xero_delta.corpse.named",
            ownerName().isBlank() ? "?" : ownerName());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        migrateLegacyOverflow();
        return new CorpseMenu(containerId, playerInventory, inventory, this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        entityData.set(OWNER_NAME, tag.getString("OwnerName"));
        entityData.set(SKIN_VALUE, tag.getString("SkinValue"));
        entityData.set(SKIN_SIGNATURE, tag.getString("SkinSignature"));
        entityData.set(MOB_TYPE, tag.getString("MobType"));
        entityData.set(MOB_DATA, tag.contains("MobData", Tag.TAG_COMPOUND)
            ? tag.getCompound("MobData").copy() : new CompoundTag());
        readEquipment(tag, "HeadItem", HEAD_ITEM);
        readEquipment(tag, "ChestItem", CHEST_ITEM);
        readEquipment(tag, "LegsItem", LEGS_ITEM);
        readEquipment(tag, "FeetItem", FEET_ITEM);
        readEquipment(tag, "KnifeItem", KNIFE_ITEM);
        inventory.fromTag(tag.getList("Items", Tag.TAG_COMPOUND), registryAccess());
        readCarrier(tag, "ChestRigItem", CHEST_RIG_ITEM, CorpseMenu.CHEST_RIG_SLOT);
        readCarrier(tag, "BackpackItem", BACKPACK_ITEM, CorpseMenu.BACKPACK_SLOT);
        raidEarnings = Math.max(0L, tag.getLong("RaidEarnings"));
        mobCorpse = tag.getBoolean("MobCorpse");
        lifeTicks = Math.max(0, tag.getInt("LifeTicks"));
        spilled = tag.getBoolean("Spilled");
        // /summon loads an empty tag; the dedicated box type must keep its default.
        entityData.set(LOOT_BOX, lootBoxEntity || tag.getBoolean("LootBox"));
        entityData.set(OPENED, tag.getBoolean("Opened"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = ownerId();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName());
        tag.putString("SkinValue", skinValue());
        tag.putString("SkinSignature", skinSignature());
        if (!mobTypeId().isBlank()) tag.putString("MobType", mobTypeId());
        if (!mobData().isEmpty()) tag.put("MobData", mobData());
        writeEquipment(tag, "HeadItem", HEAD_ITEM);
        writeEquipment(tag, "ChestItem", CHEST_ITEM);
        writeEquipment(tag, "LegsItem", LEGS_ITEM);
        writeEquipment(tag, "FeetItem", FEET_ITEM);
        writeEquipment(tag, "KnifeItem", KNIFE_ITEM);
        // The inventory is authoritative while a menu is open; mirror carrier slots
        // immediately before serializing so a just-equipped item survives a save.
        syncCarrierSlot(CorpseMenu.CHEST_RIG_SLOT);
        syncCarrierSlot(CorpseMenu.BACKPACK_SLOT);
        writeEquipment(tag, "ChestRigItem", CHEST_RIG_ITEM);
        writeEquipment(tag, "BackpackItem", BACKPACK_ITEM);
        tag.put("Items", inventory.createTag(registryAccess()));
        if (raidEarnings > 0L) tag.putLong("RaidEarnings", raidEarnings);
        tag.putBoolean("MobCorpse", mobCorpse);
        tag.putInt("LifeTicks", Math.max(0, lifeTicks));
        if (spilled) tag.putBoolean("Spilled", true);
        if (isLootBox()) tag.putBoolean("LootBox", true);
        if (wasOpened()) tag.putBoolean("Opened", true);
    }

    private void readEquipment(CompoundTag tag, String key, EntityDataAccessor<ItemStack> accessor) {
        entityData.set(accessor, tag.contains(key, Tag.TAG_COMPOUND)
            ? ItemStack.parseOptional(registryAccess(), tag.getCompound(key)) : ItemStack.EMPTY);
    }

    private void writeEquipment(CompoundTag tag, String key, EntityDataAccessor<ItemStack> accessor) {
        ItemStack stack = entityData.get(accessor);
        if (!stack.isEmpty()) tag.put(key, stack.save(registryAccess()));
    }

    private void readCarrier(CompoundTag tag, String key,
                             EntityDataAccessor<ItemStack> accessor, int inventorySlot) {
        ItemStack stored = inventory.getItem(inventorySlot);
        ItemStack serialized = tag.contains(key, Tag.TAG_COMPOUND)
            ? ItemStack.parseOptional(registryAccess(), tag.getCompound(key))
            : ItemStack.EMPTY;
        boolean storedValid = isCarrierStackForSlot(inventorySlot, stored);
        boolean serializedValid = isCarrierStackForSlot(inventorySlot, serialized);
        ItemStack authoritative = storedValid ? stored : serializedValid ? serialized : ItemStack.EMPTY;
        if (!storedValid) {
            inventory.setItem(inventorySlot, authoritative.isEmpty() ? ItemStack.EMPTY : authoritative.copy());
        }
        entityData.set(accessor, authoritative.copy());
    }
}
