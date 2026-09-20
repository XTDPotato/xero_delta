package com.xtdpotato.xero_delta.mail;

import com.xtdpotato.xero_delta.network.MailSyncPacket;
import com.xtdpotato.xero_delta.trading.TradingMarketData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class MailService {
    private MailService() {}

    public static MailMessage sendTradeSale(MinecraftServer server, UUID sellerId, String sellerName,
                                            String buyerName, ItemStack soldStack, long proceeds,
                                            String publicOrderId) {
        String itemName = soldStack.getHoverName().getString();
        String text = "&f你的 &6" + itemName + " x" + soldStack.getCount() + "&f 已由 &b" + buyerName
            + "&f 购买。\n&7订单号：" + publicOrderId
            + "\n&f售款不会自动入账，请领取下方的货币附件。";
        MailMessage message = MailData.get(server).send(sellerId, sellerName, "Xero Delta 交易行",
            "交易行出售成功", text, List.of(MailAttachment.currency(proceeds)));
        notifyRecipient(server, sellerId);
        return message;
    }

    public static int sendCustom(MinecraftServer server, Collection<ServerPlayer> recipients,
                                 MailPayloadParser.Draft draft) {
        int sent = 0;
        for (ServerPlayer recipient : recipients) {
            MailData.get(server).send(recipient.getUUID(), recipient.getGameProfile().getName(),
                draft.sender(), draft.title(), draft.text(), draft.attachments());
            notifyRecipient(server, recipient.getUUID());
            sent++;
        }
        return sent;
    }

    public static int sendFromComposer(ServerPlayer sender, String recipientText,
                                       MailPayloadParser.Draft draft) {
        return sendFromComposer(sender, recipientText, draft, "");
    }

    public static int sendFromComposer(ServerPlayer sender, String recipientText,
                                       MailPayloadParser.Draft draft, String payloadJson) {
        MinecraftServer server = sender.server;
        String raw = recipientText == null ? "" : recipientText.trim();
        UUID dispatchId = UUID.randomUUID();
        if (raw.equals("*") || raw.equalsIgnoreCase("@a")) {
            MailData data = MailData.get(server);
            data.sendBroadcast(dispatchId, draft.sender(), draft.title(), draft.text(), draft.attachments());
            data.recordSent(sender.getUUID(), dispatchId, draft.title(), "*", payloadJson);
            int delivered = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                delivered += data.deliverBroadcasts(player.getUUID(), player.getGameProfile().getName());
                notifyRecipient(server, player.getUUID());
            }
            return Math.max(1, delivered);
        }
        java.util.Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String token : raw.split("[,，;；]")) {
            if (!token.isBlank()) names.add(token.trim());
        }
        int sent = 0;
        for (String name : names) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            UUID recipientId;
            String recipientName;
            if (online != null) {
                recipientId = online.getUUID(); recipientName = online.getGameProfile().getName();
            } else {
                var cached = server.getProfileCache().get(name);
                if (cached.isPresent()) {
                    recipientId = cached.get().getId(); recipientName = cached.get().getName();
                } else {
                    recipientId = TradingMarketData.get(server).unresolvedAccount(name); recipientName = name;
                }
            }
            MailData.get(server).sendWithId(dispatchId, recipientId, recipientName, draft.sender(), draft.title(),
                draft.text(), draft.attachments());
            notifyRecipient(server, recipientId); sent++;
        }
        if (sent > 0) MailData.get(server).recordSent(sender.getUUID(), dispatchId,
            draft.title(), raw, payloadJson);
        return sent;
    }

    public static int resendFromHistory(ServerPlayer sender, UUID sentId) {
        MailData.SentMail sent = MailData.get(sender.server).sent(sender.getUUID(), sentId);
        if (sent == null || !sent.retracted() || sent.payloadJson().isBlank()) return 0;
        MailPayloadParser.Draft draft = MailPayloadParser.parse(sent.payloadJson(),
            sender.getGameProfile().getName());
        return sendFromComposer(sender, sent.recipients(), draft, sent.payloadJson());
    }

    public static List<ServerPlayer> resolveOnlineRecipients(ServerPlayer sender, String names) {
        List<ServerPlayer> result = new ArrayList<>();
        String value = names == null ? "" : names.trim();
        if (value.equals("@a") || value.equals("*")) {
            return List.copyOf(sender.server.getPlayerList().getPlayers());
        }
        for (String raw : value.split("[,，;；]")) {
            String name = raw.trim();
            if (name.isEmpty()) continue;
            ServerPlayer player = sender.server.getPlayerList().getPlayerByName(name);
            if (player != null && !result.contains(player)) result.add(player);
        }
        return List.copyOf(result);
    }

    public static void notifyRecipient(MinecraftServer server, UUID recipientId) {
        ServerPlayer online = server.getPlayerList().getPlayer(recipientId);
        if (online != null) {
            PacketDistributor.sendToPlayer(online,
                MailSyncPacket.snapshot(online, false, null, "", true, 0L));
        }
    }
}
