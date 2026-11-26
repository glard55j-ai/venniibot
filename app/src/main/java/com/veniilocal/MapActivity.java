package com.veniilocal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
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
    private static final int BACKGROUND = Color.rgb(9, 19, 32);
    private static final int TEXT = Color.rgb(220, 233, 240);
    private static final int MUTED = Color.rgb(164, 183, 201);
    private static final int WARNING = Color.rgb(255, 191, 106);
    private static final int REQUEST_TXT = 407;
    private static final int REQUEST_MAP_JSON = 408;
    private static final int REQUEST_PNG = 409;
    private static final int MAX_IMPORT_BYTES = 6 * 1024 * 1024;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private TextView importResult, heading, metadata, statistics, modeLabel, cellLabel;
    private Button importTxtButton, importMapButton;
    private Button rotateBtn, paletteBtn, zoomInBtn, zoomOutBtn, zoomFitBtn, exportBtn;
    private MapPreviewView mapView;
    private byte[] activeCells;
    private int activeWidth, activeHeight, turns, paletteMode;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        title(root, "KOOMA-I · Cartographie", 24, Color.WHITE);
        heading = title(root, "DÉMONSTRATION — carte fictive", 16, WARNING);
        metadata = title(root, "Aucune carte réelle décodée. L'orientation et la signification des octets restent inconnues.", 13, MUTED);

        mapView = new MapPreviewView();
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, dp(400));
        mapParams.topMargin = dp(12);
        root.addView(mapView, mapParams);

        modeLabel = title(root, "Aperçu en attente • zoom 1×", 13, MUTED);
        cellLabel = title(root, "Touchez une cellule d'une grille pour lire sa valeur brute.", 13, TEXT);

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(firstRow, new LinearLayout.LayoutParams(-1, -2));
        zoomOutBtn = smallButton(firstRow, "Zoom −");
        zoomFitBtn = smallButton(firstRow, "Cadrer");
        zoomInBtn = smallButton(firstRow, "Zoom +");
        zoomOutBtn.setOnClickListener(v -> {
            mapView.setZoom(mapView.zoom - 0.5f); updateModeLabel();
        });
        zoomInBtn.setOnClickListener(v -> {
            mapView.setZoom(mapView.zoom + 0.5f); updateModeLabel();
        });
        zoomFitBtn.setOnClickListener(v -> {
            mapView.setZoom(1f); updateModeLabel();
        });

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(secondRow, new LinearLayout.LayoutParams(-1, -2));
        rotateBtn = smallButton(secondRow, "Pivoter 90°");
        paletteBtn = smallButton(secondRow, "Palette");
        exportBtn = smallButton(secondRow, "Exporter PNG");
        rotateBtn.setOnClickListener(v -> {
            if (activeCells == null) return;
            turns = (turns + 1) % 4; renderActiveGrid();
        });
        paletteBtn.setOnClickListener(v -> {
            if (activeCells == null) return;
            paletteMode = (paletteMode + 1) % 3; renderActiveGrid();
        });
        exportBtn.setOnClickListener(v -> confirmExportPng());
        setToolsEnabled(false);

        title(root, "Une grille importée est représentée en niveaux de gris BRUTS. Elle ne permet pas encore de reconnaître les murs, les pièces ou la position du robot.", 13, WARNING);

        Button synthetic = button(root, "Tester une grille synthétique (sans robot)");
        synthetic.setOnClickListener(v -> showSyntheticExample());

        importMapButton = button(root, "Importer une carte JSON locale");
        importMapButton.setOnClickListener(v -> openDocument(REQUEST_MAP_JSON, new String[]{"application/json", "text/plain", "application/octet-stream"}));

        importTxtButton = button(root, "Analyser un export LiDAR TXT");
        importTxtButton.setOnClickListener(v -> openDocument(REQUEST_TXT, new String[]{"text/plain"}));

        Button reset = button(root, "Revenir à la carte fictive");
        reset.setOnClickListener(v -> showDemo());

        importResult = title(root, "Aucun fichier analysé. Lecture locale uniquement, sans téléchargement ni transfert.", 13, TEXT);
        statistics = title(root, "Statistiques : en attente d’une grille décodée.", 13, MUTED);
        title(root, "Format JSON expérimental attendu : champs width, height et map (Base64 d'un bloc LZ4). Champs facultatifs : resolution, x_min, y_min. Aucun fichier n'est enregistré dans l'application.", 12, MUTED);
        title(root, "Confidentialité : une vraie carte peut révéler le plan du domicile. N'exporte ou ne partage ces fichiers que volontairement.", 12, WARNING);

        Button back = button(root, "Retour au tableau de bord");
        back.setOnClickListener(v -> finish());
    }

    private Button button(LinearLayout root, String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        root.addView(b, params);
        return b;
    }

    private Button smallButton(LinearLayout row, String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinHeight(dp(52));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
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
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setPadding(0, 0, 0, dp(9));
        root.addView(t);
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
                String raw = readBounded(uri);
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
                            // A candidate may contain unknown/non-map data; do not claim it is a map.
                            invalidCandidates++;
                        }
                    }
                    if (decoded != null) {
                        message = report + "\n\nGrille Base64/LZ4 complète repérée dans le bloc "
                                + decodedId + " et décodée localement. Valeurs brutes seulement.";
                    } else {
                        message = report + "\n\nAucune grille complète et décodable avec width, height et map "
                                + "dans cet export. " + blocks.size() + " bloc(s) analysé(s)"
                                + (invalidCandidates > 0
                                ? ", dont " + invalidCandidates + " non compatible(s)." : ".")
                                + "\nCela ne prouve pas que le robot n'a pas de carte.";
                    }
                } else {
                    decoded = decodeDocument(raw);
                    if (decoded == null)
                        throw new IllegalArgumentException("Aucun objet de carte complet (width, height, map) trouvé dans ce JSON");
                    message = "Décodage Base64/LZ4 terminé sur un fichier choisi localement.";
                }
                final DecodedMap finalDecoded = decoded;
                final String resultMessage = message;
                runOnUiThread(() -> {
                    if (destroyed) return;
                    if (finalDecoded != null) {
                        setActiveGrid(finalDecoded.cells, finalDecoded.width, finalDecoded.height);
                        statistics.setText(finalDecoded.analysis);
                        heading.setText("GRILLE DÉCODÉE — pas une carte interprétée");
                        metadata.setText(finalDecoded.info);
                        importResult.setText(resultMessage + "\n0 = noir, 255 = blanc. "
                                + "Le sens des valeurs et l'orientation sont inconnus.");
                    } else {
                        importResult.setText(resultMessage);
                    }
                    unlock();
                });
            } catch (Exception error) {
                String message = error instanceof IllegalArgumentException
                    ? error.getMessage() : "Fichier non compatible ou illisible";
                runOnUiThread(() -> {
                    if (destroyed) return;
                    importResult.setText("Import impossible : " + message
                        + ". L'affichage précédent est conservé.");
                    unlock();
                });
            }
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
        MapPreviewView() {
            super(MapActivity.this);
            setContentDescription("Carte fictive en attente d'une grille importée");
        }
        void setPreview(MapGridRasterizer.Preview preview) {
            previewBitmap = preview == null ? null
                : Bitmap.createBitmap(preview.argb, preview.width, preview.height, Bitmap.Config.ARGB_8888);
            setContentDescription(preview == null
                ? "Carte fictive de démonstration"
                : "Grille de valeurs brutes en niveaux de gris, murs et orientation inconnus");
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(Color.rgb(21, 37, 55));
            if (previewBitmap != null) {
                float scale = Math.min((getWidth()-dp(12))/(float)previewBitmap.getWidth(),
                                       (getHeight()-dp(12))/(float)previewBitmap.getHeight());
                float w = previewBitmap.getWidth() * scale, h = previewBitmap.getHeight() * scale;
                RectF rect = new RectF((getWidth()-w)/2f, (getHeight()-h)/2f,
                                       (getWidth()+w)/2f, (getHeight()+h)/2f);
                paint.setFilterBitmap(false);
                c.drawBitmap(previewBitmap, null, rect, paint);
                return;
            }
            float scale = Math.min(getWidth()/320f, getHeight()/400f);
            c.save();
            c.translate((getWidth()-320*scale)/2f, (getHeight()-400*scale)/2f);
            c.scale(scale,scale);
            paint.setColor(Color.rgb(72,102,120));
            c.drawRect(35,38,279,352,paint);
            paint.setColor(Color.rgb(21,37,55));
            c.drawRect(44,48,270,343,paint);
            paint.setColor(Color.rgb(90,135,149));
            c.drawRect(44,190,270,199,paint);
            c.drawRect(170,48,179,190,paint);
            c.drawRect(125,199,134,343,paint);
            paint.setColor(Color.rgb(59,220,177));
            c.drawCircle(91,265,10,paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(15f);
            c.drawText("CARTE FICTIVE",90,22,paint);
            c.restore();
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        io.shutdownNow();
        super.onDestroy();
    }
}
