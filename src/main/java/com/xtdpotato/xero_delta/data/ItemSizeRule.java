package com.xtdpotato.xero_delta.data;

public record ItemSizeRule(ItemSize size, boolean rotateTexture, boolean stretchTexture, int proportionalScale) {
    private static final long ROTATE_TEXTURE_FALSE_FLAG = 1L << 62;
    private static final long STRETCH_TEXTURE_TRUE_FLAG = 1L << 61;
    private static final long PROPORTIONAL_SCALE_MASK = 0x1FL << 56;

    public static final ItemSizeRule DEFAULT = new ItemSizeRule(ItemSize.ONE, true, false, 0);

    public ItemSizeRule(ItemSize size, boolean rotateTexture, boolean stretchTexture) {
        this(size, rotateTexture, stretchTexture, 0);
    }

    public ItemSizeRule {
        proportionalScale = Math.max(0, Math.min(31, proportionalScale));
    }

    public static ItemSizeRule automatic(ItemSize size) {
        return new ItemSizeRule(size, true, false, 0);
    }

    public long pack() {
        long packed = size.pack();
        if (!rotateTexture) packed |= ROTATE_TEXTURE_FALSE_FLAG;
        if (stretchTexture) packed |= STRETCH_TEXTURE_TRUE_FLAG;
        packed |= (long) proportionalScale << 56;
        return packed;
    }

    public static ItemSizeRule unpack(long packed) {
        boolean rotateTexture = (packed & ROTATE_TEXTURE_FALSE_FLAG) == 0;
        boolean stretchTexture = (packed & STRETCH_TEXTURE_TRUE_FLAG) != 0;
        int proportionalScale = (int)((packed & PROPORTIONAL_SCALE_MASK) >>> 56);
        long sizePacked = packed & ~(ROTATE_TEXTURE_FALSE_FLAG | STRETCH_TEXTURE_TRUE_FLAG | PROPORTIONAL_SCALE_MASK);
        return new ItemSizeRule(ItemSize.unpack(sizePacked), rotateTexture, stretchTexture, proportionalScale);
    }
}
