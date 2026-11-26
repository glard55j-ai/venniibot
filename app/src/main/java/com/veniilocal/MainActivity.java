package com.veniilocal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Non-official, locally controlled UI. No cloud, account or analytics. Only demo preferences are saved locally. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 19, 32);
    private static final int CARD = Color.rgb(21, 37, 55);
    private static final int ACCENT = Color.rgb(59, 220, 177);
    private static final int PRIMARY = Color.WHITE;
    private static final int MUTED = Color.rgb(164, 183, 201);
    private static final int WARNING = Color.rgb(255, 191, 106);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Button btnConnect, btnStart, btnPause, btnResume, btnDock;
    private CheckBox repaired, demoMode;
    private TextView connection, state, power, suction, volume, issue, note, telemetrySummary, dashboardMode, dashboardState, dashboardBattery, dashboardBatteryHint, historyView, diagnostic, demoStatus;
    private View robotPreview, settingsPanel, manualPanel;
    private VeniiClient.Status lastReport;
    private VeniiClient.LidarReport lidarReport;
    private TextView lidarStatus;
    private TextView stateHistoryView, monitorStatus;
    private Button monitorButton;
    private final java.util.ArrayDeque<String> stateTransitions = new java.util.ArrayDeque<>();
    private String lastObservedState = null;
    private boolean monitorActive = false;
    private long monitorStartedAt = 0L;
    private static final long MONITOR_MAX_MS = 5 * 60 * 1000L;
    private static final long MONITOR_DELAY_MS = 12000L;
    private final Runnable monitorTick = () -> readMonitorState();
    private Button lidarListen, lidarRaw;
    private String pendingLidarExport;
    private static final int LIDAR_EXPORT_REQUEST = 260;
    private String lastReportTime = "Jamais", lastAttempt = "Aucune tentative réelle", lastCommand = "Aucune commande réelle", lastBefore = "—", lastAfter = "—";
    private DemoMap demoMap;
    private TextView elapsedLabel;
    private long elapsedBefore = 0, startedAt = 0;
    private boolean timerRunning = false;
    private final Runnable demoTick = new Runnable() {
        public void run() {
            if (!timerRunning) return;
            updateElapsed();
            if (demoMap != null) demoMap.autoStep();
            handler.postDelayed(this, 1000);
        }
    };
    private TextView settingsSummary, scheduleLabel;
    private android.widget.Spinner aspirationChoice, waterChoice, cleaningChoice;
    private android.widget.SeekBar volumeChoice;
    private CheckBox scheduleEnabled;
    private int scheduleHour = 9, scheduleMinute = 0;
    private android.animation.ObjectAnimator demoAnimation;
    private final java.util.ArrayDeque<String> history = new java.util.ArrayDeque<>();
    private volatile boolean working = false;
    private boolean hasStatus = false;
    private long lastStatusAt = 0L;
    private static final long STATUS_TTL_MS = 60000L;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable expireStatus = () -> { invalidateStatus("Lecture expirée • actualiser"); if (dashboardState != null) enableButtons(); };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        buildUi();
    }

    private int dp(float size) { return (int)(getResources().getDisplayMetrics().density * size + .5f); }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout column(int padding, int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        if (color != 0) layout.setBackground(bg(color, 22));
        return layout;
    }

    private void add(LinearLayout root, View child, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        root.addView(child, p);
    }

    private Button button(String caption, int background, int color) {
        Button btn = new Button(this);
        btn.setText(caption);
        btn.setAllCaps(false);
        btn.setTextColor(color);
        btn.setTextSize(15);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btn.setPadding(dp(14), dp(12), dp(14), dp(12));
        btn.setBackground(bg(background, 14));
        btn.setMinHeight(dp(54));
        return btn;
    }

    private TextView valueRow(LinearLayout card, String label) {
        add(card, text(label, 12, MUTED, false), 14);
        TextView value = text("—", 21, PRIMARY, true);
        add(card, value, 1);
        return value;
    }

    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { return "inconnue"; }
    }

    private String clock() {
        return new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
    }

    private void activity(String message) {
        note.setText(message);
        history.addFirst(clock() + " • " + message);
        while (history.size() > 30) history.removeLast();
        historyView.setText(android.text.TextUtils.join("\n\n", history));
    }

    private void stopDemoAnimation() {
        if (demoAnimation != null) { demoAnimation.cancel(); demoAnimation = null; }
        if (robotPreview != null) robotPreview.setTranslationX(0f);
    }

    private void showDemoMotion(int code, String label) {
        stopDemoAnimation();
        demoStatus.setText("Simulation : " + label);
        if (code == 1020 || code == 1070 || code == 1050) {
            demoAnimation = android.animation.ObjectAnimator.ofFloat(robotPreview, "translationX", -dp(34), dp(34));
            demoAnimation.setDuration(code == 1050 ? 1100 : 1700);
            demoAnimation.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            demoAnimation.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            demoAnimation.start();
        }
    }

    private View vacuumPreview() {
        View view = new View(this) {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                float x = getWidth()/2f, y = getHeight()/2f, r = dp(27);
                paint.setColor(Color.rgb(5, 13, 22));
                canvas.drawOval(x-r, y+r-4, x+r, y+r+dp(7), paint);
                paint.setColor(Color.rgb(208, 225, 233));
                canvas.drawCircle(x,y,r,paint);
                paint.setColor(Color.rgb(38,60,80));
                canvas.drawCircle(x,y,r-dp(4),paint);
                paint.setColor(ACCENT);
                canvas.drawCircle(x,y-dp(10),dp(6),paint);
                paint.setColor(MUTED);
                canvas.drawRoundRect(x-dp(12),y+dp(7),x+dp(12),y+dp(13),dp(3),dp(3),paint);
            }
        };
        view.setContentDescription("Illustration du robot en démonstration");
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(76)));
        return view;
    }

    private android.widget.Spinner demoChoice(LinearLayout panel, String label, String[] options) {
        add(panel, text(label, 13, MUTED, true), 12);
        android.widget.Spinner spinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) ((TextView)view).setTextColor(PRIMARY);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        spinner.setPadding(dp(8), dp(8), dp(8), dp(8));
        add(panel, spinner, 3);
        return spinner;
    }

    private String scheduledTime() {
        return String.format(java.util.Locale.FRANCE, "%02d:%02d", scheduleHour, scheduleMinute);
    }

    private void updateScheduleLabel() {
        scheduleLabel.setText("Horaire fictif : " + scheduledTime() + " • " + (scheduleEnabled.isChecked() ? "activé en démo" : "désactivé"));
    }

    private LinearLayout buildDemoSettings() {
        LinearLayout section = column(14, CARD);
        add(section, text("RÉGLAGES EXPÉRIMENTAUX", 12, ACCENT, true), 0);
        add(section, text("DÉMO UNIQUEMENT • options illustratives, non validées sur le N1 Max. Aucun réglage transmis au robot.", 13, WARNING, true), 8);
        aspirationChoice = demoChoice(section, "Puissance d’aspiration", new String[]{"Silencieux", "Standard", "Fort", "Maximum"});
        aspirationChoice.setSelection(Math.max(0,Math.min(3,demoPrefs().getInt("aspiration",1))));
        waterChoice = demoChoice(section, "Débit d’eau", new String[]{"Sans eau", "Faible", "Moyen", "Élevé"});
        cleaningChoice = demoChoice(section, "Mode de nettoyage", new String[]{"Automatique", "Zone ciblée", "Bordures", "Lavage"});
        waterChoice.setSelection(Math.max(0,Math.min(3,demoPrefs().getInt("water",0))));
        cleaningChoice.setSelection(Math.max(0,Math.min(3,demoPrefs().getInt("cleaning",0))));
        int savedVolume = Math.max(0,Math.min(100,demoPrefs().getInt("volume",50)));
        TextView volumeLabel = text("Volume simulé : " + savedVolume + " %", 13, MUTED, true);
        add(section, volumeLabel, 12);
        volumeChoice = new android.widget.SeekBar(this);
        volumeChoice.setMax(100);
        volumeChoice.setProgress(savedVolume);
        volumeChoice.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        volumeChoice.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        add(section, volumeChoice, 5);
        volumeChoice.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar, int value, boolean fromUser) { volumeLabel.setText("Volume simulé : " + value + " %"); }
            public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            public void onStopTrackingTouch(android.widget.SeekBar bar) {}
        });
        scheduleEnabled = new CheckBox(this);
        scheduleEnabled.setText("Programmer un nettoyage fictif");
        scheduleEnabled.setTextColor(PRIMARY);
        scheduleEnabled.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        scheduleHour = Math.max(0,Math.min(23,demoPrefs().getInt("hour",9)));
        scheduleMinute = Math.max(0,Math.min(59,demoPrefs().getInt("minute",0)));
        scheduleEnabled.setChecked(demoPrefs().getBoolean("schedule_demo",false));
        add(section, scheduleEnabled, 12);
        scheduleLabel = text("", 13, MUTED, false);
        add(section, scheduleLabel, 5);
        updateScheduleLabel();
        scheduleEnabled.setOnCheckedChangeListener((b, checked) -> updateScheduleLabel());
        Button time = button("Choisir une heure", Color.rgb(38,60,80), PRIMARY);
        add(section, time, 8);
        time.setOnClickListener(v -> {
            if (!demoMode.isChecked()) return;
            new android.app.TimePickerDialog(this, (picker, hour, minute) -> {
                scheduleHour = hour;
                scheduleMinute = minute;
                updateScheduleLabel();
            }, scheduleHour, scheduleMinute, true).show();
        });
        add(section, text("Aucune alarme ni tâche automatique n’est créée. Les choix appliqués sont sauvegardés uniquement sur ce téléphone.", 12, MUTED, false), 8);
        Button apply = button("Appliquer en démonstration", ACCENT, BG);
        add(section, apply, 12);
        settingsSummary = text("Aucun réglage de démonstration appliqué.", 13, MUTED, false);
        add(section, settingsSummary, 8);
        apply.setOnClickListener(v -> {
            if (!demoMode.isChecked() || working) return;
            String summary = "Aspiration : " + aspirationChoice.getSelectedItem()
                + " • Eau : " + waterChoice.getSelectedItem()
                + " • Mode : " + cleaningChoice.getSelectedItem()
                + " • Volume : " + volumeChoice.getProgress() + " %"
                + " • Horaire fictif : " + (scheduleEnabled.isChecked() ? scheduledTime() : "désactivé");
            saveDemoPreferences();
            settingsSummary.setText("DÉMO : " + summary + " • préférences enregistrées");
            activity("DÉMO : réglages appliqués localement. " + summary + ". Aucun ordre envoyé.");
        });
        return section;
    }

    private android.content.SharedPreferences demoPrefs() {
        return getSharedPreferences("demo_preferences", MODE_PRIVATE);
    }

    private void saveDemoPreferences() {
        demoPrefs().edit()
            .putInt("aspiration", aspirationChoice.getSelectedItemPosition())
            .putInt("water", waterChoice.getSelectedItemPosition())
            .putInt("cleaning", cleaningChoice.getSelectedItemPosition())
            .putInt("volume", volumeChoice.getProgress())
            .putInt("hour", scheduleHour).putInt("minute", scheduleMinute)
            .putBoolean("schedule_demo", scheduleEnabled.isChecked()).apply();
    }

    private void updateElapsed() {
        if (elapsedLabel == null) return;
        long seconds = (elapsedBefore + (timerRunning ? android.os.SystemClock.elapsedRealtime() - startedAt : 0)) / 1000;
        elapsedLabel.setText(String.format(java.util.Locale.FRANCE, "%02d:%02d", seconds / 60, seconds % 60));
    }

    private void startDemoTimer(boolean reset) {
        pauseDemoTimer();
        if (reset) elapsedBefore = 0;
        startedAt = android.os.SystemClock.elapsedRealtime();
        timerRunning = true;
        handler.post(demoTick);
    }

    private void pauseDemoTimer() {
        if (timerRunning) elapsedBefore += android.os.SystemClock.elapsedRealtime() - startedAt;
        timerRunning = false;
        handler.removeCallbacks(demoTick);
        updateElapsed();
    }

    private final class DemoMap extends View {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final boolean[][] visited = new boolean[6][8];
        private int x = 1, y = 1, direction = 1;
        DemoMap() { super(MainActivity.this); visited[y][x] = true; setContentDescription("Carte fictive de démonstration, sans données du robot"); }
        void step(int dx, int dy) {
            x = Math.max(0, Math.min(7, x + dx)); y = Math.max(0, Math.min(5, y + dy));
            visited[y][x] = true; invalidate();
        }
        void autoStep() {
            if ((direction > 0 && x == 7) || (direction < 0 && x == 0)) {
                if (y == 5) { y = 0; } else { y++; }
                direction = -direction; visited[y][x] = true; invalidate();
            } else step(direction, 0);
        }
        void reset() {
            for (boolean[] row : visited) java.util.Arrays.fill(row, false);
            x = 1; y = 1; direction = 1; visited[y][x] = true; invalidate();
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            super.onDraw(c);
            float pad = dp(8), cw = (getWidth()-2*pad)/8f, ch = (getHeight()-2*pad)/6f;
            for (int iy=0; iy<6; iy++) for (int ix=0; ix<8; ix++) {
                p.setColor(visited[iy][ix] ? Color.rgb(30,112,100) : Color.rgb(38,60,80));
                c.drawRoundRect(pad+ix*cw+2,pad+iy*ch+2,pad+(ix+1)*cw-2,pad+(iy+1)*ch-2,dp(4),dp(4),p);
            }
            float cx=pad+(x+.5f)*cw, cy=pad+(y+.5f)*ch;
            p.setColor(PRIMARY); c.drawCircle(cx,cy,Math.min(cw,ch)*.34f,p);
            p.setColor(ACCENT); c.drawCircle(cx,cy,Math.min(cw,ch)*.19f,p);
        }
    }

    private void manualStep(int dx, int dy, String label) {
        if (!demoMode.isChecked() || working) return;
        pauseDemoTimer();
        stopDemoAnimation();
        demoMap.step(dx,dy);
        demoStatus.setText("Simulation : " + label);
        activity("DÉMO : direction " + label + " • carte fictive uniquement.");
    }

    private LinearLayout buildManualPanel() {
        LinearLayout section = column(14, CARD);
        add(section, text("TÉLÉCOMMANDE ET CARTE", 12, ACCENT, true), 0);
        add(section, text("DÉMO UNIQUEMENT • carte fictive, aucun mouvement réel.", 13, WARNING, true), 8);
        elapsedLabel = text("00:00", 30, PRIMARY, true);
        elapsedLabel.setGravity(Gravity.CENTER);
        add(section, elapsedLabel, 8);
        TextView caption = text("Temps de nettoyage simulé", 12, MUTED, false);
        caption.setGravity(Gravity.CENTER);
        add(section, caption, 0);
        demoMap = new DemoMap();
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1,dp(170));
        mp.topMargin = dp(10);
        section.addView(demoMap,mp);
        Button up = button("↑  Avancer", Color.rgb(38,60,80), PRIMARY);
        add(section, up, 8);
        up.setOnClickListener(v -> manualStep(0,-1,"Avancer"));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button left = button("←", Color.rgb(38,60,80), PRIMARY);
        Button stop = button("STOP", Color.rgb(155,46,57), PRIMARY);
        Button right = button("→", Color.rgb(38,60,80), PRIMARY);
        for (Button b : new Button[]{left,stop,right}) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,-2,1f);
            bp.setMargins(dp(3),0,dp(3),0); row.addView(b,bp);
        }
        left.setContentDescription("Gauche"); right.setContentDescription("Droite");
        add(section,row,8);
        left.setOnClickListener(v -> manualStep(-1,0,"Gauche"));
        right.setOnClickListener(v -> manualStep(1,0,"Droite"));
        stop.setOnClickListener(v -> {
            if (!demoMode.isChecked()) return;
            pauseDemoTimer(); stopDemoAnimation();
            demoStatus.setText("Simulation : STOP");
            activity("DÉMO : STOP simulé • aucun ordre réel.");
        });
        Button down = button("↓  Reculer", Color.rgb(38,60,80), PRIMARY);
        add(section,down,8);
        down.setOnClickListener(v -> manualStep(0,1,"Reculer"));
        Button reset = button("Réinitialiser la carte et le temps", Color.rgb(38,60,80), PRIMARY);
        add(section,reset,8);
        reset.setOnClickListener(v -> {
            if (!demoMode.isChecked()) return;
            pauseDemoTimer(); stopDemoAnimation(); elapsedBefore=0; updateElapsed(); demoMap.reset();
            demoStatus.setText("Simulation : en attente");
            activity("DÉMO : carte et chronomètre réinitialisés.");
        });
        return section;
    }

    private String diagnosticReport() {
        StringBuilder r = new StringBuilder();
        r.append("KOOMA-I • v").append(appVersion()).append("\n");
        r.append("Rapport local • ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.FRANCE).format(new java.util.Date())).append("\n");
        r.append("Android : ").append(android.os.Build.VERSION.SDK_INT).append("\n");
        r.append("Mode : ").append(demoMode.isChecked() ? "DÉMONSTRATION" : "RÉEL").append("\n");
        r.append("Dernière tentative réelle : ").append(lastAttempt).append("\n");
        r.append("Dernière lecture réelle : ").append(lastReportTime).append("\n");
        r.append("Fraîcheur : ").append(freshStatus() ? "récente" : "absente ou expirée").append("\n");
        if (lastReport != null) {
            r.append("État 888 : ").append(lastReport.generalState).append("\n");
            r.append("Alimentation 60 (brut) : ").append(lastReport.powerReport).append("\n");
            r.append("Aspiration 1300 (brut) : ").append(lastReport.suction).append("\n");
            r.append("Volume 1502 (brut) : ").append(lastReport.volume).append("\n");
            r.append("Erreur 70 (brut) : ").append(lastReport.error).append("\n");
            r.append("Champs reçus : ").append(lastReport.messageCount).append("\n");
        } else r.append("Aucune donnée réelle reçue.\n");
        r.append("Dernier ordre réel : ").append(lastCommand).append("\n");
        r.append("État avant : ").append(lastBefore).append(" • après : ").append(lastAfter).append("\n");
        r.append("LiDAR • ").append(lidarReport == null ? "Aucun essai réel effectué" : lidarReport.summary()).append("\n");
        r.append("Un ordre transmis ne confirme pas un mouvement. Valeurs non interprétées. Aucun identifiant réseau ni jeton inclus.");
        return r.toString();
    }

    private void showDiagnosticReport() {
        String report = diagnosticReport();
        new android.app.AlertDialog.Builder(this)
            .setTitle("Rapport de diagnostic")
            .setMessage(report)
            .setNegativeButton("Fermer", null)
            .setPositiveButton("Copier", (dialog, which) -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("KOOMA-I diagnostic", report));
                    android.widget.Toast.makeText(this,"Rapport copié",android.widget.Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    private String stateLabel(String code) {
        if ("2".equals(code)) return "Nettoyage en cours";
        if ("3".equals(code)) return "En pause";
        if ("4".equals(code)) return "Retour à la base";
        if ("11".equals(code)) return "Recharge en cours";
        if ("12".equals(code)) return "Charge terminée";
        return "Code " + (code == null ? "inconnu" : code);
    }

    private void recordStateTransition(VeniiClient.Status report) {
        String code = report.generalState;
        if (code == null || code.trim().isEmpty()) return;
        if (!code.equals(lastObservedState)) {
            String label = stateLabel(code) + ("2".equals(code) || "3".equals(code) || "4".equals(code) || "11".equals(code) || "12".equals(code) ? " • interprétation issue des essais" : " • inconnu");
            String entry = clock() + " • État " + code + " (" + label + ")"
                + " • alimentation brute " + report.powerReport;
            stateTransitions.addFirst(entry);
            while (stateTransitions.size() > 40) stateTransitions.removeLast();
            if (stateHistoryView != null)
                stateHistoryView.setText(android.text.TextUtils.join("\n\n", stateTransitions));
            lastObservedState = code;
        }
    }

    private void stopMonitor(String reason) {
        monitorActive = false;
        handler.removeCallbacks(monitorTick);
        if (monitorButton != null) monitorButton.setText("Démarrer l’observation (5 min)");
        if (monitorStatus != null) monitorStatus.setText(reason);
        if (btnConnect != null) enableButtons();
    }

    private void readMonitorState() {
        if (!monitorActive) return;
        if (android.os.SystemClock.elapsedRealtime() - monitorStartedAt >= MONITOR_MAX_MS) {
            stopMonitor("Observation terminée (5 min maximum).");
            return;
        }
        if (working) {
            handler.postDelayed(monitorTick, MONITOR_DELAY_MS);
            return;
        }
        io.execute(() -> {
            VeniiClient.Status observed = null;
            String failure = null;
            try {
                bindToWifiIfAvailable();
                observed = VeniiClient.readStatus(); // Lecture 888 uniquement, aucun ordre moteur
            } catch (Exception e) {
                failure = "Lecture impossible : " + e.getClass().getSimpleName();
            }
            final VeniiClient.Status report = observed;
            final String error = failure;
            runOnUiThread(() -> {
                if (!monitorActive) return;
                if (report != null) {
                    recordStateTransition(report);
                    monitorStatus.setText("Observation active • dernière lecture à " + clock()
                        + " • état brut " + report.generalState
                        + " • alimentation " + report.powerReport);
                } else monitorStatus.setText("Observation active • " + error);
                long elapsed = android.os.SystemClock.elapsedRealtime() - monitorStartedAt;
                if (elapsed >= MONITOR_MAX_MS)
                    stopMonitor("Observation terminée. Les données sont conservées pour la session.");
                else handler.postDelayed(monitorTick, MONITOR_DELAY_MS);
            });
        });
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout main = column(20, 0);
        main.setPadding(dp(20), dp(24), dp(20), dp(30));
        scroll.addView(main);
        setContentView(scroll);

        add(main, text("KOOMA-I  •  v" + appVersion(), 14, ACCENT, true), 0);
        add(main, text("N1 Max", 34, PRIMARY, true), 5);
        add(main, text("Pilotage privé en Wi-Fi, sans compte ni cloud", 14, MUTED, false), 4);

        LinearLayout dashboard = column(18, CARD);
        add(main, dashboard, 20);
        add(dashboard, text("TABLEAU DE BORD", 12, ACCENT, true), 0);
        dashboardMode = text("DÉMONSTRATION • aucune commande réelle", 13, ACCENT, true);
        add(dashboard, dashboardMode, 9);
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout batteryTile = column(12, Color.rgb(38, 60, 80));
        LinearLayout statusTile = column(12, Color.rgb(38, 60, 80));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1f);
        left.rightMargin = dp(8);
        tiles.addView(batteryTile, left);
        tiles.addView(statusTile, new LinearLayout.LayoutParams(0, -2, 1f));
        add(batteryTile, text("BATTERIE", 11, MUTED, true), 0);
        dashboardBattery = text("—", 24, PRIMARY, true);
        add(batteryTile, dashboardBattery, 5);
        dashboardBatteryHint = text("En attente de lecture", 12, MUTED, false);
        add(batteryTile, dashboardBatteryHint, 3);
        add(statusTile, text("ÉTAT DU ROBOT", 11, MUTED, true), 0);
        dashboardState = text("En attente", 16, PRIMARY, true);
        add(statusTile, dashboardState, 5);
        add(statusTile, text("Dernière lecture", 12, MUTED, false), 3);
        tiles.setGravity(Gravity.TOP);
        add(dashboard, tiles, 14);
        add(dashboard, text("Les valeurs réelles sont affichées uniquement après lecture du robot.", 12, MUTED, false), 12);

        robotPreview = vacuumPreview();
        dashboard.addView(robotPreview);
        demoStatus = text("Simulation : en attente", 12, MUTED, false);
        demoStatus.setGravity(Gravity.CENTER);
        add(dashboard, demoStatus, 0);
        Button quick = button("Accéder aux commandes", Color.rgb(38,60,80), PRIMARY);
        add(dashboard, quick, 10);

        LinearLayout connectivity = column(16, CARD);
        add(main, connectivity, 25);
        add(connectivity, text("CONNEXION LOCALE", 12, ACCENT, true), 0);
        connection = text("Non connecté", 19, PRIMARY, true);
        add(connectivity, connection, 10);
        add(connectivity, text("Réseau VENII du robot • 192.168.78.1:9000", 12, MUTED, false), 6);
        btnConnect = button("Connecter et lire l’état", ACCENT, BG);
        add(connectivity, btnConnect, 16);
        btnConnect.setOnClickListener(v -> refresh());
        Button wifi = button("Ouvrir les réglages Wi-Fi", Color.rgb(38, 60, 80), PRIMARY);
        add(connectivity, wifi, 9);
        wifi.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        LinearLayout readings = column(18, CARD);
        // Données techniques placées sous les commandes.
        add(readings, text("DONNÉES DU ROBOT", 12, ACCENT, true), 0);
        telemetrySummary = text("En attente d’une lecture réelle • données non disponibles", 13, MUTED, false);
        add(readings, telemetrySummary, 8);
        state = valueRow(readings, "État général (code brut 888)");
        power = valueRow(readings, "Alimentation • valeur brute (code 60, pas un % batterie confirmé)");
        suction = valueRow(readings, "Puissance nettoyage (1300)");
        volume = valueRow(readings, "Volume (1502)");
        issue = valueRow(readings, "Code d’erreur (70, si présent)");
        add(readings, text("« — » signifie : donnée absente ou non décodée. Le code 60 semble refléter la batterie : estimation à confirmer. Les états 11 et 12 sont provisoires, les autres restent bruts.", 12, MUTED, false), 14);

        LinearLayout safety = column(14, CARD);
        add(main, safety, 16);
        add(safety, text("SÉCURITÉ", 12, WARNING, true), 0);
        add(safety, text("Vérifiez que le pare-chocs et les capteurs latéraux sont libres, puis dégagez le sol avant toute commande réelle.", 15, PRIMARY, true), 8);
        add(safety, text("Le contrôle à distance n’est pas un arrêt d’urgence. Gardez le bouton physique accessible et éloignez le robot des escaliers.", 13, MUTED, false), 8);
        repaired = new CheckBox(this);
        repaired.setText("J’ai contrôlé le pare-chocs, les capteurs et dégagé le sol.");
        repaired.setTextColor(PRIMARY);
        repaired.setTextSize(13);
        repaired.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        add(safety, repaired, 12);
        repaired.setOnCheckedChangeListener((b, checked) -> enableButtons());

        LinearLayout controls = column(14, CARD);
        add(main, controls, 12);
        quick.setOnClickListener(v -> scroll.smoothScrollTo(0, controls.getTop()));
        add(controls, text("COMMANDES", 12, ACCENT, true), 0);
        demoMode = new CheckBox(this);
        demoMode.setText("Mode démonstration (aucun ordre envoyé)");
        demoMode.setTextColor(PRIMARY);
        demoMode.setTextSize(14);
        demoMode.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        demoMode.setChecked(true);
        add(controls, demoMode, 10);
        demoMode.setOnCheckedChangeListener((b, checked) -> {
            stopDemoAnimation();
            robotPreview.setVisibility(checked ? View.VISIBLE : View.GONE);
            demoStatus.setVisibility(checked ? View.VISIBLE : View.GONE);
            demoStatus.setText("Simulation : en attente");
            pauseDemoTimer();
            elapsedBefore = 0; updateElapsed();
            if (demoMap != null) demoMap.reset();
            if (manualPanel != null) manualPanel.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (settingsPanel != null) settingsPanel.setVisibility(checked ? View.VISIBLE : View.GONE);
            invalidateStatus("Lecture nécessaire");
            repaired.setChecked(false);
            dashboardMode.setText(checked ? "DÉMONSTRATION • aucune commande réelle" : "MODE RÉEL • commandes directes");
            dashboardMode.setTextColor(checked ? ACCENT : WARNING);
            connection.setText(checked ? "Mode démo • sans connexion" : "Mode réel • lecture d’état obligatoire");
            activity(checked ? "Démo : appuyez sur les boutons pour simuler." : "Réel : connectez-vous au Wi-Fi VENII et relisez l’état avant de commander.");
            enableButtons();
        });
        btnStart = button("Démarrer  ·  1020", ACCENT, BG);
        add(controls, btnStart, 14);
        btnStart.setOnClickListener(v -> { if (demoMode.isChecked()) simulate(1020, "Démarrer"); else sendStart(); });
        btnPause = button("Pause  ·  1060", Color.rgb(38, 60, 80), PRIMARY);
        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(0, -2, 1f);
        pauseParams.rightMargin = dp(8);
        pair.addView(btnPause, pauseParams);
        add(controls, pair, 8);
        btnPause.setOnClickListener(v -> { if (demoMode.isChecked()) simulate(1060, "Pause"); else sendUnverified(1060, "Pause"); });
        btnResume = button("Reprendre  ·  1070", Color.rgb(38, 60, 80), PRIMARY);
        pair.addView(btnResume, new LinearLayout.LayoutParams(0, -2, 1f));
        btnResume.setOnClickListener(v -> { if (demoMode.isChecked()) simulate(1070, "Reprendre"); else sendUnverified(1070, "Reprendre"); });
        btnDock = button("Retour à la base  ·  1050", Color.rgb(38, 60, 80), PRIMARY);
        add(controls, btnDock, 9);
        btnDock.setOnClickListener(v -> { if (demoMode.isChecked()) simulate(1050, "Retour à la base"); else sendUnverified(1050, "Retour à la base"); });
        add(controls, text("Les codes 1020, 1060, 1070 et 1050 ont répondu lors des essais physiques du 26/11/2026. Un appui envoie toujours un seul ordre ; restez près du robot et de son bouton physique.", 12, MUTED, false), 12);

        LinearLayout manual = buildManualPanel();
        manualPanel = manual;
        add(main, manual, 12);
        LinearLayout settings = buildDemoSettings();
        settingsPanel = settings;
        add(main, settings, 12);

        LinearLayout logs = column(18, CARD);
        add(main, logs, 12);
        add(logs, text("ACTIVITÉ", 12, ACCENT, true), 0);
        note = text("Prêt. Connectez d’abord le téléphone au réseau Wi-Fi VENII.", 13, MUTED, false);
        add(logs, note, 10);
        add(logs, text("HISTORIQUE • 30 événements maximum, session en cours", 12, ACCENT, true), 14);
        historyView = text("Aucun événement", 12, MUTED, false);
        add(logs, historyView, 8);
        Button clearHistory = button("Effacer l’historique", Color.rgb(38,60,80), PRIMARY);
        add(logs, clearHistory, 8);
        clearHistory.setOnClickListener(v -> { history.clear(); historyView.setText("Aucun événement"); });
        add(main, readings, 12);
        LinearLayout observation = column(14, CARD);
        add(main, observation, 12);
        add(observation, text("HISTORIQUE DES ÉTATS RÉELS", 12, ACCENT, true), 0);
        add(observation, text("Chaque changement de code est horodaté. Observation facultative : lecture seule toutes les 12 s, pendant 5 min maximum, écran ouvert. Aucun ordre de déplacement ; aucune donnée envoyée sur Internet.", 12, MUTED, false), 8);
        monitorStatus = text("Observation arrêtée. Relire l’état ou lancer l’observation.", 13, MUTED, false);
        add(observation, monitorStatus, 10);
        monitorButton = button("Démarrer l’observation (5 min)", ACCENT, BG);
        add(observation, monitorButton, 9);
        monitorButton.setOnClickListener(v -> {
            if (monitorActive) {
                stopMonitor("Observation arrêtée manuellement.");
                return;
            }
            if (demoMode.isChecked() || working) {
                monitorStatus.setText("Désactivez la démo et terminez toute commande avant d’observer.");
                return;
            }
            monitorActive = true;
            monitorStartedAt = android.os.SystemClock.elapsedRealtime();
            monitorButton.setText("Arrêter l’observation");
            invalidateStatus("Observation en lecture seule • relire l’état avant de commander");
            enableButtons();
            monitorStatus.setText("Observation démarrée • lecture en cours");
            handler.post(monitorTick);
        });
        stateHistoryView = text("Aucun changement d’état enregistré.", 12, PRIMARY, false);
        add(observation, stateHistoryView, 12);
        Button clearStates = button("Effacer les états enregistrés", Color.rgb(38,60,80), PRIMARY);
        add(observation, clearStates, 10);
        clearStates.setOnClickListener(v -> {
            stateTransitions.clear();
            lastObservedState = null;
            stateHistoryView.setText("Aucun changement d’état enregistré.");
        });
        add(observation, text("Historique limité à 40 transitions, conservé uniquement en mémoire jusqu’à la fermeture de l’application. Les changements plus rapides que 12 s peuvent être manqués. Les lectures d’observation n’autorisent jamais les commandes.", 12, MUTED, false), 8);
        LinearLayout diag = column(14, CARD);
        add(main, diag, 12);
        add(diag, text("DIAGNOSTIC LOCAL", 12, ACCENT, true), 0);
        diagnostic = text("Aucune lecture réelle effectuée.", 13, MUTED, false);
        add(diag, diagnostic, 8);
        add(diag, text("UDP : découverte • TCP 9000 : authentification 67 et état 888. Une transmission ne prouve pas un mouvement.", 12, MUTED, false), 8);
        Button retry = button("Relire l’état (sans mouvement)", Color.rgb(38,60,80), PRIMARY);
        add(diag, retry, 8);
        retry.setOnClickListener(v -> refresh());
        Button reportButton = button("Afficher / copier le rapport", ACCENT, BG);
        add(diag, reportButton, 8);
        reportButton.setOnClickListener(v -> showDiagnosticReport());
        add(diag, text("Le rapport sépare les essais réels de la simulation et exclut les jetons et identifiants réseau. Partage manuel uniquement.", 12, MUTED, false), 8);
        LinearLayout lidar = column(14, CARD);
        add(main, lidar, 12);
        add(lidar, text("LIDAR • CARTOGRAPHIE EXPÉRIMENTALE", 12, ACCENT, true), 0);
        add(lidar, text("Écoute 15 s après authentification et demande d’état connue. Aucun ordre LiDAR ni déplacement. À effectuer près du robot, hors démo.", 12, MUTED, false), 8);
        lidarStatus = text("Aucune écoute réelle effectuée • carte indisponible", 13, PRIMARY, false);
        add(lidar, lidarStatus, 10);
        lidarListen = button("Écouter les données du robot", ACCENT, BG);
        add(lidar, lidarListen, 8);
        lidarListen.setOnClickListener(v -> listenLidar());
        lidarRaw = button("Exporter les données candidates brutes", Color.rgb(38,60,80), PRIMARY);
        add(lidar, lidarRaw, 8);
        lidarRaw.setOnClickListener(v -> exportLidarRaw());
        add(lidar, text("Le rapport standard ne contient que les codes et tailles. Les données brutes peuvent révéler le plan de votre maison. Conservées en mémoire, jamais envoyées automatiquement.", 12, MUTED, false), 8);
        LinearLayout changes = column(18, CARD);
        add(main, changes, 16);
        add(changes, text("VERSION " + appVersion(), 12, ACCENT, true), 0);
        add(changes, text("2.9 • Affichage lisible des états 2, 3, 4, 11 et 12 sur la base des essais réels. Codes bruts conservés.", 13, PRIMARY, false), 8);
        add(changes, text("2.8 • Historique horodaté des transitions et observation volontaire en lecture seule pendant 5 minutes.", 13, PRIMARY, false), 8);
        add(changes, text("2.7 • Indicateur de batterie estimé (60), états 11/12 provisoires, valeurs brutes conservées.", 13, PRIMARY, false), 8);
        add(changes, text("2.6 • Écoute cartographie/LiDAR en lecture seule, codes et tailles, export brut volontaire.", 13, PRIMARY, false), 8);
        add(changes, text("2.5.2 • Nouveau nom KOOMA-I dans Android et dans l’application.", 13, PRIMARY, false), 8);
        add(changes, text("2.5.1 • Nouvelle icône KOOMA-I.", 13, PRIMARY, false), 8);
        add(changes, text("2.5 • Rapport de diagnostic copiable, états avant/après et résultat de la dernière tentative réelle.", 13, PRIMARY, false), 8);
        add(changes, text("2.4 • Carte et télécommande fictives, chronomètre, préférences démo locales.", 13, PRIMARY, false), 8);
        add(changes, text("2.3 • Réglages de démonstration : aspiration, eau, mode, volume et horaire fictif.", 13, PRIMARY, false), 8);
        add(changes, text("2.2 • Historique horodaté, animation démo, accès rapide et diagnostic local.", 13, PRIMARY, false), 8);
        add(changes, text("2.1 • Tableau de bord, version visible, contrôle de fraîcheur des données.", 13, PRIMARY, false), 8);
        add(changes, text("2.0 • Commandes directes sans confirmation, mode démo et lecture d’état.", 12, MUTED, false), 6);
        add(main, text("Prototype indépendant • Aucun jeton stocké • Aucun serveur externe", 12, MUTED, false), 20);
        enableButtons();
    }

    private void invalidateStatus(String message) {
        hasStatus = false;
        handler.removeCallbacks(expireStatus);
        if (dashboardState != null) dashboardState.setText(message);
        if (dashboardBattery != null) dashboardBattery.setText("—");
        if (dashboardBatteryHint != null) dashboardBatteryHint.setText("Lecture non actualisée");
        if (telemetrySummary != null) telemetrySummary.setText("Données non actualisées • relire l’état");
        if (diagnostic != null) diagnostic.setText(message + " • données à actualiser.");
    }

    private boolean freshStatus() {
        return hasStatus && android.os.SystemClock.elapsedRealtime() - lastStatusAt < STATUS_TTL_MS;
    }

    private void enableButtons() {
        btnConnect.setEnabled(!working && !monitorActive);
        if (lidarListen != null) lidarListen.setEnabled(!working && !monitorActive && !demoMode.isChecked());
        if (lidarRaw != null) lidarRaw.setEnabled(!working && lidarReport != null && lidarReport.hasPayload());
        demoMode.setEnabled(!working && !monitorActive);
        repaired.setEnabled(!working && !monitorActive);
        boolean ready = !working && !monitorActive && (demoMode.isChecked() || (freshStatus() && repaired.isChecked()));
        btnStart.setEnabled(ready);
        btnPause.setEnabled(ready);
        btnResume.setEnabled(ready);
        btnDock.setEnabled(ready);
        for (Button b : new Button[]{btnStart, btnPause, btnResume, btnDock}) b.setAlpha(b.isEnabled() ? 1f : .4f);
        btnConnect.setAlpha(btnConnect.isEnabled() ? 1f : .5f);
    }

    private void bindToWifiIfAvailable() {
        ConnectivityManager connectivity = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) return;
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                connectivity.bindProcessToNetwork(network);
                return;
            }
        }
        throw new IllegalStateException("Connectez le téléphone au Wi-Fi VENII.");
    }

    private interface Task { void run() throws Exception; }

    private void execute(String message, Task task) {
        if (working) return;
        working = true;
        lastAttempt = "En cours";
        enableButtons();
        activity(message);
        io.execute(() -> {
            try {
                bindToWifiIfAvailable();
                task.run();
            } catch (Exception e) {
                String why;
                if (e instanceof SocketTimeoutException) why = "Délai dépassé. Vérifiez le Wi-Fi VENII.";
                else if (e instanceof IllegalStateException) why = "Réseau Wi-Fi indisponible ou état invalide.";
                else if (e instanceof java.net.ConnectException) why = "Le robot ne répond pas au port 9000.";
                else if (e instanceof java.io.IOException) why = "Connexion/lecture impossible. Vérifiez le Wi-Fi et l’état du robot.";
                else why = "Réponse inattendue. Aucune donnée sensible affichée.";
                runOnUiThread(() -> { lastAttempt = why; invalidateStatus("Connexion non confirmée"); connection.setText("Connexion non confirmée"); diagnostic.setText(why); if (lidarReport == null && lidarStatus != null && lidarStatus.getText().toString().startsWith("Écoute en cours")) lidarStatus.setText(why); activity(why + " • résultat d’un ordre éventuel non confirmé."); });
            } finally {
                runOnUiThread(() -> { working = false; enableButtons(); });
            }
        });
    }

    private void render(VeniiClient.Status report) {
        recordStateTransition(report);
        lastReport = report;
        lastReportTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.FRANCE).format(new java.util.Date());
        lastAttempt = "Lecture d’état reçue";
        hasStatus = true;
        lastStatusAt = android.os.SystemClock.elapsedRealtime();
        handler.removeCallbacks(expireStatus);
        handler.postDelayed(expireStatus, STATUS_TTL_MS);
        dashboardState.setText(stateLabel(report.generalState));
        dashboardBattery.setText("—");
        dashboardBatteryHint.setText("Code 60 non interprétable");
        try {
            int level = Integer.parseInt(report.powerReport.trim());
            if (level >= 0 && level <= 100) {
                dashboardBattery.setText(level + " %");
                dashboardBatteryHint.setText("Estimation (code 60)");
            }
        } catch (NumberFormatException ignored) { /* Garder la valeur brute sans inventer de pourcentage. */ }
        connection.setText("Robot détecté • état reçu");
        state.setText(report.generalState);
        power.setText(report.powerReport);
        suction.setText(report.suction);
        volume.setText(report.volume);
        issue.setText(report.error);
        telemetrySummary.setText("Lecture réelle reçue • " + report.messageCount
            + " champ(s) reconnus • " + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date())
            + " • valeurs non interprétées");
        diagnostic.setText("État 888 reçu à " + clock() + " • " + report.messageCount + " champ(s) reconnus. Signification des codes à valider.");
        enableButtons();
    }

    private void listenLidar() {
        if (working || demoMode.isChecked()) return;
        lidarReport = null;
        lidarStatus.setText("Écoute en cours • aucun ordre de mouvement");
        invalidateStatus("Diagnostic LiDAR • relire l’état ensuite");
        execute("Diagnostic LiDAR en lecture seule…", () -> {
            VeniiClient.LidarReport result = VeniiClient.listenForLidar();
            runOnUiThread(() -> {
                lidarReport = result;
                lidarStatus.setText(result.summary());
                lastAttempt = "Diagnostic LiDAR terminé • consulter le rapport";
                activity("Diagnostic LiDAR terminé • aucun ordre de mouvement envoyé.");
            });
        });
    }

    private void exportLidarRaw() {
        if (working || lidarReport == null || !lidarReport.hasPayload()) return;
        new android.app.AlertDialog.Builder(this)
            .setTitle("Données cartographiques privées")
            .setMessage("Ces données non décodées peuvent contenir le plan de votre domicile ou d’autres informations privées. Ne les partagez que si vous le souhaitez.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Choisir un fichier", (dialog, which) -> {
                if (lidarReport == null) return;
                pendingLidarExport = lidarReport.rawPayloads();
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "KOOMA-I-lidar.txt");
                try { startActivityForResult(intent, LIDAR_EXPORT_REQUEST); }
                catch (android.content.ActivityNotFoundException failure) {
                    pendingLidarExport = null;
                    android.widget.Toast.makeText(this,"Sélecteur de fichiers indisponible",android.widget.Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != LIDAR_EXPORT_REQUEST) return;
        String payload = pendingLidarExport;
        pendingLidarExport = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || payload == null) return;
        try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (out == null) throw new java.io.IOException();
            out.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            android.widget.Toast.makeText(this,"Fichier LiDAR enregistré",android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception failure) {
            android.widget.Toast.makeText(this,"Export impossible • réessayez",android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        if (working) return;
        invalidateStatus("Lecture en cours");
        execute("Recherche du robot, authentification puis lecture d’état…", () -> {
            VeniiClient.Status result = VeniiClient.readStatus();
            runOnUiThread(() -> { render(result); activity("Lecture d’état reçue • aucune commande de mouvement envoyée."); });
        });
    }

    private void sendStart() {
        if (working || demoMode.isChecked() || !repaired.isChecked() || !freshStatus()) return;
        lastCommand = "1020 • tentative, résultat non confirmé"; lastBefore = "—"; lastAfter = "—";
        execute("Authentification et état de sécurité, puis un seul ordre 1020…", () -> {
            VeniiClient.StartResult result = VeniiClient.startOnce();
            runOnUiThread(() -> {
                lastCommand = "1020 • transmis, effet non confirmé";
                lastBefore = result.before.generalState;
                lastAfter = result.after == null ? "—" : result.after.generalState;
                connection.setText("Commande 1020 envoyée • effet à observer");
                if (result.after != null) {
                    render(result.after);
                    activity("1020 envoyé. État avant : " + result.before.generalState
                        + " • après : " + result.after.generalState
                        + ". Vérifiez physiquement le robot.");
                } else {
                    invalidateStatus("État non actualisé");
                    lastAttempt = "Ordre transmis, nouvelle lecture indisponible";
                    activity("1020 transmis. Aucune nouvelle lecture d’état. Vérifiez physiquement le robot.");
                }

            });
        });
    }


    private void simulate(int code, String label) {
        showDemoMotion(code, label);
        if (code == 1020) { demoMap.reset(); startDemoTimer(true); }
        else if (code == 1070) startDemoTimer(false);
        else pauseDemoTimer();
        activity("DÉMO : " + label + " (" + code + ") simulé. Aucune commande envoyée.");
        connection.setText("Mode démo • commande " + code + " simulée");
    }

    private void sendUnverified(int code, String label) {
        if (working || !freshStatus() || !repaired.isChecked() || demoMode.isChecked()) return;
        lastCommand = code + " • tentative, résultat non confirmé"; lastBefore = "—"; lastAfter = "—";
        execute("Envoi local de " + label + " (" + code + ")…", () -> {
            VeniiClient.StartResult result = VeniiClient.commandOnce(code);
            runOnUiThread(() -> {
                lastCommand = code + " • transmis, effet non confirmé";
                lastBefore = result.before.generalState;
                lastAfter = result.after == null ? "—" : result.after.generalState;
                connection.setText("Commande " + code + " transmise • résultat à vérifier");
                if (result.after != null) {
                    render(result.after);
                    activity(label + " (" + code + ") transmis. État avant : "
                        + result.before.generalState + " • après : " + result.after.generalState
                        + ". Observez physiquement le robot.");
                } else {
                    invalidateStatus("État non actualisé");
                    lastAttempt = "Ordre transmis, nouvelle lecture indisponible";
                    activity(label + " (" + code + ") transmis, mais état ultérieur indisponible. Vérifiez physiquement le robot.");
                }
            });
        });
    }

    @Override protected void onStop() {
        stopMonitor("Observation arrêtée : application en arrière-plan.");
        pauseDemoTimer();
        stopDemoAnimation();
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopMonitor("Observation arrêtée.");
        pauseDemoTimer();
        stopDemoAnimation();
        handler.removeCallbacks(expireStatus);
        io.shutdownNow();
        super.onDestroy();
    }
}
