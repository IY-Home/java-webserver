package com.youfuns.repo;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Extension of Repository for in-memory implementations.
 * Adds predicate-based query methods for flexible filtering.
 */
public interface InMemoryRepository<I, T> extends Repository<I, T> {

    /**
     * Find all entities matching a predicate.
     */
    default List<T> selectWhere(Predicate<? super T> predicate) {
        return findAllAsMap().values().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /**
     * Find all entities matching a predicate, returned as a Map.
     */
    default Map<I, T> selectWhereAsMap(Predicate<? super T> predicate) {
        Map<I, T> result = new HashMap<>();
        findAllAsMap().forEach((id, value) -> {
            if (predicate.test(value)) {
                result.put(id, value);
            }
        });
        return result;
    }

    /**
     * Count entities matching a predicate.
     */
    default int countWhere(Predicate<? super T> predicate) {
        return (int) findAllAsMap().values().stream()
                .filter(predicate)
                .count();
    }

    /**
     * Delete all entities matching a predicate.
     */
    default void deleteWhere(Predicate<? super T> predicate) {
        findAllAsMap().entrySet().stream()
                .filter(entry -> predicate.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .forEach(this::deleteById);
    }

    /**
     * Check if any entity matches a predicate.
     */
    default boolean anyWhere(Predicate<? super T> predicate) {
        return findAllAsMap().values().stream()
                .anyMatch(predicate);
    }

    /**
     * Find first entity matching a predicate.
     */
    default Optional<T> findFirstWhere(Predicate<? super T> predicate) {
        return findAllAsMap().values().stream()
                .filter(predicate)
                .findFirst();
    }
}