package com.xtdpotato.xero_delta.data;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Optional reflection bridge for FTB Teams; the mod remains usable without FTB Teams installed. */
public final class FtbTeamIntegration {
    private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";

    private FtbTeamIntegration() {
    }

    public static List<ServerPlayer> onlineTeammates(ServerPlayer viewer) {
        Set<UUID> memberIds = memberIds(viewer);
        if (memberIds.size() <= 1 || !memberIds.contains(viewer.getUUID())) return List.of();
        List<ServerPlayer> result = new ArrayList<>();
        for (UUID id : memberIds) {
            if (id.equals(viewer.getUUID())) continue;
            ServerPlayer member = viewer.server.getPlayerList().getPlayer(id);
            if (member != null) result.add(member);
        }
        result.sort(java.util.Comparator.comparing(
            player -> player.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    /** Stable member set used by server-authoritative team shared systems. */
    public static Set<UUID> memberIdsIncludingSelf(ServerPlayer viewer) {
        Set<UUID> ids = memberIds(viewer);
        if (ids.isEmpty() || !ids.contains(viewer.getUUID())) return Set.of(viewer.getUUID());
        return ids;
    }

    private static Set<UUID> memberIds(ServerPlayer viewer) {
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Object api = apiClass.getMethod("api").invoke(null);
            Object manager = invokeNoArgs(api, "getManager");
            if (manager == null) return Set.of();
            Object team = findTeam(manager, viewer);
            team = unwrap(team);
            if (team == null) return Set.of();
            LinkedHashSet<UUID> ids = new LinkedHashSet<>();
            collectIds(invokeNoArgs(team, "getMembers"), ids);
            collectIds(invokeNoArgs(team, "getAllMembers"), ids);
            collectIds(invokeNoArgs(team, "getOwner"), ids);
            return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(ids));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return Set.of();
        }
    }

    private static Object findTeam(Object manager, ServerPlayer player)
        throws ReflectiveOperationException {
        for (Method method : manager.getClass().getMethods()) {
            if (!method.getName().equals("getTeamForPlayer") || method.getParameterCount() != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            if (type == UUID.class) return method.invoke(manager, player.getUUID());
            if (type.isInstance(player)) return method.invoke(manager, player);
            if (type.isInstance(player.getGameProfile())) return method.invoke(manager, player.getGameProfile());
        }
        return null;
    }

    private static Object invokeNoArgs(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Object unwrap(Object value) {
        if (value instanceof Optional<?> optional) return optional.orElse(null);
        return value;
    }

    private static void collectIds(Object source, Collection<UUID> output) {
        source = unwrap(source);
        if (source == null) return;
        if (source instanceof UUID id) {
            output.add(id);
        } else if (source instanceof Map<?, ?> map) {
            map.keySet().forEach(value -> collectIds(value, output));
            map.values().forEach(value -> collectIds(value, output));
        } else if (source instanceof Iterable<?> iterable) {
            iterable.forEach(value -> collectIds(value, output));
        } else {
            Object id = invokeNoArgs(source, "getId");
            if (id != source) collectIds(id, output);
        }
    }
}
