package ro.mathlms.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Side effects that must follow the database, not run ahead of it: deleting a file only once the row that pointed at
 * it is really gone, removing a file saved for a change that was rolled back, evicting a cache after the new value is
 * committed. Called inside a transaction they wait for its outcome; called outside one, see each method.
 */
public final class AfterTransaction {

    private AfterTransaction() {
    }

    /** Runs {@code action} once the current transaction commits - never if it rolls back; at once without one. */
    public static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /** Runs {@code action} only if the current transaction rolls back; without one there is nothing to undo. */
    public static void ifRolledBack(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}
