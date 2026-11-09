package ro.mathlms.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Side effects wait for the transaction's outcome; without one, "after commit" runs at once and "on rollback" never. */
class AfterTransactionTest {

    private final List<String> ran = new ArrayList<>();

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Ends a simulated transaction the way Spring does: afterCommit on commit, then afterCompletion either way. */
    private static void finish(boolean committed) {
        List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        for (TransactionSynchronization s : registered) {
            if (committed) {
                s.afterCommit();
            }
            s.afterCompletion(committed ? TransactionSynchronization.STATUS_COMMITTED
                    : TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }

    @Test
    void withoutATransactionAfterCommitRunsAtOnceAndRollbackNever() {
        AfterTransaction.afterCommit(() -> ran.add("commit"));
        AfterTransaction.ifRolledBack(() -> ran.add("rollback"));

        assertThat(ran).containsExactly("commit");
    }

    @Test
    void aCommitRunsOnlyTheAfterCommitAction() {
        TransactionSynchronizationManager.initSynchronization();
        AfterTransaction.afterCommit(() -> ran.add("commit"));
        AfterTransaction.ifRolledBack(() -> ran.add("rollback"));
        assertThat(ran).isEmpty(); // nothing before the outcome

        finish(true);

        assertThat(ran).containsExactly("commit");
    }

    @Test
    void aRollbackRunsOnlyTheRollbackAction() {
        TransactionSynchronizationManager.initSynchronization();
        AfterTransaction.afterCommit(() -> ran.add("commit"));
        AfterTransaction.ifRolledBack(() -> ran.add("rollback"));

        finish(false);

        assertThat(ran).containsExactly("rollback");
    }
}
