package com.veniilocal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * KOOMA-I's friendly navigation shell. Existing controls and LiDAR analysis
 * remain in their previously tested activities; no movement is triggered here.
 */
public final class KoomaHomeActivity extends Activity {
    private static final int GREEN = Color.rgb(8, 191, 150);
    private static final int DARK_GREEN = Color.rgb(3, 116, 100);
    private static final int INK = Color.rgb(28, 51, 55);
    private static final int SUBTLE = Color.rgb(113, 136, 137);
    private static final int PALE = Color.rgb(241, 250, 247);
    private static final int BACKGROUND = Color.rgb(249, 251, 250);
    private static final int LINE = Color.rgb(231, 239, 236);
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private KoomaDraftStore drafts;

    private LinearLayout contents, navbar;
    private TextView[] navSymbols = new TextView[5];
    private TextView[] navLabels = new TextView[5];
    private LinearLayout[] navItems = new LinearLayout[5];
    private int selectedTab = 2;
    private String robotState = "Non vérifié";
    private String robotBattery = "—";
    private String lastCheck = "Touchez « Lire l'état » pour interroger le robot.";
    private boolean checking;
    private boolean sendingCommand;
    private boolean destroyed;
    private boolean lastStatusValid;
    private android.widget.CheckBox safetyCheck;
    private long lastReadElapsed;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable expireStatus = () -> {
        if (destroyed || checking || lastReadElapsed <= 0) return;
        if (android.os.SystemClock.elapsedRealtime() - lastReadElapsed >= 60000) {
            robotState = "Lecture expirée";
            lastStatusValid = false;
            robotBattery = "—";
            lastCheck = "Dernière lecture expirée : appuie sur « Lire l'état » pour actualiser.";
            lastReadElapsed = 0;
            if (selectedTab == 2) selectTab(2);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        drafts = new KoomaDraftStore(this);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        renderShell();
        int openingTab = getIntent().getIntExtra("open_tab", 2);
        selectTab(openingTab >= 0 && openingTab <= 4 ? openingTab : 2);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int openingTab = intent.getIntExtra("open_tab", selectedTab);
        if (openingTab >= 0 && openingTab <= 4) selectTab(openingTab);
    }

    private int dp(float n) {
        return (int)(getResources().getDisplayMetrics().density * n + .5f);
    }

    private GradientDrawable background(int fill, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable outlined(int fill, int radius, int border) {
        GradientDrawable d = background(fill, radius);
        d.setStroke(dp(1), border);
        return d;
    }

    private TextView text(String label, int size, int color, boolean strong) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(color);
        t.setTextSize(size);
        if (strong) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return t;
    }

    private void add(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(margin);
        parent.addView(view, p);
    }

    private LinearLayout vertical(int padding) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = vertical(18);
        c.setBackground(outlined(Color.WHITE, 22, LINE));
        return c;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(primary ? Color.WHITE : DARK_GREEN);
        b.setBackground(primary ? background(GREEN, 16) : outlined(PALE, 16, LINE));
        b.setMinHeight(dp(54));
        return b;
    }

    private void renderShell() {
        LinearLayout root = vertical(0);
        root.setBackgroundColor(BACKGROUND);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(16), dp(22), dp(10));
        header.setBackgroundColor(Color.WHITE);
        TextView brand = text("KOOMA-I", 22, DARK_GREEN, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView device = text("VENIIBOT N1 MAX", 10, SUBTLE, true);
        device.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(device);
        root.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        contents = vertical(20);
        contents.setPadding(dp(20), dp(18), dp(20), dp(22));
        scroll.addView(contents);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        navbar = new LinearLayout(this);
        navbar.setBackground(outlined(Color.WHITE, 22, LINE));
        navbar.setPadding(dp(5), dp(8), dp(5), dp(9));
        navbar.setGravity(Gravity.CENTER);
        String[] labels = {"Carte", "Planning", "Robot", "Historique", "Réglages"};
        String[] symbols = {"⌖", "▦", "◉", "◷", "⚙"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            LinearLayout item = vertical(2);
            item.setGravity(Gravity.CENTER);
            TextView icon = text(symbols[i], 24, SUBTLE, true);
            icon.setGravity(Gravity.CENTER);
            item.addView(icon, new LinearLayout.LayoutParams(-1, dp(32)));
            TextView label = text(labels[i], 10, SUBTLE, false);
            label.setGravity(Gravity.CENTER);
            item.addView(label, new LinearLayout.LayoutParams(-1, dp(18)));
            navbar.addView(item, new LinearLayout.LayoutParams(0, dp(54), 1f));
            item.setOnClickListener(v -> selectTab(tab));
            navItems[i] = item;
            navSymbols[i] = icon;
            navLabels[i] = label;
        }
        root.addView(navbar);
    }

