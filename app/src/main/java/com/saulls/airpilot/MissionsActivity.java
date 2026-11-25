package com.saulls.airpilot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Saved roof-scan missions: import, preview on a map, and fly. */
public class MissionsActivity extends Activity {

    public static final String EXTRA_SHOW = "show";
    private static final int RC_IMPORT = 42;
    private static final int RC_DRIVE_READ = 43;
    public static final String PLANNER_URL = "https://uberwulf.github.io/DJIDroneMavic/";

    private LinearLayout list;
    private MapView map;
    private TextView details;
    private Button flyButton, deleteButton;
    private Mission selected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        int pad = DensityUtil.dip2px(this, 16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(pad, 0, pad, pad / 2);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(DensityUtil.dip2px(this, 56));
        Button back = textButton("‹ Back");
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = new TextView(this);
        title.setText("Missions");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(20);
        title.setPadding(DensityUtil.dip2px(this, 8), 0, 0, 0);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button planner = pill("Open planner");
        planner.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PLANNER_URL))));
        bar.addView(planner);
        Button sync = pill("\u27F3 Drive");
        sync.setOnClickListener(v -> syncDrive(true));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                DensityUtil.dip2px(this, 48));
        sp.leftMargin = DensityUtil.dip2px(this, 8);
        bar.addView(sync, sp);
        Button imp = pill("Import file");
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, RC_IMPORT);
        });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                DensityUtil.dip2px(this, 48));
        ip.leftMargin = DensityUtil.dip2px(this, 8);
        bar.addView(imp, ip);
        root.addView(bar);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        body.addView(scroll, new LinearLayout.LayoutParams(DensityUtil.dip2px(this, 230), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        rp.leftMargin = pad;
        body.addView(right, rp);

        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        right.addView(map, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        details = new TextView(this);
        details.setTextColor(Ui.TEXT_2);
        details.setTextSize(13);
        foot.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        deleteButton = textButton("Delete");
        deleteButton.setTextColor(Ui.DANGER);
        deleteButton.setOnClickListener(v -> confirmDelete());
        foot.addView(deleteButton);
        flyButton = new Button(this);
        flyButton.setText("Fly this mission");
        flyButton.setAllCaps(false);
        flyButton.setTextColor(getColorStateList(R.color.on_accent_text));
        flyButton.setStateListAnimator(null);
        flyButton.setTextSize(16);
        flyButton.setTypeface(null, android.graphics.Typeface.BOLD);
        flyButton.setBackgroundResource(R.drawable.button_bg);
        flyButton.setPadding(pad, 0, pad, 0);
        flyButton.setOnClickListener(v -> fly());
        foot.addView(flyButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                DensityUtil.dip2px(this, 48)));
        right.addView(foot);

        setContentView(root);
        refresh(getIntent().getStringExtra(EXTRA_SHOW));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        refresh(intent.getStringExtra(EXTRA_SHOW));
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        if (DriveMissions.signedIn(this)) syncDrive(false);
    }

    private boolean syncing;

    /** Pull plans saved by the planner into the list (Google Drive: Air Pilot / Missions). */
    private void syncDrive(boolean userAsked) {
        if (syncing) return;
        if (!DriveMissions.signedIn(this)) {
            if (userAsked) new AlertDialog.Builder(this).setTitle("Google Drive")
                    .setMessage("Sign in to Google Drive in Safety & Settings first. Then plans you save from the planner "
                            + "with \"Save to Drive\" show up here automatically.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (userAsked && !DriveMissions.canReadAll(this)) {
            // One-time: allow reading plans that the PC's Google Drive app copied into the folder.
            com.google.android.gms.auth.api.signin.GoogleSignIn.requestPermissions(this, RC_DRIVE_READ,
                    DriveSync.account(this), new com.google.android.gms.common.api.Scope(DriveMissions.READ_SCOPE));
            return;
        }
        syncing = true;
        String keep = selected == null ? null : selected.id;
        new Thread(() -> {
            String msg;
            try {
                msg = DriveMissions.sync(this);
            } catch (Exception e) {
                msg = e.getMessage();
            }
            String result = msg;
            runOnUiThread(() -> {
                syncing = false;
                if (isDestroyed()) return;
                refresh(keep);
                if (userAsked || (result != null && !result.contains("up to date") && !result.startsWith("No plans"))) {
                    Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
                }
            });
        }, "drive-missions").start();
    }

    @Override
    protected void onPause() {
        map.onPause();
        super.onPause();
    }

    private Button textButton(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Ui.TEXT);
        b.setTextSize(15);
        b.setBackgroundResource(R.drawable.button_ghost_bg);
        b.setStateListAnimator(null);
        b.setMinHeight(DensityUtil.dip2px(this, 48));
        b.setMinimumHeight(DensityUtil.dip2px(this, 48));
        return b;
    }

    private Button pill(String t) {
        Button b = textButton(t);
        b.setTextSize(14);
        b.setBackgroundResource(R.drawable.button_secondary_bg);
        b.setPadding(DensityUtil.dip2px(this, 12), 0, DensityUtil.dip2px(this, 12), 0);
        return b;
    }

    private void refresh(String showId) {
        list.removeAllViews();
        List<Mission> all = Mission.all(this);
        if (all.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("No missions yet.\n\n1. On a computer, open the planner:\n" + PLANNER_URL
                    + "\n\n2. Draw a path, then either Send to phone (scan the QR code) or \u2601 Save to Drive. "
                    + "Plans saved to Drive appear here automatically.");
            t.setTextColor(Ui.TEXT_2);
            t.setTextSize(14);
            t.setTextIsSelectable(true);
            list.addView(t);
            select(null);
            return;
        }
        Mission show = all.get(0);
        for (Mission m : all) if (m.id.equals(showId)) show = m;
        for (Mission m : all) {
            TextView row = new TextView(this);
            row.setText((m.id.startsWith("d_") ? "\u2601 " : "") + m.name + "\n" + ("orbit".equals(m.kind) ? "Orbit" : "custom".equals(m.kind) ? "Custom path" : "Roof sweep") + " · "
                    + m.wps.size() + " points · " + Units.duration(m.estimatedSeconds()));
            row.setTextColor(Ui.TEXT);
            row.setTextSize(15);
            row.setLineSpacing(DensityUtil.dip2px(this, 2), 1f);
            int p = DensityUtil.dip2px(this, 12);
            row.setPadding(p, p, p, p);
            row.setBackground(Ui.cardButton(this, m == show));
            row.setOnClickListener(v -> refresh(m.id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = DensityUtil.dip2px(this, 8);
            list.addView(row, lp);
        }
        select(show);
    }

    private void select(Mission m) {
        selected = m;
        flyButton.setEnabled(m != null);
        flyButton.setAlpha(m != null ? 1f : 0.4f);
        deleteButton.setVisibility(m != null ? View.VISIBLE : View.GONE);
        map.getOverlays().clear();
        if (m == null) {
            details.setText("");
            map.invalidate();
            return;
        }
        boolean imp = Prefs.isImperial(this);
        List<String> problems = m.problems();
        details.setText(String.format(Locale.US, "%d photos · %s · ~%d%% battery · up to %s · %s",
                m.photos(), Units.duration(m.estimatedSeconds()), m.estimatedBatteryPct(),
                Units.distance(m.maxAlt(), imp), m.goHome ? "flies home after" : "hovers after")
                + (problems.isEmpty() ? "" : "\n⚠ " + TextUtils.join("\n⚠ ", problems)));

        List<GeoPoint> pts = new ArrayList<>();
        for (Mission.Wp w : m.wps) pts.add(new GeoPoint(w.lat, w.lng));
        if (m.house.size() >= 3) {
            Polygon house = new Polygon(map);
            List<GeoPoint> hp = new ArrayList<>();
            for (double[] h : m.house) hp.add(new GeoPoint(h[0], h[1]));
            house.setPoints(hp);
            house.getFillPaint().setColor(0x33FF6B6B);
            house.getOutlinePaint().setColor(0xFFFF6B6B);
            house.getOutlinePaint().setStrokeWidth(4f);
            map.getOverlays().add(house);
        }
        Polyline line = new Polyline(map);
        line.setPoints(pts);
        line.getOutlinePaint().setColor(0xFFFFD54F);
        line.getOutlinePaint().setStrokeWidth(6f);
        map.getOverlays().add(line);
        Marker start = new Marker(map);
        start.setPosition(pts.get(0));
        start.setTitle("Start");
        start.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(start);
        List<GeoPoint> all = new ArrayList<>(pts);
        for (double[] h : m.house) all.add(new GeoPoint(h[0], h[1]));
        map.post(() -> {
            if (map.getWidth() > 0) map.zoomToBoundingBox(BoundingBox.fromGeoPoints(all).increaseByScale(1.5f), false);
            else map.addOnFirstLayoutListener((v, l, t, r, b) ->
                    map.zoomToBoundingBox(BoundingBox.fromGeoPoints(all).increaseByScale(1.5f), false));
        });
        map.invalidate();
    }

    private void confirmDelete() {
        if (selected == null) return;
        Mission m = selected;
        new AlertDialog.Builder(this).setTitle("Delete \"" + m.name + "\"?")
                .setMessage(m.id.startsWith("d_") ? "This plan comes from Google Drive; delete it there (in the planner's Drive folder) "
                        + "or it will come back on the next sync." : null)
                .setPositiveButton("Delete", (d, w) -> {
                    Mission.delete(this, m.id);
                    refresh(null);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void fly() {
        if (selected == null) return;
        List<String> problems = selected.problems();
        if (!problems.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Can't fly this plan").setMessage(TextUtils.join("\n", problems))
                    .setPositiveButton("OK", null).show();
            return;
        }
        startActivity(new Intent(this, FlyActivity.class).putExtra(FlyActivity.EXTRA_MISSION, selected.id));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_DRIVE_READ) {
            syncing = false;
            if (resultCode == RESULT_OK) syncDrive(false);
            else Toast.makeText(this, "Without that, only plans saved by the planner's \u2601 button show up.", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode != RC_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream s = getContentResolver().openInputStream(data.getData())) {
            Mission m = Mission.fromStream(s);
            m.save(this);
            refresh(m.id);
            Toast.makeText(this, "Imported \"" + m.name + "\"", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "That isn't an Air Pilot plan: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
