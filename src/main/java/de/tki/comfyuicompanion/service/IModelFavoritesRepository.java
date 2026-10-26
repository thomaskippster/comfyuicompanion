package de.tki.comfyuicompanion.service;

import java.util.Set;

/**
 * Repository interface for managing bookmarked/favorite model identifiers across sessions
 * with thread-safe, atomic file storage.
 */
public interface IModelFavoritesRepository {

    /**
     * Checks whether the specified model key (ID, repoId, or filename) is marked as a favorite.
     *
     * @param modelKey unique model identifier
     * @return {@code true} if bookmarked
     */
    boolean isFavorite(String modelKey);

    /**
     * Toggles the favorite status of the specified model key and persists the updated set atomically.
     *
     * @param modelKey unique model identifier
     * @return the new favorite state ({@code true} if now favorited, {@code false} if removed)
     */
    boolean toggleFavorite(String modelKey);

    /**
     * Adds a model key to the favorites collection.
     *
     * @param modelKey unique model identifier
     */
    void addFavorite(String modelKey);

    /**
     * Removes a model key from the favorites collection.
     *
     * @param modelKey unique model identifier
     */
    void removeFavorite(String modelKey);

    /**
     * Returns an unmodifiable snapshot of all favorited model keys.
     *
     * @return set of favorite model identifiers
     */
    Set<String> getAllFavorites();
}
