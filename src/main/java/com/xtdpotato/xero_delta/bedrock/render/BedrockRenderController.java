package com.xtdpotato.xero_delta.bedrock.render;

import com.xtdpotato.xero_delta.bedrock.model.BedrockModel;
import net.minecraft.resources.ResourceLocation;

public interface BedrockRenderController {
    ResourceLocation texture(BedrockModel model);

    default BedrockRenderFilter filter(BedrockModel model) {
        return BedrockRenderFilter.ALL;
    }
}
