package com.ozerlabs.triterra.car;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Pure logic for the read-only car view of today's plan, per the field-region-map data contract
 * (main@4bdaab9). No Firebase types here so it is unit-testable; Firestore I/O lives in
 * FirestoreTodayPlanRepository. All checks fail closed.
 */
public final class TodayPlan {
    private TodayPlan() {}

    /** What the car may show. Demo data is NOT a status: it is a separate, debug-only screen. */
    public enum Status { READY, SIGN_IN_REQUIRED, LOAD_FAILED }

    public static final class Result {
        public final Status status;
        /** Real stops; always empty unless status == READY. */
        public final List<Branch> stops;

        private Result(Status status, List<Branch> stops) {
            this.status = status;
            this.stops = stops;
        }

        public static Result ready(List<Branch> stops) {
            return new Result(Status.READY, stops);
        }

        public static Result blocked(Status status) {
            if (status == Status.READY) throw new IllegalArgumentException("READY needs stops");
            return new Result(status, java.util.Collections.<Branch>emptyList());
        }
    }

    /**
     * Stop rows that fit in {@code limit} host rows. When not all fit, one row is kept for the
     * "+N more" entry, so the result is limit - 1 and the remainder is total - result.
     */
    public static int visibleStops(int total, int limit) {
        return total <= limit ? total : Math.max(limit - 1, 0);
    }

    private static final TimeZone JERUSALEM = TimeZone.getTimeZone("Asia/Jerusalem");

    /** visitDayKey(): YYYY-MM-DD in Asia/Jerusalem (rolls over at local midnight). */
    public static String dayId(long epochMillis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(JERUSALEM);
        return f.format(new Date(epochMillis));
    }

    /** Integer 1 only; the string "1" (or 1.5, or null) is rejected, as the web and server rules do. */
    static boolean isSchemaV1(Object v) {
        return (v instanceof Long && (Long) v == 1L) || (v instanceof Integer && (Integer) v == 1);
    }

    /** Mirrors validateProfile (region-config.js). Returns null when it passes, else the failed check. */
    public static String profileGateFailure(Map<String, Object> profile, String authEmail) {
        if (profile == null) return "no-profile";
        if (!Boolean.TRUE.equals(profile.get("active"))) return "inactive";
        if (!isSchemaV1(profile.get("schemaVersion"))) return "schema-version";
        // Field "email", trimmed + lowercased (validateProfile, region-config.js:31; confirmed in the appendix).
        Object email = profile.get("email");
        if (authEmail == null || !(email instanceof String)
                || !((String) email).trim().toLowerCase(Locale.ROOT).equals(authEmail.trim().toLowerCase(Locale.ROOT))) {
            return "email-mismatch";
        }
        Object regionId = profile.get("regionId");
        if (!(regionId instanceof String) || ((String) regionId).isEmpty()) return "no-region";
        return null;
    }

    /** The parts of buildRegionConfig the car needs (map center/zoom are irrelevant to the host-drawn map). */
    public static String regionGateFailure(Map<String, Object> region, String regionId) {
        if (region == null) return "no-region-doc";
        if (!Boolean.TRUE.equals(region.get("active"))) return "region-inactive";
        if (!isSchemaV1(region.get("schemaVersion"))) return "region-schema-version";
        if (!regionId.equals(region.get("regionId"))) return "region-mismatch";
        return null;
    }

