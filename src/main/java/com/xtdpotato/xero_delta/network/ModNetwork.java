package com.xtdpotato.xero_delta.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    private static final String VERSION = "39";
    private static final String NOTICE_LOCK_ICON = "xero_delta:quality/lock";

    public static void register(final RegisterPayloadHandlersEvent event) {
        final var reg = event.registrar(VERSION);
        reg.playToClient(SyncDataPacket.TYPE, SyncDataPacket.STREAM_CODEC, SyncDataPacket::handle);
        reg.playToClient(CorpseRulesSyncPacket.TYPE, CorpseRulesSyncPacket.STREAM_CODEC,
            CorpseRulesSyncPacket::handle);
        reg.playToServer(CorpseRulesUpdatePacket.TYPE, CorpseRulesUpdatePacket.STREAM_CODEC,
            CorpseRulesUpdatePacket::handle);
        reg.playToClient(GridSyncPacket.TYPE, GridSyncPacket.STREAM_CODEC, GridSyncPacket::handle);
        reg.playToServer(ConfigUpdatePacket.TYPE, ConfigUpdatePacket.STREAM_CODEC, ConfigUpdatePacket::handle);
        reg.playToServer(CreativeRuleBatchPacket.TYPE, CreativeRuleBatchPacket.STREAM_CODEC,
            CreativeRuleBatchPacket::handle);
        reg.playToServer(GridActionPacket.TYPE, GridActionPacket.STREAM_CODEC, GridActionPacket::handleServer);
        reg.playToServer(CarriedRotationPacket.TYPE, CarriedRotationPacket.STREAM_CODEC, CarriedRotationPacket::handle);
        reg.playToServer(OverlaySlotSyncPacket.TYPE, OverlaySlotSyncPacket.STREAM_CODEC, OverlaySlotSyncPacket::handle);
        reg.playToServer(ItemGridConfigPacket.TYPE, ItemGridConfigPacket.STREAM_CODEC, ItemGridConfigPacket::handle);
        reg.playToServer(InspectRequestPacket.TYPE, InspectRequestPacket.STREAM_CODEC, InspectRequestPacket::handle);
        reg.playToClient(InspectAnimationPacket.TYPE, InspectAnimationPacket.STREAM_CODEC, InspectAnimationPacket::handle);
        reg.playToServer(InspectCancelRequestPacket.TYPE, InspectCancelRequestPacket.STREAM_CODEC, InspectCancelRequestPacket::handle);
        reg.playToClient(InspectCancelPacket.TYPE, InspectCancelPacket.STREAM_CODEC, InspectCancelPacket::handle);
        reg.playToClient(SafetyBoxPackReloadPacket.TYPE, SafetyBoxPackReloadPacket.STREAM_CODEC, SafetyBoxPackReloadPacket::handle);
        reg.playToServer(TradingActionPacket.TYPE, TradingActionPacket.STREAM_CODEC, TradingActionPacket::handle);
        reg.playToServer(SplitItemStackPacket.TYPE, SplitItemStackPacket.STREAM_CODEC,
            SplitItemStackPacket::handle);
        reg.playToServer(OpenNearbyWarehousePacket.TYPE, OpenNearbyWarehousePacket.STREAM_CODEC,
            OpenNearbyWarehousePacket::handle);
        reg.playToServer(OpenWarehouseCategoryPacket.TYPE,
            OpenWarehouseCategoryPacket.STREAM_CODEC, OpenWarehouseCategoryPacket::handle);
        reg.playToServer(WarehouseSourceTransferPacket.TYPE,
            WarehouseSourceTransferPacket.STREAM_CODEC, WarehouseSourceTransferPacket::handle);
        reg.playToServer(WarehouseSlotCategoryTransferPacket.TYPE,
            WarehouseSlotCategoryTransferPacket.STREAM_CODEC,
            WarehouseSlotCategoryTransferPacket::handle);
        reg.playToServer(WarehouseSafetyBoxTransferPacket.TYPE,
            WarehouseSafetyBoxTransferPacket.STREAM_CODEC,
            WarehouseSafetyBoxTransferPacket::handle);
        reg.playToServer(RenameWarehousePacket.TYPE,
            RenameWarehousePacket.STREAM_CODEC, RenameWarehousePacket::handle);
        reg.playToServer(CreativeListingPacket.TYPE, CreativeListingPacket.STREAM_CODEC, CreativeListingPacket::handle);
        reg.playToClient(TradingSyncPacket.TYPE, TradingSyncPacket.STREAM_CODEC, TradingSyncPacket::handle);
        reg.playToServer(MailActionPacket.TYPE, MailActionPacket.STREAM_CODEC, MailActionPacket::handle);
        reg.playToClient(MailSyncPacket.TYPE, MailSyncPacket.STREAM_CODEC, MailSyncPacket::handle);
        reg.playToClient(PlayerStatusPacket.TYPE, PlayerStatusPacket.STREAM_CODEC, PlayerStatusPacket::handle);
        reg.playToClient(TeamStatusPacket.TYPE, TeamStatusPacket.STREAM_CODEC, TeamStatusPacket::handle);
        reg.playToClient(XeroTitlePacket.TYPE, XeroTitlePacket.STREAM_CODEC, XeroTitlePacket::handle);
        reg.playToClient(GuiOpenPacket.TYPE, GuiOpenPacket.STREAM_CODEC, GuiOpenPacket::handle);
        reg.playToClient(DialogPacket.TYPE, DialogPacket.STREAM_CODEC, DialogPacket::handle);
        reg.playToClient(SafetyBoxAccessPacket.TYPE, SafetyBoxAccessPacket.STREAM_CODEC, SafetyBoxAccessPacket::handle);
        reg.playToClient(SafetyBoxSelectionResultPacket.TYPE,
            SafetyBoxSelectionResultPacket.STREAM_CODEC,
            SafetyBoxSelectionResultPacket::handle);
        reg.playToServer(SafetyBoxSelectPacket.TYPE, SafetyBoxSelectPacket.STREAM_CODEC, SafetyBoxSelectPacket::handle);
        reg.playToServer(SafetyBoxSkinSelectPacket.TYPE, SafetyBoxSkinSelectPacket.STREAM_CODEC,
            SafetyBoxSkinSelectPacket::handle);
        reg.playToServer(CurioSlotSwapPacket.TYPE, CurioSlotSwapPacket.STREAM_CODEC, CurioSlotSwapPacket::handle);
        reg.playToServer(CardHolderSelectPacket.TYPE, CardHolderSelectPacket.STREAM_CODEC,
            CardHolderSelectPacket::handle);
        reg.playToServer(EquippedStorageActionPacket.TYPE, EquippedStorageActionPacket.STREAM_CODEC, EquippedStorageActionPacket::handle);
        reg.playToServer(EquippedStorageShortcutPacket.TYPE,
            EquippedStorageShortcutPacket.STREAM_CODEC,
            EquippedStorageShortcutPacket::handle);
        reg.playToServer(InventorySourceToMenuPacket.TYPE,
            InventorySourceToMenuPacket.STREAM_CODEC,
            InventorySourceToMenuPacket::handle);
        reg.playToServer(InventorySourceToEquippedStoragePacket.TYPE,
            InventorySourceToEquippedStoragePacket.STREAM_CODEC,
            InventorySourceToEquippedStoragePacket::handle);
        reg.playToServer(InventorySourceQuickMovePacket.TYPE,
            InventorySourceQuickMovePacket.STREAM_CODEC,
            InventorySourceQuickMovePacket::handle);
        reg.playToServer(CarrierReplacePacket.TYPE,
            CarrierReplacePacket.STREAM_CODEC, CarrierReplacePacket::handle);
        reg.playToClient(KnifeAccessPacket.TYPE, KnifeAccessPacket.STREAM_CODEC, KnifeAccessPacket::handle);
        reg.playToServer(KnifeSelectPacket.TYPE, KnifeSelectPacket.STREAM_CODEC, KnifeSelectPacket::handle);
        reg.playToServer(PlayerLayoutSlotClickPacket.TYPE, PlayerLayoutSlotClickPacket.STREAM_CODEC, PlayerLayoutSlotClickPacket::handle);
        reg.playToServer(PlayerLayoutTogglePacket.TYPE, PlayerLayoutTogglePacket.STREAM_CODEC,
            PlayerLayoutTogglePacket::handle);
        reg.playToServer(PlayerEquipmentSlotClickPacket.TYPE, PlayerEquipmentSlotClickPacket.STREAM_CODEC, PlayerEquipmentSlotClickPacket::handle);
        reg.playToClient(DownedStatePacket.TYPE, DownedStatePacket.STREAM_CODEC, DownedStatePacket::handle);
        reg.playToClient(CombatFeedPacket.TYPE, CombatFeedPacket.STREAM_CODEC,
            CombatFeedPacket::handle);
        reg.playToClient(StaminaStatePacket.TYPE, StaminaStatePacket.STREAM_CODEC, StaminaStatePacket::handle);
        reg.playToServer(MedicalUseActionPacket.TYPE, MedicalUseActionPacket.STREAM_CODEC,
            MedicalUseActionPacket::handle);
        reg.playToServer(MedicalWheelUsePacket.TYPE, MedicalWheelUsePacket.STREAM_CODEC,
            MedicalWheelUsePacket::handle);
        reg.playToServer(ItemDetailActionPacket.TYPE, ItemDetailActionPacket.STREAM_CODEC,
            ItemDetailActionPacket::handle);
        reg.playToClient(ItemDetailActionResultPacket.TYPE,
            ItemDetailActionResultPacket.STREAM_CODEC,
            ItemDetailActionResultPacket::handle);
        reg.playToClient(MedicalUseStatePacket.TYPE, MedicalUseStatePacket.STREAM_CODEC,
            MedicalUseStatePacket::handle);
        reg.playToServer(CarryActionPacket.TYPE, CarryActionPacket.STREAM_CODEC, CarryActionPacket::handle);
        reg.playToServer(CorpseOpenPacket.TYPE, CorpseOpenPacket.STREAM_CODEC, CorpseOpenPacket::handle);
        reg.playToServer(CorpseStorageTransferPacket.TYPE,
            CorpseStorageTransferPacket.STREAM_CODEC,
            CorpseStorageTransferPacket::handle);
        reg.playToServer(DownedActionPacket.TYPE, DownedActionPacket.STREAM_CODEC, DownedActionPacket::handle);
        reg.playToServer(RescueHoldPacket.TYPE, RescueHoldPacket.STREAM_CODEC, RescueHoldPacket::handle);
        reg.playToClient(RescueRequestPulsePacket.TYPE, RescueRequestPulsePacket.STREAM_CODEC,
            RescueRequestPulsePacket::handle);
        reg.playToClient(DamageDirectionPacket.TYPE, DamageDirectionPacket.STREAM_CODEC,
            DamageDirectionPacket::handle);
        reg.playToClient(LootSearchStatePacket.TYPE, LootSearchStatePacket.STREAM_CODEC,
            LootSearchStatePacket::handle);
        reg.playToServer(LootSearchHoverPacket.TYPE, LootSearchHoverPacket.STREAM_CODEC,
            LootSearchHoverPacket::handle);
        reg.playToServer(BetterLootingStorageDropPacket.TYPE,
            BetterLootingStorageDropPacket.STREAM_CODEC,
            BetterLootingStorageDropPacket::handle);
        reg.playToServer(OpenGroundPackPacket.TYPE,
            OpenGroundPackPacket.STREAM_CODEC, OpenGroundPackPacket::handle);
    }

    public static void sendSyncToPlayer(ServerPlayer player, SyncDataPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToClient(ServerPlayer player, GridSyncPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void reloadSafetyBoxPack(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SafetyBoxPackReloadPacket());
    }

    /** Displays the same rich notice panel used by the /xero_title command. */
    public static void sendTranslatedNotice(ServerPlayer player, String translationKey) {
        PacketDistributor.sendToPlayer(player, new XeroTitlePacket(
            "top|center",
            "[icon:" + NOTICE_LOCK_ICON + "] [translate:" + translationKey + "]",
            60));
    }

    public static void sendTranslatedNoticePlain(ServerPlayer player, String translationKey) {
        PacketDistributor.sendToPlayer(player, new XeroTitlePacket(
            "top|center", "[translate:" + translationKey + "]", 60));
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }
}
