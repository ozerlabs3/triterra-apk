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
        // VERIFY WITH CODE OWNER: the contract says "email match (lowercase)" but does not name the field; "email" assumed.
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
     * Today's remaining stops in route order (current stop first), done/skipped removed.
     * state = myDays/{dayId}.state; stores = region stores by doc id, overrides already applied.
     * VERIFY WITH CODE OWNER: route stop "id" is assumed to be the store doc id; coordinates come from
     * the stop itself, else the store ("lat"/"lng", number or numeric string, as city-navigation.js reads them).
     */
    @SuppressWarnings("unchecked")
    public static List<Branch> remainingStops(Map<String, Object> state, Map<String, Map<String, Object>> stores) {
        List<Branch> out = new ArrayList<>();
        if (state == null || !(state.get("route") instanceof List)) return out; // day not started
        Set<String> gone = ids(state.get("doneIds"));
        gone.addAll(ids(state.get("skippedIds")));
        Object currentId = state.get("currentId");
        for (Object o : (List<Object>) state.get("route")) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> stop = (Map<String, Object>) o;
            String id = String.valueOf(stop.get("id"));
            if (stop.get("id") == null || gone.contains(id)) continue;
            Map<String, Object> store = stores.get(id);
            String name = text(stop.get("name"));
            if (name.isEmpty() && store != null) name = text(store.get("name"));
            if (name.isEmpty()) continue;
            double lat = coord(stop.get("lat"), 90), lng = coord(stop.get("lng"), 180);
            if ((Double.isNaN(lat) || Double.isNaN(lng)) && store != null) {
                lat = coord(store.get("lat"), 90);
                lng = coord(store.get("lng"), 180);
            }
            if (Double.isNaN(lat) || Double.isNaN(lng)) lat = lng = Double.NaN;
            Branch b = new Branch(name, store == null ? "" : text(store.get("city")), lat, lng);
            if (currentId != null && id.equals(String.valueOf(currentId))) out.add(0, b);
            else out.add(b);
        }
        return out;
    }

    private static Set<String> ids(Object list) {
        Set<String> s = new HashSet<>();
        if (list instanceof Collection) for (Object o : (Collection<?>) list) if (o != null) s.add(String.valueOf(o));
        return s;
    }

    private static String text(Object v) {
        return v instanceof String ? ((String) v).trim() : "";
    }

    private static double coord(Object v, double max) {
        double d = Double.NaN;
        if (v instanceof Number) d = ((Number) v).doubleValue();
        else if (v instanceof String && !((String) v).trim().isEmpty()) {
            try { d = Double.parseDouble(((String) v).trim()); } catch (NumberFormatException ignored) { }
        }
        return Double.isFinite(d) && Math.abs(d) <= max ? d : Double.NaN;
    }
}
