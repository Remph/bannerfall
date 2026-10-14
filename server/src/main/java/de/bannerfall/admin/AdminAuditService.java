package de.bannerfall.admin;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The audit log (08-host-console §6): one row per admin action, in the same transaction as the
 * change, plus one INFO line under the logger {@code bannerfall.admin}. The actor comes from the
 * request ({@link AdminActor}).
 *
 * <p>Typical use from a controller: {@code audit.inTransaction(() -> { var r = service.change();
 * audit.record("ACTION", "player", id, Map.of(...)); return r; })}.
 */
@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger("bannerfall.admin");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

    static final int MAX_LIMIT = 500;

    private final AdminActionRepository actions;
    private final Clock clock;
    private final TransactionTemplate transaction;

    AdminAuditService(
            AdminActionRepository actions,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.actions = actions;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Writes the row. Must run inside the transaction of the change, so the row exists exactly when
     * the change does.
     *
     * @param details small values only (names, numbers); null values are left out
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, Long targetId, Map<String, ?> details) {
        recordAs(AdminActor.current(), action, targetType, targetId, details);
    }

    /**
     * Like {@link #record}, for an action the server takes by itself (the automatic lock of M14e,
     * actor {@value AdminActor#AUTO_LOCK}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(
            String actor, String action, String targetType, Long targetId, Map<String, ?> details) {
        Map<String, Object> clean = new LinkedHashMap<>();
        details.forEach(
                (key, value) -> {
                    if (value != null) {
                        clean.put(key, value);
                    }
                });
        actions.save(
                new AdminAction(
                        clock.instant(),
                        actor,
                        action,
                        targetType,
                        targetId,
                        MAPPER.writeValueAsString(clean)));
        log.info(
                "{} by {}{} {}",
                action,
                actor,
                targetType == null ? "" : " on " + targetType + " " + targetId,
                clean);
    }

    /**
     * The components of two records of the same type that differ, as {@code {name: [old, new]}}:
     * the details of a settings change.
     */
    public static Map<String, Object> changes(Record before, Record after) {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (RecordComponent component : before.getClass().getRecordComponents()) {
            try {
                Object old = component.getAccessor().invoke(before);
                Object now = component.getAccessor().invoke(after);
                if (!Objects.equals(old, now)) {
                    changed.put(component.getName(), Arrays.asList(old, now));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return changed;
    }

    /** Runs the change and its {@link #record} calls in one transaction. */
    public <T> T inTransaction(Supplier<T> change) {
        return transaction.execute(status -> change.get());
    }

    /** Like {@link #inTransaction(Supplier)}, for changes without a result. */
    public void inTransaction(Runnable change) {
        transaction.executeWithoutResult(status -> change.run());
    }

    /** The newest {@code limit} rows (1 … {@value #MAX_LIMIT}), newest first. */
    @Transactional(readOnly = true)
    public List<AdminActionDto> newest(int limit) {
        return actions
                .findAllByOrderByAtDescIdDesc(PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT)))
                .stream()
                .map(
                        a ->
                                new AdminActionDto(
                                        a.getId(),
                                        a.getAt(),
                                        a.getActor(),
                                        a.getAction(),
                                        a.getTargetType(),
                                        a.getTargetId(),
                                        MAPPER.readValue(a.getDetails(), DETAILS)))
                .toList();
    }
}
