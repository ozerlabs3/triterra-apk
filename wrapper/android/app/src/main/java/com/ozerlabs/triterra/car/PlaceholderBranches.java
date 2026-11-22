package com.ozerlabs.triterra.car;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * PLACEHOLDER DATA - these are NOT real TriTerra branches, only city-centre points so the
 * car surface renders without sign-in or any backend.
 *
 * DEMO MODE ONLY: shown solely on the debug-only demo screen, which the driver opens explicitly
 * from the sign-in-required message, always titled "מצב הדגמה · נתוני דמה". Never used as a
 * fallback where real data is expected.
 */
public final class PlaceholderBranches {
    private PlaceholderBranches() {}


    public static final List<Branch> LIST = Collections.unmodifiableList(Arrays.asList(
            new Branch("סניף לדוגמה – תל אביב", "נתוני דמה · מרכז העיר", 32.0853, 34.7818),
            new Branch("סניף לדוגמה – הרצליה", "נתוני דמה · מרכז העיר", 32.1624, 34.8447),
            new Branch("סניף לדוגמה – נתניה", "נתוני דמה · מרכז העיר", 32.3215, 34.8532)));
}
