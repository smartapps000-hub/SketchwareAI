package com.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.9.7: draws a logic-editor block the way it looks on the canvas, for the guide sheet: a rounded body in the
 * category colour with a top notch and a bottom bump (the stacking grooves), the label text, and a rounded slot for
 * every parameter. The block string is the human text with each slot written as {@code [hint]} (e.g.
 * {@code "download file [link] to folder [MyApp] notify [true]"}). Plain Android drawing, so layoutlib renders it.
 */
public final class BlockShapeView extends View {

    private final int color;
    private final List<Object> tokens = new ArrayList<Object>();   // String = word, Slot = parameter
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private final float padX;
    private final float padY;
    private final float gap;
    private final float lineHeight;
    private final float notch;      // connector size
    private final float radius;

    private final List<Line> lines = new ArrayList<Line>();
    private float bodyWidth;
    private float bodyHeight;

    private static final class Slot {
        final String hint;
        float width;

        Slot(String hint) {
            this.hint = hint;
        }
    }

    private static final class Piece {
        final Object token;   // String or Slot
        final float x;
        final float width;

        Piece(Object token, float x, float width) {
            this.token = token;
            this.x = x;
            this.width = width;
        }
    }

    private static final class Line {
        final List<Piece> pieces = new ArrayList<Piece>();
        float width;
    }

    public BlockShapeView(Context context, String block, int color) {
        super(context);
        this.color = color;
        density = context.getResources().getDisplayMetrics().density;
        padX = 14 * density;
        padY = 10 * density;
        gap = 5 * density;
        notch = 6 * density;
        radius = 7 * density;

        fill.setColor(color);
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(13.5f * density);
        lineHeight = text.descent() - text.ascent();
        // the slot is a lighter tint of the body so it reads as an input, dark text on it
        slotFill.setColor(tint(color));
        slotText.setColor(0xDE000000);
        slotText.setTypeface(Typeface.DEFAULT_BOLD);
        slotText.setTextSize(12.5f * density);

        parse(block);
    }

    private static int tint(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, hsv[1] * 0.35f);
        hsv[2] = Math.min(1f, hsv[2] * 1.35f + 0.2f);
        return Color.HSVToColor(0xFF, hsv);
    }

    private void parse(String block) {
        int i = 0;
        StringBuilder word = new StringBuilder();
        while (i < block.length()) {
            char c = block.charAt(i);
            if (c == '[') {
                flush(word);
                int end = block.indexOf(']', i);
                if (end < 0) end = block.length();
                tokens.add(new Slot(i + 1 <= end ? block.substring(i + 1, end) : ""));
                i = end + 1;
            } else if (c == ' ') {
                flush(word);
                i++;
            } else {
                word.append(c);
                i++;
            }
        }
        flush(word);
    }

    private void flush(StringBuilder word) {
        if (word.length() > 0) {
            tokens.add(word.toString());
            word.setLength(0);
        }
    }

    private float pieceWidth(Object token) {
        if (token instanceof Slot) {
            Slot slot = (Slot) token;
            slot.width = Math.max(18 * density, slotText.measureText(slot.hint) + 16 * density);
            return slot.width;
        }
        return text.measureText((String) token);
    }

    private void layout(float maxBodyWidth) {
        lines.clear();
        Line line = new Line();
        float x = padX;
        float inner = maxBodyWidth - padX;
        for (Object token : tokens) {
            float w = pieceWidth(token);
            if (x > padX && x + w > inner && !line.pieces.isEmpty()) {
                line.width = x - gap;
                lines.add(line);
                line = new Line();
                x = padX;
            }
            line.pieces.add(new Piece(token, x, w));
            x += w + gap;
        }
        line.width = x - gap;
        lines.add(line);

        bodyWidth = 0;
        for (Line l : lines) bodyWidth = Math.max(bodyWidth, l.width + padX);
        bodyWidth = Math.max(bodyWidth, 60 * density);
        bodyHeight = padY * 2 + lines.size() * lineHeight + (lines.size() - 1) * (4 * density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED || maxWidth <= 0) maxWidth = (int) (320 * density);
        layout(maxWidth - notch);
        int width = (int) Math.ceil(Math.min(bodyWidth, maxWidth - notch)) + (int) Math.ceil(notch);
        int height = (int) Math.ceil(bodyHeight + notch);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float left = 0;
        float top = 0;
        float right = bodyWidth;
        float bottom = bodyHeight;
        float tabX = left + 20 * density;   // the connector sits near the left, as on the canvas

        Path path = new Path();
        path.moveTo(left + radius, top);
        // top edge with a concave notch (where the block above plugs in)
        path.lineTo(tabX, top);
        path.lineTo(tabX + notch, top + notch);
        path.lineTo(tabX + 2 * notch, top + notch);
        path.lineTo(tabX + 3 * notch, top);
        path.lineTo(right - radius, top);
        path.quadTo(right, top, right, top + radius);
        path.lineTo(right, bottom - radius);
        path.quadTo(right, bottom, right - radius, bottom);
        // bottom edge with a convex bump (what the next block plugs onto)
        path.lineTo(tabX + 3 * notch, bottom);
        path.lineTo(tabX + 2 * notch, bottom + notch);
        path.lineTo(tabX + notch, bottom + notch);
        path.lineTo(tabX, bottom);
        path.lineTo(left + radius, bottom);
        path.quadTo(left, bottom, left, bottom - radius);
        path.lineTo(left, top + radius);
        path.quadTo(left, top, left + radius, top);
        path.close();
        canvas.drawPath(path, fill);

        float baseShift = -text.ascent();
        for (int li = 0; li < lines.size(); li++) {
            Line line = lines.get(li);
            float y = padY + li * (lineHeight + 4 * density);
            for (Piece piece : line.pieces) {
                if (piece.token instanceof Slot) {
                    Slot slot = (Slot) piece.token;
                    RectF chip = new RectF(piece.x, y + 1 * density, piece.x + piece.width, y + lineHeight - 1 * density);
                    float r = chip.height() / 2f;
                    canvas.drawRoundRect(chip, r, r, slotFill);
                    float tw = slotText.measureText(slot.hint);
                    canvas.drawText(slot.hint, chip.centerX() - tw / 2f,
                            chip.centerY() - (slotText.ascent() + slotText.descent()) / 2f, slotText);
                } else {
                    canvas.drawText((String) piece.token, piece.x, y + baseShift, text);
                }
            }
        }
    }
}
