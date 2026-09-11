package com.stoloto.balloongame.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.GameConfig;
import com.stoloto.balloongame.domain.entity.ConfigSnapshotEntity;
import com.stoloto.balloongame.domain.repository.ConfigSnapshotRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Runtime game configuration for <em>new</em> rounds (I8).
 *
 * <p>Holds an {@link AtomicReference} independent of the
 * {@code @ConfigurationProperties} bean. Admin PUT swaps the reference after
 * Jakarta Validation; flying sessions keep their start-time snapshot.
 */
@Service
public class ConfigService {

    private final AtomicReference<GameConfig> current;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ConfigSnapshotRepository snapshotRepository;

    public ConfigService(GameConfig initialConfig,
                         ObjectMapper objectMapper,
                         Validator validator,
                         ConfigSnapshotRepository snapshotRepository) {
        this.current = new AtomicReference<>(initialConfig.deepCopy());
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.snapshotRepository = snapshotRepository;
    }

    /** Deep copy — never the live mutable reference. */
    public GameConfig getSnapshot() {
        return current.get().deepCopy();
    }

    public void update(GameConfig newConfig) {
        current.set(newConfig.deepCopy());
    }

    /**
     * Partial JSON merge into the current config, validate, persist audit row, atomic swap.
     */
    @Transactional
    public GameConfig mergeAndApply(String json, String appliedBy) {
        GameConfig merged = getSnapshot();
        try {
            JsonNode tree = objectMapper.readTree(json);
            if (tree == null || !tree.isObject()) {
                throw GameException.configValidationFailed("Config body must be a JSON object");
            }
            objectMapper.readerForUpdating(merged).readValue(tree);
        } catch (GameException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw GameException.configValidationFailed("Invalid config JSON");
        } catch (Exception e) {
            throw GameException.configValidationFailed("Invalid config JSON");
        }

        validate(merged);
        try {
            String payload = objectMapper.writeValueAsString(merged);
            snapshotRepository.save(new ConfigSnapshotEntity(payload, appliedBy == null ? "admin" : appliedBy));
        } catch (JsonProcessingException e) {
            throw GameException.configValidationFailed("Failed to persist config snapshot");
        }
        update(merged);
        return getSnapshot();
    }

    private void validate(GameConfig config) {
        Set<ConstraintViolation<GameConfig>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw GameException.configValidationFailed(details);
        }
        var admin = config.getAdmin();
        if (admin.getMinBetAmount().compareTo(admin.getMaxBetAmount()) > 0) {
            throw GameException.configValidationFailed("minBetAmount must be <= maxBetAmount");
        }
    }
}
