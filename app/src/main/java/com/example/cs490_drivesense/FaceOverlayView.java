package com.example.cs490_drivesense;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class FaceOverlayView extends View {

    private final Paint boxPaint = new Paint();
    private RectF currentBox = null;
    private RectF targetBox = null;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint.setColor(0x80FFEB3B); // Semi-transparent yellow
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);
    }

    public void updateBox(RectF newBox) {
        if (targetBox == null) {
            targetBox = new RectF(newBox);
            currentBox = new RectF(newBox);
        } else {
            targetBox.set(newBox);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentBox != null && targetBox != null) {
            // Interpolate between currentBox and targetBox
            currentBox.left += (targetBox.left - currentBox.left) * 0.2f;
            currentBox.top += (targetBox.top - currentBox.top) * 0.2f;
            currentBox.right += (targetBox.right - currentBox.right) * 0.2f;
            currentBox.bottom += (targetBox.bottom - currentBox.bottom) * 0.2f;

            canvas.drawRect(currentBox, boxPaint);
            postInvalidateDelayed(16); // ~60fps
        }
    }
}
