package com.youfuns.repo;

import com.youfuns.auth.ResultReturn;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Repository<I, T> {
    ResultReturn insert(T value);
    ResultReturn update(T value);
    ResultReturn updateById(I id, T value);

    default int insertAll(List<T> items) {
        int saved = 0;
        for (T item : items) {
            if (insert(item).isSuccess()) saved++;
        }
        return saved;
    }

    ResultReturn delete(T value);
    ResultReturn deleteById(I id);
    void deleteAll();
    int deleteAllById(List<I> ids);

    Optional<T> findById(I id);
    boolean existsById(I id);
    long count();

    List<T> findAll();
    Map<I, T> findAllAsMap();
    List<T> findAllById(List<I> ids);
    Map<I, T> findAllByIdAsMap(List<I> ids);

    default boolean isEmpty() {
        return count() == 0;
    }

    default boolean isNotEmpty() {
        return !isEmpty();
    }
}