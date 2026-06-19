package com.example.slagalicaapp.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.slagalicaapp.data.models.PlayerMapDot;
import com.example.slagalicaapp.repositories.RegionRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view drawing a simplified map of Serbia with 4 clickable regions.
 * Internal coordinate space: 100 wide × 120 tall (preserves Serbia's proportions).
 */
public class SerbiaMapView extends View {

    private static final float CW = 100f;
    private static final float CH = 120f;

    // ── Region polygons (internal coordinates) ───────────────────────────────

    // Vojvodina – north of Sava/Danube, flat agricultural province
    private static final float[] VOJVODINA = {
            2, 9,   24, 2,   32, 0,   47, 2,
            75, 28,  50, 35,  42, 35,  22, 32,  2, 30
    };

    // Beograd – small district at the Sava/Danube confluence
    private static final float[] BEOGRAD = {
            38, 35,  56, 35,  56, 47,  38, 47
    };

    // Centralna Srbija – large middle region (Beograd drawn on top)
    private static final float[] CENTRALNA = {
            2, 30,   22, 32,   42, 35,   50, 35,   75, 28,
            92, 35,  95, 58,   88, 65,   62, 60,   45, 68,
            18, 76,   5, 65
    };

    // Južna Srbija – southern region
    private static final float[] JUZNA = {
            5, 65,   18, 76,   45, 68,   62, 60,   88, 65,
            95, 58,  92, 82,   80, 96,   52, 104,  30, 102,  8, 92
    };

    // Region colors
    private static final int COLOR_VOJVODINA = 0xFF3A7D44;   // green
    private static final int COLOR_BEOGRAD   = 0xFFB32727;   // red
    private static final int COLOR_CENTRALNA = 0xFF2460A7;   // blue
    private static final int COLOR_JUZNA     = 0xFFB36022;   // orange-brown
    private static final int COLOR_SELECTED  = 0xFFFFD700;   // gold highlight

    // Icon/label positions [cx, cy] in internal coords
    private static final float[] POS_VOJVODINA = {38f, 14f};
    private static final float[] POS_BEOGRAD   = {47f, 41f};
    private static final float[] POS_CENTRALNA = {42f, 48f};
    private static final float[] POS_JUZNA     = {48f, 78f};

    private final Paint vojvodinaPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beogradPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centralnaPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint juznaPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<PlayerMapDot> playerDots = new ArrayList<>();
    private String selectedRegion = null;
    private OnRegionClickListener listener;

    public interface OnRegionClickListener {
        void onRegionClick(String regionName);
    }

    public SerbiaMapView(Context ctx) { super(ctx); init(); }
    public SerbiaMapView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }
    public SerbiaMapView(Context ctx, AttributeSet attrs, int defStyle) { super(ctx, attrs, defStyle); init(); }

    private void init() {
        vojvodinaPaint.setStyle(Paint.Style.FILL);
        vojvodinaPaint.setColor(COLOR_VOJVODINA);

        beogradPaint.setStyle(Paint.Style.FILL);
        beogradPaint.setColor(COLOR_BEOGRAD);

        centralnaPaint.setStyle(Paint.Style.FILL);
        centralnaPaint.setColor(COLOR_CENTRALNA);

        juznaPaint.setStyle(Paint.Style.FILL);
        juznaPaint.setColor(COLOR_JUZNA);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(0xFF0F172A);
        strokePaint.setStrokeWidth(2f);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFFFFFFF);
        dotPaint.setAlpha(200);

        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setColor(COLOR_SELECTED);
        selectedPaint.setStrokeWidth(4f);

        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setColor(Color.WHITE);
    }

    public void setPlayerDots(List<PlayerMapDot> dots) {
        this.playerDots = dots != null ? dots : new ArrayList<>();
        invalidate();
    }

    public void setSelectedRegion(String region) {
        this.selectedRegion = region;
        invalidate();
    }

    public void setOnRegionClickListener(OnRegionClickListener l) {
        this.listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / CW;
        float sy = getHeight() / CH;

        // Draw regions (south first so north draws on top)
        drawRegion(canvas, JUZNA,    juznaPaint,    sx, sy);
        drawRegion(canvas, CENTRALNA,centralnaPaint, sx, sy);
        drawRegion(canvas, VOJVODINA,vojvodinaPaint, sx, sy);
        drawRegion(canvas, BEOGRAD,  beogradPaint,  sx, sy);

        // Selected highlight
        if ("Vojvodina".equals(selectedRegion)) {
            drawRegion(canvas, VOJVODINA, selectedPaint, sx, sy);
        } else if ("Beograd".equals(selectedRegion)) {
            drawRegion(canvas, BEOGRAD, selectedPaint, sx, sy);
        } else if ("Centralna Srbija".equals(selectedRegion)) {
            drawRegion(canvas, CENTRALNA, selectedPaint, sx, sy);
        } else if ("Južna Srbija".equals(selectedRegion)) {
            drawRegion(canvas, JUZNA, selectedPaint, sx, sy);
        }

        // Borders
        drawRegion(canvas, VOJVODINA, strokePaint, sx, sy);
        drawRegion(canvas, BEOGRAD,   strokePaint, sx, sy);
        drawRegion(canvas, CENTRALNA, strokePaint, sx, sy);
        drawRegion(canvas, JUZNA,     strokePaint, sx, sy);

        // Player dots
        float dotR = Math.min(sx, sy) * 1.8f;
        for (PlayerMapDot dot : playerDots) {
            float cx = dot.getMapX() * sx;
            float cy = dot.getMapY() * sy;
            canvas.drawCircle(cx, cy, dotR, dotPaint);
        }

        // Region icons
        float iconSize = Math.min(sx, sy) * 9f;
        iconPaint.setTextSize(iconSize);

        drawIcon(canvas, "🌾", POS_VOJVODINA, sx, sy);
        drawIcon(canvas, "🏙", POS_BEOGRAD,   sx, sy);
        drawIcon(canvas, "⛰", POS_CENTRALNA,  sx, sy);
        drawIcon(canvas, "🌊", POS_JUZNA,      sx, sy);
    }

    private void drawRegion(Canvas canvas, float[] poly, Paint paint, float sx, float sy) {
        Path path = new Path();
        path.moveTo(poly[0] * sx, poly[1] * sy);
        for (int i = 2; i < poly.length; i += 2) {
            path.lineTo(poly[i] * sx, poly[i + 1] * sy);
        }
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawIcon(Canvas canvas, String icon, float[] pos, float sx, float sy) {
        float cx = pos[0] * sx;
        float cy = pos[1] * sy + iconPaint.getTextSize() / 3f;
        canvas.drawText(icon, cx, cy, iconPaint);
    }

    // ── Touch handling ───────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float sx = getWidth() / CW;
            float sy = getHeight() / CH;
            float px = event.getX() / sx;
            float py = event.getY() / sy;

            String tapped = null;
            if (inPoly(px, py, BEOGRAD)) {
                tapped = "Beograd";
            } else if (inPoly(px, py, VOJVODINA)) {
                tapped = "Vojvodina";
            } else if (inPoly(px, py, CENTRALNA)) {
                tapped = "Centralna Srbija";
            } else if (inPoly(px, py, JUZNA)) {
                tapped = "Južna Srbija";
            }

            if (tapped != null) {
                selectedRegion = tapped;
                invalidate();
                if (listener != null) listener.onRegionClick(tapped);
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    /** Ray-casting point-in-polygon test. Coordinates in internal space. */
    private boolean inPoly(float px, float py, float[] poly) {
        boolean inside = false;
        int n = poly.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = poly[i * 2], yi = poly[i * 2 + 1];
            float xj = poly[j * 2], yj = poly[j * 2 + 1];
            if (((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
