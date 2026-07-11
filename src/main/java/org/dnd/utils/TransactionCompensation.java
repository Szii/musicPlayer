package org.dnd.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Undoes writes to external systems (Keycloak, ...) when the surrounding transaction rolls back.
 * Those writes are not transactional, so the database rollback alone would leave them behind.
 */
@Slf4j
@Component
public class TransactionCompensation {

  public void onRollback(Runnable undo) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status != STATUS_ROLLED_BACK) {
          return;
        }

        try {
          undo.run();
        } catch (Exception exception) {
          log.error("Failed to compensate an external write after rollback", exception);
        }
      }
    });
  }
}
