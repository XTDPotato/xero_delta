package com.xtdpotato.xero_delta.bedrock.molang;

public interface MolangContext {
    double animTime();
    double lifeTime();
    boolean isSneaking();

    default double query(String name) {
        return switch (name) {
            case "query.anim_time" -> animTime();
            case "query.life_time" -> lifeTime();
            case "query.is_sneaking" -> isSneaking() ? 1 : 0;
            default -> 0;
        };
    }
}
