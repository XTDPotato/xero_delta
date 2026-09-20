package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.MailSyncPacket;
import com.xtdpotato.xero_delta.screen.MailScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.UUID;

public final class MailClientState {
    public static final MailClientState INSTANCE = new MailClientState();
    private volatile List<MailSyncPacket.MessageView> messages = List.of();
    private volatile List<MailSyncPacket.SentView> sentMessages = List.of();
    private volatile int unreadCount;
    private volatile int mailboxCount;
    private volatile int mailboxLimit = 200;
    private volatile boolean canCompose;
    private volatile String resultMessage = "";
    private volatile boolean success = true;
    private volatile long resultValue;
    private volatile long revision;
    private volatile long openRequestedUntil;
    private Screen requestedReturnScreen;

    private MailClientState() {}

    public synchronized void update(MailSyncPacket packet) {
        messages = List.copyOf(packet.messages()); sentMessages = List.copyOf(packet.sentMessages());
        unreadCount = packet.unreadCount();
        mailboxCount = packet.mailboxCount(); mailboxLimit = packet.mailboxLimit();
        canCompose = packet.canCompose(); resultMessage = packet.resultMessage(); success = packet.success();
        resultValue = packet.resultValue(); revision++;
        Minecraft minecraft = Minecraft.getInstance();
        boolean requestedOpen = System.currentTimeMillis() <= openRequestedUntil;
        if (packet.openScreen() && (requestedOpen || minecraft.screen == null)) {
            openRequestedUntil = 0L;
            Screen returnScreen = requestedOpen ? requestedReturnScreen : minecraft.screen;
            requestedReturnScreen = null;
            minecraft.setScreen(new MailScreen(returnScreen, packet.selectedMailId()));
        } else if (minecraft.screen instanceof MailScreen screen) {
            screen.acceptServerSelection(packet.selectedMailId());
        }
    }

    public List<MailSyncPacket.MessageView> messages() { return messages; }
    public List<MailSyncPacket.SentView> sentMessages() { return sentMessages; }
    public int unreadCount() { return unreadCount; }
    public int mailboxCount() { return mailboxCount; }
    public int mailboxLimit() { return mailboxLimit; }
    public boolean canCompose() { return canCompose; }
    public String resultMessage() { return resultMessage; }
    public boolean success() { return success; }
    public long resultValue() { return resultValue; }
    public long revision() { return revision; }

    /** Marks the next OPEN response as UI-authorized so later syncs cannot steal another screen. */
    public void requestOpen() {
        requestedReturnScreen = Minecraft.getInstance().screen;
        openRequestedUntil = System.currentTimeMillis() + 5_000L;
    }

    public MailSyncPacket.MessageView find(UUID id) {
        if (id == null) return null;
        for (var message : messages) if (message.id().equals(id)) return message;
        return null;
    }
}
