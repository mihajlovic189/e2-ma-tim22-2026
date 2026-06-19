package com.example.slagalicaapp.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.models.PlayerMapDot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom view that renders a geographically accurate map of Serbia
 * with 4 clickable regions, parsed from a GeoJSON raw resource.
 *
 * Internal coordinate space: 100 wide × 120 tall.
 * WGS84 bounds used for normalisation:
 *   lng [18.8099, 22.9577]  lat [42.2276, 46.1894]
 */
public class SerbiaMapView extends View {

    private static final float CW = 100f;
    private static final float CH = 120f;

    private static final double LNG_MIN = 18.8099;
    private static final double LNG_MAX = 22.9577;
    private static final double LAT_MIN = 42.2276;
    private static final double LAT_MAX = 46.1894;

    // GADM HASC_1 district code → app region name
    private static final Map<String, String> HASC_TO_REGION = new HashMap<>();
    static {
        for (String h : new String[]{"RS.JC", "RS.JN", "RS.SC", "RS.SN", "RS.SD", "RS.SM", "RS.ZC"})
            HASC_TO_REGION.put(h, "Vojvodina");
        HASC_TO_REGION.put("RS.BG", "Beograd");
        for (String h : new String[]{"RS.KB", "RS.MA", "RS.MR", "RS.PD", "RS.PM", "RS.RN", "RS.RS", "RS.SU", "RS.ZL"})
            HASC_TO_REGION.put(h, "Centralna Srbija");
        for (String h : new String[]{"RS.BO", "RS.BR", "RS.JA", "RS.NS", "RS.PC", "RS.PI", "RS.TO", "RS.ZJ"})
            HASC_TO_REGION.put(h, "Južna Srbija");
    }

    private static final int COLOR_VOJVODINA = 0xFF3A7D44;
    private static final int COLOR_BEOGRAD   = 0xFFB32727;
    private static final int COLOR_CENTRALNA = 0xFF2460A7;
    private static final int COLOR_JUZNA     = 0xFFB36022;
    private static final int COLOR_SELECTED  = 0xFFFFD700;

    // Icon centres in internal 100×120 space (computed from geographic centres)
    private static final float[] POS_VOJVODINA = {30f, 22f};
    private static final float[] POS_BEOGRAD   = {38f, 44f};
    private static final float[] POS_CENTRALNA = {22f, 73f};
    private static final float[] POS_JUZNA     = {74f, 84f};

    // region → list of district polygons (each polygon is float[] x0,y0,x1,y1,…)
    private final Map<String, List<float[]>> regionPolygons = new LinkedHashMap<>();

    private final Map<String, Paint> fillPaints = new HashMap<>();
    private final Paint strokePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<PlayerMapDot> playerDots = new ArrayList<>();
    private String selectedRegion = null;
    private OnRegionClickListener listener;

    public interface OnRegionClickListener {
        void onRegionClick(String regionName);
    }

    public SerbiaMapView(Context ctx) { super(ctx); init(); }
    public SerbiaMapView(Context ctx, AttributeSet a) { super(ctx, a); init(); }
    public SerbiaMapView(Context ctx, AttributeSet a, int s) { super(ctx, a, s); init(); }

    private void init() {
        Paint p;
        p = makeFill(COLOR_VOJVODINA); fillPaints.put("Vojvodina", p);
        p = makeFill(COLOR_BEOGRAD);   fillPaints.put("Beograd", p);
        p = makeFill(COLOR_CENTRALNA); fillPaints.put("Centralna Srbija", p);
        p = makeFill(COLOR_JUZNA);     fillPaints.put("Južna Srbija", p);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(0xFF0F172A);
        strokePaint.setStrokeWidth(1.5f);

        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setColor(COLOR_SELECTED);
        selectedPaint.setStrokeWidth(5f);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFFFFFFF);
        dotPaint.setAlpha(210);

        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setColor(Color.WHITE);

