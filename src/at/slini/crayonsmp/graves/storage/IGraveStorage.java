package at.slini.crayonsmp.graves.storage;

import at.slini.crayonsmp.graves.model.Grave;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface IGraveStorage {
    void load();

    void save();

    void saveAsync();

    void put(Grave paramGrave);

    void remove(UUID paramUUID);

    Collection<Grave> getAll();

    Optional<Grave> findByLocation(UUID paramUUID, int paramInt1, int paramInt2, int paramInt3);

    default boolean isLimitedStorage() {
        return false;
    }
}
