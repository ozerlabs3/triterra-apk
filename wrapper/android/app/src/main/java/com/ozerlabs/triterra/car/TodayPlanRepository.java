package com.ozerlabs.triterra.car;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

/**
 * Source of today's stops for the car screen. READ-ONLY: implementations must never write.
 *
 * Planned Firestore implementation (field names pending the code owner's filtered contract):
 *   - read users/{uid}/myDays/{dayId} (dayId = Jerusalem-local date); route / current stop /
 *     completed stops live inside the envelope's state (envelope: state, revision, updatedAt);
 *     settings under myDaySettings; store details from stores filtered by regionId, with the
 *     user's storeOverrides layered on top.
 *   - deliver data only after the gate passes: Firebase Auth user + active profile + schema
 *     version + email/region match. uid comes from the signed-in user ONLY - never a
 *     hardcoded uid/email. If the gate fails, deliver an empty list (the screen says so).
 *   - no writes (the web app guards today's doc with a revision transaction), no Storage.
 */
public interface TodayPlanRepository {
    /** Delivers exactly one Result on the main thread. Never substitutes demo data. */
    void loadToday(@NonNull Consumer<TodayPlan.Result> onResult);
}
