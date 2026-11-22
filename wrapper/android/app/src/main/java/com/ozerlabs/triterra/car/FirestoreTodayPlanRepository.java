package com.ozerlabs.triterra.car;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * READ-ONLY Firestore source for today's stops (contract: field-region-map main@4bdaab9).
 * Reads, in order, only after each gate passes:
 *   users/{uid} -> regions/{regionId} -> users/{uid}/myDays/{dayId}, stores (regionId ==),
 *   users/{uid}/storeOverrides.
 * There is no write, no transaction, no Storage and no Worker call anywhere in this class, by design:
 * the web app owns the myDays revision-transaction protocol.
 * Identity is the signed-in Firebase user only; nothing is hardcoded.
 */
public final class FirestoreTodayPlanRepository implements TodayPlanRepository {
    private static final String TAG = "TriTerraCar";
    @Nullable private final TodayPlanRepository whenGated;

    /** @param whenGated what to show when the gate fails (debug: the placeholder list; release: null = empty). */
    public FirestoreTodayPlanRepository(@Nullable TodayPlanRepository whenGated) {
        this.whenGated = whenGated;
    }

    @Override
    public void loadTodayStops(@NonNull Consumer<List<Branch>> onResult) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { gated("not-signed-in", onResult); return; }
        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid).get().addOnCompleteListener(p -> {
            if (!p.isSuccessful()) { gated("profile-read: " + p.getException(), onResult); return; }
            Map<String, Object> profile = p.getResult().getData();
            String fail = TodayPlan.profileGateFailure(profile, user.getEmail());
            if (fail != null) { gated(fail, onResult); return; }
            String regionId = (String) profile.get("regionId");

            db.collection("regions").document(regionId).get().addOnCompleteListener(r -> {
                if (!r.isSuccessful()) { gated("region-read: " + r.getException(), onResult); return; }
                String rFail = TodayPlan.regionGateFailure(r.getResult().getData(), regionId);
                if (rFail != null) { gated(rFail, onResult); return; }

                String dayId = TodayPlan.dayId(System.currentTimeMillis());
                Tasks.whenAllSuccess(
                        db.collection("users").document(uid).collection("myDays").document(dayId).get(),
                        db.collection("stores").whereEqualTo("regionId", regionId).get(),
                        db.collection("users").document(uid).collection("storeOverrides").get()
                ).addOnCompleteListener(all -> {
                    if (!all.isSuccessful()) { gated("plan-read: " + all.getException(), onResult); return; }
                    // Auth-epoch fence: drop the result if the account changed while we were reading.
                    FirebaseUser now = FirebaseAuth.getInstance().getCurrentUser();
                    if (now == null || !uid.equals(now.getUid())) { gated("account-changed", onResult); return; }
                    List<Object> res = all.getResult();
                    onResult.accept(TodayPlan.remainingStops(state((DocumentSnapshot) res.get(0)),
                            stores((QuerySnapshot) res.get(1), (QuerySnapshot) res.get(2))));
                });
            });
        });
    }

    /** The {state, revision, updatedAt} envelope is read as a whole; a missing doc = day not started. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(DocumentSnapshot day) {
        Object s = day.exists() ? day.get("state") : null;
        return s instanceof Map ? (Map<String, Object>) s : null;
    }

    // shortcut: overrides are a shallow field patch and user-added stores (projectUserStores) and fixed
    // warehouse/office facilities are not projected; port applyStoreOverrides exactly once its shape is confirmed.
    private static Map<String, Map<String, Object>> stores(QuerySnapshot stores, QuerySnapshot overrides) {
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (DocumentSnapshot d : stores.getDocuments()) byId.put(d.getId(), new HashMap<>(d.getData()));
        for (DocumentSnapshot o : overrides.getDocuments()) {
            Map<String, Object> s = byId.get(o.getId());
            if (s != null && o.getData() != null) s.putAll(o.getData());
        }
        return byId;
    }

    private void gated(String reason, Consumer<List<Branch>> onResult) {
        Log.w(TAG, "today plan not shown: " + reason); // reason codes only, never identity
        if (whenGated != null) whenGated.loadTodayStops(onResult);
        else onResult.accept(Collections.emptyList());
    }
}
