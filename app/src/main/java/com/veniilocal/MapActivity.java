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
    private static final int MAX_IMPORT_BYTES = 6 * 1024 * 1024;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private TextView importResult, heading, metadata;
    private Button importTxtButton, importMapButton;
    private MapPreviewView mapView;

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
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, dp(330));
        mapParams.topMargin = dp(12);
        root.addView(mapView, mapParams);

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
        if ((request != REQUEST_TXT && request != REQUEST_MAP_JSON)
                || result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        importResult.setText("Analyse locale en cours…");
        importTxtButton.setEnabled(false);
        importMapButton.setEnabled(false);
        io.execute(() -> {
            try {
                String raw = readBounded(uri);
                if (request == REQUEST_TXT) {
                    String report = MapPayloadInspector.inspect(raw);
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        importResult.setText(report);
                        unlock();
                    });
                } else {
                    JSONObject obj = new JSONObject(raw);
                    int width = integerField(obj, "width");
                    int height = integerField(obj, "height");
                    Object mapValue = obj.opt("map");
                    if (!(mapValue instanceof String) || ((String)mapValue).isEmpty())
                        throw new IllegalArgumentException("Champ map (Base64) manquant");
                    byte[] cells = MapGridDecoder.decodeBase64Lz4((String)mapValue, width, height);
                    MapGridRasterizer.Preview preview = MapGridRasterizer.rasterize(cells, width, height, 600);
                    String info = width + " × " + height + " cellules ; aperçu "
                            + preview.width + " × " + preview.height + "."
                            + optionalNumber(obj, "resolution")
                            + optionalNumber(obj, "x_min")
                            + optionalNumber(obj, "y_min");
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        mapView.setPreview(preview);
                        heading.setText("OCTETS IMPORTÉS — pas une carte interprétée");
                        metadata.setText(info);
                        importResult.setText("Décodage Base64/LZ4 terminé localement. Valeurs des cellules affichées en gris brut (0 = noir, 255 = blanc). Le sens de ces valeurs et l'orientation sont inconnus.");
                        unlock();
                    });
                }
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
        mapView.setPreview(null);
        heading.setText("DÉMONSTRATION — carte fictive");
        metadata.setText("Aucune carte réelle décodée. L'orientation et la signification des octets restent inconnues.");
        importResult.setText("Démonstration seulement : aucun fichier lu ni ordre envoyé au robot.");
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
        mapView.setPreview(preview);
        heading.setText("EXEMPLE SYNTHÉTIQUE — aucun robot");
        metadata.setText("16 × 12 octets artificiels ; aperçu des nuances de gris, sans murs ni pièces.");
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
