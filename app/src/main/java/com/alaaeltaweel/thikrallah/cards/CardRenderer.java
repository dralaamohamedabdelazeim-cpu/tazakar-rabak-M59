package com.alaaeltaweel.thikrallah.cards;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * بيرسم الكارت كله (خلفية + زخارف + نصوص) على Bitmap بالكود.
 * المعاينة والتصدير بيستخدموا نفس الدالة عشان الشكل يطلع مطابق.
 */
public final class CardRenderer {

    private CardRenderer() {}

    private static final Map<Integer, Typeface> FONT_CACHE = new HashMap<Integer, Typeface>();

    public static synchronized Typeface typeface(Context ctx, int fontIndex) {
        Typeface cached = FONT_CACHE.get(fontIndex);
        if (cached != null) return cached;
        Typeface tf = Typeface.DEFAULT;
        try {
            String asset = (fontIndex >= 0 && fontIndex < CardCatalog.FONT_ASSETS.length)
                    ? CardCatalog.FONT_ASSETS[fontIndex] : null;
            if (asset != null) {
                tf = Typeface.createFromAsset(ctx.getApplicationContext().getAssets(), asset);
            }
        } catch (Throwable ignored) {
            tf = Typeface.DEFAULT;
        }
        FONT_CACHE.put(fontIndex, tf);
        return tf;
    }

    /** كارت كامل بعرض معيّن (الارتفاع = العرض للمربع، أو 16:9 للطولي). */
    public static Bitmap render(Context ctx, CardState s, int width) {
        int height = s.portrait ? Math.round(width * 16f / 9f) : width;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        draw(ctx, new Canvas(bmp), s, width, height, true);
        return bmp;
    }

    /** الخلفية بس (من غير نصوص) لمعاينات اختيار الخلفية. */
    public static Bitmap renderBackground(Context ctx, int styleIndex, int paletteIndex, int size) {
        CardState s = new CardState();
        s.styleIndex = styleIndex;
        s.paletteIndex = paletteIndex;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        draw(ctx, new Canvas(bmp), s, size, size, false);
        return bmp;
    }

    // ------------------------------------------------------------------

    private static void draw(Context ctx, Canvas c, CardState s, int w, int h, boolean withText) {
        int styleIdx = Math.max(0, Math.min(CardCatalog.STYLES.length - 1, s.styleIndex));
        int palIdx = Math.max(0, Math.min(CardCatalog.PALETTES.length - 1, s.paletteIndex));
        int orn = CardCatalog.STYLES[styleIdx].ornaments;
        CardCatalog.Palette pal = CardCatalog.PALETTES[palIdx];

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, pal.top, pal.bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        float zoneH = Math.min(h * 0.32f, w * 0.5f);
        float topY = h * 0.05f;
        boolean hasMoon = (orn & CardCatalog.ORN_MOON) != 0;
        boolean hasMosque = (orn & CardCatalog.ORN_MOSQUE) != 0;
        boolean hasArch = (orn & CardCatalog.ORN_ARCH) != 0;
        float mosqueH = Math.min(h * 0.12f, w * 0.15f);

        if ((orn & CardCatalog.ORN_RAYS) != 0) drawRays(c, w, h, w / 2f, topY + zoneH * 0.5f, pal.ornamentColor);
        if ((orn & CardCatalog.ORN_GEO) != 0) drawGeometric(c, w, h, pal.ornamentColor);
        if ((orn & CardCatalog.ORN_DOTS) != 0) drawDots(c, w, h, pal.ornamentColor);
        if ((orn & CardCatalog.ORN_STARS) != 0) {
            drawStars(c, w, hasMoon ? topY + zoneH : h * 0.95f, pal.ornamentColor);
        }
        drawFrame(c, w, h, pal.frameColor);
        if (hasArch) drawArch(c, w, h, pal.ornamentColor);
        if (hasMosque) drawMosque(c, w, h, mosqueH, pal.ornamentColor);
        if (hasMoon) drawMoon(c, w / 2f, topY + zoneH * 0.5f, zoneH * 0.34f, pal.ornamentColor);
        if ((orn & CardCatalog.ORN_LANTERNS) != 0) drawLanterns(c, w, h, zoneH, pal.ornamentColor);

        if (withText) {
            float reservedBottom = hasMosque ? mosqueH * 1.1f : 0f;
            drawTexts(ctx, c, s, w, h, topY + zoneH + w * 0.01f, reservedBottom, hasArch);
        }
    }

    // ------------------------------------------------------------------
    // الزخارف
    // ------------------------------------------------------------------

