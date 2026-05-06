package at.slini204.bgravestones.compat;

public enum ServerPlatform {
    FOLIA,
    PURPUR,
    PAPER,
    SPIGOT,
    CRAFTBUKKIT,
    UNKNOWN;

    public boolean isPaperFamily() {
        return this == PAPER || this == PURPUR || this == FOLIA;
    }

    public boolean isClassicBukkitThreadModel() {
        return this != FOLIA;
    }

    public String displayName() {
        return switch (this) {
            case FOLIA -> "Folia";
            case PURPUR -> "Purpur";
            case PAPER -> "Paper";
            case SPIGOT -> "Spigot";
            case CRAFTBUKKIT -> "CraftBukkit/Bukkit";
            case UNKNOWN -> "Unknown Bukkit-compatible server";
        };
    }
}
