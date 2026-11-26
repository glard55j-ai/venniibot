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

    private LinearLayout contents, navbar;
    private TextView[] navSymbols = new TextView[5];
    private TextView[] navLabels = new TextView[5];
    private LinearLayout[] navItems = new LinearLayout[5];
    private int selectedTab = 2;
    private String robotState = "Non vérifié";
    private String robotBattery = "—";
    private String lastCheck = "Touchez « Lire l'état » pour interroger le robot.";
    private boolean checking;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        renderShell();
        selectTab(2);
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
        sectionTitle("Bienvenue à la maison", "Ton robot, ses commandes et ses données au même endroit.");

        RobotPicture robot = new RobotPicture(this);
        LinearLayout.LayoutParams picture = new LinearLayout.LayoutParams(-1, dp(260));
        picture.topMargin = dp(9);
        contents.addView(robot, picture);

        TextView robotName = text("Mon Veniibot N1 Max", 21, INK, true);
        robotName.setGravity(Gravity.CENTER);
        add(contents, robotName, 1);
        TextView status = text("État : " + robotState, 15, DARK_GREEN, true);
        status.setGravity(Gravity.CENTER);
        add(contents, status, 7);
        TextView battery = text("Batterie : " + robotBattery, 14, SUBTLE, false);
        battery.setGravity(Gravity.CENTER);
        add(contents, battery, 4);

        LinearLayout actions = card();
        add(actions, text("Connexion locale", 17, INK, true), 0);
        add(actions, text(lastCheck, 13, SUBTLE, false), 7);
        Button read = button(checking ? "Lecture en cours…" : "Lire l'état du robot", true);
        read.setEnabled(!checking);
        add(actions, read, 14);
        read.setOnClickListener(v -> readRobotStatus());
        Button commands = button("Connexion et commandes de nettoyage", false);
        add(actions, commands, 9);
        commands.setOnClickListener(v -> openExistingControls());
        add(contents, actions, 20);

        add(contents, text("Les commandes réelles de démarrage, pause, reprise et retour à la base restent dans l'écran de contrôle existant, avec leurs protections de sécurité.", 12, SUBTLE, false), 13);
    }

    private void readRobotStatus() {
        if (checking) return;
        checking = true;
        robotState = "Lecture en cours";
        robotBattery = "—";
        lastCheck = "Interrogation locale en cours. Aucune commande de mouvement envoyée.";
        selectTab(2);
        work.execute(() -> {
            String state = "Lecture impossible", battery = "—", info;
            try {
                // The user has explicitly requested this status-only operation.
                ConnectivityManager cm =
                    (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                boolean wifi = false;
                if (cm != null) for (Network network : cm.getAllNetworks()) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        cm.bindProcessToNetwork(network);
                        wifi = true;
                        break;
                    }
                }
                if (!wifi) throw new IllegalStateException("Connecte le téléphone au Wi-Fi du robot.");
                VeniiClient.Status report = VeniiClient.readStatus();
                state = stateText(report.generalState);
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
            final String stateText = state, batteryText = battery, detail = info;
            runOnUiThread(() -> {
                if (destroyed) return;
                robotState = stateText;
                robotBattery = batteryText;
                lastCheck = detail;
                checking = false;
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
        add(mapCard, preview, 15);
        add(mapCard, text("Zoom, rotation, palettes, analyse des cellules et import local de cartes JSON ou TXT.", 13, SUBTLE, false), 12);
        Button open = button("Ouvrir la cartographie", true);
        add(mapCard, open, 13);
        open.setOnClickListener(v -> openExistingMap());
        add(contents, mapCard, 18);
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

        add(contents, text("KOOMA-I 4.7 · Interface expérimentale\nLes fonctions non encore vérifiées sur le robot sont indiquées comme telles.", 12, SUBTLE, false), 20);
    }

    /** An original stylized robot illustration, no product imagery or robot status implied. */
    private static final class RobotPicture extends View {
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
    private static final class MapIllustration extends View {
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

    @Override protected void onDestroy() {
        destroyed = true;
        work.shutdownNow();
        super.onDestroy();
    }
}
