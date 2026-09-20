package com.xtdpotato.xero_delta.bedrock.molang;

@FunctionalInterface
public interface MolangExpression {
    MolangExpression ZERO = context -> 0;
    double evaluate(MolangContext context);
}
