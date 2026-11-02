package com.ruleweaver.ui.auth;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * The woven-thread background of the sign-in screen: slow blue threads that
 * lean toward the mouse, with small glowing dots travelling along some of them.
 */
final class WeaveCanvas extends Canvas {

    private static final int ROWS = 16;
    private static final int COLUMNS = 10;
    private static final double STEP = 14;

    private final AnimationTimer timer;
    private long startNanos;
    private double targetX = .5, targetY = .5;
    private double pointerX = .5, pointerY = .5;

    WeaveCanvas() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (startNanos == 0) {
                    startNanos = now;
                }
                pointerX += (targetX - pointerX) * .05;
                pointerY += (targetY - pointerY) * .05;
                draw((now - startNanos) / 1e9);
            }
        };
        widthProperty().addListener(o -> draw(0));
        heightProperty().addListener(o -> draw(0));
    }

    /** Pointer position as a fraction of the window (0 to 1). */
    void setPointer(double x, double y) {
        targetX = x;
        targetY = y;
    }

    void play(boolean animate) {
        if (animate) {
            timer.start();
        } else {
            draw(0);
        }
    }

    void stop() {
        timer.stop();
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double prefWidth(double height) {
        return getWidth();
    }

    @Override
    public double prefHeight(double width) {
        return getHeight();
    }

    private void draw(double t) {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setLineWidth(1.2);

        g.setStroke(Color.rgb(143, 177, 255, .14));
        for (int i = 0; i < ROWS; i++) {
            g.beginPath();
            for (double x = 0; x <= w + STEP; x += STEP) {
                g.lineTo(x, offset(i, ROWS, x, w, h, t, pointerY));
            }
            g.stroke();
        }

        g.setStroke(Color.rgb(143, 177, 255, .07));
        for (int i = 0; i < COLUMNS; i++) {
            g.beginPath();
            for (double y = 0; y <= h + STEP; y += STEP) {
                g.lineTo(offset(i, COLUMNS, y, h, w, t, pointerX), y);
            }
            g.stroke();
        }

        g.setFill(Color.rgb(180, 203, 255, .9));
        for (int i = 0; i < ROWS; i += 3) {
            double x = (t * 70 + i * 150) % w;
            double y = offset(i, ROWS, x, w, h, t, pointerY);
            g.fillOval(x - 2.6, y - 2.6, 5.2, 5.2);
        }
    }

    /** Position of thread i across the screen at distance p along it. */
    private static double offset(int i, int count, double p, double length, double span, double t, double pointer) {
        double base = (i + .5) / count * span;
        double amplitude = span * .03;
        double wave = Math.sin(p / length * 6 + t * .6 + i * .55) * amplitude * (1 + Math.sin(i));
        return base + wave + (pointer - .5) * 40;
    }
}
