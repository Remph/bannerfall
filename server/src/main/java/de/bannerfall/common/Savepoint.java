package de.bannerfall.common;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a step behind a JDBC savepoint of the open transaction (2.1 feature review, finding 11): an
 * error in the step rolls back only the step's own database writes, and the transaction goes on
 * (Postgres refuses every further statement of a transaction after an error otherwise). The JPA
 * provider offers no savepoints, so this sets one on the transaction's connection by hand; pending
 * JPA changes are flushed first so they stay outside it. The step itself should write through JDBC
 * (entities it changed would still be flushed at commit).
 *
 * <p>Not covered: an error that leaves a {@code @Transactional} bean inside the step (the {@code
 * Chronicle}, a repository) has already marked the whole transaction rollback-only on its way out,
 * and the commit then fails anyway (checked in the review of finding 11). So the step's calls into
 * such beans must be ones that can't fail but for a database outage.
 */
@Component
public class Savepoint {

    private final DataSource dataSource;
    private final EntityManager entityManager;

    Savepoint(DataSource dataSource, EntityManager entityManager) {
        this.dataSource = dataSource;
        this.entityManager = entityManager;
    }

    /** Runs {@code step}; on a runtime error its writes are rolled back and the error rethrown. */
    public void run(Runnable step) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            step.run();
            return;
        }
        if (entityManager.isJoinedToTransaction()) {
            entityManager.flush();
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        java.sql.Savepoint savepoint;
        try {
            savepoint = connection.setSavepoint();
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("Could not set a savepoint", e);
        }
        try {
            step.run();
        } catch (RuntimeException e) {
            try {
                connection.rollback(savepoint);
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        }
        try {
            connection.releaseSavepoint(savepoint);
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("Could not release a savepoint", e);
        }
    }
}
