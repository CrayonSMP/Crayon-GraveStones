package at.slini204.bgravestones.storage;

import at.slini204.bgravestones.model.Grave;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface IGraveStorage {
    void load();

    void save();

    void saveAsync();

    void put(Grave grave);

    void remove(UUID graveId);

    Collection<Grave> getAll();

    Optional<Grave> findByLocation(UUID worldUuid, int x, int y, int z);

    default boolean isLimitedStorage() {
        return false;
    }

    default void close() {
        // Optional lifecycle hook for storages with external resources.
    }
}
