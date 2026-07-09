package com.enpower.hannyburg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

public class HudView extends View {
    private final HannyburgRenderer renderer;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HudView(Context context, HannyburgRenderer renderer) {
        super(context);
        this.renderer = renderer;
        setWillNotDraw(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(4f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        drawTopPanel(canvas, w, h);
        drawMissionCard(canvas, w, h);
        drawControls(canvas, w, h);
    }

    private void drawTopPanel(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, w, 0, 0xDD081421, 0xAA2A0C3B, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(18, 14, w - 18, 112), 22, 22, paint);
        paint.setShader(null);

        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(34f);
        paint.setFakeBoldText(true);
        c.drawText("HANNYBURG DELUXE", 34, 52, paint);

        paint.setTextSize(17f);
        paint.setFakeBoldText(false);
        paint.setColor(0xFFBFE8FF);
        c.drawText("Original 3D open-city prototype", 36, 79, paint);

        paint.setTextSize(17f);
        paint.setColor(0xFFFFE48A);
        c.drawText("Hanny boi 123", w - 340, 48, paint);
        paint.setColor(0xFFFF8A8A);
        c.drawText("Rafialdo", w - 340, 76, paint);

        drawHealthBar(c, w - 220, 88, 180, 12, renderer.getHealth());
    }

    private void drawMissionCard(Canvas c, int w, int h) {
        float cardTop = 126f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB009111C);
        c.drawRoundRect(new RectF(18, cardTop, w - 18, cardTop + 92), 18, 18, paint);

        paint.setFakeBoldText(true);
        paint.setTextSize(21f);
        paint.setColor(0xFFFFFFFF);
        drawWrappedText(c, renderer.getMissionText(), 36, cardTop + 30, w - 72, 25, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(16f);
        paint.setColor(0xFFBEEBFF);
        drawWrappedText(c, renderer.getHintText(), 36, cardTop + 69, w - 72, 22, paint);

        paint.setTextSize(15f);
        paint.setColor(0xFFE6F7FF);
        c.drawText(renderer.getStatusText(), 36, cardTop + 90, paint);
    }

    private void drawHealthBar(Canvas c, float x, float y, float width, float height, int health) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x77222222);
        c.drawRoundRect(new RectF(x, y, x + width, y + height), 8, 8, paint);
        float fill = Math.max(0f, Math.min(1f, health / 100f));
        int color = health > 55 ? 0xFF69FF84 : (health > 25 ? 0xFFFFD55A : 0xFFFF5A5A);
        paint.setColor(color);
        c.drawRoundRect(new RectF(x, y, x + width * fill, y + height), 8, 8, paint);
    }

    private void drawControls(Canvas c, int w, int h) {
        float joyX = w * 0.17f;
        float joyY = h * 0.75f;
        float joyR = Math.max(92f, Math.min(w, h) * 0.13f);
        float knobX = joyX + renderer.getControlX() * joyR * 0.78f;
        float knobY = joyY + renderer.getControlY() * joyR * 0.78f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x331EB9FF);
        c.drawCircle(joyX, joyY, joyR, paint);
        stroke.setColor(0xBCE8F6FF);
        stroke.setStrokeWidth(4f);
        c.drawCircle(joyX, joyY, joyR, stroke);
        paint.setColor(0xDDE8F6FF);
        c.drawCircle(knobX, knobY, joyR * 0.32f, paint);

        paint.setTextSize(18f);
        paint.setFakeBoldText(true);
        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("MOVE", joyX, joyY + joyR + 32, paint);

        float actionX = w * 0.86f;
        float actionY = h * 0.76f;
        float actionR = Math.max(84f, Math.min(w, h) * 0.12f);
        paint.setColor(0x775CFF8A);
        c.drawCircle(actionX, actionY, actionR, paint);
        stroke.setColor(0xEEFFFFFF);
        c.drawCircle(actionX, actionY, actionR, stroke);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(23f);
        c.drawText("ACTION", actionX, actionY + 8, paint);

        float restartX = w * 0.91f;
        float restartY = h * 0.14f;
        float restartR = Math.max(58f, Math.min(w, h) * 0.075f);
        paint.setColor(0x55FFFFFF);
        c.drawCircle(restartX, restartY, restartR, paint);
        stroke.setColor(0xCCFFFFFF);
        c.drawCircle(restartX, restartY, restartR, stroke);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(15f);
        c.drawText("RESTART", restartX, restartY + 5, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setFakeBoldText(false);
        paint.setTextSize(15f);
        paint.setColor(0xCCFFFFFF);
        c.drawText("Drag right side to rotate camera", w - 26, h - 28, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawWrappedText(Canvas c, String text, float x, float y, float maxWidth, float lineHeight, Paint p) {
        if (text == null || text.length() == 0) return;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float cy = y;
        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (p.measureText(test) > maxWidth && line.length() > 0) {
                c.drawText(line.toString(), x, cy, p);
                line = new StringBuilder(word);
                cy += lineHeight;
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) c.drawText(line.toString(), x, cy, p);
    }
}