    private void selectTab(int index) {
        selectedTab = index;
        for (int i = 0; i < navItems.length; i++) {
            boolean active = i == index;
            navItems[i].setBackground(active ? background(PALE, 16) : null);
            navSymbols[i].setTextColor(active ? GREEN : SUBTLE);
            navLabels[i].setTextColor(active ? DARK_GREEN : SUBTLE);
            navLabels[i].setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            navItems[i].setContentDescription((active ? "Onglet sélectionné : " : "Ouvrir ") + navLabels[i].getText());
        }
        contents.removeAllViews();
        if (index == 0) showMap();
        else if (index == 1) showPlanning();
        else if (index == 2) showRobot();
        else if (index == 3) showHistory();
        else showSettings();
    }

    private void sectionTitle(String main, String caption) {
        add(contents, text(main, 25, INK, true), 0);
        add(contents, text(caption, 13, SUBTLE, false), 8);
    }

    private void openExistingControls() {
        startActivity(new Intent(this, MainActivity.class));
    }

    private void openExistingMap() {
        startActivity(new Intent(this, MapActivity.class));
    }

    private void showRobot() {
        sectionTitle("Mon Veniibot N1 Max", "Commandes réelles • connexion locale");

        RobotPicture robot = new RobotPicture(this);
        LinearLayout.LayoutParams picture = new LinearLayout.LayoutParams(-1, dp(164));
        picture.topMargin = dp(3);
        contents.addView(robot, picture);

        LinearLayout summary = card();
        add(summary, text("État : " + robotState, 17, DARK_GREEN, true), 0);
        add(summary, text("Batterie estimée : " + robotBattery, 14, SUBTLE, false), 6);
        add(summary, text(lastCheck, 12, SUBTLE, false), 8);
        Button read = button(checking ? "Lecture en cours…" : "Actualiser l'état du robot", true);
        read.setEnabled(!checking && !sendingCommand);
        add(summary, read, 12);
        read.setOnClickListener(v -> readRobotStatus());
        add(contents, summary, 12);

        LinearLayout control = card();
        add(control, text("Contrôler mon robot", 18, INK, true), 0);
        add(control, text("Boutons réels : 1020 démarrer, 1060 pause, 1070 reprendre, 1050 retour à la base. "
            + "Un ordre est transmis uniquement après lecture récente, contrôle de sécurité et confirmation.",
            12, SUBTLE, false), 8);

        safetyCheck = new android.widget.CheckBox(this);
        safetyCheck.setText("J'ai vérifié le pare-chocs, les capteurs, les escaliers et dégagé le sol.");
        safetyCheck.setTextColor(INK);
        safetyCheck.setTextSize(13);
        safetyCheck.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        add(control, safetyCheck, 12);

        Button start = button("▶ Démarrer le nettoyage", true);
        start.setEnabled(!sendingCommand);
        add(control, start, 11);
        start.setOnClickListener(v -> requestHomeCommand(1020, "Démarrer le nettoyage"));

        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        Button pause = button("Ⅱ Pause", false);
        Button resume = button("▶ Reprendre", false);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(58), 1f);
        half.rightMargin = dp(5);
        pair.addView(pause, half);
        LinearLayout.LayoutParams otherHalf = new LinearLayout.LayoutParams(0, dp(58), 1f);
        otherHalf.leftMargin = dp(5);
        pair.addView(resume, otherHalf);
        pause.setEnabled(!sendingCommand);
        resume.setEnabled(!sendingCommand);
        pause.setOnClickListener(v -> requestHomeCommand(1060, "Mettre en pause"));
        resume.setOnClickListener(v -> requestHomeCommand(1070, "Reprendre le nettoyage"));
        add(control, pair, 7);

