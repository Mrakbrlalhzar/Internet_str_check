package com.example.signaldeep;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class BarChartView extends View {
    private float[] values = new float[60];
    private int[] colors = new int[60];
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float minY = 0, maxY = 100;
    private float threshGood = 75, threshFair = 40;

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < values.length; i++) {
            values[i] = minY;
            colors[i] = 0xFF4CAF50;
        }
        gridPaint.setColor(0xFF333333);
        gridPaint.setStrokeWidth(2f);
        textPaint.setColor(0xFF888888);
        textPaint.setTextSize(24f);
        thresholdPaint.setStrokeWidth(2f);
    }

    public void setYRange(float min, float max) { this.minY = min; this.maxY = max; }
    public void setThresholds(float good, float fair) { this.threshGood = good; this.threshFair = fair; }

    public void addValue(float value, int color) {
        for (int i = 0; i < values.length - 1; i++) {
            values[i] = values[i + 1];
            colors[i] = colors[i + 1];
        }
        values[values.length - 1] = Math.max(minY, Math.min(maxY, value));
        colors[colors.length - 1] = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.length == 0) return;

        float width = getWidth();
        float height = getHeight();
        float paddingLeft = 60f;
        float paddingBottom = 20f;
        float chartWidth = width - paddingLeft;
        float chartHeight = height - paddingBottom;

        textPaint.setTextAlign(Paint.Align.RIGHT);
        float step = (maxY - minY) / 3f;
        for (int i = 0; i <= 3; i++) {
            float val = minY + (step * i);
            float y = chartHeight - ((val - minY) / (maxY - minY) * chartHeight);
            canvas.drawLine(paddingLeft, y, width, y, gridPaint);
            canvas.drawText(String.valueOf((int)val), paddingLeft - 8, y + 8, textPaint);
        }

        thresholdPaint.setColor(0xFF4CAF50);
        float yGood = chartHeight - ((threshGood - minY) / (maxY - minY) * chartHeight);
        if (yGood >= 0 && yGood <= chartHeight) {
            canvas.drawLine(paddingLeft, yGood, width, yGood, thresholdPaint);
        }

        thresholdPaint.setColor(0xFFFF9800);
        float yFair = chartHeight - ((threshFair - minY) / (maxY - minY) * chartHeight);
        if (yFair >= 0 && yFair <= chartHeight) {
            canvas.drawLine(paddingLeft, yFair, width, yFair, thresholdPaint);
        }

        float barWidth = chartWidth / values.length;
        for (int i = 0; i < values.length; i++) {
            paint.setColor(colors[i]);
            float left = paddingLeft + i * barWidth;
            float right = left + barWidth - 1;
            float val = values[i];
            float top = chartHeight - ((val - minY) / (maxY - minY) * chartHeight);
            canvas.drawRect(left, top, right, chartHeight, paint);
        }
    }
}
