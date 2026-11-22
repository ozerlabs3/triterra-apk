package com.ozerlabs.triterra.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.ozerlabs.triterra.R;

import java.util.List;

/** The stops that did not fit on the map list, as a plain list (ListTemplate allows a non-clickable note row). */
final class MoreStopsScreen extends Screen {
    private final List<Branch> stops;
    private final boolean demo;

    MoreStopsScreen(@NonNull CarContext ctx, @NonNull List<Branch> stops, boolean demo) {
        super(ctx);
        this.stops = stops;
        this.demo = demo;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext ctx = getCarContext();
        int shown = TodayPlan.visibleStops(stops.size(), CarUi.limit(ctx, ConstraintManager.CONTENT_LIMIT_TYPE_LIST));
        ItemList.Builder items = new ItemList.Builder();
        for (Branch b : stops.subList(0, shown)) {
            items.addItem(new Row.Builder().setTitle(b.name)
                    .setOnClickListener(() -> getScreenManager().push(new BranchDetailScreen(ctx, b))).build());
        }
        // shortcut: no third page; beyond two pages the note says the rest appears as earlier stops are done.
        if (shown < stops.size()) {
            items.addItem(new Row.Builder().setTitle(ctx.getString(R.string.car_more_note, stops.size() - shown)).build());
        }
        return new ListTemplate.Builder()
                .setTitle(ctx.getString(demo ? R.string.car_demo_title : R.string.car_more_title))
                .setHeaderAction(Action.BACK)
                .setSingleList(items.build())
                .build();
    }
}