        Button dock = button("⌂ Retour à la base", false);
        dock.setEnabled(!sendingCommand);
        add(control, dock, 9);
        dock.setOnClickListener(v -> requestHomeCommand(1050, "Retour à la base"));

        Button advanced = button("Diagnostics et réglages avancés", false);
        add(control, advanced, 13);
        advanced.setOnClickListener(v -> openExistingControls());
        add(contents, control, 12);

        showDraftCards(KoomaDraftStore.FAVORITES);
    }

    private boolean freshRobotState() {
        return lastStatusValid && lastReadElapsed > 0
            && android.os.SystemClock.elapsedRealtime() - lastReadElapsed < 60000L;
    }

    private void requestHomeCommand(int code, String label) {
        if (sendingCommand || checking) {
            new AlertDialog.Builder(this).setTitle("Action en cours")
                .setMessage("Attends la fin de la commande ou de la lecture.")
                .setPositiveButton("OK", null).show();
            return;
        }
        if (!freshRobotState()) {
            new AlertDialog.Builder(this).setTitle("Lecture réelle nécessaire")
                .setMessage("L'état du robot est absent ou expiré. Lis son état avant de commander.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Lire l'état", (d,w) -> readRobotStatus()).show();
            return;
        }
        if (safetyCheck == null || !safetyCheck.isChecked()) {
            new AlertDialog.Builder(this).setTitle("Sécurité à confirmer")
                .setMessage("Vérifie que le robot peut bouger sans risque, puis coche la case de sécurité.")
                .setPositiveButton("Compris", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Envoyer la commande réelle ?")
            .setMessage(label + " (" + code + ") sera transmis une fois au N1 Max, sans simulation. "
                + "Reste près du bouton physique : un ordre envoyé ne garantit pas son exécution.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Envoyer", (d,w) -> {
                if (sendingCommand || checking || !freshRobotState()
                        || safetyCheck == null || !safetyCheck.isChecked()) return;
                executeHomeCommand(code, label);
            }).show();
    }

    private void executeHomeCommand(int code, String label) {
        if (sendingCommand) return;
        sendingCommand = true;
        lastStatusValid = false;
        lastReadElapsed = 0L;
        handler.removeCallbacks(expireStatus);
        robotState = "Commande en cours";
        robotBattery = "—";
        lastCheck = label + " : tentative réelle en cours. Le résultat reste à vérifier.";
        if (selectedTab == 2) selectTab(2);
        work.execute(() -> {
            String feedback;
            try {
                bindToRobotWifi();
                VeniiClient.StartResult result = VeniiClient.commandOnce(code);
                String before = result.before == null ? "—" : result.before.generalState;
                String after = result.after == null ? "indisponible" : result.after.generalState;
                feedback = label + " : commande " + code + " envoyée une fois. État avant : "
                    + before + ", après : " + after + ". Vérifie physiquement le robot puis relis l'état.";
            } catch (Exception error) {
                feedback = label + " : transmission non confirmée. Vérifie le Wi-Fi et l'état du robot "
                    + "avant de réessayer ; ne suppose pas que l'ordre a échoué.";
            }
            final String message = feedback;
            runOnUiThread(() -> {
                if (destroyed) return;
                sendingCommand = false;
                robotState = "À actualiser";
                lastCheck = message;
                if (selectedTab == 2) selectTab(2);
                new AlertDialog.Builder(this).setTitle("Résultat de la commande")
                    .setMessage(message).setPositiveButton("OK", null).show();
            });
        });
    }

    private void bindToRobotWifi() {
        ConnectivityManager cm =
            (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) throw new IllegalStateException("Wi-Fi non disponible");
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                cm.bindProcessToNetwork(network);
                return;
            }
        }
        throw new IllegalStateException("Connecte le téléphone au Wi-Fi du robot");
    }

    private void readRobotStatus() {
        if (checking || sendingCommand) return;
        checking = true;
        robotState = "Lecture en cours";
        lastStatusValid = false;
        lastReadElapsed = 0L;
        robotBattery = "—";
        lastCheck = "Interrogation locale en cours. Aucune commande de mouvement envoyée.";
        selectTab(2);
        work.execute(() -> {
            String state = "Lecture impossible", battery = "—", info;
            boolean valid = false;
            try {
                bindToRobotWifi();
                VeniiClient.Status report = VeniiClient.readStatus();
                state = stateText(report.generalState);
                valid = report.generalState != null && report.generalState.matches("[0-9]{1,4}");
                String value = report.powerReport;
                try {
                    int percent = Integer.parseInt(value);
                    if (percent >= 0 && percent <= 100) battery = percent + " % (code 60)";
                } catch (Exception ignored) { /* Raw battery format not confirmed. */ }
                info = "Dernière lecture réussie. Ces valeurs pourront expirer ; relire avant toute action.";
            } catch (Exception failure) {
                info = failure instanceof IllegalStateException ? failure.getMessage()
                    : "Connexion non établie. Vérifie le Wi-Fi du N1 Max puis réessaie.";
            }
            final boolean succeeded = valid;
            final boolean rawStatusValid = valid;
            final String stateText = state, batteryText = battery, detail = info;
            runOnUiThread(() -> {
                if (destroyed) return;
                robotState = stateText;
                robotBattery = batteryText;
                lastCheck = detail;
                checking = false;
                lastStatusValid = rawStatusValid;
                handler.removeCallbacks(expireStatus);
                if (succeeded) {
                    lastReadElapsed = android.os.SystemClock.elapsedRealtime();
                    handler.postDelayed(expireStatus, 60000);
                } else lastReadElapsed = 0;
                if (selectedTab == 2) selectTab(2);
            });
        });
    }

    private static String stateText(String raw) {
        if ("2".equals(raw)) return "Nettoyage (état 2)";
        if ("3".equals(raw)) return "En pause (état 3)";
        if ("4".equals(raw)) return "Retour à la base (état 4)";
        if ("11".equals(raw)) return "En charge (état 11)";
        if ("12".equals(raw)) return "Chargé (état 12)";
        if (raw == null || raw.equals("—")) return "État non reçu";
        return raw.matches("[0-9]{1,4}") ? "État brut : " + raw : "État non interprété";
    }

    private void showMap() {
        sectionTitle("Cartographie", "Explore les cartes disponibles et les diagnostics LiDAR.");
        LinearLayout mapCard = card();
        add(mapCard, text("Explorateur de carte", 18, INK, true), 0);
        View preview = new MapIllustration(this);
        LinearLayout.LayoutParams mapPreviewParams = new LinearLayout.LayoutParams(-1, dp(220));
        mapPreviewParams.topMargin = dp(15);
        mapCard.addView(preview, mapPreviewParams);
        add(mapCard, text("Zoom, rotation, palettes, analyse des cellules et import local de cartes JSON ou TXT.", 13, SUBTLE, false), 12);
        Button open = button("Ouvrir la cartographie", true);
        add(mapCard, open, 13);
        open.setOnClickListener(v -> openExistingMap());
        add(contents, mapCard, 18);
        showDraftCards(KoomaDraftStore.ZONES);
        LinearLayout diag = card();
        add(diag, text("Diagnostic du LiDAR", 17, INK, true), 0);
        add(diag, text("Écoute en lecture seule de 15 ou 45 secondes. Les vraies cartes et les murs du robot ne sont pas encore validés.", 13, SUBTLE, false), 8);
        Button btn = button("Accéder au diagnostic avancé", false);
        add(diag, btn, 12);
        btn.setOnClickListener(v -> openExistingControls());
        add(contents, diag, 13);
    }

    private void showPlanning() {
        sectionTitle("Programmation", "Organise tes futurs nettoyages.");
        LinearLayout warning = card();
        add(warning, text("Brouillons uniquement", 16, DARK_GREEN, true), 0);
        add(warning, text("Le protocole de programmation du N1 Max n'est pas validé. Les horaires créés ici restent sur le téléphone et ne sont jamais envoyés au robot.", 13, SUBTLE, false), 7);
        add(contents, warning, 16);
        add(contents, text("Favoris locaux : " + drafts.read(KoomaDraftStore.FAVORITES).size()
            + " • ne déclenchent aucun nettoyage.", 13, SUBTLE, false), 12);

        android.content.SharedPreferences prefs = getSharedPreferences("kooma_planning_draft", MODE_PRIVATE);
        if (prefs.contains("time")) {
            LinearLayout draft = card();
            add(draft, text("Routine locale", 18, INK, true), 0);
            add(draft, text("Tous les jours · " + prefs.getString("time", "—"), 17, DARK_GREEN, true), 10);
            add(draft, text("Non synchronisée avec le robot", 12, SUBTLE, false), 6);
            Button delete = button("Supprimer le brouillon", false);
            add(draft, delete, 12);
            delete.setOnClickListener(v -> {
                prefs.edit().remove("time").apply();
                selectTab(1);
            });
            add(contents, draft, 16);
        } else {
            add(contents, text("Aucun horaire enregistré sur ce téléphone.", 14, SUBTLE, false), 16);
        }
        Button create = button("Créer un brouillon d'horaire", true);
        add(contents, create, 18);
        create.setOnClickListener(v -> {
            java.util.Calendar today = java.util.Calendar.getInstance();
            new TimePickerDialog(this, (picker, hour, minute) -> {
                String time = String.format(Locale.FRANCE, "%02d:%02d", hour, minute);
                prefs.edit().putString("time", time).apply();
                selectTab(1);
            }, today.get(java.util.Calendar.HOUR_OF_DAY),
                today.get(java.util.Calendar.MINUTE), true).show();
        });
    }

    private void showHistory() {
        sectionTitle("Historique", "Retrouve les observations enregistrées pendant tes essais.");
        LinearLayout card = card();
        add(card, text("Historique de nettoyage", 18, INK, true), 0);
        add(card, text("Aucune synchronisation automatique des anciens nettoyages n'est encore disponible. Nous ne présentons donc pas de fausses durées, cartes ou consommations.", 13, SUBTLE, false), 9);
        Button open = button("Ouvrir les observations et diagnostics", true);
        add(card, open, 16);
        open.setOnClickListener(v -> openExistingControls());
        add(contents, card, 18);
        add(contents, text("Les diagnostics existants restent accessibles depuis l'écran technique.", 12, SUBTLE, false), 14);
    }

    private void showSettings() {
        sectionTitle("Réglages", "Préférences, connexion et fonctionnalités avancées.");
        LinearLayout c = card();
        add(c, text("Veniibot N1 Max", 18, INK, true), 0);
        add(c, text("KOOMA-I fonctionne en local, sans compte cloud. Le robot doit être accessible sur son réseau Wi-Fi pour les lectures et les commandes.", 13, SUBTLE, false), 9);
        add(contents, c, 16);

        LinearLayout advanced = card();
        add(advanced, text("Centre technique", 18, INK, true), 0);
        add(advanced, text("Connexion, télécommande, sécurité, diagnostics LiDAR, historique des états et réglages expérimentaux.", 13, SUBTLE, false), 9);
        Button open = button("Ouvrir le mode avancé", true);
        add(advanced, open, 14);
        open.setOnClickListener(v -> openExistingControls());
        add(contents, advanced, 13);

        add(contents, text("KOOMA-I 4.8 · Interface expérimentale\nFavoris et zones = brouillons locaux non transmis au robot.", 12, SUBTLE, false), 20);
    }

    private static final int[] DRAFT_COLORS = {
        0xFF08BF96, 0xFF568BAC, 0xFFE6A44D, 0xFF9A7BC3, 0xFFB78E73
    };
    private static final int[] DRAFT_PALE = {
        0xFFF0FAF6, 0xFFF0F6FA, 0xFFFFF8EC, 0xFFF8F3FB, 0xFFFAF4F0
    };

    private void showDraftCards(String category) {
        boolean favorites = KoomaDraftStore.FAVORITES.equals(category);
        java.util.List<KoomaDraftStore.Draft> list = drafts.read(category);
        LinearLayout section = card();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(favorites ? "Mes favoris" : "Mes zones", 18, INK, true);
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView count = text(list.size() + "/" + KoomaDraftStore.LIMIT, 12, SUBTLE, false);
        header.addView(count);
        add(section, header, 0);

        add(section, text(favorites
            ? "Prépare des routines personnelles. Aucun favori n'est exécutable sur le robot pour le moment."
            : "Étiquettes de zones uniquement : pas de coordonnées, de délimitation sur la carte ni d'interdiction transmise au robot.",
            12, SUBTLE, false), 7);

        if (list.isEmpty()) {
            add(section, text(favorites
                ? "Aucun favori créé. Tu peux préparer « Après le petit-déjeuner », « Nettoyage rapide », etc."
                : "Aucune zone définie. Ajoute un nom de pièce ou une zone à éviter comme brouillon.",
                13, SUBTLE, false), 15);
        } else {
            for (int i = 0; i < list.size(); i++) {
                final int index = i;
                KoomaDraftStore.Draft draft = list.get(i);
                int colorIdx = draft.color;
                LinearLayout tile = vertical(13);
                tile.setBackground(outlined(DRAFT_PALE[colorIdx], 17, LINE));
                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                TextView marker = text(favorites ? "★" : "⌖", 23, DRAFT_COLORS[colorIdx], true);
                line.addView(marker, new LinearLayout.LayoutParams(dp(34), dp(42)));
                LinearLayout detail = vertical(0);
                detail.setPadding(0, 0, 0, 0);
                add(detail, text(draft.name, 16, INK, true), 0);
                add(detail, text(draft.detail, 12, SUBTLE, false), 4);
                line.addView(detail, new LinearLayout.LayoutParams(0, -2, 1f));
                tile.addView(line);
                add(tile, text("Brouillon local • non exécuté", 11, DARK_GREEN, true), 6);
                LinearLayout row = new LinearLayout(this);
                Button edit = button("Modifier", false);
                edit.setTextSize(12);
                Button delete = button("Supprimer", false);
                delete.setTextSize(12);
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(48), 1f);
                l.rightMargin = dp(4);
                row.addView(edit, l);
                LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, dp(48), 1f);
                r.leftMargin = dp(4);
                row.addView(delete, r);
                add(tile, row, 9);
                edit.setOnClickListener(v -> editDraft(category, index, draft));
                delete.setOnClickListener(v -> confirmDeleteDraft(category, index, draft.name));
                add(section, tile, 12);
            }
        }
        Button addDraft = button(favorites ? "+ Créer un favori" : "+ Ajouter une zone", true);
        addDraft.setEnabled(list.size() < KoomaDraftStore.LIMIT);
        add(section, addDraft, 15);
        addDraft.setOnClickListener(v -> editDraft(category, -1, null));
        add(contents, section, 16);
    }

    private void confirmDeleteDraft(String category, int index, String name) {
        new AlertDialog.Builder(this)
            .setTitle("Supprimer ce brouillon ?")
            .setMessage("« " + name + " » sera supprimé du téléphone. Aucune action n'est envoyée au robot.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer", (d, w) -> {
                if (!drafts.delete(category, index))
                    android.widget.Toast.makeText(this, "Suppression impossible", android.widget.Toast.LENGTH_SHORT).show();
                selectTab(KoomaDraftStore.FAVORITES.equals(category) ? 2 : 0);
            }).show();
    }

    private void editDraft(String category, int index, KoomaDraftStore.Draft previous) {
        boolean favorite = KoomaDraftStore.FAVORITES.equals(category);
        LinearLayout form = vertical(18);
        add(form, text(favorite
            ? "Favori de présentation uniquement : aucune commande ne sera envoyée au N1 Max."
            : "Une zone ici est un nom de brouillon. Elle ne désigne aucune surface réelle sur une carte.",
            13, SUBTLE, false), 0);

        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setTextSize(17);
        title.setHint(favorite ? "Nom : Après le dîner" : "Nom : Cuisine");
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(35)});
        if (previous != null) title.setText(previous.name);
        add(form, title, 12);

        String[] options = favorite
            ? new String[]{"Nettoyage partout (brouillon)", "Zones à définir (brouillon)"}
            : new String[]{"Zone à nettoyer (étiquette)", "Zone à éviter (étiquette)"};
        Spinner scope = new Spinner(this);
        scope.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, options));
        if (previous != null) {
            for (int i = 0; i < options.length; i++)
                if (options[i].equals(previous.detail)) scope.setSelection(i);
        }
        add(form, scope, 12);

        String[] colorOptions = {"Vert", "Bleu", "Orange", "Violet", "Sable"};
        Spinner colors = new Spinner(this);
        colors.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, colorOptions));
        if (previous != null) colors.setSelection(previous.color);
        add(form, colors, 9);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(index < 0 ? (favorite ? "Nouveau favori" : "Nouvelle zone")
                               : "Modifier le brouillon")
            .setView(form)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", null)
            .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(clicked -> {
                try {
                    KoomaDraftStore.Draft next = new KoomaDraftStore.Draft(
                        title.getText().toString(), options[scope.getSelectedItemPosition()],
                        colors.getSelectedItemPosition());
                    boolean saved = index < 0 ? drafts.add(category, next)
                        : drafts.replace(category, index, next);
                    if (!saved) {
                        title.setError("Enregistrement impossible ou limite atteinte");
                        return;
                    }
                    dialog.dismiss();
                    selectTab(favorite ? 2 : 0);
                } catch (IllegalArgumentException problem) {
                    title.setError(problem.getMessage());
                }
            }));
        dialog.show();
    }

    /** An original stylized robot illustration, no product imagery or robot status implied. */
    private final class RobotPicture extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RobotPicture(Context context) { super(context); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float width = getWidth(), height = getHeight();
            c.drawColor(BACKGROUND);
            float cx = width / 2f, cy = height / 2f + 1;
            float r = Math.min(width * 0.31f, height * 0.37f);
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 4; i++) {
                p.setColor(Color.rgb(225 + i * 3, 244 + i * 2, 235 + i * 4));
                p.setStrokeWidth(dp(1.4f));
                c.drawCircle(cx,cy, r + dp(18 + i*15),p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setShadowLayer(dp(15), 0, dp(8), 0x26000000);
            p.setColor(Color.WHITE);
            c.drawCircle(cx, cy, r, p);
            p.clearShadowLayer();
            p.setColor(Color.rgb(235, 241, 240));
            c.drawRoundRect(cx-r*.73f,cy-r*.28f,cx+r*.73f,cy+r*.28f,
                dp(24),dp(24),p);
            p.setColor(DARK_GREEN);
            c.drawRoundRect(cx-r*.70f,cy-r*.26f,cx+r*.70f,cy+r*.25f,
                dp(23),dp(23),p);
            p.setColor(GREEN);
            p.setStrokeWidth(dp(4));
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(cx-r*.35f,cy, cx-r*.15f,cy,p);
            c.drawLine(cx+r*.15f,cy, cx+r*.35f,cy,p);
            p.setColor(Color.rgb(210, 225, 220));
            c.drawCircle(cx, cy+r*.55f, r*.07f,p);
            p.setColor(GREEN);
            c.drawCircle(cx,cy-r*.71f, r*.06f,p);
        }
    }

    /** Explicitly fictional preview: no inferred floor plan or robot position. */
    private final class MapIllustration extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        MapIllustration(Context context) {super(context);}
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(PALE);
            float w=getWidth(),h=getHeight();
            p.setColor(Color.rgb(211, 233, 225));
            float px=w*.12f, py=h*.13f, pw=w*.76f, ph=h*.74f;
            c.drawRoundRect(px,py,px+pw,py+ph,20,20,p);
            p.setColor(Color.WHITE);
            c.drawRoundRect(px+dp(8),py+dp(8),px+pw-dp(8),py+ph-dp(8),15,15,p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(GREEN);
            p.setStrokeWidth(dp(5));
            c.drawLine(px+pw*.45f,py+dp(10),px+pw*.45f,py+ph*.58f,p);
            c.drawLine(px+pw*.45f,py+ph*.58f,px+pw-dp(10),py+ph*.58f,p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(GREEN);
            c.drawCircle(px+pw*.25f,py+ph*.62f,dp(10),p);
            p.setColor(SUBTLE);
            p.setTextSize(dp(12));
            c.drawText("APERÇU FICTIF", px+dp(15),py+ph-dp(14),p);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        expireStatus.run();
        // Reflect any changes to local zone drafts after returning from Cartographie.
        if (contents != null && (selectedTab == 0 || selectedTab == 2))
            selectTab(selectedTab);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacks(expireStatus);
        work.shutdownNow();
        super.onDestroy();
    }
}
