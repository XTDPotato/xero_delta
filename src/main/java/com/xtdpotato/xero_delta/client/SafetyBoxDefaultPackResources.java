package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.SafetyBoxInspectResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class SafetyBoxDefaultPackResources extends AbstractPackResources {
    private static final byte[] PACK_METADATA = """
        {"pack":{"pack_format":34,"description":"Xero Delta Packs default pack"}}
        """.getBytes(StandardCharsets.UTF_8);
    private final Path root;

    public SafetyBoxDefaultPackResources(PackLocationInfo location, Path root) {
        super(location);
        this.root = root;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length != 1 || !PackResources.PACK_META.equals(path[0])) return null;
        return () -> new ByteArrayInputStream(PACK_METADATA);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES || !XeroDelta.MOD_ID.equals(location.getNamespace())) return null;
        if (isBundledResource(location.getPath())) return null;
        return PathPackResources.getResource(location, root);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !XeroDelta.MOD_ID.equals(namespace)) return;
        List<String> segments = path.isEmpty() ? List.of() : Arrays.asList(path.split("/"));
        PathPackResources.listPath(namespace, root, segments, (location, supplier) -> {
            if (!isBundledResource(location.getPath())) {
                output.accept(location, supplier);
            }
        });
    }

    /**
     * Layout files remain user-owned in the default pack. Item assets are
     * bundled code resources and must update with the jar instead of letting
     * an old on-disk snapshot override current models or textures.
     */
    private static boolean isBundledResource(String path) {
        return SafetyBoxInspectResources.isBundledRig(path)
            || path.startsWith("models/item/")
            || path.startsWith("textures/item/")
            || path.startsWith("textures/models/armor/");
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(XeroDelta.MOD_ID) : Set.of();
    }

    @Override
    public void close() {
    }
}
