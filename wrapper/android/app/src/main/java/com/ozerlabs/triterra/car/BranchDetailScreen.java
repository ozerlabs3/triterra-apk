package com.ozerlabs.triterra.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.ozerlabs.triterra.R;

/** One stop: its name and a single "נווט" action (the navigation handoff). */
final class BranchDetailScreen extends Screen {
    private final Branch branch;

    BranchDetailScreen(@NonNull CarContext ctx, @NonNull Branch branch) {
        super(ctx);
        this.branch = branch;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext ctx = getCarContext();
        Row.Builder row = new Row.Builder().setTitle(branch.name);
        if (!branch.address.isEmpty()) row.addText(branch.address);
        if (!branch.hasLocation()) row.addText(ctx.getString(R.string.car_no_point));
        return new PaneTemplate.Builder(new Pane.Builder()
                .addRow(row.build())
                .addAction(new Action.Builder().setTitle(ctx.getString(R.string.car_navigate))
                        .setOnClickListener(() -> CarUi.navigate(ctx, branch)).build())
                .build())
                .setTitle(branch.name)
                .setHeaderAction(Action.BACK)
                .build();
    }
}
