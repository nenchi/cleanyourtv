package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Local change: the weather moving over the sky - falling rain, drifting snow, slow clouds, low fog, lightning in a
 * storm, twinkling stars on a clear night. Drawn only while Home is in front ({@link #start()} / {@link #stop()}),
 * at most 30 frames a second (20 when only clouds move), with fixed particle counts and one small cloud bitmap,
 * so an app opened from Home never shares the GPU with it.
 */
public class WeatherView extends View {

    private static final int RAIN_DROPS = 110, STORM_DROPS = 150, SNOW_FLAKES = 90, STARS = 70;

    private final Random random = new Random(11);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int kind = -1;
    private int code = -1;
    private boolean day = true;
    /** Particle count and size scale from the code's strength (drizzle 0.5 ... heavy 1.6). */
    private float strength = 1f;
    /** Freezing drizzle or rain: icier drops. Snow grains: tiny fast flakes. Hail with the storm. */
    private boolean freezing, grains, hail;
    private boolean running;
    private long lastFrame;
    private float w, h;

    // Particles: x, y, speed (px/s), size, phase.
    private float[] px = new float[0], py = px, speed = px, size = px, phase = px;
    private float[] lines = px;
    // Clouds: x, y, width, speed, alpha.
    private float[] cx = px, cy = px, cw = px, cs = px, ca = px;
    private Bitmap cloud;
    private final RectF dst = new RectF();
    // Lightning: when the next flash starts, and when the current one started.
    private long nextFlash, flashAt = -1;

    public WeatherView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** The WMO code and day/night, or code -1 for nothing. */
    public void setWeather(int wmo, boolean isDay) {
        if (wmo == code && isDay == day) return;
        int k = wmo < 0 ? -1 : Sky.kind(wmo);
        code = wmo;
        kind = k;
        day = isDay;
        strength = strength(wmo);
        freezing = wmo == 56 || wmo == 57 || wmo == 66 || wmo == 67;
        grains = wmo == 77;
        hail = wmo == 96 || wmo == 99;
        setVisibility(k < 0 ? GONE : VISIBLE);
        seed();
        if (running) kick();
    }

    /** How hard it falls, from the WMO code: light, moderate, heavy; drizzle lighter than rain. */
    static float strength(int wmo) {
        switch (wmo) {
            case 51: case 56: return 0.45f;            // light drizzle, light freezing drizzle
            case 53: return 0.6f;
            case 55: case 57: return 0.75f;
            case 61: case 66: case 80: case 71: case 85: case 77: return 0.7f;   // light rain/snow, showers
            case 63: case 81: case 73: return 1.0f;
            case 65: case 67: case 82: case 75: case 86: return 1.6f;          // heavy
            case 96: case 99: return 1.3f;
            default: return 1.0f;
        }
    }

    public void start() {
        running = true;
        lastFrame = 0;
        kick();
    }

    public void stop() {
        running = false;
    }

    private void kick() {
        if (getVisibility() == VISIBLE) postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldW, int oldH) {
        w = width;
        h = height;
        seed();
    }

    private void seed() {
        if (w == 0 || kind < 0) return;
        boolean drizzle = code >= 51 && code <= 57;
        boolean stars = !day && (kind == Sky.CLEAR || kind == Sky.PARTLY);
        int n = kind == Sky.RAIN ? RAIN_DROPS : kind == Sky.STORM ? STORM_DROPS : kind == Sky.SNOW ? SNOW_FLAKES
                : stars ? (kind == Sky.CLEAR ? STARS : STARS / 2) : 0;
        if (!stars) n = Math.round(n * strength);
        moonPath = null;
        sunShown = day && (kind == Sky.CLEAR || kind == Sky.PARTLY);
        moonShown = !day && (kind == Sky.CLEAR || kind == Sky.PARTLY);
        if (moonShown) buildMoon();
        if (sunShown) buildSun();
        px = new float[n];
        py = new float[n];
        speed = new float[n];
        size = new float[n];
        phase = new float[n];
        lines = new float[n * 4];
        for (int i = 0; i < n; i++) {
            px[i] = random.nextFloat() * (w + 200);
            py[i] = random.nextFloat() * h;
            phase[i] = random.nextFloat() * 6.2832f;
            if (kind == Sky.SNOW && grains) {
                speed[i] = 160 + random.nextFloat() * 120;   // snow grains: tiny, falling fast
                size[i] = 0.8f + random.nextFloat() * 1.0f;
            } else if (kind == Sky.SNOW) {
                speed[i] = (35 + random.nextFloat() * 60) * (0.8f + 0.25f * strength);
                size[i] = (1.5f + random.nextFloat() * 2.8f) * (0.8f + 0.2f * strength);
            } else if (stars) {
                py[i] = random.nextFloat() * h * 0.6f;   // stars in the upper sky
                size[i] = 0.8f + random.nextFloat() * 1.4f;
                speed[i] = 0.6f + random.nextFloat() * 1.4f;   // twinkle rate
            } else if (drizzle) {
                speed[i] = 550 + random.nextFloat() * 250;   // drizzle: fine, short, slower
                size[i] = 8 + random.nextFloat() * 8;
            } else {
                speed[i] = (kind == Sky.STORM ? 1300 : 950) + random.nextFloat() * 500;
                size[i] = (18 + random.nextFloat() * 20) * (0.75f + 0.25f * strength);
            }
        }
        int clouds;
        switch (kind) {
            case Sky.PARTLY: clouds = 3; break;
            case Sky.FOG: clouds = 6; break;
            case Sky.CLEAR: clouds = code == 1 ? 1 : 0; break;   // mainly clear: one lone cloud
            default: clouds = 5; break;   // overcast, rain, snow, storm
        }
        cx = new float[clouds];
        cy = new float[clouds];
        cw = new float[clouds];
        cs = new float[clouds];
        ca = new float[clouds];
        for (int i = 0; i < clouds; i++) {
            cw[i] = w * (kind == Sky.FOG ? 0.9f + random.nextFloat() * 0.5f : 0.35f + random.nextFloat() * 0.35f);
            cx[i] = random.nextFloat() * (w + cw[i]) - cw[i];
            cy[i] = kind == Sky.FOG ? h * (0.45f + random.nextFloat() * 0.45f) : h * random.nextFloat() * 0.45f;
            cs[i] = (kind == Sky.FOG ? 6 : 10) + random.nextFloat() * 14;
            float a = kind == Sky.FOG ? 0.16f : kind == Sky.PARTLY ? 0.13f : 0.11f;
            ca[i] = (a + random.nextFloat() * 0.06f) * (day ? 1f : 0.7f);
        }
        if (clouds > 0 && cloud == null) cloud = makeCloud();
        nextFlash = SystemClock.uptimeMillis() + 4000 + random.nextInt(8000);
        flashAt = -1;
    }

    // ---- sun and moon ------------------------------------------------------------------------------------------

    private boolean sunShown, moonShown;
    private float bodyX, bodyY;
    private android.graphics.Path moonPath, raysPath;
    private Paint glowPaint, rayPaint;

    private int sunMinute = -1;
    /** For the ADB preview only: a minute of the day to show the sun at, or -1 for now. */
    static int previewMinute = -1;
    /** 0 at midday to 1 at sunrise/sunset: how warm (golden) the low sun is. */
    private float warmth;
    private Paint hazePaint;

    private static int minuteNow() {
        if (previewMinute >= 0) return previewMinute;
        java.util.Calendar now = java.util.Calendar.getInstance();
        return now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
    }

    /**
     * How far through today's daylight it is, 0 at sunrise to 1 at sunset (config.xml latitude/longitude), so the
     * sun rises low on the left in the morning, stands high at midday and sets low on the right.
     */
    private float dayFraction() {
        int minute = minuteNow();
        int rise = 7 * 60, set = 19 * 60;
        try {
            int[] sun = Sun.riseAndSet(java.time.LocalDate.now(),
                    Double.parseDouble(getContext().getString(R.string.latitude)),
                    Double.parseDouble(getContext().getString(R.string.longitude)), java.time.ZoneId.systemDefault());
            if (sun != null) {
                rise = sun[0];
                set = sun[1];
            }
        } catch (NumberFormatException ignored) {
            // no location: a fixed 07:00 to 19:00 day
        }
        sunMinute = minute;
        return Math.max(0.03f, Math.min(0.97f, (minute - rise) / (float) Math.max(1, set - rise)));
    }

    /** The sun on its arc for this minute, with slow rays reaching across the sky. */
    private void buildSun() {
        float f = dayFraction();
        bodyX = w * (0.06f + 0.88f * f);
        bodyY = h * (0.62f - 0.70f * (float) Math.sin(Math.PI * f));   // low at dawn and dusk, above the top at noon
        warmth = 1f - (float) Math.sin(Math.PI * f);
        // Midday pale yellow-white, morning and evening golden orange.
        int core = Sky.lerpColor(0xFFFFF4D8, 0xFFFFB35C, warmth) & 0x00FFFFFF;
        float reach = h * 0.85f;
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setShader(new RadialGradient(bodyX, bodyY, h * 0.42f,
                new int[]{0x5C000000 | core, 0x1E000000 | core, core}, new float[]{0f, 0.35f, 1f},
                Shader.TileMode.CLAMP));
        rayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rayPaint.setShader(new RadialGradient(bodyX, bodyY, reach,
                new int[]{0x12000000 | core, core}, null, Shader.TileMode.CLAMP));
        // Low sun after sunrise or before sunset: a faint warm haze along the bottom of the screen.
        hazePaint = null;
        if (warmth > 0.7f) {
            hazePaint = new Paint();
            hazePaint.setShader(new android.graphics.LinearGradient(0, h * 0.55f, 0, h,
                    core, Math.round(0x30 * (warmth - 0.7f) / 0.3f) << 24 | core, Shader.TileMode.CLAMP));
        }
        raysPath = new android.graphics.Path();
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * 2 * i / 9, spread = 0.10 + (i % 3) * 0.04;   // fewer, wider, softer rays
            raysPath.moveTo(bodyX, bodyY);
            raysPath.lineTo(bodyX + (float) Math.cos(a - spread) * reach, bodyY + (float) Math.sin(a - spread) * reach);
            raysPath.lineTo(bodyX + (float) Math.cos(a + spread) * reach, bodyY + (float) Math.sin(a + spread) * reach);
            raysPath.close();
        }
    }

    /** Tonight's moon in its real phase, in the upper middle, clear of the clock and the buttons. */
    private void buildMoon() {
        bodyX = w * 0.62f;
        bodyY = h * 0.30f;
        float r = h * 0.032f;
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setShader(new RadialGradient(bodyX, bodyY, r * 6,
                new int[]{0x30E8EEFF, 0x00E8EEFF}, null, Shader.TileMode.CLAMP));
        moonPath = moonShape(moonPhase(System.currentTimeMillis()), bodyX, bodyY, r);
    }

    /** 0 = new moon, 0.5 = full, from a known new moon (2000-01-06 18:14 UTC) and the synodic month. */
    static double moonPhase(long nowMs) {
        double days = (nowMs - 947182440000L) / 86_400_000.0;
        double p = (days / 29.530588853) % 1.0;
        return p < 0 ? p + 1 : p;
    }

    /** The lit part of the disc: a half disc on the lit side, with the terminator as half an ellipse. */
    static android.graphics.Path moonShape(double phase, float x, float y, float r) {
        boolean waxing = phase < 0.5;
        float k = (float) Math.cos(2 * Math.PI * phase);   // 1 new, 0 half, -1 full
        android.graphics.Path half = new android.graphics.Path();
        RectF disc = new RectF(x - r, y - r, x + r, y + r);
        half.addArc(disc, waxing ? -90 : 90, 180);   // right half while waxing, left half while waning
        half.close();
        android.graphics.Path ellipse = new android.graphics.Path();
        float rx = Math.abs(k) * r;
        ellipse.addOval(new RectF(x - rx, y - r, x + rx, y + r), android.graphics.Path.Direction.CW);
        android.graphics.Path lit = new android.graphics.Path();
        lit.op(half, ellipse, k > 0 ? android.graphics.Path.Op.DIFFERENCE : android.graphics.Path.Op.UNION);
        return lit;
    }

    private void drawSunOrMoon(Canvas canvas, float t) {
        if (sunShown && raysPath != null) {
            if (minuteNow() != sunMinute) buildSun();
            if (hazePaint != null) canvas.drawRect(0, h * 0.55f, w, h, hazePaint);
            canvas.drawCircle(bodyX, bodyY, h * 0.42f, glowPaint);
            canvas.save();
            canvas.rotate(t * 1.5f, bodyX, bodyY);   // 1.5 degrees a second
            canvas.drawPath(raysPath, rayPaint);
            canvas.restore();
        } else if (moonShown && moonPath != null) {
            float r = h * 0.032f;
            canvas.drawCircle(bodyX, bodyY, r * 6, glowPaint);
            paint.setShader(null);
            paint.setColor(0x14FFFFFF);   // the dark part, faintly lit by earthshine
            canvas.drawCircle(bodyX, bodyY, r, paint);
            paint.setColor(0xF2F4F1E6);
            canvas.drawPath(moonPath, paint);
        }
    }

    /** One soft white cloud, a few overlapping round puffs, 256x128; drawn scaled for every cloud on screen. */
    private static Bitmap makeCloud() {
        Bitmap b = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[][] puffs = {{70, 78, 52}, {120, 60, 62}, {175, 74, 50}, {100, 90, 48}, {150, 92, 46}, {205, 88, 36}};
        for (float[] f : puffs) {
            p.setShader(new RadialGradient(f[0], f[1], f[2], 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(f[0], f[1], f[2], p);
        }
        return b;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (kind < 0 || w == 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float t = now / 1000f;

        drawSunOrMoon(canvas, t);

        // Clouds and fog.
        for (int i = 0; i < cx.length; i++) {
            cx[i] += cs[i] * dt;
            if (cx[i] > w) cx[i] = -cw[i];
            float ch = cw[i] * (kind == Sky.FOG ? 0.28f : 0.5f);
            dst.set(cx[i], cy[i] - ch / 2, cx[i] + cw[i], cy[i] + ch / 2);
            paint.setShader(null);
            paint.setAlpha(Math.round(255 * ca[i]));
            canvas.drawBitmap(cloud, null, dst, paint);
        }
        paint.setAlpha(255);

        int n = px.length;
        if (kind == Sky.RAIN || kind == Sky.STORM) {
            for (int i = 0; i < n; i++) {
                py[i] += speed[i] * dt;
                px[i] -= speed[i] * 0.18f * dt;
                if (py[i] > h || px[i] < -40) {
                    py[i] = -size[i];
                    px[i] = random.nextFloat() * (w + 200);
                }
                lines[i * 4] = px[i];
                lines[i * 4 + 1] = py[i];
                lines[i * 4 + 2] = px[i] - size[i] * 0.18f;
                lines[i * 4 + 3] = py[i] + size[i];
            }
            paint.setColor(freezing ? 0x66E6FAFF : kind == Sky.STORM ? 0x55D8E4F0 : 0x48D8E4F0);
            paint.setStrokeWidth(code >= 51 && code <= 57 ? 1.1f : 1.6f);
            canvas.drawLines(lines, paint);
            if (hail) {
                // Hailstones: every fifth drop is a small white stone.
                paint.setColor(0xCCFFFFFF);
                for (int i = 0; i < n; i += 5) canvas.drawCircle(px[i], py[i] + size[i], 2.4f, paint);
            }
        } else if (kind == Sky.SNOW) {
            paint.setColor(0xFFFFFFFF);
            for (int i = 0; i < n; i++) {
                py[i] += speed[i] * dt;
                px[i] += (float) Math.sin(t * 0.9f + phase[i]) * 22 * dt;
                if (py[i] > h) {
                    py[i] = -size[i];
                    px[i] = random.nextFloat() * w;
                }
                paint.setAlpha(Math.round(120 + size[i] * 25));
                canvas.drawCircle(px[i], py[i], size[i], paint);
            }
        } else if (!day && (kind == Sky.CLEAR || kind == Sky.PARTLY)) {
            paint.setColor(0xFFFFFFFF);
            for (int i = 0; i < n; i++) {
                float twinkle = 0.55f + 0.45f * (float) Math.sin(t * speed[i] + phase[i]);
                paint.setAlpha(Math.round(150 * twinkle));
                canvas.drawCircle(px[i], py[i], size[i], paint);
            }
        }

        if (kind == Sky.STORM) {
            if (flashAt < 0 && now >= nextFlash) flashAt = now;
            if (flashAt >= 0) {
                long since = now - flashAt;
                // Two quick flickers, then a fade.
                float a = since < 70 ? 0.30f : since < 140 ? 0.05f : since < 220 ? 0.22f
                        : Math.max(0f, 0.22f * (1 - (since - 220) / 400f));
                if (since > 620) {
                    flashAt = -1;
                    nextFlash = now + 6000 + random.nextInt(14000);
                } else {
                    canvas.drawColor(Math.round(255 * a) << 24 | 0xE8ECFF);
                }
            }
        }

        if (running) {
            // 30 fps for falling things, 20 for clouds and stars only.
            long frame = (kind == Sky.RAIN || kind == Sky.STORM || kind == Sky.SNOW) ? 33 : 50;
            postInvalidateDelayed(frame);
        }
    }
}