    private static void drawFrame(Canvas c, int w, int h, int color) {
        Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
        fp.setStyle(Paint.Style.STROKE);
        fp.setColor(color);
        float m = w * 0.035f;
        fp.setStrokeWidth(Math.max(2f, w * 0.004f));
        fp.setAlpha(220);
        c.drawRoundRect(new RectF(m, m, w - m, h - m), w * 0.03f, w * 0.03f, fp);
        fp.setStrokeWidth(Math.max(1f, w * 0.002f));
        fp.setAlpha(120);
        float m2 = m + w * 0.016f;
        c.drawRoundRect(new RectF(m2, m2, w - m2, h - m2), w * 0.02f, w * 0.02f, fp);
    }

    private static void drawStar(Canvas c, float cx, float cy, float outer, float inner, Paint paint) {
        Path path = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rad = (i % 2 == 0) ? outer : inner;
            float x = (float) (cx + rad * Math.cos(a));
            float y = (float) (cy + rad * Math.sin(a));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        c.drawPath(path, paint);
    }

    private static void drawArch(Canvas c, int w, int h, int color) {
        Paint ap = new Paint(Paint.ANTI_ALIAS_FLAG);
        ap.setStyle(Paint.Style.STROKE);
        ap.setColor(color);
        float left = w * 0.14f;
        float right = w * 0.86f;
        float r = (right - left) / 2f;
        float topArch = w * 0.08f;
        float bottom = h - w * 0.09f;
        for (int i = 0; i < 2; i++) {
            float inset = i * w * 0.018f;
            Path path = new Path();
            path.moveTo(left + inset, bottom - inset);
            path.lineTo(left + inset, topArch + r);
            path.arcTo(new RectF(left + inset, topArch + inset, right - inset, topArch + 2 * r - inset),
                    180f, 180f);
            path.lineTo(right - inset, bottom - inset);
            ap.setStrokeWidth(Math.max(i == 0 ? 2f : 1f, w * (i == 0 ? 0.004f : 0.002f)));
            ap.setAlpha(i == 0 ? 160 : 90);
            c.drawPath(path, ap);
        }
    }

    private static void drawRays(Canvas c, int w, int h, float cx, float cy, int color) {
        Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
        rp.setStyle(Paint.Style.STROKE);
        rp.setColor(color);
        rp.setAlpha(34);
        rp.setStrokeWidth(Math.max(1f, w * 0.003f));
        float len = h * 1.3f;
        for (int i = 0; i < 28; i++) {
            double a = i * (2 * Math.PI / 28);
            c.drawLine(cx, cy, (float) (cx + len * Math.cos(a)), (float) (cy + len * Math.sin(a)), rp);
        }
    }

