package com.ozerlabs.triterra.car;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.HostException;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.CarLocation;
import androidx.car.app.model.Metadata;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Place;
import androidx.car.app.model.PlaceMarker;
import androidx.car.app.model.Row;
import androidx.car.app.versioning.CarAppApiLevels;

import com.ozerlabs.triterra.R;

/** Row builders and host helpers shared by the car screens. Row builders are static for JVM tests. */
final class CarUi {
    private CarUi() {}

    /** Documented default place/list limit on hosts too old (car API 1) to report one. */
    static final int FALLBACK_LIMIT = 6;

    /**
     * Map-list stop row. Browsable: PlaceListMapTemplate rejects non-browsable rows without a distance,
     * and distance would need the car's location (a permission this app does not take).
     */
    static Row stopRow(Branch b, OnClickListener open) {
        Row.Builder r = new Row.Builder().setTitle(b.name).setBrowsable(true).setOnClickListener(open);
        if (!b.address.isEmpty()) r.addText(b.address);
        if (b.hasLocation()) {
            r.setMetadata(new Metadata.Builder()
                    .setPlace(new Place.Builder(CarLocation.create(b.lat, b.lng))
                            .setMarker(new PlaceMarker.Builder().build())
                            .build())
                    .build());
        }
        return r.build();
    }

    /** The "+N more" entry: browsable, opens the rest as a plain list. */
    static Row moreRow(CharSequence title, OnClickListener open) {
        return new Row.Builder().setTitle(title).setBrowsable(true).setOnClickListener(open).build();
    }

    /** Host row limit for a content type (ConstraintManager needs car API 2+). */
    static int limit(@NonNull CarContext ctx, int contentLimitType) {
        if (ctx.getCarAppApiLevel() < CarAppApiLevels.LEVEL_2) return FALLBACK_LIMIT;
        return ctx.getCarService(ConstraintManager.class).getContentLimit(contentLimitType);
    }

    /** Hands the stop to the driver's default navigation app (Waze / Google Maps) on the car screen. */
    static void navigate(@NonNull CarContext ctx, Branch b) {
        try {
            ctx.startCarApp(new Intent(CarContext.ACTION_NAVIGATE, Uri.parse(b.geoUri())));
        } catch (HostException | SecurityException e) {
            CarToast.makeText(ctx, R.string.car_nav_failed, CarToast.LENGTH_LONG).show();
        }
    }
}
