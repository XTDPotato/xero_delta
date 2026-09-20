package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

/** Optional FTB Quests bridge with item-specific task detection. */
public final class FtbQuestIntegration {
    private static final String CLIENT_QUEST_FILE = "dev.ftb.mods.ftbquests.client.ClientQuestFile";
    private static final String ITEM_TASK = "dev.ftb.mods.ftbquests.quest.task.ItemTask";
    private static final String ITEM_REWARD = "dev.ftb.mods.ftbquests.quest.reward.ItemReward";

    private static Object cachedQuestFile;
    private static ItemStack cachedStack = ItemStack.EMPTY;
    private static List<Match> cachedMatches = List.of();

    public record Match(long taskId, Component questTitle, Component taskTitle) {
    }

    public record UnlockMatch(long questId, Component chapterTitle, Component questTitle) {
    }

    public record RewardMatch(long rewardId, Component questTitle, Component rewardTitle) {
    }

    private FtbQuestIntegration() {
    }

    public static Match findMatch(ItemStack stack) {
        List<Match> matches = findMatches(stack);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public static List<Match> findMatches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        Object questFile = clientQuestFile();
        if (questFile == null) {
            clearCache();
            return List.of();
        }
        if (questFile == cachedQuestFile
            && !cachedStack.isEmpty()
            && ItemStack.isSameItemSameComponents(cachedStack, stack)) return cachedMatches;

        cachedQuestFile = questFile;
        cachedStack = stack.copyWithCount(1);
        cachedMatches = scanTasks(questFile, cachedStack);
        return cachedMatches;
    }