    private static void drawMoon(Canvas c, float cx, float cy, float r, int color) {
        // توهّج خفيف
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        int rgb = color & 0x00FFFFFF;
        glow.setShader(new RadialGradient(cx, cy, r * 1.9f, 0x40000000 | rgb, rgb,
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.9f, glow);

        Path outer = new Path();
        outer.addCircle(cx, cy, r, Path.Direction.CW);
        Path inner = new Path();
        inner.addCircle(cx + r * 0.42f, cy - r * 0.12f, r * 0.82f, Path.Direction.CW);
        Path crescent = new Path();
        crescent.op(outer, inner, Path.Op.DIFFERENCE);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawPath(crescent, p);
        drawStar(c, cx + r * 0.95f, cy - r * 0.05f, r * 0.2f, r * 0.08f, p);
    }

    private static void drawStars(Canvas c, int w, float maxY, int color) {
        Random rnd = new Random(11);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        for (int i = 0; i < 22; i++) {
            float x = w * 0.06f + rnd.nextFloat() * w * 0.88f;
            float y = w * 0.06f + rnd.nextFloat() * Math.max(1f, maxY - w * 0.06f);
            float R = w * (0.007f + rnd.nextFloat() * 0.012f);
            if (Math.abs(x - w / 2f) < w * 0.14f && y < maxY) {
                // نسيب مكان للهلال
                continue;
            }
            p.setAlpha(110 + rnd.nextInt(120));
            drawStar(c, x, y, R, R * 0.42f, p);
        }
    }

    private static void drawGeometric(Canvas c, int w, int h, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setAlpha(38);
        p.setStrokeWidth(Math.max(1f, w * 0.0025f));
        float cell = w * 0.2f;
        float r = cell * 0.36f;
        int row = 0;
        for (float y = -cell / 2f; y < h + cell; y += cell, row++) {
            float offset = (row % 2 == 0) ? 0f : cell / 2f;
            for (float x = -cell / 2f + offset; x < w + cell; x += cell) {
                c.save();
                c.translate(x, y);
                c.drawRect(-r, -r, r, r, p);
                c.rotate(45f);
                c.drawRect(-r, -r, r, r, p);
                c.restore();
            }
        }
    }

    private static void drawDots(Canvas c, int w, int h, int color) {
        Random rnd = new Random(5);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        for (int i = 0; i < 36; i++) {
            float x = rnd.nextFloat() * w;
            float y = rnd.nextFloat() * h;
            float r = w * (0.006f + rnd.nextFloat() * 0.02f);
            p.setAlpha(70 + rnd.nextInt(60));
            c.drawCircle(x, y, r, p);
        }
    }

    private static void drawLanterns(Canvas c, int w, int h, float zoneH, int color) {
        float base = h * 0.05f;
        drawLantern(c, w * 0.20f, base + zoneH * 0.10f, zoneH * 0.78f, color);
        drawLantern(c, w * 0.50f, base + zoneH * 0.02f, zoneH * 0.62f, color);
        drawLantern(c, w * 0.80f, base + zoneH * 0.18f, zoneH * 0.70f, color);
    }

    private static void drawLantern(Canvas c, float cx, float top, float size, int color) {
        float bw = size * 0.5f;

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(color);
        line.setStrokeWidth(Math.max(1f, size * 0.02f));
        c.drawLine(cx, 0, cx, top, line);

        // توهّج
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        int rgb = color & 0x00FFFFFF;
        float gy = top + size * 0.55f;
        glow.setShader(new RadialGradient(cx, gy, size * 0.9f, 0x55000000 | rgb, rgb,
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, gy, size * 0.9f, glow);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);

        // الغطاء والكتف
        Path cap = new Path();
        cap.moveTo(cx - bw * 0.18f, top);
        cap.lineTo(cx + bw * 0.18f, top);
        cap.lineTo(cx + bw * 0.30f, top + size * 0.10f);
        cap.lineTo(cx + bw * 0.50f, top + size * 0.28f);
        cap.lineTo(cx - bw * 0.50f, top + size * 0.28f);
        cap.lineTo(cx - bw * 0.30f, top + size * 0.10f);
        cap.close();
        c.drawPath(cap, fill);

        // الجسم
        fill.setAlpha(235);
        c.drawRoundRect(new RectF(cx - bw / 2f, top + size * 0.28f, cx + bw / 2f, top + size * 0.78f),
                bw * 0.12f, bw * 0.12f, fill);

        // القاعدة
        Path bottom = new Path();
        bottom.moveTo(cx - bw / 2f, top + size * 0.78f);
        bottom.lineTo(cx + bw / 2f, top + size * 0.78f);
        bottom.lineTo(cx + bw * 0.22f, top + size * 0.90f);
        bottom.lineTo(cx - bw * 0.22f, top + size * 0.90f);
        bottom.close();
        fill.setAlpha(255);
        c.drawPath(bottom, fill);

        // الشرّابة
        Paint tassel = new Paint(Paint.ANTI_ALIAS_FLAG);
        tassel.setStyle(Paint.Style.STROKE);
        tassel.setColor(color);
        tassel.setStrokeWidth(Math.max(1f, size * 0.025f));
        c.drawLine(cx, top + size * 0.90f, cx, top + size, tassel);

        // نوافذ الفانوس
        Paint win = new Paint(Paint.ANTI_ALIAS_FLAG);
        win.setStyle(Paint.Style.STROKE);
        win.setColor(0x66000000);
        win.setStrokeWidth(Math.max(1f, size * 0.018f));
        c.drawLine(cx - bw * 0.17f, top + size * 0.32f, cx - bw * 0.17f, top + size * 0.74f, win);
        c.drawLine(cx + bw * 0.17f, top + size * 0.32f, cx + bw * 0.17f, top + size * 0.74f, win);
        c.drawOval(new RectF(cx - bw * 0.17f, top + size * 0.32f, cx + bw * 0.17f, top + size * 0.74f), win);
    }

    private static void drawMosque(Canvas c, int w, int h, float hh, int color) {
        float base = h;
        Path path = new Path();
        // الأرضية والصالة الرئيسية
        path.addRect(0, base - hh * 0.22f, w, base, Path.Direction.CW);
        path.addRect(w * 0.32f, base - hh * 0.55f, w * 0.68f, base, Path.Direction.CW);
        // القبة الرئيسية
        path.addOval(new RectF(w * 0.37f, base - hh * 1.05f, w * 0.63f, base - hh * 0.10f), Path.Direction.CW);
        path.addRect(w * 0.497f, base - hh * 1.12f, w * 0.503f, base - hh * 1.0f, Path.Direction.CW);
        path.addCircle(w * 0.5f, base - hh * 1.14f, hh * 0.05f, Path.Direction.CW);
        // القباب الجانبية
        path.addOval(new RectF(w * 0.24f, base - hh * 0.78f, w * 0.34f, base - hh * 0.30f), Path.Direction.CW);
        path.addOval(new RectF(w * 0.66f, base - hh * 0.78f, w * 0.76f, base - hh * 0.30f), Path.Direction.CW);
        // المآذن
        float[] minaretX = {0.12f, 0.84f};
        for (float mx : minaretX) {
            path.addRect(w * mx, base - hh * 1.30f, w * (mx + 0.04f), base, Path.Direction.CW);
            path.addOval(new RectF(w * (mx - 0.005f), base - hh * 1.62f, w * (mx + 0.045f), base - hh * 1.28f),
                    Path.Direction.CW);
            path.addCircle(w * (mx + 0.02f), base - hh * 1.68f, hh * 0.04f, Path.Direction.CW);
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setAlpha(235);
        c.drawPath(path, p);
    }

    // ------------------------------------------------------------------
    // النصوص
    // ------------------------------------------------------------------

    private static StaticLayout layout(CharSequence text, TextPaint paint, int width) {
        if (Build.VERSION.SDK_INT >= 23) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(false)
                    .build();
        }
        return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.2f, 0f, false);
    }

