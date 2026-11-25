package com.saulls.airpilot;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import dji.keysdk.CameraKey;
import dji.keysdk.KeyManager;
import dji.ux.panel.CameraSettingAdvancedPanel;
import dji.ux.panel.CameraSettingExposurePanel;
import dji.ux.utils.DJIProductUtil;
import dji.ux.widget.FPVOverlayWidget;
import dji.ux.widget.FPVWidget;
import dji.ux.widget.ThermalPaletteWidget;
import dji.ux.widget.config.CameraConfigApertureWidget;
import dji.ux.widget.config.CameraConfigEVWidget;
import dji.ux.widget.config.CameraConfigISOAndEIWidget;
import dji.ux.widget.config.CameraConfigSSDWidget;
import dji.ux.widget.config.CameraConfigShutterWidget;
import dji.ux.widget.config.CameraConfigStorageWidget;
import dji.ux.widget.config.CameraConfigWBWidget;
import dji.ux.widget.controls.CameraControlsWidget;
import dji.ux.widget.controls.LensControlWidget;

/**
 * Activity that shows all the UI elements together
 */
public class FlyActivity extends Activity implements Telemetry.Listener, SmartMove.Listener, ActiveTracker.Listener, MissionRunner.Listener, VisionTracker.Listener {

    public static final String EXTRA_MISSION = "mission";
    private Mission pendingMission;
    private TextView missionStart;

    private FlightMap mapWidget;
    private ViewGroup parentView;
    private FPVWidget fpvWidget;
    private FPVWidget secondaryFPVWidget;
    private FPVOverlayWidget fpvOverlayWidget;
    private RelativeLayout primaryVideoView;
    private FrameLayout secondaryVideoView;
    private boolean isMapMini = true;

    private CameraSettingExposurePanel cameraSettingExposurePanel;
    private CameraSettingAdvancedPanel cameraSettingAdvancedPanel;
    private CameraConfigISOAndEIWidget cameraConfigISOAndEIWidget;
    private CameraConfigShutterWidget cameraConfigShutterWidget;
    private CameraConfigApertureWidget cameraConfigApertureWidget;
    private CameraConfigEVWidget cameraConfigEVWidget;
    private CameraConfigWBWidget cameraConfigWBWidget;
    private CameraConfigStorageWidget cameraConfigStorageWidget;
    private CameraConfigSSDWidget cameraConfigSSDWidget;
    private CameraControlsWidget controlsWidget;
    private LensControlWidget lensControlWidget;
    private ThermalPaletteWidget thermalPaletteWidget;


    // Views that only show in Advanced (Pro) mode
    private static final int[] ADVANCED_ONLY = {
            R.id.camera, R.id.camera2, R.id.manual_focus, R.id.camera_lens_control,
            R.id.expose_switch, R.id.dashboard_widget
    };

    private TextView modeToggle;
    private View closePanelsButton;
    private View moveBanner;
    private TextView moveText;
    private TextView moveHint;
    private TrackOverlay trackOverlay;
    private View trackCancel;
    private ActiveTracker.Kind pendingKind;
    private boolean pendingRecord;
    private boolean wasFlying;
    private View homePanel, readyCard;
    private TextView homeArrow, homeText, readyTitle, readyRows, countdown;
    private boolean readyDismissed;
    private boolean clean;
    /** Hidden in clean view. */
    private static final int[] EXTRAS = {
            R.id.btn_home, R.id.mode_toggle, R.id.btn_moves, R.id.quick_row, R.id.btn_mission_start,
            R.id.ready_card, R.id.home_panel, R.id.simple_hud, R.id.map_widget, R.id.camera, R.id.camera2,
            R.id.manual_focus, R.id.camera_lens_control, R.id.expose_switch, R.id.dashboard_widget,
            R.id.remaining_flight_time
    };
    private Weather.Now weather;
    private long flightStartedAt;
    private android.app.AlertDialog landingDialog;
    private int edgeInset;
    private View simpleHud;
    private TextView hudAlt, hudDist, hudSpeed, hudVSpeed, hudTime;
    private boolean advanced;
    private boolean imperial;

