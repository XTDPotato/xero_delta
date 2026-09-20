package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.data.DeltaPacksConfig;

import java.nio.file.Path;

/** Central location for Xero Delta's file-backed configuration. */
public final class ConfigPaths {
    public static final String DIRECTORY_NAME = DeltaPacksConfig.DIRECTORY_NAME;

    private ConfigPaths() {
    }

    public static Path directory() {
        return DeltaPacksConfig.directory();
    }

    public static Path file(String name) {
        return DeltaPacksConfig.file(name);
    }
}

