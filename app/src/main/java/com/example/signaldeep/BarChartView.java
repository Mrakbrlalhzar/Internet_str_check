package com.example.signaldeep;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class BarChartView extends View {
    private float[] values = new float[40];
    private int[] colors = new int[40];
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int barColor = 0xFF4CAF50;

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < values.length; i++) {
            values[i] = 0;
            colors[i] = barColor;
        }
    }

    public void setBarColor(int color) { this.barColor = color; }

    public void addValue(float value, int color) {
        for (int i = 0; i < values.length - 1; i++) {
            values[i] = values[i + 1];
            colors[i] = colors[i + 1];
        }
        values[values.length - 1] = value;
        colors[colors.length - 1] = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.length == 0) return;
        float width = getWidth();
        float height = getHeight();
        float barWidth = width / values.length;
        float spacing = 2f;

        paint.setColor(0xFF2A2A3A);
        paint.setStrokeWidth(1f);
        for (int i = 0; i < 4; i++) {
            float y = height - (height / 4f * i);
            canvas.drawLine(0, y, width, y, paint);
        }

        for (int i = 0; i < values.length; i++) {
            paint.setColor(colors[i]);
            float left = i * barWidth;
            float right = left + barWidth - spacing;
            float normalized = Math.max(0f, Math.min(1f, values[i]));
            float top = height - (normalized * (height - 10f));
            canvas.drawRect(left, top, right, height, paint);
        }
    }
}
