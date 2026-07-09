package com.enpower.hannyburg;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class GameSurfaceView extends GLSurfaceView {
    private final HannyburgRenderer renderer;
    private int movePointerId = -1;
    private int cameraPointerId = -1;
    private float lastCameraX = 0f;

    public GameSurfaceView(Context context, HannyburgRenderer renderer) {
        super(context);
        this.renderer = renderer;
        setEGLContextClientVersion(2);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        int action = event.getActionMasked();
        int index = event.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int pointerId = event.getPointerId(index);
            float x = event.getX(index);
            float y = event.getY(index);
            if (hitAction(x, y, width, height)) {
                renderer.requestAction();
            } else if (hitRestart(x, y, width, height)) {
                renderer.resetGame();
            } else if (x < width * 0.48f && y > height * 0.32f) {
                movePointerId = pointerId;
            } else if (x > width * 0.46f) {
                cameraPointerId = pointerId;
                lastCameraX = x;
            }
        }

        if (action == MotionEvent.ACTION_MOVE) {
            updateMove(event, width, height);
            updateCamera(event);
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            int pointerId = event.getPointerId(index);
            if (pointerId == movePointerId) {
                movePointerId = -1;
                renderer.setControls(0f, 0f);
            }
            if (pointerId == cameraPointerId) {
                cameraPointerId = -1;
            }
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                movePointerId = -1;
                cameraPointerId = -1;
                renderer.setControls(0f, 0f);
            }
        }
        return true;
    }

    private void updateMove(MotionEvent event, int width, int height) {
        if (movePointerId < 0) return;
        int i = event.findPointerIndex(movePointerId);
        if (i < 0) return;
        float joyX = width * 0.17f;
        float joyY = height * 0.75f;
        float joyR = Math.max(92f, Math.min(width, height) * 0.13f);
        float dx = event.getX(i) - joyX;
        float dy = event.getY(i) - joyY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 5f) {
            renderer.setControls(0f, 0f);
            return;
        }
        float scale = Math.min(1f, len / joyR);
        renderer.setControls((dx / len) * scale, (dy / len) * scale);
    }

    private void updateCamera(MotionEvent event) {
        if (cameraPointerId < 0) return;
        int i = event.findPointerIndex(cameraPointerId);
        if (i < 0) return;
        float x = event.getX(i);
        float dx = x - lastCameraX;
        lastCameraX = x;
        renderer.addCameraDrag(dx);
    }

    private boolean hitAction(float x, float y, int width, int height) {
        float actionX = width * 0.86f;
        float actionY = height * 0.76f;
        float actionR = Math.max(84f, Math.min(width, height) * 0.12f);
        float dx = x - actionX;
        float dy = y - actionY;
        return dx * dx + dy * dy <= actionR * actionR;
    }

    private boolean hitRestart(float x, float y, int width, int height) {
        float restartX = width * 0.91f;
        float restartY = height * 0.14f;
        float restartR = Math.max(58f, Math.min(width, height) * 0.075f);
        float dx = x - restartX;
        float dy = y - restartY;
        return dx * dx + dy * dy <= restartR * restartR;
    }
}
