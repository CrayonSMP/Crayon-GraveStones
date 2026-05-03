package at.slini204.bgravestones.util;

import org.bukkit.entity.Player;

public final class ExpUtil {

    private ExpUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static int getExpAtLevel(int level) {
        if (level <= 0) {
            return 0;
        }

        if (level <= 16) {
            return level * level + 6 * level;
        }

        if (level <= 31) {
            return (int) Math.floor(2.5D * level * level - 40.5D * level + 360D);
        }

        return (int) Math.floor(4.5D * level * level - 162.5D * level + 2220D);
    }

    public static int getTotalExperiencePoints(Player player) {
        if (player == null) {
            return 0;
        }

        int level = player.getLevel();
        float progress = player.getExp();

        int expAtCurrentLevel = getExpAtLevel(level);
        int expToNextLevel = player.getExpToLevel();
        int expInsideCurrentLevel = Math.round(progress * expToNextLevel);

        return Math.max(0, expAtCurrentLevel + expInsideCurrentLevel);
    }

    public static int applyPercentage(int experiencePoints, double configPercentage) {
        if (experiencePoints <= 0) {
            return 0;
        }

        double percentage = normalizePercentage(configPercentage);
        return (int) Math.round(experiencePoints * percentage);
    }

    public static int getPercentageExperiencePoints(Player player, double configPercentage) {
        int totalExperience = getTotalExperiencePoints(player);
        return applyPercentage(totalExperience, configPercentage);
    }

    public static double normalizePercentage(double configPercentage) {
        if (Double.isNaN(configPercentage) || Double.isInfinite(configPercentage)) {
            return 0.0D;
        }

        if (configPercentage <= 0.0D) {
            return 0.0D;
        }

        if (configPercentage >= 1.0D) {
            return 1.0D;
        }

        return configPercentage;
    }
}
