package com.edaxortho.interphone.watchdog;

/**
 * Logique pure du watchdog logiciel, extraite de InterphoneApplication pour
 * être testable sans dépendre du port série réel.
 *
 * Compte les échecs consécutifs du test de signal (AT+CSQ sans réponse
 * exploitable) et signale, une seule fois par blocage, le moment où le
 * seuil configuré est atteint (à l'appelant de déclencher l'action de
 * récupération, ex: redémarrage de la Raspberry Pi).
 *
 * Ajouté suite à l'incident du 12/09/2026 : le module SIM7600E-H a cessé de
 * répondre à toute commande AT pendant plus de 5h sans qu'aucune exception
 * ne soit levée côté Java.
 */
public class SignalWatchdog {

    private final int maxFailures;
    private int consecutiveFailures = 0;
    private boolean rebootTriggered = false;

    public SignalWatchdog(int maxFailures) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures doit être >= 1");
        }
        this.maxFailures = maxFailures;
    }

    /**
     * À appeler après chaque test de signal, avec le résultat de ce test
     * (true si une mesure exploitable a été obtenue, false sinon).
     *
     * @return true si le seuil de déclenchement vient d'être atteint pour
     *         la première fois depuis le dernier succès (l'appelant doit
     *         alors déclencher l'action de récupération) ; false sinon,
     *         y compris si le seuil était déjà dépassé lors d'un appel
     *         précédent (une seule notification par blocage).
     */
    public boolean onSignalTestResult(boolean success) {
        if (success) {
            consecutiveFailures = 0;
            rebootTriggered = false;
            return false;
        }
        consecutiveFailures++;
        if (!rebootTriggered && consecutiveFailures >= maxFailures) {
            rebootTriggered = true;
            return true;
        }
        return false;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public boolean isRebootTriggered() {
        return rebootTriggered;
    }

    public int getMaxFailures() {
        return maxFailures;
    }
}
