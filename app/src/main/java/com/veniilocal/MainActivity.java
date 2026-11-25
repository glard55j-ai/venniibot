package com.veniilocal;

import android.app.Activity;
import android.app.AlertDialog;
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
    private TextView connection, state, power, suction, volume, issue, note, telemetrySummary;
    private volatile boolean working = false;
    private boolean hasStatus = false;

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

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout main = column(20, 0);
        main.setPadding(dp(20), dp(24), dp(20), dp(30));
        scroll.addView(main);
        setContentView(scroll);

        add(main, text("VENII LOCAL", 14, ACCENT, true), 0);
        add(main, text("N1 Max", 34, PRIMARY, true), 5);
        add(main, text("Pilotage privé en Wi-Fi, sans compte ni cloud", 14, MUTED, false), 4);

        LinearLayout connectivity = column(18, CARD);
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
        add(main, readings, 16);
        add(readings, text("DONNÉES DU ROBOT", 12, ACCENT, true), 0);
        telemetrySummary = text("En attente d’une lecture réelle • données non disponibles", 13, MUTED, false);
        add(readings, telemetrySummary, 8);
        state = valueRow(readings, "État général (code brut 888)");
        power = valueRow(readings, "Alimentation • valeur brute (code 60, pas un % batterie confirmé)");
        suction = valueRow(readings, "Puissance nettoyage (1300)");
        volume = valueRow(readings, "Volume (1502)");
        issue = valueRow(readings, "Code d’erreur (70, si présent)");
        add(readings, text("« — » signifie : donnée absente ou non décodée. Le pourcentage de batterie et la signification des codes d’erreur restent à identifier ; ne pas les deviner.", 12, MUTED, false), 14);

        LinearLayout safety = column(18, CARD);
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

        LinearLayout controls = column(18, CARD);
        add(main, controls, 16);
        add(controls, text("COMMANDES", 12, ACCENT, true), 0);
        demoMode = new CheckBox(this);
        demoMode.setText("Mode démonstration (aucun ordre envoyé)");
        demoMode.setTextColor(PRIMARY);
        demoMode.setTextSize(14);
        demoMode.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        demoMode.setChecked(true);
        add(controls, demoMode, 10);
        demoMode.setOnCheckedChangeListener((b, checked) -> {
            hasStatus = false; // Require a fresh reading before any real action.
            connection.setText(checked ? "Mode démo • sans connexion" : "Mode réel • lecture d’état obligatoire");
            note.setText(checked ? "Démo : appuyez sur les boutons pour simuler." : "Réel : connectez-vous au Wi-Fi VENII et relisez l’état avant de commander.");
            enableButtons();
        });
        btnStart = button("Démarrer le nettoyage  ·  1020", ACCENT, BG);
        add(controls, btnStart, 14);
        btnStart.setOnClickListener(v -> { if (demoMode.isChecked()) simulate(1020, "Démarrer"); else sendStart(); });
        btnPause = button("Pause  ·  1060", Color.rgb(38, 60, 80), PRIMARY);
        add(controls, btnPause, 9);
        btnPause.setOnClickListener(v -> { if (demoMode.isChecked()) simulate(1060, "Pause"); else sendUnverified(1060, "Pause"); });
        btnResume = button("Reprendre  ·  1070", Color.rgb(38, 60, 80), PRIMARY);
        add(controls, btnResume, 9);
        btnResume.setOnClickListener(v -> { if (demoMode.isChecked()) simulate(1070, "Reprendre"); else sendUnverified(1070, "Reprendre"); });
        btnDock = button("Retour à la base  ·  1050", Color.rgb(38, 60, 80), PRIMARY);
        add(controls, btnDock, 9);
        btnDock.setOnClickListener(v -> { if (demoMode.isChecked()) simulate(1050, "Retour à la base"); else sendUnverified(1050, "Retour à la base"); });
        add(controls, text("Les codes 1050, 1060 et 1070 restent à valider sur votre robot. Le mode réel demande un état récent et votre vérification physique. Un appui envoie directement un seul ordre : restez près du bouton physique du robot.", 12, MUTED, false), 12);

        LinearLayout logs = column(18, CARD);
        add(main, logs, 16);
        add(logs, text("ACTIVITÉ", 12, ACCENT, true), 0);
        note = text("Prêt. Connectez d’abord le téléphone au réseau Wi-Fi VENII.", 13, MUTED, false);
        add(logs, note, 10);
        add(main, text("Prototype indépendant • Aucun jeton stocké • Aucun serveur externe", 12, MUTED, false), 20);
        enableButtons();
    }

    private void enableButtons() {
        btnConnect.setEnabled(!working);
        boolean ready = !working && (demoMode.isChecked() || (hasStatus && repaired.isChecked()));
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
        note.setText(message);
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
                runOnUiThread(() -> { connection.setText("Connexion non confirmée"); note.setText(why); });
            } finally {
                runOnUiThread(() -> { working = false; enableButtons(); });
            }
        });
    }

    private void render(VeniiClient.Status report) {
        hasStatus = true;
        connection.setText("Robot détecté • état reçu");
        state.setText(report.generalState);
        power.setText(report.powerReport);
        suction.setText(report.suction);
        volume.setText(report.volume);
        issue.setText(report.error);
        telemetrySummary.setText("Lecture réelle reçue • " + report.messageCount
            + " champ(s) reconnus • " + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date())
            + " • valeurs non interprétées");
        note.setText("Lecture d’état reçue sur le Wi-Fi local. Aucune commande de mouvement envoyée.");
        enableButtons();
    }

    private void refresh() {
        execute("Recherche du robot, authentification puis lecture d’état…", () -> {
            VeniiClient.Status result = VeniiClient.readStatus();
            runOnUiThread(() -> render(result));
        });
    }

    private void sendStart() {
        if (working || demoMode.isChecked() || !repaired.isChecked() || !hasStatus) return;
        execute("Authentification et état de sécurité, puis un seul ordre 1020…", () -> {
            VeniiClient.StartResult result = VeniiClient.startOnce();
            runOnUiThread(() -> {
                connection.setText("Commande 1020 envoyée • effet à observer");
                if (result.after != null) {
                    render(result.after);
                    note.setText("1020 envoyé. État avant : " + result.before.generalState
                        + " • après : " + result.after.generalState
                        + ". Vérifiez physiquement le robot.");
                } else {
                    note.setText("1020 transmis. Aucune nouvelle lecture d’état. Vérifiez physiquement le robot.");
                }

            });
        });
    }


    private void simulate(int code, String label) {
        note.setText("DÉMO : " + label + " (" + code + ") simulé. Aucune commande envoyée.");
        connection.setText("Mode démo • commande " + code + " simulée");
    }

    private void sendUnverified(int code, String label) {
        if (working || !hasStatus || !repaired.isChecked() || demoMode.isChecked()) return;
        execute("Envoi local de " + label + " (" + code + ")…", () -> {
            VeniiClient.StartResult result = VeniiClient.commandOnce(code);
            runOnUiThread(() -> {
                connection.setText("Commande " + code + " transmise • résultat à vérifier");
                if (result.after != null) {
                    render(result.after);
                    note.setText(label + " (" + code + ") transmis. État avant : "
                        + result.before.generalState + " • après : " + result.after.generalState
                        + ". Observez physiquement le robot.");
                } else {
                    note.setText(label + " (" + code + ") transmis, mais état ultérieur indisponible. Vérifiez physiquement le robot.");
                }
            });
        });
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
