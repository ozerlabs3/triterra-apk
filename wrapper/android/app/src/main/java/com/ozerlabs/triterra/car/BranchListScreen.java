package com.ozerlabs.triterra.car;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.HostException;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarLocation;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Metadata;
import androidx.car.app.model.Place;
import androidx.car.app.model.PlaceListMapTemplate;
import androidx.car.app.model.PlaceMarker;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.ozerlabs.triterra.R;

import java.util.List;

/** Today's stops on the host-drawn map; one tap on a row hands off to turn-by-turn navigation. */
public final class BranchListScreen extends Screen {
    private List<Branch> branches; // null = still loading

    public BranchListScreen(@NonNull CarContext carContext, @NonNull TodayPlanRepository plan) {
        super(carContext);
        plan.loadTodayStops(stops -> {
            branches = stops;
            invalidate();
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        // shortcut: no ConstraintManager place-list limit check; fine for the 3 placeholder rows, add it with the real data feed.
        if (branches == null) {
            return new PlaceListMapTemplate.Builder()
                    .setTitle(getCarContext().getString(R.string.car_list_title))
                    .setHeaderAction(Action.APP_ICON)
                    .setLoading(true)
                    .build();
        }
        ItemList.Builder items = new ItemList.Builder().setNoItemsMessage(getCarContext().getString(R.string.car_no_stops));
        for (Branch b : branches) {
            items.addItem(new Row.Builder()
                    .setTitle(b.name)
                    .addText(b.address)
                    .setMetadata(new Metadata.Builder()
                            .setPlace(new Place.Builder(CarLocation.create(b.lat, b.lng))
                                    .setMarker(new PlaceMarker.Builder().build())
                                    .build())
                            .build())
                    .setOnClickListener(() -> navigate(b))
                    .build());
        }
        return new PlaceListMapTemplate.Builder()
                .setTitle(getCarContext().getString(R.string.car_list_title))
                .setHeaderAction(Action.APP_ICON)
                .setItemList(items.build())
                .build();
    }

    private void navigate(Branch b) {
        try {
            getCarContext().startCarApp(new Intent(CarContext.ACTION_NAVIGATE, Uri.parse(b.geoUri())));
        } catch (HostException | SecurityException e) {
            CarToast.makeText(getCarContext(), R.string.car_nav_failed, CarToast.LENGTH_LONG).show();
        }
    }
}
