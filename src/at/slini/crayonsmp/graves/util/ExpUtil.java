package at.slini.crayonsmp.graves.util;

import org.bukkit.entity.Player;

public final class ExpUtil {

    public static int getExpAtLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    public static int getTotalExperiencePoints(Player p) {
        int level = p.getLevel();
        float progress = p.getExp();
        int expForLevel = getExpAtLevel(level);
        int expToNext = p.getExpToLevel();
        int intoLevel = Math.round(progress * expToNext);
        return expForLevel + intoLevel;
    }
}

