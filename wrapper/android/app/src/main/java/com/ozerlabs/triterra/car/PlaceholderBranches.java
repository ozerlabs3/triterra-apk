package com.ozerlabs.triterra.car;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import androidx.annotation.NonNull;

/**
 * PLACEHOLDER DATA - these are NOT real TriTerra branches, only city-centre points so the
 * car surface renders without sign-in or any backend.
 *
 * INTERIM, DEBUG BUILDS ONLY: shown when the Firestore gate does not pass (today always, because
 * native sign-in is blocked - see wrapper/README.md), so the car surface can be checked on the DHU.
 */
public final class PlaceholderBranches implements TodayPlanRepository {

    public static final List<Branch> LIST = Collections.unmodifiableList(Arrays.asList(
            new Branch("סניף לדוגמה – תל אביב", "נתוני דמה · מרכז העיר", 32.0853, 34.7818),
            new Branch("סניף לדוגמה – הרצליה", "נתוני דמה · מרכז העיר", 32.1624, 34.8447),
            new Branch("סניף לדוגמה – נתניה", "נתוני דמה · מרכז העיר", 32.3215, 34.8532)));

    @Override
    public void loadTodayStops(@NonNull Consumer<List<Branch>> onResult) {
        onResult.accept(LIST);
    }
}
