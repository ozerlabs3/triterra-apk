package com.ozerlabs.triterra.car;

import static org.junit.Assert.assertEquals;
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
        Map<String, Map<String, Object>> stores = new HashMap<>();
        stores.put("s1", map("name", "A", "city", "נתניה", "lat", "32.32", "lng", "34.85"));
        stores.put("s2", map("name", "B", "city", "הרצליה", "lat", 32.16, "lng", 34.84));
        stores.put("s3", map("name", "C", "lat", 999, "lng", 34.8));
        Map<String, Object> state = map(
                "route", Arrays.asList(map("id", "s1", "name", "סניף א"), map("id", "s2", "name", "סניף ב"),
                        map("id", "s3", "name", "סניף ג"), map("id", "s4", "name", "סניף ד")),
                "doneIds", Collections.singletonList("s4"),
                "skippedIds", Collections.emptyList(),
                "currentId", "s2");
        List<Branch> out = TodayPlan.remainingStops(state, stores);
        assertEquals(3, out.size());
        assertEquals("סניף ב", out.get(0).name);
        assertEquals("geo:32.16,34.84", out.get(0).geoUri());
        assertEquals("geo:32.32,34.85", out.get(1).geoUri()); // numeric strings accepted
        assertTrue(!out.get(2).hasLocation()); // out-of-range lat -> name search instead
        assertTrue(out.get(2).geoUri().startsWith("geo:0,0?q="));
    }

    @Test
    public void missingDayIsEmptyNotAnError() {
        assertTrue(TodayPlan.remainingStops(null, new HashMap<>()).isEmpty());
        assertTrue(TodayPlan.remainingStops(map("started", true), new HashMap<>()).isEmpty());
    }
}