    /**
     * Today's remaining stops in state.route order (route is authoritative; manualOrder is never needed),
     * done/skipped dropped, current stop first. Mapping per the code-owner appendix (main@4bdaab9):
     * - route[].id shares the store id space (base store id, "user-..." id, or fixed-facility id); compared as String(id).
     * - name: the stop's inline snapshot (already the Hebrew display name at day start).
     * - coordinates: effectiveCoordinates(fresh store, override) where the fresh store is the base store after
     *   applyStoreOverrides or a projected user-added store; when neither exists (fixed facility, or a store
     *   hidden after day start) the stop's own snapshot is used. Fixed-facility constants are NOT replicated.
     * @param baseStores region stores by doc id (raw Firestore data)
     * @param overrides users/{uid}/storeOverrides by doc id
     */
    @SuppressWarnings("unchecked")
    public static List<Branch> remainingStops(Map<String, Object> state, Map<String, Map<String, Object>> baseStores,
                                              Map<String, Map<String, Object>> overrides) {
        List<Branch> out = new ArrayList<>();
        if (state == null || !(state.get("route") instanceof List)) return out; // day not started
        Set<String> gone = ids(state.get("doneIds"));
        gone.addAll(ids(state.get("skippedIds")));
        Object currentId = state.get("currentId");
        for (Object o : (List<Object>) state.get("route")) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> stop = (Map<String, Object>) o;
            if (stop.get("id") == null) continue;
            String id = String.valueOf(stop.get("id"));
            if (gone.contains(id)) continue;
            String name = text(stop.get("name"));
            if (name.isEmpty()) continue;
            Map<String, Object> fresh = freshStore(id, baseStores.get(id), overrides.get(id));
            double[] c = effectiveCoordinates(id, fresh != null ? fresh : stop, overrides.get(id));
            Branch b = new Branch(name, "", c[0], c[1]);
            if (currentId != null && id.equals(String.valueOf(currentId))) out.add(0, b);
            else out.add(b);
        }
        return out;
    }

    /** applyStoreOverrides + projectUserStores (user-stores.js), reduced to the fields the car reads (coordinates). */
    static Map<String, Object> freshStore(String id, Map<String, Object> base, Map<String, Object> override) {
        if (base != null) {
            if (override != null && Boolean.TRUE.equals(override.get("hidden"))) return null; // per-user soft delete
            if (override == null || !truthy(override.get("name"))) return base;               // passes through untouched
            Map<String, Object> patched = new java.util.HashMap<>(base);
            // Number.isFinite(override.lat): numbers only, no string coercion; else the base value stays.
            if (isFiniteNumber(override.get("lat"))) patched.put("lat", override.get("lat"));
            if (isFiniteNumber(override.get("lng"))) patched.put("lng", override.get("lng"));
            return patched;
        }
        if (override != null && Boolean.TRUE.equals(override.get("userAdded")) && truthy(override.get("name"))
                && finite(override.get("lat")) && finite(override.get("lng"))) {
            return override; // user-added store (id "user-..."); malformed docs are skipped, never crash
        }
        return null;
    }

    /** effectiveCoordinates (data-quality.js): a pin fix for this exact id wins, else the store's own point; NaN = none. */
    @SuppressWarnings("unchecked")
    static double[] effectiveCoordinates(String id, Map<String, Object> store, Map<String, Object> override) {
        Object fixObj = override == null ? null : override.get("coordinateOverride");
        if (fixObj instanceof Map) {
            Map<String, Object> fix = (Map<String, Object>) fixObj;
            // fix.siteId === store.id: strict, so a numeric siteId never matches a string id.
            if (id.equals(fix.get("siteId")) && finite(fix.get("newLat")) && finite(fix.get("newLng"))) {
                return new double[] {num(fix.get("newLat")), num(fix.get("newLng"))};
            }
        }
        if (finite(store.get("lat")) && finite(store.get("lng"))) return new double[] {num(store.get("lat")), num(store.get("lng"))};
        return new double[] {Double.NaN, Double.NaN};
    }

    /** JS: value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value)). */
    // shortcut: Number() coercion is mirrored for numbers and numeric strings only (not booleans/hex); upgrade if such data appears.
    static boolean finite(Object v) {
        return !(v == null || "".equals(v)) && Double.isFinite(num(v));
    }

    private static double num(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            String t = ((String) v).trim();
            if (t.isEmpty()) return 0; // Number("   ") === 0 in JS
            try { return Double.parseDouble(t); } catch (NumberFormatException e) { return Double.NaN; }
        }
        return Double.NaN;
    }

    private static boolean isFiniteNumber(Object v) {
        return v instanceof Number && Double.isFinite(((Number) v).doubleValue());
    }

    private static boolean truthy(Object v) {
        return v instanceof String ? !((String) v).isEmpty() : v != null && !Boolean.FALSE.equals(v);
    }

    private static Set<String> ids(Object list) {
        Set<String> s = new HashSet<>();
        if (list instanceof Collection) for (Object o : (Collection<?>) list) if (o != null) s.add(String.valueOf(o));
        return s;
    }

    private static String text(Object v) {
        return v instanceof String ? ((String) v).trim() : "";
    }
}
