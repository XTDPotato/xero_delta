package com.xtdpotato.xero_delta.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record GridEntry(UUID id, int x, int y, int width, int height, boolean rotated, ItemStack stack) {

    public static final Codec<GridEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("id_most").forGetter(e -> e.id.getMostSignificantBits()),
        Codec.LONG.fieldOf("id_least").forGetter(e -> e.id.getLeastSignificantBits()),
        Codec.INT.fieldOf("x").forGetter(GridEntry::x),
        Codec.INT.fieldOf("y").forGetter(GridEntry::y),
        Codec.INT.fieldOf("width").forGetter(GridEntry::width),
        Codec.INT.fieldOf("height").forGetter(GridEntry::height),
        Codec.BOOL.fieldOf("rotated").forGetter(GridEntry::rotated),
        ItemStack.CODEC.fieldOf("stack").forGetter(GridEntry::stack)
    ).apply(instance, (most, least, x, y, w, h, r, s) ->
        new GridEntry(new UUID(most, least), x, y, w, h, r, s)));

    public static final StreamCodec<RegistryFriendlyByteBuf, GridEntry> STREAM_CODEC = StreamCodec.of(
        (buf, entry) -> {
            buf.writeLong(entry.id.getMostSignificantBits());
            buf.writeLong(entry.id.getLeastSignificantBits());
            buf.writeInt(entry.x);
            buf.writeInt(entry.y);
            buf.writeInt(entry.width);
            buf.writeInt(entry.height);
            buf.writeBoolean(entry.rotated);
            ItemStack.STREAM_CODEC.encode(buf, entry.stack);
        },
        buf -> new GridEntry(
            new UUID(buf.readLong(), buf.readLong()),
            buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
            buf.readBoolean(),
            ItemStack.STREAM_CODEC.decode(buf)
        )
    );

    public boolean occupies(int cellX, int cellY) {
        return cellX >= x && cellX < x + width && cellY >= y && cellY < y + height;
    }

    public GridEntry copy() {
        return new GridEntry(id, x, y, width, height, rotated, stack.copy());
    }
}