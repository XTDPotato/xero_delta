package com.xtdpotato.xero_delta.trading;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageJsonSyntaxTest {
    @Test
    void languageBundlesAreValidJsonObjects() throws Exception {
        assertValid("assets/xero_delta/lang/zh_cn.json");
        assertValid("assets/xero_delta/lang/en_us.json");
    }

    private static void assertValid(String path) throws Exception {
        var stream = LanguageJsonSyntaxTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            assertTrue(JsonParser.parseReader(reader).isJsonObject(), path);
        }
    }
}