        new Thread(() -> {
            loadGeoJson();
            post(this::invalidate);
        }).start();
    }

    private Paint makeFill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    // ── GeoJSON parsing ──────────────────────────────────────────────────────

    private void loadGeoJson() {
        try {
            InputStream is = getResources().openRawResource(R.raw.serbia_map);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) sb.append(new String(buf, 0, n, "UTF-8"));
            is.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONArray features = root.getJSONArray("features");

            Map<String, List<float[]>> parsed = new LinkedHashMap<>();

            for (int i = 0; i < features.length(); i++) {
                JSONObject feat  = features.getJSONObject(i);
                JSONObject props = feat.getJSONObject("properties");
                String hasc      = props.optString("HASC_1", "");
                String region    = HASC_TO_REGION.get(hasc);
                if (region == null) continue;

                JSONObject geom = feat.getJSONObject("geometry");
                JSONArray  coords = geom.getJSONArray("coordinates");
                String     type   = geom.getString("type");

                if ("Polygon".equals(type)) {
                    addRing(parsed, region, coords.getJSONArray(0));
                } else if ("MultiPolygon".equals(type)) {
                    for (int j = 0; j < coords.length(); j++)
                        addRing(parsed, region, coords.getJSONArray(j).getJSONArray(0));
                }
            }

            synchronized (regionPolygons) {
                regionPolygons.clear();
                regionPolygons.putAll(parsed);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addRing(Map<String, List<float[]>> map, String region, JSONArray ring)
            throws Exception {
        float[] coords = new float[ring.length() * 2];
        for (int i = 0; i < ring.length(); i++) {
            JSONArray pt = ring.getJSONArray(i);
            coords[i * 2]     = normX(pt.getDouble(0));
            coords[i * 2 + 1] = normY(pt.getDouble(1));
        }
        if (!map.containsKey(region)) map.put(region, new ArrayList<>());
        map.get(region).add(coords);
    }

    private float normX(double lng) {
        return (float) ((lng - LNG_MIN) / (LNG_MAX - LNG_MIN) * CW);
    }

    private float normY(double lat) {
        return (float) ((LAT_MAX - lat) / (LAT_MAX - LAT_MIN) * CH);
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth()  / CW;
        float sy = getHeight() / CH;

        synchronized (regionPolygons) {
            // Draw south-to-north so north regions overlay south ones at borders
            drawRegionFill(canvas, "Južna Srbija",    sx, sy);
            drawRegionFill(canvas, "Centralna Srbija", sx, sy);
            drawRegionFill(canvas, "Vojvodina",        sx, sy);
            drawRegionFill(canvas, "Beograd",          sx, sy);

            // Selected highlight
            if (selectedRegion != null)
                drawRegionOutline(canvas, selectedRegion, selectedPaint, sx, sy);

            // District borders
            for (List<float[]> polys : regionPolygons.values())
                for (float[] poly : polys)
                    drawPoly(canvas, poly, strokePaint, sx, sy);
        }

        // Player dots
        float dotR = Math.min(sx, sy) * 1.8f;
        for (PlayerMapDot dot : playerDots)
            canvas.drawCircle(dot.getMapX() * sx, dot.getMapY() * sy, dotR, dotPaint);

        // Region icons
        float iconSz = Math.min(sx, sy) * 9f;
        iconPaint.setTextSize(iconSz);
        drawIcon(canvas, "🌾", POS_VOJVODINA, sx, sy);
        drawIcon(canvas, "🏙",  POS_BEOGRAD,   sx, sy);
        drawIcon(canvas, "⛰",  POS_CENTRALNA,  sx, sy);
        drawIcon(canvas, "🌊", POS_JUZNA,      sx, sy);
    }

    private void drawRegionFill(Canvas canvas, String region, float sx, float sy) {
        List<float[]> polys = regionPolygons.get(region);
        if (polys == null) return;
        Paint paint = fillPaints.get(region);
        for (float[] poly : polys) drawPoly(canvas, poly, paint, sx, sy);
    }

    private void drawRegionOutline(Canvas canvas, String region, Paint paint, float sx, float sy) {
        List<float[]> polys = regionPolygons.get(region);
        if (polys == null) return;
        for (float[] poly : polys) drawPoly(canvas, poly, paint, sx, sy);
    }

    private void drawPoly(Canvas canvas, float[] poly, Paint paint, float sx, float sy) {
        if (poly.length < 4) return;
        Path path = new Path();
        path.moveTo(poly[0] * sx, poly[1] * sy);
        for (int i = 2; i < poly.length; i += 2)
            path.lineTo(poly[i] * sx, poly[i + 1] * sy);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawIcon(Canvas canvas, String icon, float[] pos, float sx, float sy) {
        canvas.drawText(icon, pos[0] * sx, pos[1] * sy + iconPaint.getTextSize() / 3f, iconPaint);
    }

    // ── Touch handling ───────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Always return true for ACTION_DOWN so Android delivers ACTION_UP to this view.
        // Without this, super returns false (view is not clickable), and ACTION_UP is never sent.
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;

        if (event.getAction() == MotionEvent.ACTION_UP) {
            float sx = getWidth()  / CW;
            float sy = getHeight() / CH;
            float px = event.getX() / sx;
            float py = event.getY() / sy;

            // Check Beograd first — it's small and sits inside Centralna's bounding box
            String[] hitOrder = {"Beograd", "Vojvodina", "Centralna Srbija", "Južna Srbija"};
            synchronized (regionPolygons) {
                for (String region : hitOrder) {
                    List<float[]> polys = regionPolygons.get(region);
                    if (polys == null) continue;
                    for (float[] poly : polys) {
                        if (inPoly(px, py, poly)) {
                            selectedRegion = region;
                            invalidate();
                            if (listener != null) listener.onRegionClick(region);
                            return true;
                        }
                    }
                }
            }
        }
        return super.onTouchEvent(event);
    }

    /** Ray-casting point-in-polygon test (internal coordinate space). */
    private boolean inPoly(float px, float py, float[] poly) {
        boolean inside = false;
        int n = poly.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = poly[i * 2], yi = poly[i * 2 + 1];
            float xj = poly[j * 2], yj = poly[j * 2 + 1];
            if (((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi) + xi))
                inside = !inside;
        }
        return inside;
    }

    // ── Public API ───────────────────────────────────────────────────────────

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
}
