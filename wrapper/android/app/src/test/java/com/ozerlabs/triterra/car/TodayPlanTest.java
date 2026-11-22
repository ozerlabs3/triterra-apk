package com.ozerlabs.triterra.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TodayPlanTest {
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    public void dayIdIsJerusalemLocalDate() {
        // 2026-03-01T21:59:59Z = 23:59:59 in Jerusalem (UTC+2); one second later is the next local day.
        assertEquals("2026-03-01", TodayPlan.dayId(1772402399000L));
        assertEquals("2026-03-02", TodayPlan.dayId(1772402400000L));
    }

    @Test
    public void profileGateFailsClosed() {
        Map<String, Object> ok = map("active", true, "schemaVersion", 1L, "email", "A@x.com", "regionId", "r1");
        assertNull(TodayPlan.profileGateFailure(ok, "a@X.com"));
        assertEquals("no-profile", TodayPlan.profileGateFailure(null, "a@x.com"));
        ok.put("schemaVersion", "1");
        assertEquals("schema-version", TodayPlan.profileGateFailure(ok, "a@x.com"));
        ok.put("schemaVersion", 1L);
        assertEquals("email-mismatch", TodayPlan.profileGateFailure(ok, "b@x.com"));
        assertEquals("email-mismatch", TodayPlan.profileGateFailure(ok, null));
        ok.put("active", "true");
        assertEquals("inactive", TodayPlan.profileGateFailure(ok, "a@x.com"));
        ok.put("active", true);
        ok.remove("regionId");
        assertEquals("no-region", TodayPlan.profileGateFailure(ok, "a@x.com"));
    }

    @Test
    public void regionGateRequiresExactRegion() {
        Map<String, Object> region = map("active", true, "schemaVersion", 1L, "regionId", "r1");
        assertNull(TodayPlan.regionGateFailure(region, "r1"));
        assertEquals("region-mismatch", TodayPlan.regionGateFailure(region, "r2"));
        region.put("active", false);
        assertEquals("region-inactive", TodayPlan.regionGateFailure(region, "r1"));
    }

    @Test
    public void remainingStopsFollowRouteSkipDoneAndPutCurrentFirst() {
        Map<String, Map<String, Object>> base = new HashMap<>();
        base.put("s1", map("name", "raw1", "lat", "32.32", "lng", "34.85"));
        base.put("s2", map("name", "raw2", "lat", 32.16, "lng", 34.84));
        Map<String, Object> state = map(
                "route", Arrays.asList(map("id", "s1", "name", "סניף א"), map("id", "s2", "name", "סניף ב"),
                        map("id", "s4", "name", "סניף ד")),
                "doneIds", Collections.singletonList("s4"),
                "skippedIds", Collections.emptyList(),
                "currentId", "s2");
        List<Branch> out = TodayPlan.remainingStops(state, base, new HashMap<>());
        assertEquals(2, out.size());
        assertEquals("סניף ב", out.get(0).name);             // current first, snapshot (Hebrew) name
        assertEquals("geo:32.16,34.84", out.get(0).geoUri());
        assertEquals("geo:32.32,34.85", out.get(1).geoUri()); // numeric strings accepted (Number())
    }

    @Test
    public void coordinatesFollowEffectiveCoordinatesAndOverrides() {
        Map<String, Map<String, Object>> base = new HashMap<>();
        base.put("s1", map("lat", 31.0, "lng", 34.0));
        base.put("s2", map("lat", 31.0, "lng", 34.0));
        base.put("s3", map("lat", 31.0, "lng", 34.0));
        base.put("s4", map("lat", null, "lng", ""));
        Map<String, Map<String, Object>> ov = new HashMap<>();
        // pin fix wins only for the exact id
        ov.put("s1", map("coordinateOverride", map("siteId", "s1", "newLat", "32.5", "newLng", 34.9)));
        ov.put("s2", map("coordinateOverride", map("siteId", "other", "newLat", 32.5, "newLng", 34.9)));
        // edit-form lat/lng apply only with a name, and only as real numbers
        ov.put("s3", map("name", "edited", "lat", 32.0, "lng", "34.7"));
        ov.put("u", map("userAdded", true, "name", "בית לקוח", "lat", "32.1", "lng", "34.8"));
        Map<String, Object> state = map("route", Arrays.asList(
                map("id", "s1", "name", "a"), map("id", "s2", "name", "b"), map("id", "s3", "name", "c"),
                map("id", "s4", "name", "d", "lat", 30.0, "lng", 35.0), // base has no point, fresh store wins -> none
                map("id", "u", "name", "e"),
                map("id", "warehouse-x", "name", "מחסן", "lat", 32.2, "lng", 34.8))); // fixed facility: snapshot
        List<Branch> out = TodayPlan.remainingStops(state, base, ov);
        assertEquals("geo:32.5,34.9", out.get(0).geoUri());
        assertEquals("geo:31.0,34.0", out.get(1).geoUri());
        assertEquals("geo:32.0,34.0", out.get(2).geoUri()); // lat patched, string lng ignored
        assertFalse(out.get(3).hasLocation());
        assertEquals("geo:32.1,34.8", out.get(4).geoUri());
        assertEquals("geo:32.2,34.8", out.get(5).geoUri());
    }

    @Test
    public void hiddenStoreFallsBackToSnapshotAndMalformedUserStoreIsSkipped() {
        Map<String, Map<String, Object>> base = new HashMap<>();
        base.put("s1", map("lat", 31.0, "lng", 34.0));
        Map<String, Map<String, Object>> ov = new HashMap<>();
        ov.put("s1", map("hidden", true));
        ov.put("user-1", map("userAdded", true, "name", "x", "lat", "", "lng", 34.0));
        Map<String, Object> state = map("route", Arrays.asList(
                map("id", "s1", "name", "a", "lat", 32.0, "lng", 34.5),
                map("id", "user-1", "name", "b")));
        List<Branch> out = TodayPlan.remainingStops(state, base, ov);
        assertEquals("geo:32.0,34.5", out.get(0).geoUri());
        assertFalse(out.get(1).hasLocation());
    }

    @Test
    public void visibleStopsKeepsOneRowForMore() {
        assertEquals(3, TodayPlan.visibleStops(3, 6));
        assertEquals(6, TodayPlan.visibleStops(6, 6));
        assertEquals(5, TodayPlan.visibleStops(7, 6));
        assertEquals(0, TodayPlan.visibleStops(4, 1));
    }

    @Test
    public void blockedResultsNeverCarryStops() {
        assertTrue(TodayPlan.Result.blocked(TodayPlan.Status.SIGN_IN_REQUIRED).stops.isEmpty());
        assertTrue(TodayPlan.Result.blocked(TodayPlan.Status.LOAD_FAILED).stops.isEmpty());
    }

    @Test
    public void missingDayIsEmptyNotAnError() {
        assertTrue(TodayPlan.remainingStops(null, new HashMap<>(), new HashMap<>()).isEmpty());
        assertTrue(TodayPlan.remainingStops(map("started", true), new HashMap<>(), new HashMap<>()).isEmpty());
    }
}