    private int height;
    private int width;
    private int margin;
    private int deviceWidth;
    private int deviceHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fly);

        height = DensityUtil.dip2px(this, 100);
        width = DensityUtil.dip2px(this, 150);
        margin = DensityUtil.dip2px(this, 12);

        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        final Display display = windowManager.getDefaultDisplay();
        Point outPoint = new Point();
        display.getRealSize(outPoint);
        deviceHeight = outPoint.y;
        deviceWidth = outPoint.x;

        mapWidget = findViewById(R.id.map_widget);
        mapWidget.setOnClickListener(v -> onViewClick(mapWidget));

        initCameraView();
        parentView = (ViewGroup) findViewById(R.id.root_view);

        fpvWidget = findViewById(R.id.fpv_widget);
        fpvWidget.setOnClickListener(view -> onViewClick(fpvWidget));
        fpvOverlayWidget = findViewById(R.id.fpv_overlay_widget);
        primaryVideoView = findViewById(R.id.fpv_container);
        secondaryVideoView = findViewById(R.id.secondary_video_view);
        secondaryFPVWidget = findViewById(R.id.secondary_fpv_widget);
        secondaryFPVWidget.setOnClickListener(view -> swapVideoSource());

        fpvWidget.setCameraIndexListener((cameraIndex, lensIndex) -> cameraWidgetKeyIndexUpdated(fpvWidget.getCameraKeyIndex(), fpvWidget.getLensKeyIndex()));
        updateSecondaryVideoVisibility();

        // Never let the screen sleep while flying.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        simpleHud = findViewById(R.id.simple_hud);
        hudAlt = findViewById(R.id.hud_alt);
        hudDist = findViewById(R.id.hud_dist);
        hudSpeed = findViewById(R.id.hud_speed);
        hudVSpeed = findViewById(R.id.hud_vspeed);
        hudTime = findViewById(R.id.hud_time);
        modeToggle = findViewById(R.id.mode_toggle);
        findViewById(R.id.btn_home).setOnClickListener(v -> finish());
        findViewById(R.id.btn_clean).setOnClickListener(v -> {
            clean = !clean;
            Prefs.raw(this).edit().putBoolean("clean_view", clean).apply();
            applyMode();
        });
        applyEdgeInsets();
        homePanel = findViewById(R.id.home_panel);
        homeArrow = findViewById(R.id.home_arrow);
        homeText = findViewById(R.id.home_text);
        readyCard = findViewById(R.id.ready_card);
        readyTitle = findViewById(R.id.ready_title);
        readyRows = findViewById(R.id.ready_rows);
        countdown = findViewById(R.id.countdown);
        findViewById(R.id.ready_close).setOnClickListener(v -> {
            readyDismissed = true;
            readyCard.setVisibility(View.GONE);
        });
        findViewById(R.id.btn_look_down).setOnClickListener(v -> pointGimbal(-90));
        findViewById(R.id.btn_level).setOnClickListener(v -> pointGimbal(0));
        findViewById(R.id.btn_camera_modes).setOnClickListener(v -> showCameraModes());
        missionStart = findViewById(R.id.btn_mission_start);
        missionStart.setOnClickListener(v -> confirmMission());
        moveBanner = findViewById(R.id.move_banner);
        moveText = findViewById(R.id.move_text);
        findViewById(R.id.btn_moves).setOnClickListener(v -> showMovesDialog());
        moveHint = findViewById(R.id.move_hint);
        trackOverlay = findViewById(R.id.track_overlay);
        trackCancel = findViewById(R.id.track_cancel);
        trackOverlay.setOnBoxListener(this::onSubjectBoxed);
        trackOverlay.setOnSubjectListener(index -> {
            if (VisionTracker.get(this).isChoosing()) {
                VisionTracker.Det d = visionDets != null && index < visionDets.size() ? visionDets.get(index) : null;
                String problem = VisionTracker.get(this).pick(d);
                trackCancel.setVisibility(View.GONE);
                if (problem != null) {
                    new android.app.AlertDialog.Builder(this).setTitle("Can't start").setMessage(problem)
                            .setPositiveButton("OK", null).show();
                    return;
                }
                moveText.setText("Locking on\u2026");
                moveHint.setText("Move a stick or tap STOP to end");
                moveBanner.setVisibility(View.VISIBLE);
                return;
            }
            ActiveTracker.get().pick(index);
            FlightRecorder.event(this, "Picked subject #" + index);
            moveText.setText("Locking on…");
            moveHint.setText("Move a stick or tap STOP to end");
            moveBanner.setVisibility(View.VISIBLE);
            trackCancel.setVisibility(View.GONE);
        });
        trackCancel.setOnClickListener(v -> cancelSelecting());
        findViewById(R.id.move_stop).setOnClickListener(v -> {
            SmartMove.get(this).stop("Stopped.");
            ActiveTracker.get().stop();
            VisionTracker.get(this).stop("Stopped.");
            MissionRunner.get(this).stop("Mission stopped. The drone is hovering; fly it home when ready.");
        });
        closePanelsButton = findViewById(R.id.btn_close_panels);
        closePanelsButton.setOnClickListener(v -> closeAllPanels());
        parentView.getViewTreeObserver().addOnGlobalLayoutListener(this::updateCloseButton);
        enlargeCameraMenus();
        modeToggle.setOnClickListener(v -> {
            Prefs.setAdvanced(this, !advanced);
            applyMode();
        });
    }

    /** Re-hooks telemetry every few seconds, so a USB reconnect mid-flight picks right back up. */
    private final Runnable reattach = new Runnable() {
        @Override
        public void run() {
            Telemetry.get(FlyActivity.this).attach();
            releaseStaleAppControl();
            parentView.postDelayed(this, 3000);
        }
    };

    private long lastRelease;

    /**
     * Safety: if the link dropped during an automatic move, the drone can come back still in
     * app-control ("Joystick") mode with nothing driving it. Hand control back to the sticks.
     */
    private void releaseStaleAppControl() {
        Telemetry.Snapshot s = Telemetry.get(this).last();
        if (!s.connected || System.currentTimeMillis() - s.time > 2000) return;
        if (s.flightMode == null || !s.flightMode.toLowerCase(java.util.Locale.US).contains("joystick")) return;
        if (SmartMove.get(this).isRunning() || MissionRunner.get(this).isRunning() || ActiveTracker.get().isRunning()
                || VisionTracker.get(this).isRunning()) return;
        if (System.currentTimeMillis() - lastRelease < 5000) return;
        lastRelease = System.currentTimeMillis();
        dji.sdk.flightcontroller.FlightController fc = Telemetry.flightController();
        if (fc == null) return;
        fc.setVirtualStickModeEnabled(false, err -> {
            if (err == null) runOnUiThread(() -> toast("Control handed back to your sticks."));
        });
    }

    // ------------------------------------------------------------ missions

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadPendingMission(intent);
    }

    private void loadPendingMission(android.content.Intent intent) {
        String id = intent == null ? null : intent.getStringExtra(EXTRA_MISSION);
        if (id != null) {
            pendingMission = Mission.load(this, id);
            intent.removeExtra(EXTRA_MISSION);
        }
        if (MissionRunner.get(this).isRunning()) pendingMission = MissionRunner.get(this).mission();
        if (pendingMission == null) {
            missionStart.setVisibility(View.GONE);
            mapWidget.setPlannedPath(null);
            return;
        }
        java.util.List<org.osmdroid.util.GeoPoint> pts = new java.util.ArrayList<>();
        for (Mission.Wp w : pendingMission.wps) pts.add(new org.osmdroid.util.GeoPoint(w.lat, w.lng));
        mapWidget.setPlannedPath(pts);
        missionStart.setText("\u25B6  Start mission: " + pendingMission.name);
        missionStart.setVisibility(MissionRunner.get(this).isRunning() ? View.GONE : View.VISIBLE);
    }

    private void confirmMission() {
        Mission m = pendingMission;
        if (m == null) return;
        String problem = MissionRunner.get(this).preflight(m);
        if (problem != null) {
            logEvent("Mission \"" + m.name + "\" not ready: " + problem);
            new android.app.AlertDialog.Builder(this).setTitle("Not yet").setMessage(problem)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Remove mission", (d, w) -> {
                        pendingMission = null;
                        loadPendingMission(null);
                    }).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Fly \"" + m.name + "\"?")
                .setMessage(m.photos() + " photos, about " + Units.duration(m.estimatedSeconds()) + " and ~"
                        + m.estimatedBatteryPct() + "% battery. The drone flies the blue path on its own"
                        + (m.goHome ? " and then comes home." : " and then hovers.")
                        + "\n\nCheck the path is clear of trees and wires. Move a stick or tap STOP at any time to take over.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", (d, w) -> {
                    String err = MissionRunner.get(this).start(m);
                    logEvent("Mission \"" + m.name + "\"" + (err == null ? " started" : " couldn't start: " + err));
                    if (err != null) {
                        new android.app.AlertDialog.Builder(this).setTitle("Can't start").setMessage(err)
                                .setPositiveButton("OK", null).show();
                        return;
                    }
                    readyDismissed = true;
                    readyCard.setVisibility(View.GONE);
                    missionStart.setVisibility(View.GONE);
                    moveText.setText("Sending the plan to the drone…");
                    moveHint.setText("Move a stick or tap STOP to take over");
                    moveBanner.setVisibility(View.VISIBLE);
                })
                .show();
    }

    @Override
    public void onMissionProgress(Mission m, String text) {
        moveBanner.setVisibility(View.VISIBLE);
        moveText.setText(text);
    }

    @Override
    public void onMissionEnded(Mission m, String reason, boolean completed) {
        moveBanner.setVisibility(View.GONE);
        FlightRecorder.event(this, "Mission \"" + m.name + "\" ended: " + reason);
        if (completed) {
            pendingMission = null;
            loadPendingMission(null);
        } else {
            missionStart.setText("\u25B6  Restart mission: " + m.name);
            missionStart.setVisibility(View.VISIBLE);
        }
        new android.app.AlertDialog.Builder(this).setTitle(completed ? "Mission complete" : "Mission ended")
                .setMessage(reason).setPositiveButton("OK", null).show();
    }

    private long lastLowLightCheck;

    /** In Auto mode, brighten the camera as the sun gets low (and undo it when it's up again). */
    private void checkLowLight(Telemetry.Snapshot s) {
        long now = System.currentTimeMillis();
        if (now - lastLowLightCheck < 60_000 || !s.connected) return;
        lastLowLightCheck = now;
        double lat = s.lat, lng = s.lng;
        if (Double.isNaN(lat)) {
            android.location.Location me = PhoneLocation.get(this).last();
            if (me != null) {
                lat = me.getLatitude();
                lng = me.getLongitude();
            }
        }
        Boolean want = LowLight.autoDecision(this, lat, lng);
        if (want == null) return;
        LowLight.apply(this, want, () -> runOnUiThread(() -> {
            VoiceAlerts va = VoiceAlerts.get(this);
            va.say("lowlight", want
                    ? va.line("Low light mode on.", "Sun's going down. I've brightened up the camera for you.",
                    "Getting dark, so I'm letting more light into the camera.")
                    : va.line("Low light mode off.", "Plenty of light again. Camera's back to normal."), 60_000);
            toast(want ? "Sun's low: low-light camera mode on (+0.7 EV, 30 fps)." : "Daylight: camera back to normal.");
        }));
    }

    // ------------------------------------------------------------ home arrow

    private void updateHomePanel(Telemetry.Snapshot s) {
        boolean show = !clean && !advanced && s.flying && !Double.isNaN(s.distanceM) && s.distanceM > 5;
        homePanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        if (!Double.isNaN(s.homeRelBearing)) homeArrow.setRotation((float) s.homeRelBearing);
        StringBuilder t = new StringBuilder("Home ").append(Units.distance(s.distanceM, imperial));
        int color = 0xFFFFFFFF;
        if (s.returnSeconds > 0) t.append(" \u00B7 ").append(Units.duration(s.returnSeconds)).append(" back");
        if (s.returnBatteryNeeded > 0 && s.batteryPct > 0) {
            t.append(" \u00B7 needs ~").append(s.returnBatteryNeeded).append("%");
            int margin = s.batteryPct - s.returnBatteryNeeded;
            color = margin <= 5 ? Ui.DANGER : margin <= 15 ? Ui.WARN : Ui.SUCCESS;
        }
        homeText.setText(t);
        homeText.setTextColor(color);
        homeArrow.setTextColor(color);
    }

    // ------------------------------------------------------------ ready to fly

    private void updateReadyCard(Telemetry.Snapshot s) {
        boolean show = !clean && !readyDismissed && !s.flying && s.connected
                && System.currentTimeMillis() - s.time < 3000 && !ActiveTracker.get().isRunning();
        readyCard.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        int worst = 0;
        StringBuilder rows = new StringBuilder();

        int gps = s.satellites >= 10 && !Double.isNaN(s.homeLat) ? 0 : s.satellites >= 6 ? 1 : 2;
        worst = Math.max(worst, gps);
        rows.append(mark(gps)).append(" GPS: ").append(s.satellites).append(" satellites")
                .append(Double.isNaN(s.homeLat) ? ", home not set yet" : ", home point set").append('\n');

        int batt;
        if (s.batteryPct < 0) {
            batt = 1;
            rows.append(mark(1)).append(" Battery: reading…\n");
        } else {
            batt = s.batteryPct >= 60 ? 0 : s.batteryPct >= 30 ? 1 : 2;
            String temp = "";
            if (!Double.isNaN(s.batteryTempC)) {
                temp = " \u00B7 " + Math.round(s.batteryTempC) + "°C";
                if (s.batteryTempC < 15) { batt = Math.max(batt, 1); temp += " (cold: hover a minute first)"; }
                if (s.batteryTempC > 50) { batt = Math.max(batt, 1); temp += " (warm: let it cool)"; }
            }
            rows.append(mark(batt)).append(" Battery: ").append(s.batteryPct).append('%').append(temp).append('\n');
        }
        worst = Math.max(worst, batt);

        BatteryBook.Pack pack = BatteryBook.get(this, s.batterySerial);
        if (pack != null) {
            int h = pack.healthPct();
            int hl = h < 0 ? 0 : h >= 85 ? 0 : h >= 70 ? 1 : 2;
            worst = Math.max(worst, hl);
            rows.append(mark(hl)).append(' ').append(pack.name).append(": ")
                    .append(h >= 0 ? h + "% health" : "health unknown")
                    .append(pack.cycles >= 0 ? " \u00B7 " + pack.cycles + " cycles" : "").append('\n');
        }

        int comp = s.compassError ? 2 : 0;
        worst = Math.max(worst, comp);
        rows.append(mark(comp)).append(" Compass: ").append(s.compassError
                ? "interference \u2014 move away from metal or calibrate" : "OK").append('\n');

        if (!s.sdInserted) {
            worst = 2;
            rows.append(mark(2)).append(" SD card: none in the drone\n");
        } else if (s.sdRemainingMB >= 0) {
            int sd = s.sdFull || s.sdRemainingMB < 500 ? 2 : s.sdRemainingMB < 2000 ? 1 : 0;
            worst = Math.max(worst, sd);
            rows.append(mark(sd)).append(" SD card: ")
                    .append(String.format(java.util.Locale.US, "%.1f GB free", s.sdRemainingMB / 1024.0))
                    .append(s.sdRecordSecondsLeft > 0 ? " (~" + (s.sdRecordSecondsLeft / 60) + " min video)" : "")
                    .append('\n');
        }

        if (weather != null) {
            int wl = weather.level();
            worst = Math.max(worst, wl);
            rows.append(mark(wl)).append(' ').append(weather.summary(imperial))
                    .append(wl == 2 ? " \u2014 too windy for a Mavic Air" : wl == 1 ? " \u2014 fly carefully" : "");
        } else {
            rows.append(mark(1)).append(" Wind: unavailable (needs internet)");
        }

        readyTitle.setText(worst == 0 ? "\u2705 Ready to fly" : worst == 1 ? "\u26A0\uFE0F Check before flying" : "\u26D4 Not ready");
        readyRows.setText(rows.toString().trim());
    }

    private static String mark(int level) {
        return level == 0 ? "\u2714" : level == 1 ? "\u26A0" : "\u2716";
    }

    // ------------------------------------------------------------ gimbal + camera

    private void pointGimbal(float pitch) {
        dji.sdk.base.BaseProduct p = dji.sdk.sdkmanager.DJISDKManager.getInstance().getProduct();
        dji.sdk.gimbal.Gimbal g = p != null && p.isConnected() ? p.getGimbal() : null;
        if (g == null) {
            toast("Drone not connected.");
            return;
        }
        g.rotate(new dji.common.gimbal.Rotation.Builder()
                .mode(dji.common.gimbal.RotationMode.ABSOLUTE_ANGLE)
                .pitch(pitch)
                .time(1.0)
                .build(), err -> {
            if (err != null) runOnUiThread(() -> toast("Gimbal: " + err.getDescription()));
        });
    }

    private dji.sdk.camera.Camera camera() {
        dji.sdk.base.BaseProduct p = dji.sdk.sdkmanager.DJISDKManager.getInstance().getProduct();
        return p != null && p.isConnected() ? p.getCamera() : null;
    }

    private void toast(String t) {
        android.widget.Toast.makeText(this, t, android.widget.Toast.LENGTH_LONG).show();
        logEvent(t);
    }

    private String lastTrackStatus;

    private void logEvent(String t) {
        FlightRecorder.event(this, t);
    }

    private void showCameraModes() {
        boolean ll = LowLight.active(this);
        String[] items = {
                (ll ? "\u2600  Low light: ON (tap for normal)" : "\u263E  Low light: brighter for sunset / dusk"),
                "\u2913  Point camera straight down",
                "\u2194  Level the camera",
                "\u25C9  Single photo",
                "\u23F1  Photo in 3 seconds",
                "\u23F1  Photo in 10 seconds",
                "\u2630  Burst: 3 photos",
                "\u2630  Burst: 5 photos",
                "\u25CB  Panorama: Sphere (the drone spins and tilts, ~1 min)",
                "\u25E0  Panorama: 180\u00B0",
                "\u25AD  Panorama: Wide",
                "\u25AF  Panorama: Vertical",
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle("Camera")
                .setItems(items, (d, which0) -> {
                    if (which0 == 0) {
                        boolean on = !LowLight.active(this);
                        if (LowLight.AUTO.equals(LowLight.mode(this))) LowLight.setMode(this, on ? LowLight.ON : LowLight.OFF);
                        LowLight.apply(this, on, () -> runOnUiThread(() -> toast(on
                                ? "Low light on: brighter exposure, video at 30 fps or less."
                                : "Low light off: normal exposure.")));
                        return;
                    }
                    if (which0 == 1) {
                        pointGimbal(-90);
                        return;
                    }
                    if (which0 == 2) {
                        pointGimbal(0);
                        return;
                    }
                    int which = which0 - 3;
                    switch (which) {
                        case 0: shoot(dji.common.camera.SettingsDefinitions.ShootPhotoMode.SINGLE, null, null, "Photo taken"); break;
                        case 1: timerShot(3); break;
                        case 2: timerShot(10); break;
                        case 3: shoot(dji.common.camera.SettingsDefinitions.ShootPhotoMode.BURST,
                                dji.common.camera.SettingsDefinitions.PhotoBurstCount.BURST_COUNT_3, null, "Burst taken"); break;
                        case 4: shoot(dji.common.camera.SettingsDefinitions.ShootPhotoMode.BURST,
                                dji.common.camera.SettingsDefinitions.PhotoBurstCount.BURST_COUNT_5, null, "Burst taken"); break;
                        case 5: pano(dji.common.camera.SettingsDefinitions.PhotoPanoramaMode.PANORAMA_MODE_SPHERE); break;
                        case 6: pano(dji.common.camera.SettingsDefinitions.PhotoPanoramaMode.PANORAMA_MODE_180); break;
                        case 7: pano(dji.common.camera.SettingsDefinitions.PhotoPanoramaMode.PANORAMA_MODE_3X3); break;
                        default: pano(dji.common.camera.SettingsDefinitions.PhotoPanoramaMode.PANORAMA_MODE_1X3); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void timerShot(int seconds) {
        final int[] left = {seconds};
        countdown.setVisibility(View.VISIBLE);
        countdown.setText(String.valueOf(left[0]));
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                left[0]--;
                if (left[0] <= 0) {
                    countdown.setVisibility(View.GONE);
                    shoot(dji.common.camera.SettingsDefinitions.ShootPhotoMode.SINGLE, null, null, "Photo taken");
                } else {
                    countdown.setText(String.valueOf(left[0]));
                    if (left[0] <= 3) VoiceAlerts.get(FlyActivity.this).say("count" + left[0], String.valueOf(left[0]), 500);
                    parentView.postDelayed(this, 1000);
                }
            }
        };
        parentView.postDelayed(tick, 1000);
    }

    private void pano(dji.common.camera.SettingsDefinitions.PhotoPanoramaMode mode) {
        if (!Telemetry.get(this).last().flying) {
            toast("Take off first: the drone turns itself to shoot a panorama.");
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Panorama")
                .setMessage("The drone will hover and turn on its own while it takes the photos. "
                        + "Keep your thumbs off the sticks until it says it's done.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", (d, w) -> shoot(dji.common.camera.SettingsDefinitions.ShootPhotoMode.PANORAMA,
                        null, mode, "Panorama started. The drone stitches it when done."))
                .show();
    }

    /** Switch to photo mode with the given sub-mode, shoot once, then go back to single shots. */
    private void shoot(dji.common.camera.SettingsDefinitions.ShootPhotoMode mode,
                       dji.common.camera.SettingsDefinitions.PhotoBurstCount burst,
                       dji.common.camera.SettingsDefinitions.PhotoPanoramaMode panoMode, String done) {
        dji.sdk.camera.Camera cam = camera();
        if (cam == null) {
            toast("Drone not connected.");
            return;
        }
        Runnable fire = () -> cam.startShootPhoto(err -> runOnUiThread(() -> {
            if (err != null) {
                toast("Camera: " + err.getDescription());
            } else {
                android.widget.Toast.makeText(this, done, android.widget.Toast.LENGTH_SHORT).show();
                logEvent(done);
                VoiceAlerts va = VoiceAlerts.get(this);
                va.say("shot", mode == dji.common.camera.SettingsDefinitions.ShootPhotoMode.PANORAMA
                        ? va.line("Panorama started.", "Panorama started. Hands off the sticks, I'm doing a little spin.",
                        "Doing the panorama thing. Hold still, well, I'll hold still.")
                        : va.line("Photo taken.", "Got it.", "Say cheese. Too late, got it.", "Snap. That's a keeper."), 1000);
            }
            if (mode == dji.common.camera.SettingsDefinitions.ShootPhotoMode.BURST) {
                parentView.postDelayed(() -> cam.setShootPhotoMode(
                        dji.common.camera.SettingsDefinitions.ShootPhotoMode.SINGLE, null), 4000);
            }
        }));
        cam.setMode(dji.common.camera.SettingsDefinitions.CameraMode.SHOOT_PHOTO, e1 -> {
            cam.setShootPhotoMode(mode, e2 -> {
                if (e2 != null) {
                    runOnUiThread(() -> toast("This camera doesn't support that mode: " + e2.getDescription()));
                    return;
                }
                if (burst != null) cam.setPhotoBurstCount(burst, e3 -> fire.run());
                else if (panoMode != null) cam.setPhotoPanoramaMode(panoMode, e3 -> {
                    if (e3 != null) runOnUiThread(() -> toast("Panorama type not supported: " + e3.getDescription()));
                    else fire.run();
                });
                else fire.run();
            });
        });
    }

    // ------------------------------------------------------------ smart moves

    private void showMovesDialog() {
        if (SmartMove.get(this).isRunning() || ActiveTracker.get().isRunning() || VisionTracker.get(this).isRunning()) return;
        final ActiveTracker.Kind[] kinds = ActiveTracker.Kind.values();
        // DJI vision tracking (ActiveTrack) times out on the Mavic Air from third-party apps, so it's
        // only offered on other drones.
        dji.sdk.base.BaseProduct prod = dji.sdk.sdkmanager.DJISDKManager.getInstance().getProduct();
        boolean trackingOk = true; // retrying with DJI's subject-sensing method
        final int base = 6;
        String[] items = new String[trackingOk ? kinds.length + base : base];
        items[0] = "\uD83D\uDC41  Track & follow  (phone vision: person, bike, car, dog)";
        items[1] = "\uD83D\uDC41  Spotlight  (stays put, turns to keep them in frame)";
        items[2] = "\uD83D\uDEB4  Follow me  (uses your phone's GPS)";
        items[3] = "\u21BB  Orbit me  (circles your phone's GPS)";
        items[4] = "\u21BB  Orbit a spot  (the spot the camera faces)";
        items[5] = "\u2196  Dronie  (fly back and up)";
        if (trackingOk) for (int i = 0; i < kinds.length; i++) items[i + base] = "\u25CE  " + kinds[i].title + "  (DJI tracking, experimental)";
        new android.app.AlertDialog.Builder(this)
                .setTitle("Smart moves")
                .setItems(items, (d, which) -> {
                    if (which == 0) confirmVision(VisionTracker.Mode.FOLLOW);
                    else if (which == 1) confirmVision(VisionTracker.Mode.SPOTLIGHT);
                    else if (which == 2) showMeMoveDialog(true);
                    else if (which == 3) showMeMoveDialog(false);
                    else if (which < 6) showGpsMoveDialog(which == 4);
                    else confirmTrack(kinds[which - base]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Follow me / Orbit me setup, using the phone's GPS as "you". */
    private void showMeMoveDialog(boolean follow) {
        boolean imp = imperial;
        // Follow has a "Close" option (15 ft behind) that only flies 33 ft or higher, so GPS wander can't bring it near you.
        final double[] sizes = follow ? new double[]{4.5, 8, 12, 20} : new double[]{5, 8, 12};
        final double[] heights = {6, 10, 15};
        String[] sizeNames = new String[sizes.length], heightNames = new String[3];
        for (int i = 0; i < sizes.length; i++) sizeNames[i] = Units.distance(sizes[i], imp) + (follow && i == 0 ? " close" : "");
        for (int i = 0; i < 3; i++) heightNames[i] = Units.distance(heights[i], imp);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = DensityUtil.dip2px(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView help = new TextView(this);
        help.setTextColor(Ui.TEXT);
        help.setTextSize(14);
        help.setText(follow
                ? "The drone stays behind you at the set distance and height, camera on you, using your phone's GPS. "
                + "Keep the controller and phone with you. Great for a bike ride or a run.\n\nOpen areas only: "
                + "the drone can't see obstacles to its sides, so fly high enough to clear trees and wires."
                : "The drone circles your phone's position with the camera on you. For a cartwheel, set the "
                + "controller down where you'll be. Keep the area clear.");
        box.addView(help);
        TextView l1 = new TextView(this);
        l1.setText(follow ? "\nDISTANCE BEHIND YOU" : "\nCIRCLE RADIUS");
        l1.setTextColor(Ui.ACCENT);
        box.addView(l1);
        android.widget.RadioGroup size = radioRow(sizeNames, 1);
        TextView closeNote = new TextView(this);
        closeNote.setTextColor(Ui.WARN);
        closeNote.setTextSize(13);
        closeNote.setText("Close: flies at least " + Units.distance(10, imp) + " high, since phone GPS can be off by 10\u201315 ft. "
                + "Open areas only.");
        closeNote.setVisibility(View.GONE);
        box.addView(size);
        android.widget.RadioGroup height = null;
        if (follow) {
            TextView l2 = new TextView(this);
            l2.setText("\nHEIGHT");
            l2.setTextColor(Ui.ACCENT);
            box.addView(l2);
            height = radioRow(heightNames, 1);
            box.addView(height);
            box.addView(closeNote);
            final android.widget.RadioGroup hg = height;
            size.setOnCheckedChangeListener((g, id) -> {
                boolean close = g.indexOfChild(g.findViewById(id)) == 0;
                closeNote.setVisibility(close ? View.VISIBLE : View.GONE);
                View low = hg.getChildAt(0);
                low.setEnabled(!close);
                if (close && hg.indexOfChild(hg.findViewById(hg.getCheckedRadioButtonId())) == 0)
                    ((android.widget.RadioButton) hg.getChildAt(1)).setChecked(true);
            });
        }
        android.widget.CheckBox record = new android.widget.CheckBox(this);
        record.setText("Record video");
        record.setChecked(true);
        record.setTextColor(0xFFFFFFFF);
        box.addView(record);
        final android.widget.RadioGroup heightGroup = height;
        new android.app.AlertDialog.Builder(this)
                .setTitle(follow ? "Follow me" : "Orbit me")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", (d, w) -> {
                    SmartMove.Params p = new SmartMove.Params();
                    p.type = follow ? SmartMove.Type.FOLLOW_ME : SmartMove.Type.ORBIT_ME;
                    int si = size.indexOfChild(size.findViewById(size.getCheckedRadioButtonId()));
                    p.leashM = sizes[Math.max(0, si)];
                    p.radiusM = sizes[Math.max(0, si)];
                    if (heightGroup != null) {
                        int hi = heightGroup.indexOfChild(heightGroup.findViewById(heightGroup.getCheckedRadioButtonId()));
                        p.heightM = heights[Math.max(0, hi)];
                    }
                    if (follow && p.leashM < 6) p.heightM = Math.max(p.heightM, 10); // close follow stays high
                    p.speedMps = 2.5;
                    p.continuous = true;
                    p.clockwise = true;
                    p.recordVideo = record.isChecked();
                    String problem = SmartMove.get(this).start(p);
                    logEvent((follow ? "Follow me" : "Orbit me") + (problem == null ? " started" : " couldn't start: " + problem));
                    if (problem != null) {
                        new android.app.AlertDialog.Builder(this).setTitle("Can't start").setMessage(problem)
                                .setPositiveButton("OK", null).show();
                    } else {
                        moveText.setText((follow ? "Follow me" : "Orbit me") + " starting…");
                        moveHint.setText("Move any stick to take over");
                        moveBanner.setVisibility(View.VISIBLE);
                    }
                })
                .show();
    }

    private android.widget.RadioGroup radioRow(String[] labels, int checked) {
        android.widget.RadioGroup g = new android.widget.RadioGroup(this);
        g.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            android.widget.RadioButton b = new android.widget.RadioButton(this);
            b.setId(View.generateViewId());
            b.setText(labels[i]);
            b.setTextColor(0xFFFFFFFF);
            g.addView(b, new android.widget.RadioGroup.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i == checked) b.setChecked(true);
        }
        return g;
    }

    private void confirmTrack(ActiveTracker.Kind kind) {
        android.widget.CheckBox record = new android.widget.CheckBox(this);
        record.setText("Record video while tracking");
        record.setChecked(true);
        record.setTextColor(0xFFFFFFFF);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = DensityUtil.dip2px(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView tips = new TextView(this);
        tips.setTextColor(Ui.TEXT);
        tips.setTextSize(14);
        tips.setText(kind.help + "\n\nTips: hover 3\u201310 m (10\u201330 ft) from the person at head height or a bit above, "
                + "with the whole body in view. Open areas work best \u2014 the drone can't see obstacles to its sides. "
                + "Move a stick or tap STOP to end it.");
        box.addView(tips);
        if (!kind.title.startsWith("QuickShot")) box.addView(record);
        new android.app.AlertDialog.Builder(this)
                .setTitle(kind.title)
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose person", (d, w) -> {
                    closeAllPanels();
                    if (ActiveTracker.get().autoSensingSupported()) {
                        // Newer DJI method: the drone detects people, you tap one.
                        String problem = ActiveTracker.get().beginSensing(this, kind, record.isChecked());
                        FlightRecorder.event(this, kind.title + ": subject sensing " + (problem == null ? "started" : "couldn't start: " + problem));
                        if (problem != null) {
                            new android.app.AlertDialog.Builder(this).setTitle("Can't start").setMessage(problem)
                                    .setPositiveButton("OK", null).show();
                            return;
                        }
                        lastTrackStatus = null;
                        trackOverlay.setSubjects(new java.util.ArrayList<>(), new int[0]);
                        trackCancel.setVisibility(View.VISIBLE);
                        return;
                    }
                    FlightRecorder.event(this, kind.title + ": this drone doesn't support subject sensing; using draw-a-box");
                    pendingKind = kind;
                    pendingRecord = record.isChecked();
                    trackOverlay.setSelecting(true);
                    trackCancel.setVisibility(View.VISIBLE);
                })
                .show();
    }

    // ------------------------------------------------------------ phone-vision tracking

    private java.util.List<VisionTracker.Det> visionDets;

    private void confirmVision(VisionTracker.Mode mode) {
        boolean follow = mode == VisionTracker.Mode.FOLLOW;
        android.widget.CheckBox record = new android.widget.CheckBox(this);
        record.setText("Record video while tracking");
        record.setChecked(true);
        record.setTextColor(0xFFFFFFFF);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = DensityUtil.dip2px(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView tips = new TextView(this);
        tips.setTextColor(Ui.TEXT);
        tips.setTextSize(14);
        tips.setText((follow
                ? "Your phone watches the video and steers the drone: it turns to keep the subject centred and moves "
                + "forward or back to keep them the same size, so it follows as they walk, run or ride away."
                : "The drone stays where it is and turns (and tilts the camera) to keep the subject in frame.")
                + "\n\nNew and experimental: start slow, in an open area, 15\u201330 ft from the subject at head height or "
                + "a bit above, whole body in view. It holds height and won't fly sideways. It hovers if it loses "
                + "them and stops after 10 seconds. Move a stick or tap STOP any time.");
        box.addView(tips);
        box.addView(record);
        new android.app.AlertDialog.Builder(this)
                .setTitle(follow ? "Track & follow" : "Spotlight")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose subject", (d, w) -> {
                    closeAllPanels();
                    String problem = VisionTracker.get(this).beginChoosing(videoView(), mode, record.isChecked());
                    if (problem != null) {
                        new android.app.AlertDialog.Builder(this).setTitle("Can't start").setMessage(problem)
                                .setPositiveButton("OK", null).show();
                        return;
                    }
                    visionDets = null;
                    trackOverlay.setSubjects(new java.util.ArrayList<>(), new int[0]);
                    trackCancel.setVisibility(View.VISIBLE);
                })
                .show();
    }

    @Override
    public void onVisionDetections(java.util.List<VisionTracker.Det> dets) {
        visionDets = dets;
        java.util.List<android.graphics.RectF> view = new java.util.ArrayList<>();
        int[] idx = new int[dets.size()];
        for (int i = 0; i < dets.size(); i++) {
            view.add(fromVideo(dets.get(i).box));
            idx[i] = i;
        }
        trackOverlay.setSubjects(view, idx);
    }

    @Override
    public void onVisionTrack(android.graphics.RectF box, String status, int quality) {
        moveBanner.setVisibility(View.VISIBLE);
        moveText.setText(status);
        moveHint.setText("Move a stick or tap STOP to end");
        if (box == null) trackOverlay.setTarget(null, 0);
        else trackOverlay.setTarget(fromVideo(box), quality == 2 ? 0xFF4ADE80 : quality == 1 ? 0xFFFBBF24 : 0xFFF87171);
    }

    @Override
    public void onVisionEnded(String reason) {
        visionDets = null;
        trackOverlay.setSubjects(null, null);
        trackOverlay.setTarget(null, 0);
        trackCancel.setVisibility(View.GONE);
        if (!SmartMove.get(this).isRunning() && !MissionRunner.get(this).isRunning()) moveBanner.setVisibility(View.GONE);
        android.widget.Toast.makeText(this, reason, android.widget.Toast.LENGTH_LONG).show();
    }

    private void cancelSelecting() {
        if (VisionTracker.get(this).isChoosing()) {
            VisionTracker.get(this).cancelChoosing();
            trackOverlay.setSubjects(null, null);
        }
        if (ActiveTracker.get().isSensing()) {
            ActiveTracker.get().cancelSensing();
            trackOverlay.setSubjects(null, null);
        }
        pendingKind = null;
        trackOverlay.setSelecting(false);
        trackCancel.setVisibility(View.GONE);
    }

    private void onSubjectBoxed(android.graphics.RectF screenBox) {
        trackCancel.setVisibility(View.GONE);
        ActiveTracker.Kind kind = pendingKind;
        pendingKind = null;
        if (kind == null) return;
        android.graphics.RectF norm = toVideo(screenBox);
        String problem = norm == null ? "Live video isn't showing yet."
                : ActiveTracker.get().start(this, kind, norm, pendingRecord);
        lastTrackStatus = null;
        FlightRecorder.event(this, kind.title + (problem == null ? " started" : " couldn't start: " + problem));
        if (problem != null) {
            trackOverlay.setTarget(null, 0);
            new android.app.AlertDialog.Builder(this).setTitle("Can't start").setMessage(problem)
                    .setPositiveButton("OK", null).show();
            return;
        }
        trackOverlay.setTarget(screenBox, 0xFFFFFFFF);
        moveText.setText("Locking on…");
        moveHint.setText("Move a stick or tap STOP to end");
        moveBanner.setVisibility(View.VISIBLE);
    }

    /** The actual video picture inside the FPV widget (it's letterboxed to the camera's aspect ratio). */
    private View videoView() {
        View tv = findTexture(fpvWidget);
        return tv != null ? tv : fpvWidget;
    }

    private static View findTexture(View v) {
        if (v instanceof android.view.TextureView || v instanceof android.view.SurfaceView) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = findTexture(g.getChildAt(i));
                if (f != null) return f;
            }
        }
        return null;
    }

    /** Overlay coordinates -> normalized video coordinates (0..1). */
    private android.graphics.RectF toVideo(android.graphics.RectF r) {
        View video = videoView();
        if (video.getWidth() == 0 || video.getHeight() == 0) return null;
        int[] vo = new int[2], oo = new int[2];
        video.getLocationOnScreen(vo);
        trackOverlay.getLocationOnScreen(oo);
        float w = video.getWidth(), h = video.getHeight();
        float l = (r.left + oo[0] - vo[0]) / w, t = (r.top + oo[1] - vo[1]) / h;
        float rr = (r.right + oo[0] - vo[0]) / w, b = (r.bottom + oo[1] - vo[1]) / h;
        return new android.graphics.RectF(clamp01(l), clamp01(t), clamp01(rr), clamp01(b));
    }

    /** Normalized video coordinates -> overlay coordinates. */
    private android.graphics.RectF fromVideo(android.graphics.RectF n) {
        View video = videoView();
        int[] vo = new int[2], oo = new int[2];
        video.getLocationOnScreen(vo);
        trackOverlay.getLocationOnScreen(oo);
        float w = video.getWidth(), h = video.getHeight();
        return new android.graphics.RectF(n.left * w + vo[0] - oo[0], n.top * h + vo[1] - oo[1],
                n.right * w + vo[0] - oo[0], n.bottom * h + vo[1] - oo[1]);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    public void onTrackUpdate(ActiveTracker.Kind kind, String status, android.graphics.RectF target, int confidence) {
        moveBanner.setVisibility(View.VISIBLE);
        String key = status.replaceAll("[0-9]+%", "").trim();
        if (!key.equals(lastTrackStatus)) {
            lastTrackStatus = key;
            FlightRecorder.event(this, kind.title + ": " + status);
        }
        moveText.setText(status);
        if (target != null && target.width() > 0) {
            int color = confidence == 2 ? 0xFF4CAF50 : confidence == 1 ? 0xFFFFC107 : confidence < 0 ? 0xFFE53935 : 0xFFFFFFFF;
            trackOverlay.setTarget(fromVideo(target), color);
        }
    }

    @Override
    public void onSubjects(ActiveTracker.Kind kind, java.util.List<android.graphics.RectF> rects, int[] indexes) {
        java.util.List<android.graphics.RectF> view = new java.util.ArrayList<>();
        for (android.graphics.RectF r : rects) view.add(fromVideo(r));
        trackOverlay.setSubjects(view, indexes);
        if (!rects.isEmpty() && lastTrackStatus == null) {
            lastTrackStatus = "subjects";
            FlightRecorder.event(this, kind.title + ": drone detected " + rects.size() + " subject(s)");
        }
    }

    @Override
    public void onTrackEnded(ActiveTracker.Kind kind, String reason) {
        trackOverlay.setSubjects(null, null);
        trackCancel.setVisibility(View.GONE);
        moveBanner.setVisibility(View.GONE);
        FlightRecorder.event(this, (kind == null ? "Tracking" : kind.title) + " ended: " + reason);
        trackOverlay.setTarget(null, 0);
        android.widget.Toast.makeText(this, reason, android.widget.Toast.LENGTH_LONG).show();
    }

    private void showGpsMoveDialog(boolean orbitFirst) {
        boolean imperial = Prefs.isImperial(this);
        View v = getLayoutInflater().inflate(R.layout.dialog_moves, null);
        android.widget.RadioGroup type = v.findViewById(R.id.move_type);
        TextView help = v.findViewById(R.id.move_help);
        TextView sizeLabel = v.findViewById(R.id.size_label);
        android.widget.RadioButton sizeS = v.findViewById(R.id.size_s);
        android.widget.RadioButton sizeM = v.findViewById(R.id.size_m);
        android.widget.RadioButton sizeL = v.findViewById(R.id.size_l);
        View orbitOptions = v.findViewById(R.id.orbit_options);
        final double[] orbitSizes = {10, 15, 30};
        final double[] dronieSizes = {30, 50, 100};

        Runnable refresh = () -> {
            boolean orbit = type.getCheckedRadioButtonId() == R.id.type_orbit;
            double[] sizes = orbit ? orbitSizes : dronieSizes;
            sizeLabel.setText(orbit ? "CIRCLE RADIUS" : "HOW FAR BACK");
            sizeS.setText(Units.distance(sizes[0], imperial));
            sizeM.setText(Units.distance(sizes[1], imperial));
            sizeL.setText(Units.distance(sizes[2], imperial));
            orbitOptions.setVisibility(orbit ? View.VISIBLE : View.GONE);
            help.setText(orbit
                    ? "Circles the spot the camera is pointing at, keeping it in frame. Point the camera at your subject first."
                    : "Flies backward and up while filming you, for a reveal shot. Face the camera at yourself first.");
        };
        type.check(orbitFirst ? R.id.type_orbit : R.id.type_dronie);
        type.setOnCheckedChangeListener((g, id) -> refresh.run());
        refresh.run();

        new android.app.AlertDialog.Builder(this)
                .setTitle("Smart moves")
                .setView(v)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", (d, w) -> {
                    SmartMove.Params p = new SmartMove.Params();
                    boolean orbit = type.getCheckedRadioButtonId() == R.id.type_orbit;
                    p.type = orbit ? SmartMove.Type.ORBIT : SmartMove.Type.DRONIE;
                    int sizeId = ((android.widget.RadioGroup) v.findViewById(R.id.size)).getCheckedRadioButtonId();
                    int idx = sizeId == R.id.size_s ? 0 : sizeId == R.id.size_l ? 2 : 1;
                    p.radiusM = orbitSizes[idx];
                    p.distanceM = dronieSizes[idx];
                    int speedId = ((android.widget.RadioGroup) v.findViewById(R.id.speed)).getCheckedRadioButtonId();
                    double[] speeds = orbit ? new double[]{1.5, 3, 5} : new double[]{2, 4, 6};
                    p.speedMps = speeds[speedId == R.id.speed_slow ? 0 : speedId == R.id.speed_fast ? 2 : 1];
                    p.clockwise = ((android.widget.RadioGroup) v.findViewById(R.id.direction))
                            .getCheckedRadioButtonId() == R.id.dir_cw;
                    p.continuous = ((android.widget.CheckBox) v.findViewById(R.id.continuous)).isChecked();
                    p.recordVideo = ((android.widget.CheckBox) v.findViewById(R.id.record)).isChecked();
                    String problem = SmartMove.get(this).start(p);
                    logEvent((orbit ? "Orbit" : "Dronie") + (problem == null ? " started" : " couldn't start: " + problem));
                    if (problem != null) {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("Can't start")
                                .setMessage(problem)
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        moveText.setText(orbit ? "Orbit starting…" : "Dronie starting…");
                        moveHint.setText("Move any stick to take over");
                        moveBanner.setVisibility(View.VISIBLE);
                    }
                })
                .show();
    }

    @Override
    public void onMoveProgress(SmartMove.Type type, float progress, String text) {
        moveBanner.setVisibility(View.VISIBLE);
        moveText.setText(text);
    }

    @Override
    public void onMoveEnded(SmartMove.Type type, String reason, boolean completed) {
        moveBanner.setVisibility(View.GONE);
        FlightRecorder.event(this, SmartMove.title(type) + " ended: " + reason);
        android.widget.Toast.makeText(this, reason, android.widget.Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------ after landing

    private void onLanded() {
        if (!Prefs.isAutoDownload(this) || isFinishing()) return;
        long since = flightStartedAt > 0 ? flightStartedAt : System.currentTimeMillis() - 30 * 60 * 1000L;
        final int[] seconds = {5};
        landingDialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Landed")
                .setMessage("Downloading this flight's photos and videos in 5…")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Download now", (d, w) -> startAutoDownload(since))
                .show();
        Runnable countdown = new Runnable() {
            @Override
            public void run() {
                if (landingDialog == null || !landingDialog.isShowing()) return;
                seconds[0]--;
                if (seconds[0] <= 0) {
                    landingDialog.dismiss();
                    startAutoDownload(since);
                } else {
                    landingDialog.setMessage("Downloading this flight's photos and videos in " + seconds[0] + "…");
                    parentView.postDelayed(this, 1000);
                }
            }
        };
        parentView.postDelayed(countdown, 1000);
    }

    private void startAutoDownload(long since) {
        if (isFinishing() || Telemetry.get(this).last().flying) return;
        startActivity(new android.content.Intent(this, MediaActivity.class)
                .putExtra(MediaActivity.EXTRA_AUTO_SINCE, since));
    }

    /**
     * Keeps the buttons away from the phone's edges (where they're hard to tap, especially
     * without a case) while the live video still fills the whole screen.
     */
    private void applyEdgeInsets() {
        edgeInset = DensityUtil.dip2px(this, 28);
        parentView.setPadding(edgeInset, 0, edgeInset, 0);
        for (int id : new int[]{R.id.fpv_container, R.id.fpv_overlay_widget}) {
            View v = findViewById(id);
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
            lp.leftMargin = -edgeInset;
            lp.rightMargin = -edgeInset;
            v.setLayoutParams(lp);
        }
    }

    private boolean anyPanelOpen() {
        for (int id : new int[]{R.id.camera_setting_exposure_panel, R.id.camera_setting_advanced_panel,
                R.id.pre_flight_check_list, R.id.rtk_panel, R.id.spotlight_panel, R.id.speaker_panel}) {
            View v = findViewById(id);
            if (v != null && v.getVisibility() == View.VISIBLE) return true;
        }
        return false;
    }

    private void updateCloseButton() {
        if (closePanelsButton == null) return;
        int want = anyPanelOpen() ? View.VISIBLE : View.GONE;
        if (closePanelsButton.getVisibility() != want) closePanelsButton.setVisibility(want);
    }

    private void closeAllPanels() {
        controlsWidget.setAdvancedPanelVisibility(false);
        controlsWidget.setExposurePanelVisibility(false);
        for (int id : new int[]{R.id.camera_setting_exposure_panel, R.id.camera_setting_advanced_panel,
                R.id.pre_flight_check_list, R.id.rtk_panel, R.id.spotlight_panel, R.id.speaker_panel}) {
            View v = findViewById(id);
            if (v != null) v.setVisibility(View.GONE);
        }
        updateCloseButton();
    }

    @Override
    public void onBackPressed() {
        if (trackOverlay != null && (trackOverlay.isSelecting() || ActiveTracker.get().isSensing())) {
            cancelSelecting();
            return;
        }
        if (anyPanelOpen()) {
            closeAllPanels();
            return;
        }
        super.onBackPressed();
    }

    /** Clean view hides everything but the essentials; tapping ◐ again restores the normal layout. */
    private void applyClean() {
        TextView b = findViewById(R.id.btn_clean);
        b.setText(clean ? "\u25D1" : "\u25D0");
        b.setAlpha(clean ? 0.55f : 1f);
        if (clean) {
            closeAllPanels();
            for (int id : EXTRAS) {
                View v = findViewById(id);
                if (v != null) v.setVisibility(View.GONE);
            }
        } else {
            for (int id : new int[]{R.id.btn_home, R.id.mode_toggle, R.id.btn_moves, R.id.quick_row,
                    R.id.map_widget, R.id.remaining_flight_time}) {
                View v = findViewById(id);
                if (v != null) v.setVisibility(View.VISIBLE);
            }
            if (pendingMission != null && !MissionRunner.get(this).isRunning()) missionStart.setVisibility(View.VISIBLE);
            // Mode-dependent views (HUD, pro camera bars) are restored by applyMode's own rules.
            for (int id : ADVANCED_ONLY) {
                View v = findViewById(id);
                if (v != null) v.setVisibility(advanced ? View.VISIBLE : View.INVISIBLE);
            }
            simpleHud.setVisibility(advanced ? View.GONE : View.VISIBLE);
        }
    }

    /** DJI draws its camera menus for tablets; scale them up so the text is readable on a phone. */
    private void enlargeCameraMenus() {
        final float scale = 1.25f;
        for (View panel : new View[]{cameraSettingAdvancedPanel, cameraSettingExposurePanel}) {
            panel.post(() -> {
                panel.setPivotX(panel.getWidth()); // grow toward the left, away from the shutter column
                panel.setPivotY(0);
                panel.setScaleX(scale);
                panel.setScaleY(scale);
            });
        }
    }

    /** Shows or hides the pro camera controls. Hidden views keep their space so nothing jumps around. */
    private void applyMode() {
        advanced = Prefs.isAdvanced(this);
        for (int id : ADVANCED_ONLY) {
            View v = findViewById(id);
            if (v != null) v.setVisibility(advanced ? View.VISIBLE : View.INVISIBLE);
        }
        simpleHud.setVisibility(advanced ? View.GONE : View.VISIBLE);
        modeToggle.setText(advanced ? "Pro" : "Simple");
        if (!advanced) {
            controlsWidget.setAdvancedPanelVisibility(false);
            controlsWidget.setExposurePanelVisibility(false);
        }
        applyClean();
    }

    @Override
    public void onTelemetry(Telemetry.Snapshot s) {
        if (s.flying && !wasFlying) flightStartedAt = System.currentTimeMillis();
        updateHomePanel(s);
        updateReadyCard(s);
        checkLowLight(s);
        if (!s.flying && wasFlying) onLanded();
        wasFlying = s.flying;
        mapWidget.update(s);
        hudAlt.setText(Units.distance(s.altitudeM, imperial));
        hudDist.setText(Units.distance(s.distanceM, imperial));
        hudSpeed.setText(Units.speed(s.horizontalSpeedMps, imperial));
        hudVSpeed.setText(Units.speed(s.verticalSpeedMps, imperial));
        hudTime.setText(s.flying || s.flightTimeS > 0 ? Units.duration(s.flightTimeS) : "--");
    }

    private void initCameraView() {
        cameraSettingExposurePanel = findViewById(R.id.camera_setting_exposure_panel);
        cameraSettingAdvancedPanel = findViewById(R.id.camera_setting_advanced_panel);
        cameraConfigISOAndEIWidget = findViewById(R.id.camera_config_iso_and_ei_widget);
        cameraConfigShutterWidget = findViewById(R.id.camera_config_shutter_widget);
        cameraConfigApertureWidget = findViewById(R.id.camera_config_aperture_widget);
        cameraConfigEVWidget = findViewById(R.id.camera_config_ev_widget);
        cameraConfigWBWidget = findViewById(R.id.camera_config_wb_widget);
        cameraConfigStorageWidget = findViewById(R.id.camera_config_storage_widget);
        cameraConfigSSDWidget = findViewById(R.id.camera_config_ssd_widget);
        lensControlWidget = findViewById(R.id.camera_lens_control);
        controlsWidget = findViewById(R.id.CameraCapturePanel);
        thermalPaletteWidget = findViewById(R.id.thermal_pallette_widget);
    }

    private void onViewClick(View view) {
        if (view == fpvWidget && !isMapMini) {
            resizeFPVWidget(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT, 0, 0);
            reorderCameraCapturePanel();
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, deviceWidth - 2 * edgeInset, deviceHeight, width, height, margin);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = true;
            mapWidget.setMini(true);
        } else if (view == mapWidget && isMapMini) {
            hidePanels();
            resizeFPVWidget(width, height, margin, 12);
            reorderCameraCapturePanel();
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, width, height, deviceWidth - 2 * edgeInset, deviceHeight, 0);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = false;
            mapWidget.setMini(false);
        }
    }

    private void resizeFPVWidget(int width, int height, int margin, int fpvInsertPosition) {
        RelativeLayout.LayoutParams fpvParams = (RelativeLayout.LayoutParams) primaryVideoView.getLayoutParams();
        fpvParams.height = height;
        fpvParams.width = width;
        boolean full = width == RelativeLayout.LayoutParams.MATCH_PARENT;
        fpvParams.rightMargin = full ? -edgeInset : margin;
        fpvParams.leftMargin = full ? -edgeInset : 0;
        fpvParams.bottomMargin = margin;
        if (isMapMini) {
            fpvParams.addRule(RelativeLayout.CENTER_IN_PARENT, 0);
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        } else {
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
            fpvParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        }
        primaryVideoView.setLayoutParams(fpvParams);

        parentView.removeView(primaryVideoView);
        parentView.addView(primaryVideoView, fpvInsertPosition);
    }

    private void reorderCameraCapturePanel() {
        View cameraCapturePanel = findViewById(R.id.CameraCapturePanel);
        parentView.removeView(cameraCapturePanel);
        parentView.addView(cameraCapturePanel, isMapMini ? 9 : 13);
    }

    private void swapVideoSource() {
        if (secondaryFPVWidget.getVideoSource() == FPVWidget.VideoSource.SECONDARY) {
            fpvWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
            secondaryFPVWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
        } else {
            fpvWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
            secondaryFPVWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
        }
    }

    private void cameraWidgetKeyIndexUpdated(int keyIndex, int subKeyIndex) {
        controlsWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraSettingExposurePanel.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraSettingAdvancedPanel.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigISOAndEIWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigShutterWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigApertureWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigEVWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigWBWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigStorageWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigSSDWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        controlsWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        lensControlWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        thermalPaletteWidget.updateKeyOnIndex(keyIndex, subKeyIndex);

        fpvOverlayWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
    }

    private void updateSecondaryVideoVisibility() {
        if (secondaryFPVWidget.getVideoSource() == null || !DJIProductUtil.isSupportMultiCamera()) {
            secondaryVideoView.setVisibility(View.GONE);
        } else {
            secondaryVideoView.setVisibility(View.VISIBLE);
        }
    }

    private void hidePanels() {
        //These panels appear based on keys from the drone itself.
        if (KeyManager.getInstance() != null) {
            KeyManager.getInstance().setValue(CameraKey.create(CameraKey.HISTOGRAM_ENABLED, fpvWidget.getCameraKeyIndex()), false, null);
            KeyManager.getInstance().setValue(CameraKey.create(CameraKey.COLOR_WAVEFORM_ENABLED, fpvWidget.getCameraKeyIndex()), false, null);
        }

        //These panels have buttons that toggle them, so call the methods to make sure the button state is correct.
        controlsWidget.setAdvancedPanelVisibility(false);
        controlsWidget.setExposurePanelVisibility(false);

        //These panels don't have a button state, so we can just hide them.
        findViewById(R.id.pre_flight_check_list).setVisibility(View.GONE);
        findViewById(R.id.rtk_panel).setVisibility(View.GONE);
        //findViewById(R.id.simulator_panel).setVisibility(View.GONE);
        findViewById(R.id.spotlight_panel).setVisibility(View.GONE);
        findViewById(R.id.speaker_panel).setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Hide both the navigation bar and the status bar.
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        mapWidget.onResume();

        imperial = Prefs.isImperial(this);
        clean = Prefs.raw(this).getBoolean("clean_view", false);
        applyMode();
        fpvOverlayWidget.setGridOverlayEnabled(Prefs.isGrid(this));
        if (Prefs.isGrid(this)) {
            fpvOverlayWidget.setCurrentGridOverlayType(FPVOverlayWidget.GridOverlayType.PARALLEL);
        }
        Telemetry telemetry = Telemetry.get(this);
        telemetry.attach();
        parentView.removeCallbacks(reattach);
        parentView.postDelayed(reattach, 3000);
        wasFlying = telemetry.last().flying;
        telemetry.addListener(this);
        SmartMove.get(this).setListener(this);
        ActiveTracker.get().setListener(this);
        VisionTracker.get(this).setListener(this);
        MissionRunner.get(this).setListener(this);
        loadPendingMission(getIntent());
        Weather.fetch(this, w -> {
            weather = w;
            updateReadyCard(Telemetry.get(this).last());
        });
        moveBanner.setVisibility(SmartMove.get(this).isRunning() || ActiveTracker.get().isRunning()
                || VisionTracker.get(this).isRunning()
                || MissionRunner.get(this).isRunning() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onPause() {
        Telemetry.get(this).removeListener(this);
        parentView.removeCallbacks(reattach);
        SmartMove.get(this).setListener(null);
        ActiveTracker.get().setListener(null);
        MissionRunner.get(this).setListener(null);
        mapWidget.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        VisionTracker.get(this).stop("Left the flight screen.");
        mapWidget.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    private class ResizeAnimation extends Animation {

        private View mView;
        private int mToHeight;
        private int mFromHeight;

        private int mToWidth;
        private int mFromWidth;
        private int mMargin;

        private ResizeAnimation(View v, int fromWidth, int fromHeight, int toWidth, int toHeight, int margin) {
            mToHeight = toHeight;
            mToWidth = toWidth;
            mFromHeight = fromHeight;
            mFromWidth = fromWidth;
            mView = v;
            mMargin = margin;
            setDuration(300);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float height = (mToHeight - mFromHeight) * interpolatedTime + mFromHeight;
            float width = (mToWidth - mFromWidth) * interpolatedTime + mFromWidth;
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) mView.getLayoutParams();
            p.height = (int) height;
            p.width = (int) width;
            p.rightMargin = mMargin;
            p.bottomMargin = mMargin;
            mView.requestLayout();
        }
    }
}