    private static void drawTexts(Context ctx, Canvas c, CardState s, int w, int h,
                                  float textTop, float reservedBottom, boolean hasArch) {
        Typeface tf = typeface(ctx, s.fontIndex);
        String headline = s.headline == null ? "" : s.headline.trim();
        String body = s.body == null ? "" : s.body.trim();
        String details = s.details == null ? "" : s.details.trim();
        String sender = s.sender == null ? "" : s.sender.trim();

        TextPaint hp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        hp.setTypeface(tf);
        hp.setColor(s.textColor);
        hp.setFakeBoldText(true);
        TextPaint bp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bp.setTypeface(tf);
        bp.setColor(s.textColor);
        TextPaint sp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        sp.setTypeface(tf);
        sp.setColor(s.textColor);
        sp.setTextSize(w * 0.036f);

        float senderCenterY = h - w * 0.11f - reservedBottom;
        float textBottom = senderCenterY - w * 0.06f;
        float avail = textBottom - textTop;
        int textWidth = Math.round(w * (hasArch ? 0.66f : 0.78f));
        float gap = w * 0.05f;

        boolean hasHead = headline.length() > 0;
        boolean hasBody = body.length() > 0;
        boolean hasDetails = details.length() > 0;

        float headSize = w * 0.11f * s.sizeScale;
        float bodySize = w * 0.05f * s.sizeScale;
        TextPaint dp2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        dp2.setTypeface(tf);
        dp2.setColor(s.textColor);
        StaticLayout hl = null;
        StaticLayout bl = null;
        StaticLayout dl = null;
        float total = 0f;
        for (int i = 0; i < 16; i++) {
            hp.setTextSize(headSize);
            bp.setTextSize(bodySize);
            dp2.setTextSize(bodySize * 0.88f);
            hl = hasHead ? layout(headline, hp, textWidth) : null;
            bl = hasBody ? layout(body, bp, textWidth) : null;
            dl = hasDetails ? layout(details, dp2, textWidth) : null;
            total = (hl != null ? hl.getHeight() : 0)
                    + ((hl != null && (bl != null || dl != null)) ? gap : 0)
                    + (bl != null ? bl.getHeight() : 0)
                    + ((bl != null && dl != null) ? gap * 0.6f : 0)
                    + (dl != null ? dl.getHeight() : 0);
            if (total <= avail || headSize < w * 0.035f) break;
            headSize *= 0.92f;
            bodySize *= 0.92f;
        }

        float y = textTop + Math.max(0f, (avail - total) / 2f);
        float x = (w - textWidth) / 2f;

        if (hl != null) {
            c.save();
            c.translate(x, y);
            hl.draw(c);
            c.restore();
            y += hl.getHeight();
        }
        if (hl != null && (bl != null || dl != null)) {
            // فاصل صغير بين العنوان والنص
            Paint dp = new Paint(Paint.ANTI_ALIAS_FLAG);
            dp.setColor(s.textColor);
            dp.setAlpha(170);
            dp.setStrokeWidth(Math.max(1f, w * 0.003f));
            float ly = y + gap / 2f;
            c.drawLine(w / 2f - w * 0.09f, ly, w / 2f - w * 0.015f, ly, dp);
            c.drawLine(w / 2f + w * 0.015f, ly, w / 2f + w * 0.09f, ly, dp);
            c.drawCircle(w / 2f, ly, Math.max(2f, w * 0.006f), dp);
            y += gap;
        }
        if (bl != null) {
            c.save();
            c.translate(x, y);
            bl.draw(c);
            c.restore();
            y += bl.getHeight();
        }
        if (dl != null) {
            if (bl != null) y += gap * 0.6f;
            c.save();
            c.translate(x, y);
            dl.draw(c);
            c.restore();
        }

        if (sender.length() > 0) {
            StaticLayout sl = layout("من: " + sender, sp, textWidth);
            c.save();
            c.translate(x, senderCenterY - sl.getHeight() / 2f);
            sl.draw(c);
            c.restore();
        }
    }
}
