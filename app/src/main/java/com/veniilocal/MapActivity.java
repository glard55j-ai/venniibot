package com.veniilocal;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

/** Offline map UI prototype. No connection to, commands for, or telemetry from the robot. */
public final class MapActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(9, 19, 32);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(20));
        root.setBackgroundColor(BACKGROUND);
        title(root, "KOOMA-I · Cartographie expérimentale", 22, Color.WHITE);
        title(root, "MODE DÉMONSTRATION — carte fictive", 15, Color.rgb(255, 191, 106));
        title(root, "Aucune carte réelle récupérée. Aucun échange avec le LiDAR et aucune commande envoyée.", 14, Color.rgb(164, 183, 201));
        DemoMap map = new DemoMap();
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        mapParams.topMargin = dp(18);
        mapParams.bottomMargin = dp(16);
        root.addView(map, mapParams);
        title(root, "Prochaine étape : identifier et décoder le format des données cartographiques à partir des diagnostics existants.", 14, Color.rgb(164, 183, 201));
        Button back = new Button(this);
        back.setText("Retour au tableau de bord");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        setContentView(root);
    }
    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }
    private void title(LinearLayout root, String message, int size, int color) {
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextColor(color);
        text.setTextSize(size);
        text.setPadding(0, 0, 0, dp(10));
        root.addView(text);
    }
    private final class DemoMap extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        DemoMap() { super(MapActivity.this); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            canvas.drawColor(Color.rgb(21,37,55));
            if (w <= 0 || h <= 0) return;
            float scale = Math.min(w / 320f, h / 400f);
            canvas.save();
            canvas.translate((w - 320f * scale) / 2f, (h - 400f * scale) / 2f);
            canvas.scale(scale, scale);
            p.setColor(Color.rgb(44, 66, 86));
            p.setStrokeWidth(1f);
            for (int x = 0; x <= 320; x += 20) canvas.drawLine(x, 0, x, 400, p);
            for (int y = 0; y <= 400; y += 20) canvas.drawLine(0, y, 320, y, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(72, 102, 120));
            canvas.drawRect(35, 38, 279, 352, p);
            p.setColor(Color.rgb(21,37,55));
            canvas.drawRect(44, 48, 270, 343, p);
            p.setColor(Color.rgb(90, 135, 149));
            canvas.drawRect(44, 190, 270, 199, p);
            canvas.drawRect(170, 48, 179, 190, p);
            canvas.drawRect(125, 199, 134, 343, p);
            p.setColor(Color.rgb(59,220,177));
            canvas.drawCircle(91, 265, 10, p);
            p.setColor(Color.WHITE);
            p.setTextSize(15f);
            canvas.drawText("CARTE FICTIVE", 90, 22, p);
            p.setTextSize(11f);
            canvas.drawText("Robot simulé", 104, 270, p);
            canvas.restore();
        }
    }
    // No networking; simulated drawing only.
}