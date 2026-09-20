package com.xtdpotato.xero_delta.bedrock.animation;

import com.xtdpotato.xero_delta.bedrock.model.BedrockVec3;
import com.xtdpotato.xero_delta.bedrock.molang.MolangContext;
import com.xtdpotato.xero_delta.bedrock.molang.MolangExpression;

public record BedrockVectorExpression(MolangExpression x, MolangExpression y, MolangExpression z) {
    public BedrockVec3 evaluate(MolangContext context) {
        return new BedrockVec3(x.evaluate(context), y.evaluate(context), z.evaluate(context));
    }
}
