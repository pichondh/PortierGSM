package com.edaxortho.interphone.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interprétation de la réponse AT+CREG? (enregistrement sur le réseau
 * mobile). Un CSQ correct (voir SignalHistoryStore) ne garantit pas que le
 * module est réellement enregistré sur le réseau de l'opérateur : ce test
 * complémentaire permet de le distinguer d'un problème réseau (pas de
 * carte, forfait expiré, zone non couverte...) plutôt qu'un blocage du
 * module lui-même.
 *
 * Format standard 3GPP : "+CREG: <n>,<stat>" (ex: "+CREG: 0,1"). stat=1
 * (enregistré, réseau nominal) ou stat=5 (enregistré, itinérance) signifient
 * que le module est bien enregistré ; les autres valeurs (0=non enregistré
 * et pas de recherche en cours, 2=recherche en cours, 3=enregistrement
 * refusé, 4=inconnu) signifient que non.
 */
public class NetworkStatusUtil {

    private static final Pattern CREG_PATTERN = Pattern.compile("\\+CREG:\\s*\\d+\\s*,\\s*(\\d+)");

    /**
     * @return true si enregistré (nominal ou itinérance), false si non
     *         enregistré, null si la réponse ne contient pas de +CREG
     *         exploitable (module muet, ERROR, timeout...).
     */
    public static Boolean parseRegistered(String rawResponse) {
        if (rawResponse == null) {
            return null;
        }
        Matcher matcher = CREG_PATTERN.matcher(rawResponse);
        if (!matcher.find()) {
            return null;
        }
        try {
            int stat = Integer.parseInt(matcher.group(1));
            return stat == 1 || stat == 5;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Étiquette lisible pour l'affichage sur la page de supervision.
     */
    public static String label(Boolean registered) {
        if (registered == null) {
            return "Inconnu";
        }
        return registered ? "Enregistré" : "Non enregistré";
    }
}
