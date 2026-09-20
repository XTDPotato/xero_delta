package com.xtdpotato.xero_delta.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandNamesTest {
    @Test
    void modernizesLegacyCommandRoots() {
        assertEquals("/xero weight auto", CommandNames.modernize("/xero_weight auto"));
        assertEquals("/xero gui open @s inventory",
            CommandNames.modernize("/xero_gui open @s inventory"));
        assertEquals("/xero market buy abc 2",
            CommandNames.modernize("/xero_trading buy abc 2"));
    }
}
