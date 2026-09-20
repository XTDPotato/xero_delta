package com.xtdpotato.xero_delta.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailCommandFormatterTest {
    @Test
    void convertsBroadcastRecipientToAllPlayersSelector() {
        assertEquals("/xero mail send @a {\"title\":\"Notice\"}",
            MailCommandFormatter.format("*", "{\"title\":\"Notice\"}"));
    }

    @Test
    void emitsOneRunnableCommandPerNamedRecipient() {
        assertEquals(
            "/xero mail send Alice {\"text\":\"hello\\nworld\"}\n"
                + "/xero mail send Bob {\"text\":\"hello\\nworld\"}",
            MailCommandFormatter.format("Alice, Bob", "{\"text\":\"hello\\nworld\"}"));
    }

    @Test
    void preservesASelectorAndUsesSafeFallback() {
        assertEquals("/xero mail send @p {}", MailCommandFormatter.format("@p", "{}"));
        assertEquals("/xero mail send @s {}", MailCommandFormatter.format("", "{}"));
    }
}