    public static List<Match> refreshMatches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            clearCache();
            return List.of();
        }
        Object questFile = clientQuestFile();
        if (questFile == null) {
            clearCache();
            return List.of();
        }
        cachedQuestFile = questFile;
        cachedStack = stack.copyWithCount(1);
        cachedMatches = scanTasks(questFile, cachedStack);
        return cachedMatches;
    }

    /** Finds FTB quests that award the selected item. */
    public static List<RewardMatch> findRewardMatches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        Object questFile = clientQuestFile();
        if (questFile == null) return List.of();
        List<RewardMatch> matches = new java.util.ArrayList<>();
        try {
            Class<?> itemRewardClass = Class.forName(ITEM_REWARD);
            Object objects = questFile.getClass().getMethod("getAllObjects").invoke(questFile);
            if (!(objects instanceof Iterable<?> iterable)) return List.of();
            for (Object reward : iterable) {
                if (reward == null || !itemRewardClass.isInstance(reward)) continue;
                Object itemValue = reward.getClass().getMethod("getItem").invoke(reward);
                if (!(itemValue instanceof ItemStack rewardStack)
                    || rewardStack.isEmpty() || !rewardStack.is(stack.getItem())) continue;
                Object idValue = reward.getClass().getMethod("getId").invoke(reward);
                if (!(idValue instanceof Number id)) continue;
                Component rewardTitle = componentResult(reward, "getAltTitle",
                    rewardStack.getHoverName());
                matches.add(new RewardMatch(id.longValue(),
                    questTitle(reward, rewardTitle), rewardTitle));
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            XeroDelta.LOGGER.debug("Unable to scan FTB Quests item rewards", exception);
        }
        return List.copyOf(matches);
    }

    public static boolean open(Match match) {
        if (match == null || clientQuestFile() == null) return false;
        return openQuestObject(match.taskId());
    }

    public static boolean open(UnlockMatch match) {
        if (match == null || clientQuestFile() == null) return false;
        return openQuestObject(match.questId());
    }

    public static boolean open(RewardMatch match) {
        return match != null && openQuestObject(match.rewardId());
    }

    public static List<UnlockMatch> findListingSlotUnlocks() {
        Object questFile = clientQuestFile();
        if (questFile == null) return List.of();
        List<UnlockMatch> matches = new java.util.ArrayList<>();
        try {
            Object quests = questFile.getClass().getMethod("getAllQuests").invoke(questFile);
            if (!(quests instanceof Iterable<?> iterable)) return List.of();
            for (Object quest : iterable) {
                if (quest == null || !hasListingSlotUnlockReward(quest)) continue;
                Object idValue = quest.getClass().getMethod("getId").invoke(quest);
                if (!(idValue instanceof Number id)) continue;
                Component questTitle = componentResult(quest, "getTitle",
                    Component.literal("FTB Quest"));
                Object chapter = invokeNoArgs(quest, "getChapter");
                Component chapterTitle = chapter == null ? Component.literal("FTB Quests")
                    : componentResult(chapter, "getTitle", Component.literal("FTB Quests"));
                matches.add(new UnlockMatch(id.longValue(), chapterTitle, questTitle));
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            XeroDelta.LOGGER.debug("Unable to scan FTB Quests listing-slot unlocks", exception);
        }
        return List.copyOf(matches);
    }

    /** Reads the current client quest file each time for creative mail task selection. */
    public static List<Match> allTasks() {
        Object questFile = clientQuestFile();
        if (questFile == null) return List.of();
        List<Match> matches = new java.util.ArrayList<>();
        try {
            Object tasks = questFile.getClass().getMethod("getAllTasks").invoke(questFile);
            if (!(tasks instanceof Iterable<?> iterable)) return List.of();
            for (Object task : iterable) {
                if (task == null) continue;
                Object idValue = task.getClass().getMethod("getId").invoke(task);
                if (!(idValue instanceof Number id)) continue;
                Component taskTitle = componentResult(task, "getTitle", Component.literal("FTB Task"));
                matches.add(new Match(id.longValue(), questTitle(task, taskTitle), taskTitle));
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            XeroDelta.LOGGER.debug("Unable to list FTB Quests tasks for mail composer", exception);
        }
        return List.copyOf(matches);
    }

    private static boolean openQuestObject(long id) {
        try {
            Class<?> clientFileClass = Class.forName(CLIENT_QUEST_FILE);
            clientFileClass.getMethod("openBookToQuestObject", long.class).invoke(null, id);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            XeroDelta.LOGGER.debug("Unable to open FTB Quests object {}", id, exception);
            return false;
        }
    }

    private static boolean hasListingSlotUnlockReward(Object quest) {
        Object rewards = invokeNoArgs(quest, "getRewards");
        if (!(rewards instanceof Iterable<?> iterable)) return false;
        for (Object reward : iterable) {
            if (reward == null) continue;
            StringBuilder text = new StringBuilder(reward.toString());
            for (String method : List.of("getCommand", "getCommands", "getText")) {
                Object value = invokeNoArgs(reward, method);
                if (value != null) text.append(' ').append(value);
            }
            String normalized = text.toString().toLowerCase(java.util.Locale.ROOT);
            if ((normalized.contains("xero_trading") || normalized.contains("xero market")
                || normalized.contains("/market "))
                && normalized.contains("slots")
                && normalized.contains("unlock")) return true;
        }
        return false;
    }

    private static Object invokeNoArgs(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Component componentResult(Object target, String method, Component fallback) {
        Object value = invokeNoArgs(target, method);
        return value instanceof Component component ? component.copy() : fallback.copy();
    }

    private static Object clientQuestFile() {
        try {
            Class<?> clientFileClass = Class.forName(CLIENT_QUEST_FILE);
            Object exists = clientFileClass.getMethod("exists").invoke(null);
            if (!(exists instanceof Boolean value) || !value) return null;
            return clientFileClass.getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static List<Match> scanTasks(Object questFile, ItemStack stack) {
        List<Match> matches = new java.util.ArrayList<>();
        try {
            Class<?> itemTaskClass = Class.forName(ITEM_TASK);
            Object tasks = questFile.getClass().getMethod("getAllTasks").invoke(questFile);
            if (!(tasks instanceof Iterable<?> iterable)) return List.of();
            for (Object task : iterable) {
                if (task == null || !itemTaskClass.isInstance(task)) continue;
                Method test = task.getClass().getMethod("test", ItemStack.class);
                if (!Boolean.TRUE.equals(test.invoke(task, stack))) continue;
                Object idValue = task.getClass().getMethod("getId").invoke(task);
                if (!(idValue instanceof Number id)) continue;
                Component itemTitle = taskTitle(task, stack);
                Component parentTitle = questTitle(task, itemTitle);
                matches.add(new Match(id.longValue(), parentTitle, itemTitle));
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            XeroDelta.LOGGER.debug("Unable to scan FTB Quests item tasks", exception);
        }
        return List.copyOf(matches);
    }

    private static Component taskTitle(Object task, ItemStack fallback) {
        try {
            Object title = task.getClass().getMethod("getTitle").invoke(task);
            if (title instanceof Component component) return component.copy();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
        return fallback.getHoverName().copy();
    }

    private static Component questTitle(Object task, Component fallback) {
        try {
            Object quest = task.getClass().getMethod("getQuest").invoke(task);
            if (quest != null) {
                Object title = quest.getClass().getMethod("getTitle").invoke(quest);
                if (title instanceof Component component) return component.copy();
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
        return fallback.copy();
    }

    private static void clearCache() {
        cachedQuestFile = null;
        cachedStack = ItemStack.EMPTY;
        cachedMatches = List.of();
    }
}
