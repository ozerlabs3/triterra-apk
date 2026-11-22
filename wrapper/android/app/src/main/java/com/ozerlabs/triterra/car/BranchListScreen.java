package com.ozerlabs.triterra.car;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.PlaceListMapTemplate;
import androidx.car.app.model.Template;
import androidx.lifecycle.Lifecycle;

import com.ozerlabs.triterra.R;

import java.util.List;

/**
 * Root car screen. Three distinct, never-mixed states:
 *  (a) READY - the signed-in user's real stops (title "העצירות של היום");
 *  (b) SIGN_IN_REQUIRED / LOAD_FAILED - a message only, no stop list;
 *  (c) demo - only when {@code demo} is true: a separate screen, debug builds only, opened explicitly
 *      from (b), titled "מצב הדגמה · נתוני דמה" with rows marked "לדוגמה / נתוני דמה".
 * onGetTemplate only renders current state (the host may call it at any time); loading happens in reload().
 */
public final class BranchListScreen extends Screen {
    @Nullable private final TodayPlanRepository plan; // null = demo screen
    private final boolean debug;
    @Nullable private TodayPlan.Result result;          // null = loading
    private int generation;                              // drops results of superseded loads

    private BranchListScreen(@NonNull CarContext ctx, @Nullable TodayPlanRepository plan, boolean debug) {
        super(ctx);
        this.plan = plan;
        this.debug = debug;
        reload();
    }

    static BranchListScreen today(@NonNull CarContext ctx, @NonNull TodayPlanRepository plan, boolean debug) {
        return new BranchListScreen(ctx, plan, debug);
    }

    private static BranchListScreen demo(@NonNull CarContext ctx) {
        return new BranchListScreen(ctx, null, true);
    }

    private void reload() {
        if (plan == null) {
            result = TodayPlan.Result.ready(PlaceholderBranches.LIST);
            return;
        }
        int gen = ++generation;
        result = null;
        plan.loadToday(r -> {
            if (gen != generation || getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;
            result = r;
            invalidate();
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext ctx = getCarContext();
        boolean demo = plan == null;
        String title = ctx.getString(demo ? R.string.car_demo_title : R.string.car_list_title);
        Action header = demo ? Action.BACK : Action.APP_ICON;
        if (result == null) {
            return new PlaceListMapTemplate.Builder().setTitle(title).setHeaderAction(header).setLoading(true).build();
        }
        if (result.status != TodayPlan.Status.READY) return blockedTemplate(ctx, result.status);

        List<Branch> stops = result.stops;
        int shown = TodayPlan.visibleStops(stops.size(), CarUi.limit(ctx, ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST));
        ItemList.Builder items = new ItemList.Builder().setNoItemsMessage(ctx.getString(R.string.car_no_stops));
        for (Branch b : stops.subList(0, shown)) {
            items.addItem(CarUi.stopRow(b, () -> getScreenManager().push(new BranchDetailScreen(ctx, b))));
        }
        if (shown < stops.size()) {
            List<Branch> rest = stops.subList(shown, stops.size());
            items.addItem(CarUi.moreRow(ctx.getString(R.string.car_more_stops, rest.size()),
                    () -> getScreenManager().push(new MoreStopsScreen(ctx, rest, demo))));
        }
        PlaceListMapTemplate.Builder t = new PlaceListMapTemplate.Builder()
                .setTitle(title).setHeaderAction(header).setItemList(items.build());
        if (!demo) t.setOnContentRefreshListener(this::reload); // host-driven refresh (car API 5+)
        return t.build();
    }

    private Template blockedTemplate(CarContext ctx, TodayPlan.Status status) {
        MessageTemplate.Builder m = new MessageTemplate.Builder(ctx.getString(
                status == TodayPlan.Status.SIGN_IN_REQUIRED ? R.string.car_sign_in_required : R.string.car_load_failed))
                .setTitle(ctx.getString(R.string.car_list_title))
                .setHeaderAction(Action.APP_ICON)
                .addAction(new Action.Builder().setTitle(ctx.getString(R.string.car_retry))
                        .setOnClickListener(() -> { reload(); invalidate(); }).build());
        if (debug) {
            m.addAction(new Action.Builder().setTitle(ctx.getString(R.string.car_demo_open))
                    .setOnClickListener(() -> getScreenManager().push(demo(ctx))).build());
        }
        return m.build();
    }
}
