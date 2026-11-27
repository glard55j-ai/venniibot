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

/** Local robot control and diagnostics, no simulations or cloud. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 19, 32);
    private static final int CARD = Color.rgb(21, 37, 55);
    private static final int ACCENT = Color.rgb(59, 220, 177);
    private static final int PRIMARY = Color.WHITE;
    private static final int MUTED = Color.rgb(164, 183, 201);
    private static final int WARNING = Color.rgb(255, 191, 106);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Button btnConnect, btnStart, btnPause, btnResume, btnDock;
    private CheckBox repaired;
    private TextView connection, state, power, suction, volume, issue, note, telemetrySummary, dashboardMode, dashboardState, dashboardBattery, dashboardBatteryHint, historyView, diagnostic;
    private android.widget.ProgressBar batteryProgress;
    private TextView dashboardReadTime, connectionDetail;
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
    private Button lidarListen, lidarListenLong, lidarRaw, lidarCopy, lidarOpenMap;
    private String pendingLidarExport;
    private static final int LIDAR_EXPORT_REQUEST = 260;
    private static final int STATE_EXPORT_REQUEST = 261;
    private String pendingStateExport;
    private String lastReportTime = "Jamais", lastAttempt = "Aucune tentative réelle", lastCommand = "Aucune commande réelle", lastBefore = "—", lastAfter = "—";
    private final java.util.ArrayDeque<String> history = new java.util.ArrayDeque<>();
    private volatile boolean working = false;
    private boolean hasStatus = false;
    private long lastStatusAt = 0L;
    private static final long STATUS_TTL_MS = 60000L;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable expireStatus = () -> {
        hasStatus = false;
        if (dashboardState != null) {
            dashboardState.setAlpha(0.48f);
            dashboardBattery.setAlpha(0.48f);
            batteryProgress.setAlpha(0.48f);
            dashboardBatteryHint.setAlpha(0.48f);
            dashboardReadTime.setText("Dernière lecture : " + lastReportTime + " • expirée");
            connectionDetail.setText("Lecture expirée • valeurs affichées potentiellement dépassées");
            connection.setText("Dernière lecture expirée");
            telemetrySummary.setText("Lecture expirée • données anciennes, commande réelle verrouillée");
            enableButtons();
        }
    };

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

    private String diagnosticReport() {
        StringBuilder r = new StringBuilder();
        r.append("KOOMA-I • v").append(appVersion()).append("\n");
        r.append("Rapport local • ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.FRANCE).format(new java.util.Date())).append("\n");
        r.append("Android : ").append(android.os.Build.VERSION.SDK_INT).append("\n");
        r.append("Mode : CONTRÔLE RÉEL (sans simulation)\n");
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

    private void exportStateHistory() {
        if (stateTransitions.isEmpty()) {
            android.widget.Toast.makeText(this, "Aucun changement d’état à exporter", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder body = new StringBuilder("KOOMA-I • Historique des états\\n");
        java.util.Iterator<String> it = stateTransitions.descendingIterator();
        while (it.hasNext()) {
            String entry = it.next();
            // Export public lisible : heure locale et état, sans codes ni alimentation.
            int first = entry.indexOf(" • État ");
            int open = entry.indexOf('(', first);
            int close = entry.indexOf(" • interprétation", open);
            if (first >= 0 && open > first && close > open) {
                body.append(entry.substring(0, first)).append(" – ")
                    .append(entry.substring(open + 1, close)).append("\\n");
            } else if (first >= 0) {
                body.append(entry.substring(0, first)).append(" – État inconnu").append("\\n");
            }
        }
        pendingStateExport = body.toString();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "KOOMA-I-etats.txt");
        try { startActivityForResult(intent, STATE_EXPORT_REQUEST); }
        catch (android.content.ActivityNotFoundException failure) {
            pendingStateExport = null;
            android.widget.Toast.makeText(this,"Sélecteur de fichiers indisponible",android.widget.Toast.LENGTH_LONG).show();
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
        add(main, text("Lecture locale, sans Internet", 14, MUTED, false), 4);
        Button mapButton = button("Cartographie LiDAR", CARD, PRIMARY);
        mapButton.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        add(main, mapButton, 12);

        LinearLayout dashboard = column(18, CARD);
        add(main, dashboard, 20);
        add(dashboard, text("TABLEAU DE BORD", 12, ACCENT, true), 0);
        dashboardMode = text("COMMANDES RÉELLES • lecture de sécurité obligatoire", 13, ACCENT, true);
        add(dashboard, dashboardMode, 9);
        add(dashboard, text("État du robot uniquement après lecture réelle ; commandes sécurisées par un état récent.", 12, MUTED, false), 8);
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
        batteryProgress = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        batteryProgress.setMax(100);
        batteryProgress.setProgress(0);
        batteryProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        batteryProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 78, 94)));
        add(batteryTile, batteryProgress, 7);
        dashboardBatteryHint = text("En attente de lecture", 12, MUTED, false);
        add(batteryTile, dashboardBatteryHint, 3);
        add(statusTile, text("ÉTAT DU ROBOT", 11, MUTED, true), 0);
        dashboardState = text("En attente", 16, PRIMARY, true);
        add(statusTile, dashboardState, 5);
        dashboardReadTime = text("Aucune lecture réelle", 12, MUTED, false);
        add(statusTile, dashboardReadTime, 3);
        tiles.setGravity(Gravity.TOP);
        add(dashboard, tiles, 14);
        add(dashboard, text("Les valeurs réelles sont affichées uniquement après lecture du robot.", 12, MUTED, false), 12);

        Button quick = button("Accéder aux commandes", Color.rgb(38,60,80), PRIMARY);
        add(dashboard, quick, 10);

        LinearLayout connectivity = column(16, CARD);
        add(main, connectivity, 25);
        add(connectivity, text("CONNEXION LOCALE", 12, ACCENT, true), 0);
        connection = text("Non connecté", 19, PRIMARY, true);
        add(connectivity, connection, 10);
        connectionDetail = text("Statut : aucune lecture récente du robot", 12, MUTED, false);
        add(connectivity, connectionDetail, 5);
        add(connectivity, text("Place le robot sur sa base, puis appuie sur Connecter et lire l’état.", 12, MUTED, false), 6);
        add(connectivity, text("Consulter l’état n’envoie aucune action.", 12, MUTED, false), 5);
        add(connectivity, text("Réseau VENII du robot • 192.168.78.1:9000", 12, MUTED, false), 5);
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
        btnStart = button("Démarrer  ·  1020", ACCENT, BG);
        add(controls, btnStart, 14);
        btnStart.setOnClickListener(v -> requestRealCommand(1020, "Démarrer le nettoyage"));
        btnPause = button("Pause  ·  1060", Color.rgb(38, 60, 80), PRIMARY);
        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(0, -2, 1f);
        pauseParams.rightMargin = dp(8);
        pair.addView(btnPause, pauseParams);
        add(controls, pair, 8);
        btnPause.setOnClickListener(v -> requestRealCommand(1060, "Mettre en pause"));
        btnResume = button("Reprendre  ·  1070", Color.rgb(38, 60, 80), PRIMARY);
        pair.addView(btnResume, new LinearLayout.LayoutParams(0, -2, 1f));
        btnResume.setOnClickListener(v -> requestRealCommand(1070, "Reprendre le nettoyage"));
        btnDock = button("Retour à la base  ·  1050", Color.rgb(38, 60, 80), PRIMARY);
        add(controls, btnDock, 9);
        btnDock.setOnClickListener(v -> requestRealCommand(1050, "Retour à la base"));
        add(controls, text("Les codes 1020, 1060, 1070 et 1050 ont répondu lors des essais physiques du 26/11/2026. Un appui envoie toujours un seul ordre ; restez près du robot et de son bouton physique.", 12, MUTED, false), 12);

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
            if (working) {
                monitorStatus.setText("Terminez la commande en cours avant d’observer.");
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
        Button exportStates = button("Exporter l’historique (TXT)", Color.rgb(38,60,80), PRIMARY);
        add(observation, exportStates, 10);
        exportStates.setOnClickListener(v -> exportStateHistory());
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
        add(diag, text("Le rapport ne concerne que les essais réels, sans jetons ni identifiants réseau. Partage manuel uniquement.", 12, MUTED, false), 8);
        LinearLayout lidar = column(14, CARD);
        add(main, lidar, 12);
        add(lidar, text("LIDAR • CARTOGRAPHIE EXPÉRIMENTALE", 12, ACCENT, true), 0);
        add(lidar, text("Écoute en lecture seule de 15 s ou 45 s après authentification et demande d’état 888. Aucun ordre LiDAR ni déplacement. À effectuer près du robot.", 12, MUTED, false), 8);
        lidarStatus = text("Aucune écoute réelle effectuée • carte indisponible", 13, PRIMARY, false);
        add(lidar, lidarStatus, 10);
        lidarListen = button("Écoute rapide • 15 secondes", ACCENT, BG);
        add(lidar, lidarListen, 8);
        lidarListen.setOnClickListener(v -> listenLidar(15));
        lidarListenLong = button("Écoute prolongée • 45 secondes", Color.rgb(38,60,80), PRIMARY);
        add(lidar, lidarListenLong, 8);
        lidarListenLong.setOnClickListener(v -> listenLidar(45));
        lidarCopy = button("Copier le résumé LiDAR (sans données privées)", Color.rgb(38,60,80), PRIMARY);
        add(lidar, lidarCopy, 8);
        lidarCopy.setOnClickListener(v -> copyLidarSummary());
        lidarOpenMap = button("Examiner les données reçues dans Cartographie", ACCENT, BG);
        add(lidar, lidarOpenMap, 8);
        lidarOpenMap.setOnClickListener(v -> openLidarCandidates());
        lidarRaw = button("Exporter les données candidates brutes", Color.rgb(38,60,80), PRIMARY);
        add(lidar, lidarRaw, 8);
        lidarRaw.setOnClickListener(v -> exportLidarRaw());
        add(lidar, text("Le résumé ne contient que les identifiants, types, comptes et tailles (y compris 1599, 1600 et 1707), jamais leurs valeurs ni coordonnées. Les données brutes peuvent révéler le plan du domicile : export manuel uniquement.", 12, MUTED, false), 8);
        LinearLayout changes = column(18, CARD);
        add(main, changes, 16);
        add(changes, text("VERSION " + appVersion(), 12, ACCENT, true), 0);
        add(main, text("Prototype indépendant • Aucun jeton stocké • Aucun serveur externe", 12, MUTED, false), 20);
        enableButtons();
    }

    private void invalidateStatus(String message) {
        hasStatus = false;
        handler.removeCallbacks(expireStatus);
        if (dashboardState != null) { dashboardState.setText(message); dashboardState.setAlpha(1f); }
        if (dashboardBattery != null) { dashboardBattery.setText("—"); dashboardBattery.setAlpha(1f); }
        if (dashboardBatteryHint != null) dashboardBatteryHint.setText("Lecture non actualisée");
        if (batteryProgress != null) { batteryProgress.setProgress(0); batteryProgress.setAlpha(1f); }
        if (dashboardReadTime != null)
            dashboardReadTime.setText("Dernière lecture : " +
                ("Jamais".equals(lastReportTime) ? "aucune" : lastReportTime + " • expirée"));
        if (connectionDetail != null) connectionDetail.setText("Lecture non actualisée • relire l’état");
        if (telemetrySummary != null) telemetrySummary.setText("Données non actualisées • relire l’état");
        if (diagnostic != null) diagnostic.setText(message + " • données à actualiser.");
    }

    private boolean freshStatus() {
        return hasStatus && android.os.SystemClock.elapsedRealtime() - lastStatusAt < STATUS_TTL_MS;
    }

    private void enableButtons() {
        btnConnect.setEnabled(!working && !monitorActive);
        if (lidarListen != null) lidarListen.setEnabled(!working && !monitorActive);
        if (lidarListenLong != null) lidarListenLong.setEnabled(!working && !monitorActive);
        if (lidarCopy != null) lidarCopy.setEnabled(!working && lidarReport != null);
        if (lidarOpenMap != null) lidarOpenMap.setEnabled(
                !working && lidarReport != null && lidarReport.hasPayload());
        if (lidarRaw != null) lidarRaw.setEnabled(!working && lidarReport != null && lidarReport.hasPayload());
        repaired.setEnabled(!working && !monitorActive);
        // Buttons stay clickable while idle: missing prerequisites are explained on tap.
        boolean ready = !working && !monitorActive;
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
                runOnUiThread(() -> { lastAttempt = why; invalidateStatus("Connexion non confirmée"); connection.setText("Connexion impossible"); diagnostic.setText(why); if (lidarReport == null && lidarStatus != null && lidarStatus.getText().toString().startsWith("Écoute en cours")) lidarStatus.setText(why); activity(why + " • résultat d’un ordre éventuel non confirmé."); });
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
        dashboardState.setAlpha(1f);
        dashboardBattery.setAlpha(1f);
        batteryProgress.setAlpha(1f);
        dashboardBatteryHint.setAlpha(1f);
        dashboardBattery.setText("—");
        batteryProgress.setProgress(0);
        dashboardBatteryHint.setText("Code 60 non interprétable");
        dashboardReadTime.setText("Lu à " + clock() + " • valide 60 s");
        connectionDetail.setText("Réponse réelle du robot reçue • lecture récente");
        try {
            int level = Integer.parseInt(report.powerReport.trim());
            if (level >= 0 && level <= 100) {
                dashboardBattery.setText(level + " %");
                batteryProgress.setProgress(level);
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

    private void listenLidar(int seconds) {
        if (working || monitorActive || (seconds != 15 && seconds != 45)) return;
        lidarReport = null;
        lidarStatus.setText("Écoute en cours (" + seconds + " s) • aucun ordre de mouvement");
        invalidateStatus("Diagnostic LiDAR • relire l’état ensuite");
        execute("Diagnostic LiDAR " + seconds + " s en lecture seule…", () -> {
            VeniiClient.LidarReport result = VeniiClient.listenForLidar(seconds);
            runOnUiThread(() -> {
                lidarReport = result;
                lidarStatus.setText(result.summary());
                lastAttempt = "Diagnostic LiDAR terminé • consulter le rapport";
                activity("Diagnostic LiDAR terminé • aucun ordre de mouvement envoyé.");
                enableButtons();
            });
        });
    }

    private void copyLidarSummary() {
        if (working || lidarReport == null) return;
        String summary = "KOOMA-I • Diagnostic LiDAR sans valeurs privées\n"
                + lidarReport.summary() + "\nRésumé copié à la demande.";
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            android.widget.Toast.makeText(this, "Presse-papiers indisponible", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("KOOMA-I LiDAR résumé", summary));
        android.widget.Toast.makeText(this, "Résumé LiDAR copié, sans données brutes", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void openLidarCandidates() {
        if (working || lidarReport == null || !lidarReport.hasPayload()) {
            android.widget.Toast.makeText(this,
                "Aucune donnée de carte candidate reçue lors de cette écoute",
                android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String candidates = lidarReport.rawPayloads();
        if (!MapCandidateMemory.offer(candidates)) {
            android.widget.Toast.makeText(this, "Données trop volumineuses pour l'analyse locale",
                android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new Intent(this, MapActivity.class));
        } catch (Exception failure) {
            MapCandidateMemory.clear();
            android.widget.Toast.makeText(this, "Impossible d'ouvrir la cartographie",
                android.widget.Toast.LENGTH_LONG).show();
        }
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
        if (requestCode == STATE_EXPORT_REQUEST) {
            String payload = pendingStateExport;
            pendingStateExport = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || payload == null) return;
            try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                if (out == null) throw new java.io.IOException();
                out.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                android.widget.Toast.makeText(this, "Historique enregistré dans le dossier choisi", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Exception failure) {
                android.widget.Toast.makeText(this, "Export impossible", android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
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

    /** Real commands are user-triggered, with a fresh state and a physical-safety check. */
    private void requestRealCommand(int code, String label) {
        if (working || monitorActive) {
            new android.app.AlertDialog.Builder(this).setTitle("Robot occupé")
                .setMessage("Attends la fin de la commande ou du diagnostic en cours.")
                .setPositiveButton("OK", null).show();
            return;
        }
        if (!freshStatus()) {
            new android.app.AlertDialog.Builder(this).setTitle("État réel nécessaire")
                .setMessage("La lecture est absente ou expirée. Actualise l'état avant d'envoyer une commande.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Lire l'état", (d,w) -> refresh()).show();
            return;
        }
        if (!repaired.isChecked()) {
            new android.app.AlertDialog.Builder(this).setTitle("Sécurité nécessaire")
                .setMessage("Vérifie les capteurs, le pare-chocs et les escaliers, puis coche la case Sécurité.")
                .setPositiveButton("Compris", null).show();
            return;
        }
        new android.app.AlertDialog.Builder(this).setTitle("Envoyer au robot ?")
            .setMessage(label + " (" + code + ") sera envoyé une seule fois au N1 Max. "
                + "Reste près du bouton physique : l'envoi ne garantit pas l'exécution.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Envoyer", (d,w) -> {
                if (!freshStatus() || !repaired.isChecked() || working || monitorActive) {
                    activity("Commande annulée : état expiré ou sécurité non confirmée.");
                    return;
                }
                if (code == 1020) sendStart();
                else sendUnverified(code, label);
            }).show();
    }

    private void sendStart() {
        if (working || monitorActive || !repaired.isChecked() || !freshStatus()) return;
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


    private void sendUnverified(int code, String label) {
        if (working || monitorActive || !freshStatus() || !repaired.isChecked()) return;
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
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopMonitor("Observation arrêtée.");
        handler.removeCallbacks(expireStatus);
        io.shutdownNow();
        super.onDestroy();
    }
}
