package com.veniilocal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Offline experimental cartography viewer. No robot commands, network use or upload.
 * Imported bytes are deliberately rendered as raw greys, NOT inferred walls.
 */
public final class MapActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(249, 251, 250);
    private static final int SURFACE = Color.WHITE;
    private static final int PALE = Color.rgb(239, 249, 245);
    private static final int LINE = Color.rgb(228, 238, 233);
    private static final int GREEN = Color.rgb(8, 191, 150);
    private static final int DARK_GREEN = Color.rgb(3, 116, 100);
    private static final int TEXT = Color.rgb(28, 51, 55);
    private static final int MUTED = Color.rgb(108, 134, 132);
    private static final int WARNING = Color.rgb(148, 96, 26);
    private static final int REQUEST_TXT = 407;
    private static final int REQUEST_MAP_JSON = 408;
    private static final int REQUEST_PNG = 409;
    private static final int MAX_IMPORT_BYTES = 6 * 1024 * 1024;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private TextView importResult, heading, metadata, statistics, modeLabel, cellLabel, zonesLabel;
    private Button importTxtButton, importMapButton;
    private Button rotateBtn, paletteBtn, zoomInBtn, zoomOutBtn, zoomFitBtn, exportBtn;
    private MapPreviewView mapView;
    private LinearLayout technicalPanel;
    private byte[] activeCells;
    private int activeWidth, activeHeight, turns, paletteMode;


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("‹");
        back.setAllCaps(false);
        back.setTextSize(28);
        back.setTextColor(DARK_GREEN);
        back.setBackground(shape(PALE, 15, LINE));
        back.setContentDescription("Retour à KOOMA-I");
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(v -> finish());
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(14), 0, 0, 0);
        TextView title = label("Cartographie", 24, TEXT, true);
        brand.addView(title);
        brand.addView(label("KOOMA-I  •  Veniibot N1 Max", 12, MUTED, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        LinearLayout status = panel(root, 15);
        heading = label("AUCUNE CARTE RÉELLE", 16, DARK_GREEN, true);
        add(status, heading, 0);
        metadata = label("En attente de données • la carte réelle du N1 Max n'est pas validée.", 13, MUTED, false);
        add(status, metadata, 6);

        LinearLayout mapCard = panel(root, 15);
        add(mapCard, label("Aperçu de la grille", 18, TEXT, true), 0);
        add(mapCard, label("La vue ne représente ni murs ni zones reconnues tant que le protocole n'est pas validé.", 12, MUTED, false), 6);

        mapView = new MapPreviewView();
        mapView.setBackground(shape(PALE, 18, LINE));
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, dp(350));
        mapParams.topMargin = dp(12);
        mapCard.addView(mapView, mapParams);
        modeLabel = label("Aperçu en attente • zoom 1×", 12, MUTED, false);
        add(mapCard, modeLabel, 10);
        cellLabel = label("Touchez la grille après import pour lire les octets bruts.", 13, TEXT, false);
        add(mapCard, cellLabel, 6);

        add(mapCard, label("EXPLORER LA GRILLE", 11, DARK_GREEN, true), 14);
        LinearLayout firstRow = horizontal(mapCard);
        zoomOutBtn = smallButton(firstRow, "− Zoom");
        zoomFitBtn = smallButton(firstRow, "Cadrer");
        zoomInBtn = smallButton(firstRow, "+ Zoom");
        zoomOutBtn.setOnClickListener(v -> {
            mapView.setZoom(mapView.zoom - 0.5f); updateModeLabel();
        });
        zoomInBtn.setOnClickListener(v -> {
            mapView.setZoom(mapView.zoom + 0.5f); updateModeLabel();
        });
        zoomFitBtn.setOnClickListener(v -> {
            mapView.setZoom(1f); updateModeLabel();
        });

        LinearLayout secondRow = horizontal(mapCard);
        rotateBtn = smallButton(secondRow, "Rotation 90°");
        paletteBtn = smallButton(secondRow, "Couleurs");
        exportBtn = smallButton(secondRow, "Export PNG");
        rotateBtn.setOnClickListener(v -> {
            if (activeCells == null) return;
            turns = (turns + 1) % 4;
            renderActiveGrid();
        });
        paletteBtn.setOnClickListener(v -> {
            if (activeCells == null) return;
            paletteMode = (paletteMode + 1) % 3;
            renderActiveGrid();
        });
        exportBtn.setOnClickListener(v -> confirmExportPng());
        setToolsEnabled(false);

        LinearLayout importPanel = panel(root, 15);
        add(importPanel, label("Charger une carte", 18, TEXT, true), 0);
        add(importPanel, label("Import volontaire depuis le téléphone. Aucune carte n'est envoyée sur Internet.", 12, MUTED, false), 5);

        importMapButton = button(importPanel, "Importer un fichier JSON");
        importMapButton.setOnClickListener(v ->
            openDocument(REQUEST_MAP_JSON, new String[]{"application/json", "text/plain", "application/octet-stream"}));
        importTxtButton = button(importPanel, "Analyser un export LiDAR TXT");
        importTxtButton.setOnClickListener(v ->
            openDocument(REQUEST_TXT, new String[]{"text/plain"}));
        Button synthetic = button(importPanel, "Voir une grille de démonstration");
        synthetic.setOnClickListener(v -> showSyntheticExample());
        Button reset = button(importPanel, "Effacer l'aperçu");
        reset.setOnClickListener(v -> showDemo());

        LinearLayout zoneCard = panel(root, 15);
        add(zoneCard, label("Zones de nettoyage", 18, TEXT, true), 0);
        add(zoneCard, label("Les étiquettes sont des brouillons. Elles ne sont liées à aucune position ou région réelle.", 12, MUTED, false), 6);
        zonesLabel = label("Chargement des brouillons de zones…", 13, TEXT, false);
        add(zoneCard, zonesLabel, 10);
        Button zones = button(zoneCard, "Gérer les zones dans KOOMA-I");
        zones.setOnClickListener(v -> {
            Intent intent = new Intent(this, KoomaHomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("open_tab", 0);
            startActivity(intent);
            finish();
        });
        add(zoneCard, label("La sélection d'une zone sur la véritable carte sera activée uniquement après validation des coordonnées.", 12, WARNING, false), 7);
        refreshZones();

        LinearLayout reportCard = panel(root, 15);
        add(reportCard, label("Résultat de l'analyse", 17, TEXT, true), 0);
        importResult = label("Aucun fichier analysé. Lecture locale uniquement.", 13, TEXT, false);
        add(reportCard, importResult, 9);
        Button technical = button(reportCard, "Afficher les détails techniques");
        technicalPanel = new LinearLayout(this);
        technicalPanel.setOrientation(LinearLayout.VERTICAL);
        technicalPanel.setVisibility(View.GONE);
        add(reportCard, technicalPanel, 9);
        technical.setOnClickListener(v -> {
            boolean expand = technicalPanel.getVisibility() != View.VISIBLE;
            technicalPanel.setVisibility(expand ? View.VISIBLE : View.GONE);
            technical.setText(expand ? "Masquer les détails techniques" : "Afficher les détails techniques");
        });
        statistics = label("Statistiques : en attente d'une grille décodée.", 13, MUTED, false);
        add(technicalPanel, statistics, 4);
        add(technicalPanel, label("Format expérimental : width, height, map (Base64 / LZ4). Métadonnées facultatives : resolution, x_min et y_min.", 12, MUTED, false), 8);
        add(technicalPanel, label("Une vraie carte peut révéler le plan d'un domicile. Les exports PNG et TXT restent des actions volontaires.", 12, WARNING, false), 8);

        // Only an explicit diagnostic action can leave a one-shot map candidate in memory.
        String candidates = MapCandidateMemory.take();
        if (candidates != null) {
            importResult.setText("Analyse locale des données reçues pendant la dernière écoute LiDAR…");
            analyzeInBackground(candidates, REQUEST_TXT);
        }
    }

    private GradientDrawable shape(int color, int radius, int line) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), line);
        return drawable;
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void add(LinearLayout root, View item, int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(topMargin);
        root.addView(item, p);
    }

    private LinearLayout panel(LinearLayout root, int topMargin) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        view.setBackground(shape(SURFACE, 21, LINE));
        add(root, view, topMargin);
        return view;
    }

    private LinearLayout horizontal(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        add(root, row, 7);
        return row;
    }

    private void refreshZones() {
        if (zonesLabel == null) return;
        java.util.List<KoomaDraftStore.Draft> list =
            new KoomaDraftStore(this).read(KoomaDraftStore.ZONES);
        if (list.isEmpty()) {
            zonesLabel.setText("Aucune zone en brouillon. Ajoute un nom de pièce depuis l'onglet Carte.");
            return;
        }
        StringBuilder b = new StringBuilder();
        b.append(list.size()).append(" zone(s) enregistrée(s) localement :");
        for (KoomaDraftStore.Draft draft : list)
            b.append("\n• ").append(draft.name).append(" — ").append(draft.detail);
        zonesLabel.setText(b.toString());
    }

    @Override protected void onResume() {
        super.onResume();
        refreshZones();
    }

    private Button button(LinearLayout root, String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(DARK_GREEN);
        b.setBackground(shape(PALE, 15, LINE));
        b.setMinHeight(dp(52));
        add(root, b, 9);
        return b;
    }

    private Button smallButton(LinearLayout row, String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(DARK_GREEN);
        b.setBackground(shape(PALE, 12, LINE));
        b.setMinHeight(dp(49));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(51), 1f);
        p.setMargins(dp(2), dp(3), dp(2), dp(3));
        row.addView(b, p);
        return b;
    }

    private void setToolsEnabled(boolean available) {
        for (Button b : new Button[]{zoomInBtn,zoomOutBtn,zoomFitBtn,rotateBtn,paletteBtn,exportBtn})
            if (b != null) b.setEnabled(available);
    }

    private void setActiveGrid(byte[] cells, int width, int height) {
        activeCells = cells;
        mapView.setZoom(1f);
        activeWidth = width;
        activeHeight = height;
        turns = 0;
        paletteMode = MapGridRasterizer.GRAYSCALE;
        setToolsEnabled(cells != null);
        cellLabel.setText(cells == null
            ? "Touchez une cellule d'une grille pour lire sa valeur brute."
            : "Touchez la grille pour obtenir la coordonnée et l'octet d'origine.");
        if (cells == null) mapView.setPreview(null);
        else renderActiveGrid();
        updateModeLabel();
    }

    private void renderActiveGrid() {
        if (activeCells == null) return;
        MapGridRasterizer.Preview preview =
            MapGridRasterizer.rasterize(activeCells, activeWidth, activeHeight, 600, turns, paletteMode);
        mapView.setPreview(preview);
        updateModeLabel();
        cellLabel.setText("Orientation manuelle uniquement. Touchez une cellule pour lire sa valeur.");
    }

    private void updateModeLabel() {
        if (modeLabel == null || mapView == null) return;
        if (activeCells == null) {
            modeLabel.setText("Aperçu fictif • aucun octet réel");
            return;
        }
        String palette = paletteMode == 0 ? "Gris brut" : paletteMode == 1
            ? "Gris inversé" : "Fausses couleurs";
        modeLabel.setText("Rotation visuelle : " + (turns * 90) + "° • "
            + palette + " • zoom " + String.format(java.util.Locale.FRANCE, "%.1f", mapView.zoom)
            + "× • glisser pour déplacer quand zoomé");
    }

    private void confirmExportPng() {
        if (activeCells == null || mapView.previewBitmap == null) return;
        new android.app.AlertDialog.Builder(this)
            .setTitle("Exporter un aperçu privé ?")
            .setMessage("L'image peut révéler le plan de votre domicile. Elle contient uniquement "
                + "les valeurs brutes, sans classification des murs. Aucune transmission automatique : "
                + "vous choisirez un fichier sur votre téléphone.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Choisir le fichier", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("image/png");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, "KOOMA-I-carte-brute.png");
                try { startActivityForResult(intent, REQUEST_PNG); }
                catch (android.content.ActivityNotFoundException error) {
                    importResult.setText("Enregistrement indisponible sur ce téléphone.");
                }
            }).show();
    }

    private void savePng(Uri uri) {
        final Bitmap bitmap = mapView.previewBitmap;
        if (bitmap == null) return;
        io.execute(() -> {
            boolean written = false;
            try (java.io.OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new java.io.IOException("Fichier inaccessible");
                written = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            } catch (Exception ignored) { written = false; }
            boolean done = written;
            runOnUiThread(() -> {
                if (destroyed) return;
                android.widget.Toast.makeText(this, done ? "Aperçu PNG enregistré dans le dossier choisi"
                    : "Export PNG impossible", android.widget.Toast.LENGTH_LONG).show();
            });
        });
    }

    private TextView title(LinearLayout root, String value, int sp, int color) {
        TextView t = label(value, sp, color, false);
        add(root, t, 8);
        return t;
    }

    private void openDocument(int requestCode, String[] types) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
        try { startActivityForResult(intent, requestCode); }
        catch (android.content.ActivityNotFoundException error) {
            importResult.setText("Impossible d'ouvrir le sélecteur de fichiers.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_PNG) {
            if (result == RESULT_OK && data != null && data.getData() != null) savePng(data.getData());
            return;
        }
        if ((request != REQUEST_TXT && request != REQUEST_MAP_JSON)
                || result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        importResult.setText("Analyse locale en cours…");
        importTxtButton.setEnabled(false);
        importMapButton.setEnabled(false);
        io.execute(() -> {
            try {
                processImportedData(readBounded(uri), request);
            } catch (Exception error) {
                showImportError(error);
            }
        });
    }

    private void analyzeInBackground(String raw, int request) {
        importTxtButton.setEnabled(false);
        importMapButton.setEnabled(false);
        io.execute(() -> {
            try {
                processImportedData(raw, request);
            } catch (Exception error) {
                showImportError(error);
            }
        });
    }

    /** Shared processing for voluntary file imports and one-shot in-memory handoffs. */
    private void processImportedData(String raw, int request) throws Exception {
        DecodedMap decoded = null;
        String message;
        if (request == REQUEST_TXT) {
            String report = MapPayloadInspector.inspect(raw);
            java.util.List<MapExportBlocks.Block> blocks = MapExportBlocks.extract(raw);
            int invalidCandidates = 0;
            int decodedId = -1;
            for (MapExportBlocks.Block block : blocks) {
                try {
                    DecodedMap match = decodeDocument(block.payload);
                    if (match != null) {
                        decoded = match;
                        decodedId = block.id;
                        break;
                    }
                } catch (Exception malformed) {
                    // Map candidates are not necessarily structured MapEntity data.
                    invalidCandidates++;
                }
            }
            if (decoded != null) {
                message = report + "\n\nGrille Base64/LZ4 décodable trouvée dans le bloc "
                        + decodedId + ". Les octets ne sont pas interprétés comme des murs.";
            } else {
                message = report + "\n\nAucune grille complète et décodable dans cet échantillon. "
                        + blocks.size() + " bloc(s) candidat(s) examiné(s)"
                        + (invalidCandidates > 0
                           ? ", " + invalidCandidates + " format(s) incompatible(s)." : ".")
                        + "\nIl peut exister une carte transmise par un autre canal.";
            }
        } else {
            decoded = decodeDocument(raw);
            if (decoded == null)
                throw new IllegalArgumentException(
                    "Aucun objet de carte complet (width, height, map) trouvé dans le JSON");
            message = "Données Base64/LZ4 décodées depuis un fichier local.";
        }
        final DecodedMap finalDecoded = decoded;
        final String resultMessage = message;
        runOnUiThread(() -> {
            if (destroyed) return;
            if (finalDecoded != null) {
                setActiveGrid(finalDecoded.cells, finalDecoded.width, finalDecoded.height);
                statistics.setText(finalDecoded.analysis);
                heading.setText("GRILLE DÉCODÉE — sens des octets inconnu");
                metadata.setText(finalDecoded.info);
                importResult.setText(resultMessage
                        + "\n0 = noir, 255 = blanc en mode gris brut. "
                        + "Orientation physique et valeurs non validées.");
            } else {
                importResult.setText(resultMessage);
            }
            unlock();
        });
    }

    private void showImportError(Exception error) {
        String message = error instanceof IllegalArgumentException
            ? error.getMessage() : "Fichier ou données non compatibles";
        runOnUiThread(() -> {
            if (destroyed) return;
            importResult.setText("Analyse impossible : " + message
                    + ". L'affichage précédent est conservé.");
            unlock();
        });
    }

    private static final class DecodedMap {
        final byte[] cells;
        final int width, height;
        final String analysis, info;
        DecodedMap(byte[] cells, int width, int height, String analysis, String info) {
            this.cells = cells;
            this.width = width;
            this.height = height;
            this.analysis = analysis;
            this.info = info;
        }
    }

    /** Decodes only complete MapEntity-like documents; has no networking or robot commands. */
    private static DecodedMap decodeDocument(String raw) throws Exception {
        JSONObject obj = MapDocumentParser.find(raw);
        if (obj == null) return null;
        int width = integerField(obj, "width");
        int height = integerField(obj, "height");
        Object payload = obj.opt("map");
        if (!(payload instanceof String) || ((String)payload).isEmpty())
            throw new IllegalArgumentException("Champ map (Base64) manquant");
        byte[] cells = MapGridDecoder.decodeBase64Lz4((String)payload, width, height);
        MapGridRasterizer.Preview preview = MapGridRasterizer.rasterize(cells, width, height, 600);
        String analysis = MapGridStatistics.summarize(cells, 8);
        String info = width + " × " + height + " cellules ; aperçu "
                + preview.width + " × " + preview.height + "."
                + optionalNumber(obj, "resolution")
                + optionalNumber(obj, "x_min")
                + optionalNumber(obj, "y_min");
        return new DecodedMap(cells, width, height, analysis, info);
    }

    private String readBounded(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("Fichier inaccessible");
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            int count;
            while ((count = in.read(block)) != -1) {
                if (count > MAX_IMPORT_BYTES - data.size())
                    throw new IllegalArgumentException("Fichier trop volumineux (limite 6 Mio)");
                data.write(block, 0, count);
            }
            return new String(data.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static int integerField(JSONObject obj, String key) {
        Object value = obj.opt(key);
        if (!(value instanceof Number)) throw new IllegalArgumentException("Champ " + key + " entier manquant");
        double number = ((Number)value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < 1 || number > 4194304)
            throw new IllegalArgumentException("Champ " + key + " invalide");
        return (int)number;
    }

    private static String optionalNumber(JSONObject obj, String key) {
        Object value = obj.opt(key);
        if (!(value instanceof Number)) return "";
        double number = ((Number)value).doubleValue();
        if (!Double.isFinite(number)) return "";
        return " • " + key + " (brut) : " + value;
    }

    private void unlock() {
        importTxtButton.setEnabled(true);
        importMapButton.setEnabled(true);
    }

    private void showDemo() {
        setActiveGrid(null, 0, 0);
        heading.setText("DÉMONSTRATION — carte fictive");
        metadata.setText("Aucune carte réelle décodée. L'orientation et la signification des octets restent inconnues.");
        importResult.setText("Démonstration seulement : aucun fichier lu ni ordre envoyé au robot.");
        statistics.setText("Statistiques : en attente d’une grille décodée.");
    }

    private void showSyntheticExample() {
        // Deliberately builds an original small synthetic LZ4 literal-only block.
        int width = 16, height = 12;
        byte[] input = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                input[y * width + x] = (byte)((x * 16 + y * 8) & 255);
            }
        }
        byte[] packed = new byte[input.length + 2];
        packed[0] = (byte)0xF0;
        packed[1] = (byte)(input.length - 15);
        System.arraycopy(input, 0, packed, 2, input.length);
        byte[] decoded = MapGridDecoder.decodeBase64Lz4(
                Base64.getEncoder().encodeToString(packed), width, height);
        MapGridRasterizer.Preview preview = MapGridRasterizer.rasterize(decoded, width, height, 600);
        setActiveGrid(decoded, width, height);
        heading.setText("EXEMPLE SYNTHÉTIQUE — aucun robot");
        metadata.setText("16 × 12 octets artificiels ; aperçu des nuances de gris, sans murs ni pièces.");
        statistics.setText(MapGridStatistics.summarize(decoded, 8));
        importResult.setText("Test du décodage Base64/LZ4 exécuté sur des données inventées. Ce n'est PAS la carte de ton domicile.");
    }

    private int dp(int value) {
        return (int)(getResources().getDisplayMetrics().density * value + .5f);
    }

    private final class MapPreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap previewBitmap;
        private MapGridRasterizer.Preview preview;
        private final RectF destination = new RectF();
        private float zoom = 1f, offsetX, offsetY, lastX, lastY, downX, downY;
        private boolean dragged;

        MapPreviewView() {
            super(MapActivity.this);
            setContentDescription("Carte fictive en attente d'une grille importée");
        }

        void setPreview(MapGridRasterizer.Preview data) {
            preview = data;
            previewBitmap = data == null ? null
                : Bitmap.createBitmap(data.argb, data.width, data.height, Bitmap.Config.ARGB_8888);
            offsetX = offsetY = 0;
            if (data == null) zoom = 1f;
            setContentDescription(data == null
                ? "Carte fictive de démonstration"
                : "Grille brute en niveaux de gris ou palette expérimentale. Utilisez les boutons pour le zoom.");
            invalidate();
        }

        void setZoom(float target) {
            zoom = Math.max(1f, Math.min(4f, target));
            if (zoom <= 1f) offsetX = offsetY = 0;
            invalidate();
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (previewBitmap == null || preview == null) return super.onTouchEvent(event);
            float x = event.getX(), y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = x;
                    downY = lastY = y;
                    dragged = false;
                    if (zoom > 1f) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(x - downX) > dp(7) || Math.abs(y - downY) > dp(7))
                        dragged = true;
                    if (zoom > 1f && dragged) {
                        float scale = Math.min((getWidth() - dp(12)) / (float) previewBitmap.getWidth(),
                                               (getHeight() - dp(12)) / (float) previewBitmap.getHeight());
                        float pictureW = previewBitmap.getWidth() * scale * zoom;
                        float pictureH = previewBitmap.getHeight() * scale * zoom;
                        float maxPanX = Math.max(0f, (pictureW - getWidth()) / 2f + dp(15));
                        float maxPanY = Math.max(0f, (pictureH - getHeight()) / 2f + dp(15));
                        offsetX = Math.max(-maxPanX, Math.min(maxPanX, offsetX + x - lastX));
                        offsetY = Math.max(-maxPanY, Math.min(maxPanY, offsetY + y - lastY));
                        invalidate();
                    }
                    lastX = x; lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragged && destination.contains(x, y)) {
                        int pixelX = Math.min(preview.width - 1,
                            (int)((x - destination.left) * preview.width / destination.width()));
                        int pixelY = Math.min(preview.height - 1,
                            (int)((y - destination.top) * preview.height / destination.height()));
                        MapGridRasterizer.Sample sample = preview.sample(activeCells, pixelX, pixelY);
                        if (sample != null && cellLabel != null)
                            cellLabel.setText("Cellule brute x=" + sample.x + ", y=" + sample.y
                                + " (origine 0) • valeur = " + sample.value
                                + ". Aucune interprétation mur/sol.");
                        performClick();
                    }
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(Color.rgb(21, 37, 55));
            if (previewBitmap != null) {
                float scale = Math.min((getWidth() - dp(12)) / (float) previewBitmap.getWidth(),
                                       (getHeight() - dp(12)) / (float) previewBitmap.getHeight());
                float drawW = previewBitmap.getWidth() * scale * zoom;
                float drawH = previewBitmap.getHeight() * scale * zoom;
                destination.set((getWidth() - drawW) / 2f + offsetX,
                                (getHeight() - drawH) / 2f + offsetY,
                                (getWidth() + drawW) / 2f + offsetX,
                                (getHeight() + drawH) / 2f + offsetY);
                c.save();
                c.clipRect(0, 0, getWidth(), getHeight());
                paint.setFilterBitmap(false);
                c.drawBitmap(previewBitmap, null, destination, paint);
                c.restore();
                return;
            }
            float scale = Math.min(getWidth() / 320f, getHeight() / 400f);
            c.save();
            c.translate((getWidth() - 320 * scale) / 2f, (getHeight() - 400 * scale) / 2f);
            c.scale(scale, scale);
            paint.setColor(Color.rgb(72, 102, 120));
            c.drawRect(35, 38, 279, 352, paint);
            paint.setColor(Color.rgb(21, 37, 55));
            c.drawRect(44, 48, 270, 343, paint);
            paint.setColor(Color.rgb(90, 135, 149));
            c.drawRect(44, 190, 270, 199, paint);
            c.drawRect(170, 48, 179, 190, paint);
            c.drawRect(125, 199, 134, 343, paint);
            paint.setColor(Color.rgb(59, 220, 177));
            c.drawCircle(91, 265, 10, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(15f);
            c.drawText("CARTE FICTIVE", 90, 22, paint);
            c.restore();
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        io.shutdownNow();
        super.onDestroy();
    }
}
