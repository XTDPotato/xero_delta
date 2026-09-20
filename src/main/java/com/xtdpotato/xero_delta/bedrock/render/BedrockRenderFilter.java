package com.xtdpotato.xero_delta.bedrock.render;

import com.xtdpotato.xero_delta.bedrock.model.BedrockBone;

@FunctionalInterface
public interface BedrockRenderFilter {
    BedrockRenderFilter ALL = bone -> true;
    boolean renderCubes(BedrockBone bone);
}
