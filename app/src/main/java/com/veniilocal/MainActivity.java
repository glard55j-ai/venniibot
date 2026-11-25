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

/** Non-official, locally controlled UI. No cloud, account, analytics or persistence. */
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
    private TextView connection, state, power, suction, volume, issue, note, telemetrySummary, dashboardMode, dashboardState, historyView, diagnostic, demoStatus;
    private View robotPreview;
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

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout main = column(20, 0);
        main.setPadding(dp(20), dp(24), dp(20), dp(30));
        scroll.addView(main);
        setContentView(scroll);

        add(main, text("VENII LOCAL  •  v" + appVersion(), 14, ACCENT, true), 0);
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
        add(batteryTile, text("— %", 24, PRIMARY, true), 5);
        add(batteryTile, text("Niveau non identifié", 12, MUTED, false), 3);
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
        add(readings, text("« — » signifie : donnée absente ou non décodée. Le pourcentage de batterie et la signification des codes d’erreur restent à identifier ; ne pas les deviner.", 12, MUTED, false), 14);

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
        add(controls, text("Les codes 1050, 1060 et 1070 restent à valider sur votre robot. Le mode réel demande un état récent et votre vérification physique. Un appui envoie directement un seul ordre : restez près du bouton physique du robot.", 12, MUTED, false), 12);

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
        LinearLayout diag = column(14, CARD);
        add(main, diag, 12);
        add(diag, text("DIAGNOSTIC LOCAL", 12, ACCENT, true), 0);
        diagnostic = text("Aucune lecture réelle effectuée.", 13, MUTED, false);
        add(diag, diagnostic, 8);
        add(diag, text("UDP : découverte • TCP 9000 : authentification 67 et état 888. Une transmission ne prouve pas un mouvement.", 12, MUTED, false), 8);
        Button retry = button("Relire l’état (sans mouvement)", Color.rgb(38,60,80), PRIMARY);
        add(diag, retry, 8);
        retry.setOnClickListener(v -> refresh());
        LinearLayout changes = column(18, CARD);
        add(main, changes, 16);
        add(changes, text("VERSION " + appVersion(), 12, ACCENT, true), 0);
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
        if (telemetrySummary != null) telemetrySummary.setText("Données non actualisées • relire l’état");
        if (diagnostic != null) diagnostic.setText(message + " • données à actualiser.");
    }

    private boolean freshStatus() {
        return hasStatus && android.os.SystemClock.elapsedRealtime() - lastStatusAt < STATUS_TTL_MS;
    }

    private void enableButtons() {
        btnConnect.setEnabled(!working);
        demoMode.setEnabled(!working);
        repaired.setEnabled(!working);
        boolean ready = !working && (demoMode.isChecked() || (freshStatus() && repaired.isChecked()));
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
        enableButtons();
        activity(message);
        io.execute(() -> {
            try {
                bindToWifiIfAvailable();
                task.run();
            } catch (Exception e) {
                String why;
                if (e instanceof SocketTimeoutException) why = "Délai dépassé. Vérifiez le Wi-Fi VENII.";
                else if (e instanceof IllegalStateException) why = e.getMessage();
                else if (e instanceof java.net.ConnectException) why = "Le robot ne répond pas au port 9000.";
                else if (e instanceof java.io.IOException) why = "Connexion/lecture impossible. Vérifiez le Wi-Fi et l’état du robot.";
                else why = "Réponse inattendue. Aucune donnée sensible affichée.";
                runOnUiThread(() -> { invalidateStatus("Connexion non confirmée"); connection.setText("Connexion non confirmée"); diagnostic.setText(why); activity(why + " • résultat d’un ordre éventuel non confirmé."); });
            } finally {
                runOnUiThread(() -> { working = false; enableButtons(); });
            }
        });
    }

    private void render(VeniiClient.Status report) {
        hasStatus = true;
        lastStatusAt = android.os.SystemClock.elapsedRealtime();
        handler.removeCallbacks(expireStatus);
        handler.postDelayed(expireStatus, STATUS_TTL_MS);
        dashboardState.setText("Code " + report.generalState);
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
        execute("Authentification et état de sécurité, puis un seul ordre 1020…", () -> {
            VeniiClient.StartResult result = VeniiClient.startOnce();
            runOnUiThread(() -> {
                connection.setText("Commande 1020 envoyée • effet à observer");
                if (result.after != null) {
                    render(result.after);
                    activity("1020 envoyé. État avant : " + result.before.generalState
                        + " • après : " + result.after.generalState
                        + ". Vérifiez physiquement le robot.");
                } else {
                    invalidateStatus("État non actualisé");
                    activity("1020 transmis. Aucune nouvelle lecture d’état. Vérifiez physiquement le robot.");
                }

            });
        });
    }


    private void simulate(int code, String label) {
        showDemoMotion(code, label);
        activity("DÉMO : " + label + " (" + code + ") simulé. Aucune commande envoyée.");
        connection.setText("Mode démo • commande " + code + " simulée");
    }

    private void sendUnverified(int code, String label) {
        if (working || !freshStatus() || !repaired.isChecked() || demoMode.isChecked()) return;
        execute("Envoi local de " + label + " (" + code + ")…", () -> {
            VeniiClient.StartResult result = VeniiClient.commandOnce(code);
            runOnUiThread(() -> {
                connection.setText("Commande " + code + " transmise • résultat à vérifier");
                if (result.after != null) {
                    render(result.after);
                    activity(label + " (" + code + ") transmis. État avant : "
                        + result.before.generalState + " • après : " + result.after.generalState
                        + ". Observez physiquement le robot.");
                } else {
                    invalidateStatus("État non actualisé");
                    activity(label + " (" + code + ") transmis, mais état ultérieur indisponible. Vérifiez physiquement le robot.");
                }
            });
        });
    }

    @Override protected void onStop() {
        stopDemoAnimation();
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopDemoAnimation();
        handler.removeCallbacks(expireStatus);
        io.shutdownNow();
        super.onDestroy();
    }
}
