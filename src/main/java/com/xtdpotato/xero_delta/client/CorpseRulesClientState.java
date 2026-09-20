package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.CorpseRulesSyncPacket;
import com.xtdpotato.xero_delta.data.CorpseRules;
import com.xtdpotato.xero_delta.data.CorpseRulesConfig;

import java.util.LinkedHashSet;
import java.util.Set;

/** Last server-authoritative corpse-generation settings visible to the client. */
public final class CorpseRulesClientState {
    public static final CorpseRulesClientState INSTANCE = new CorpseRulesClientState();
    private CorpseRules.Settings settings = CorpseRulesConfig.load();
    private boolean editable;

    private CorpseRulesClientState() {}

    public void update(CorpseRulesSyncPacket packet) {
        settings = packet.settings();
        editable = packet.editable();
        CorpseRulesConfig.save(settings);
    }

    public CorpseRules.Settings settings() { return settings; }
    public Set<String> entityIds() { return settings.entityIds(); }
    public boolean chestRigEnabled() { return hasAny(true); }
    public boolean backpackEnabled() { return hasAny(false); }
    public String chestRigItemId() { return firstId(true, CorpseRules.DEFAULT_CHEST_RIG_ID); }
    public String backpackItemId() { return firstId(false, CorpseRules.DEFAULT_BACKPACK_ID); }
    public boolean editable() { return editable; }

    private boolean hasAny(boolean chestRig) {
        return settings.entityIds().stream().anyMatch(id ->
            !settings.candidates(id, chestRig).isEmpty());
    }

    private String firstId(boolean chestRig, String fallback) {
        for (String entityId : settings.entityIds()) {
            var values = settings.candidates(entityId, chestRig);
            if (!values.isEmpty()) return values.getFirst().itemId();
        }
        return fallback;
    }
}
