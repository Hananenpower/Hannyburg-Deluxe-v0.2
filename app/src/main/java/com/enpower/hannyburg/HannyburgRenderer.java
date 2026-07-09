package com.enpower.hannyburg;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class HannyburgRenderer implements GLSurfaceView.Renderer {
    private static final float WORLD_LIMIT = 82f;

    private int program;
    private int aPosition;
    private int aNormal;
    private int uMvp;
    private int uModel;
    private int uColor;
    private int uLightDir;

    private Mesh cube;
    private Mesh sphere;
    private Mesh cylinder;

    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] mv = new float[16];
    private final float[] mvp = new float[16];

    private long lastTimeNs;
    private int screenWidth = 1;
    private int screenHeight = 1;

    private volatile float controlX = 0f;
    private volatile float controlY = 0f;
    private volatile float cameraDragX = 0f;
    private final AtomicInteger actionCounter = new AtomicInteger(0);

    private float playerX;
    private float playerZ;
    private float playerAngle;
    private float carX;
    private float carZ;
    private float carAngle;
    private boolean inVehicle;
    private int mission;
    private int evidenceCollected;
    private int bossHealth;
    private int playerHealth;
    private boolean gameOver;
    private float cameraYaw;
    private float walkBob;
    private float vehicleSpeed;
    private float damageTimer;
    private float time;

    private final float docksX = 42f;
    private final float docksZ = -45f;
    private final float garageX = -40f;
    private final float garageZ = 35f;
    private final float towerX = 48f;
    private final float towerZ = 47f;
    private final float bossStartX = 53f;
    private final float bossStartZ = 49f;
    private float bossX;
    private float bossZ;
    private float bossAngle;

    private final List<Building> buildings = new ArrayList<>();
    private final List<Npc> npcs = new ArrayList<>();
    private final List<Prop> props = new ArrayList<>();
    private final Evidence[] evidence = new Evidence[]{
            new Evidence(-55f, -32f),
            new Evidence(-24f, 50f),
            new Evidence(18f, -48f),
            new Evidence(36f, 15f)
    };

    private volatile String missionText = "";
    private volatile String statusText = "";
    private volatile String hintText = "";

    public HannyburgRenderer() {
        buildCity();
        resetGame();
    }

    public void setControls(float x, float y) {
        controlX = x;
        controlY = y;
    }

    public void addCameraDrag(float dx) {
        cameraDragX += dx;
    }

    public void requestAction() {
        actionCounter.incrementAndGet();
    }

    public synchronized void resetGame() {
        playerX = -7f;
        playerZ = 6f;
        playerAngle = 0f;
        carX = -2f;
        carZ = 2f;
        carAngle = 0.35f;
        inVehicle = false;
        mission = 0;
        evidenceCollected = 0;
        bossHealth = 8;
        playerHealth = 100;
        gameOver = false;
        cameraYaw = 0.15f;
        walkBob = 0f;
        vehicleSpeed = 0f;
        damageTimer = 0f;
        time = 0f;
        bossX = bossStartX;
        bossZ = bossStartZ;
        bossAngle = 0f;
        for (Evidence e : evidence) e.collected = false;
        updateHudText();
    }

    public String getMissionText() { return missionText; }
    public String getStatusText() { return statusText; }
    public String getHintText() { return hintText; }
    public float getControlX() { return controlX; }
    public float getControlY() { return controlY; }
    public boolean isInVehicle() { return inVehicle; }
    public boolean isGameOver() { return gameOver; }
    public boolean isVictory() { return mission >= 5; }
    public int getHealth() { return playerHealth; }
    public int getBossHealth() { return bossHealth; }
    public int getMissionNumber() { return mission; }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.025f, 0.035f, 0.055f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glDisable(GLES20.GL_DITHER);
        setupMeshes();
        setupShader();
        lastTimeNs = System.nanoTime();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        GLES20.glViewport(0, 0, screenWidth, screenHeight);
        float ratio = (float) screenWidth / (float) screenHeight;
        Matrix.perspectiveM(projection, 0, 63f, ratio, 0.1f, 250f);
    }

    @Override
    public synchronized void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = Math.min(0.033f, (now - lastTimeNs) / 1_000_000_000f);
        lastTimeNs = now;
        time += dt;

        update(dt);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        setCamera();
        drawWorld();
    }

    private void update(float dt) {
        cameraYaw -= cameraDragX * 0.0065f;
        cameraDragX = 0f;

        if (gameOver || mission >= 5) {
            updateHudText();
            return;
        }

        int actions = actionCounter.getAndSet(0);
        float lx = controlX;
        float ly = controlY;

        if (inVehicle) {
            float throttle = -ly;
            vehicleSpeed += throttle * 18f * dt;
            vehicleSpeed *= 0.965f;
            vehicleSpeed = clamp(vehicleSpeed, -9f, 18f);
            if (Math.abs(vehicleSpeed) > 0.25f) {
                carAngle -= lx * (1.1f + Math.abs(vehicleSpeed) * 0.055f) * dt;
            }
            carX += (float) Math.sin(carAngle) * vehicleSpeed * dt;
            carZ += (float) Math.cos(carAngle) * vehicleSpeed * dt;
            carX = clamp(carX, -WORLD_LIMIT, WORLD_LIMIT);
            carZ = clamp(carZ, -WORLD_LIMIT, WORLD_LIMIT);
            playerX = carX;
            playerZ = carZ;
            playerAngle = carAngle;
            cameraYaw += angleDifference(carAngle, cameraYaw) * 0.035f;
        } else {
            float len = (float) Math.sqrt(lx * lx + ly * ly);
            if (len > 0.05f) {
                float forwardX = (float) Math.sin(cameraYaw);
                float forwardZ = (float) Math.cos(cameraYaw);
                float rightX = (float) Math.cos(cameraYaw);
                float rightZ = -(float) Math.sin(cameraYaw);
                float dx = rightX * lx + forwardX * (-ly);
                float dz = rightZ * lx + forwardZ * (-ly);
                float moveLen = (float) Math.sqrt(dx * dx + dz * dz);
                float speed = 8.2f * Math.min(1f, len) * dt;
                playerX += (dx / moveLen) * speed;
                playerZ += (dz / moveLen) * speed;
                playerX = clamp(playerX, -WORLD_LIMIT, WORLD_LIMIT);
                playerZ = clamp(playerZ, -WORLD_LIMIT, WORLD_LIMIT);
                playerAngle = (float) Math.atan2(dx, dz);
                walkBob += dt * 8.5f * Math.min(1f, len);
            }
        }

        if (actions > 0) handleAction(actions);
        handleMissionZones();
        updateBoss(dt);
        updateNpcs(dt);
        updateHudText();
    }

    private void handleAction(int actions) {
        float distCar = distance(playerX, playerZ, carX, carZ);
        if (!inVehicle && distCar < 4.6f) {
            inVehicle = true;
            playerX = carX;
            playerZ = carZ;
            vehicleSpeed = 0f;
            mission = Math.max(mission, 1);
            return;
        }
        if (inVehicle && Math.abs(vehicleSpeed) < 1.5f) {
            inVehicle = false;
            playerX = carX + (float) Math.cos(carAngle) * 2.9f;
            playerZ = carZ - (float) Math.sin(carAngle) * 2.9f;
            playerAngle = carAngle;
            return;
        }
        if (mission == 4) {
            float d = distance(playerX, playerZ, bossX, bossZ);
            if (d < 5.3f) {
                bossHealth -= actions;
                if (bossHealth <= 0) {
                    bossHealth = 0;
                    mission = 5;
                }
            }
        }
    }

    private void handleMissionZones() {
        if (mission == 1 && distance(playerX, playerZ, garageX, garageZ) < 7f) {
            mission = 2;
        }
        if (mission == 2) {
            for (Evidence e : evidence) {
                if (!e.collected && distance(playerX, playerZ, e.x, e.z) < 3.8f) {
                    e.collected = true;
                    evidenceCollected++;
                }
            }
            if (evidenceCollected >= evidence.length) {
                mission = 3;
            }
        }
        if (mission == 3 && distance(playerX, playerZ, towerX, towerZ) < 8f) {
            mission = 4;
            bossX = bossStartX;
            bossZ = bossStartZ;
            bossHealth = 8;
        }
    }

    private void updateBoss(float dt) {
        if (mission != 4) return;
        float dx = playerX - bossX;
        float dz = playerZ - bossZ;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 0.35f) {
            float speed = 3.0f * dt;
            bossX += (dx / len) * speed;
            bossZ += (dz / len) * speed;
            bossAngle = (float) Math.atan2(dx, dz);
        }
        damageTimer += dt;
        if (len < 3.4f && damageTimer > 0.65f) {
            damageTimer = 0f;
            playerHealth -= 7;
            if (playerHealth <= 0) {
                playerHealth = 0;
                gameOver = true;
            }
        }
    }

    private void updateNpcs(float dt) {
        for (int i = 0; i < npcs.size(); i++) {
            Npc n = npcs.get(i);
            n.phase += dt;
            n.x = n.baseX + (float) Math.sin(n.phase * 0.5f + i) * 1.2f;
            n.z = n.baseZ + (float) Math.cos(n.phase * 0.4f + i) * 1.2f;
            n.angle += dt * 0.35f;
        }
    }

    private void updateHudText() {
        switch (mission) {
            case 0:
                missionText = "Mission 1: Find Hanny boi 123's blue sports car. Tap ACTION near the car.";
                hintText = "Left joystick moves. Drag right side to rotate camera. ACTION enters car.";
                break;
            case 1:
                missionText = "Mission 2: Drive to the green garage marker in Uptown Hannyburg.";
                hintText = "Use joystick up/down for throttle. ACTION exits when car is slow.";
                break;
            case 2:
                missionText = "Mission 3: Collect Rafialdo evidence files around the city. " + evidenceCollected + "/" + evidence.length;
                hintText = "Exit the car, walk to glowing evidence cases, then keep moving.";
                break;
            case 3:
                missionText = "Mission 4: Go to Rafialdo Tower and start the final confrontation.";
                hintText = "Follow the purple tower marker.";
                break;
            case 4:
                missionText = "Final Mission: Defeat Rafialdo. Get close and tap ACTION. Boss HP: " + bossHealth;
                hintText = "Keep moving. Rafialdo hurts Hanny boi 123 if he gets too close.";
                break;
            default:
                missionText = "Victory: Hanny boi 123 saved Hannyburg and defeated Rafialdo.";
                hintText = "Story complete. Tap RESTART to play again.";
                break;
        }
        if (gameOver) {
            statusText = "Game over. Tap RESTART.";
            hintText = "Rafialdo won this round. Try again.";
        } else if (mission >= 5) {
            statusText = "Story complete | Hannyburg is safe";
        } else {
            statusText = (inVehicle ? "Driving" : "Walking") + " | Health: " + playerHealth + " | Hannyburg Deluxe v0.2";
        }
    }

    private void setCamera() {
        float tx = inVehicle ? carX : playerX;
        float tz = inVehicle ? carZ : playerZ;
        float camDist = inVehicle ? 24f : 15.5f;
        float camHeight = inVehicle ? 12f : 8.4f;
        float targetHeight = inVehicle ? 1.4f : 1.7f;
        float camX = tx - (float) Math.sin(cameraYaw) * camDist;
        float camZ = tz - (float) Math.cos(cameraYaw) * camDist;
        Matrix.setLookAtM(view, 0, camX, camHeight, camZ, tx, targetHeight, tz, 0f, 1f, 0f);
    }

    private void drawWorld() {
        drawSkyBand();
        drawGroundAndRoads();
        drawBuildings();
        drawProps();
        drawNpcs();
        drawMissionMarkers();
        drawVehicle(carX, carZ, carAngle);
        if (!inVehicle) drawHero(playerX, playerZ, playerAngle, false);
        if (mission == 4) drawRafialdo(bossX, bossZ, bossAngle);
    }

    private void drawSkyBand() {
        drawShape(cube, 0f, 38f, 90f, 170f, 42f, 3f, 0f, 0f, 0f, 0.05f, 0.10f, 0.19f, 1f);
        drawShape(sphere, -55f, 44f, 72f, 7f, 7f, 7f, 0f, 0f, 0f, 1.0f, 0.72f, 0.28f, 1f);
    }

    private void drawGroundAndRoads() {
        drawShape(cube, 0f, -0.15f, 0f, 175f, 0.25f, 175f, 0f, 0f, 0f, 0.075f, 0.23f, 0.16f, 1f);
        drawRoad(0f, 0f, 175f, 12f, 0f);
        drawRoad(0f, 0f, 12f, 175f, 0f);
        drawRoad(0f, -43f, 175f, 9.5f, 0f);
        drawRoad(-45f, 0f, 9.5f, 175f, 0f);
        drawRoad(38f, 0f, 8f, 175f, 0f);
        drawRoad(0f, 38f, 175f, 8f, 0f);
        drawShape(cube, docksX, -0.07f, docksZ, 22f, 0.18f, 12f, 0f, 0f, 0f, 0.10f, 0.13f, 0.18f, 1f);
        drawShape(cube, garageX, -0.06f, garageZ, 18f, 0.18f, 15f, 0f, 0f, 0f, 0.12f, 0.16f, 0.16f, 1f);
    }

    private void drawRoad(float x, float z, float sx, float sz, float rot) {
        drawShape(cube, x, 0.02f, z, sx, 0.08f, sz, 0f, rot, 0f, 0.115f, 0.115f, 0.13f, 1f);
        if (sx > sz) {
            for (int i = -80; i <= 80; i += 9) {
                drawShape(cube, i, 0.09f, z, 3f, 0.035f, 0.22f, 0f, 0f, 0f, 1f, 0.89f, 0.45f, 1f);
            }
        } else {
            for (int i = -80; i <= 80; i += 9) {
                drawShape(cube, x, 0.09f, i, 0.22f, 0.035f, 3f, 0f, 0f, 0f, 1f, 0.89f, 0.45f, 1f);
            }
        }
    }

    private void drawBuildings() {
        for (Building b : buildings) {
            drawShape(cube, b.x, b.h / 2f, b.z, b.w, b.h, b.d, 0f, b.rot, 0f, b.r, b.g, b.b, 1f);
            drawShape(cube, b.x, b.h + 0.22f, b.z, b.w * 0.86f, 0.42f, b.d * 0.86f, 0f, b.rot, 0f, 0.04f, 0.52f, 0.74f, 1f);
            drawWindows(b);
        }
        drawShape(cube, towerX, 16.5f, towerZ, 10f, 33f, 10f, 0f, 0.78f, 0f, 0.26f, 0.07f, 0.36f, 1f);
        drawShape(cube, towerX, 34.0f, towerZ, 8f, 2.4f, 8f, 0f, 0.78f, 0f, 0.95f, 0.11f, 0.86f, 1f);
        drawShape(sphere, towerX, 37.2f, towerZ, 2.5f, 2.5f, 2.5f, 0f, 0f, 0f, 1.0f, 0.20f, 0.92f, 1f);
    }

    private void drawWindows(Building b) {
        int floors = Math.max(1, (int) (b.h / 3.2f));
        int cols = Math.max(1, (int) (b.w / 3.2f));
        for (int f = 0; f < floors; f++) {
            for (int c = 0; c < cols; c++) {
                if ((c + f) % 3 == 1) continue;
                float localX = -b.w * 0.35f + c * (b.w * 0.7f / Math.max(1, cols - 1));
                float y = 2.2f + f * 2.8f;
                float frontZ = -b.d * 0.505f;
                float wx = b.x + (float) Math.cos(b.rot) * localX + (float) Math.sin(b.rot) * frontZ;
                float wz = b.z - (float) Math.sin(b.rot) * localX + (float) Math.cos(b.rot) * frontZ;
                drawShape(cube, wx, y, wz, 0.85f, 0.75f, 0.08f, 0f, b.rot, 0f, 1.0f, 0.86f, 0.38f, 1f);
            }
        }
    }

    private void drawProps() {
        for (Prop p : props) {
            if (p.type == 0) drawPalm(p.x, p.z);
            else drawStreetLight(p.x, p.z);
        }
    }

    private void drawPalm(float x, float z) {
        drawShape(cylinder, x, 2.4f, z, 0.42f, 4.8f, 0.42f, 0f, 0f, 0f, 0.42f, 0.25f, 0.12f, 1f);
        drawShape(sphere, x, 5.15f, z, 1.7f, 0.7f, 1.7f, 0f, 0f, 0f, 0.10f, 0.55f, 0.22f, 1f);
        drawShape(cube, x, 5.25f, z, 4.4f, 0.32f, 0.8f, 0f, 0.4f, 0f, 0.08f, 0.47f, 0.18f, 1f);
        drawShape(cube, x, 5.25f, z, 4.4f, 0.32f, 0.8f, 0f, -0.55f, 0f, 0.08f, 0.47f, 0.18f, 1f);
        drawShape(cube, x, 5.25f, z, 0.8f, 0.32f, 4.4f, 0f, 0.65f, 0f, 0.08f, 0.47f, 0.18f, 1f);
    }

    private void drawStreetLight(float x, float z) {
        drawShape(cylinder, x, 2.5f, z, 0.18f, 5f, 0.18f, 0f, 0f, 0f, 0.55f, 0.60f, 0.65f, 1f);
        drawShape(cube, x + 1.0f, 5.1f, z, 2f, 0.16f, 0.16f, 0f, 0f, 0f, 0.55f, 0.60f, 0.65f, 1f);
        drawShape(sphere, x + 2.0f, 5.0f, z, 0.45f, 0.45f, 0.45f, 0f, 0f, 0f, 1f, 0.90f, 0.45f, 1f);
    }

    private void drawNpcs() {
        for (int i = 0; i < npcs.size(); i++) {
            Npc n = npcs.get(i);
            drawPerson(n.x, n.z, n.angle, n.r, n.g, n.b, false);
        }
    }

    private void drawMissionMarkers() {
        if (mission <= 1) drawMarker(garageX, garageZ, 0.10f, 1f, 0.48f);
        if (mission == 3) drawMarker(towerX, towerZ, 0.95f, 0.20f, 1f);
        if (mission == 4) drawMarker(bossX, bossZ, 1f, 0.05f, 0.08f);
        if (mission == 2) {
            for (Evidence e : evidence) {
                if (!e.collected) drawEvidence(e.x, e.z);
            }
        }
    }

    private void drawMarker(float x, float z, float r, float g, float b) {
        float pulse = 1f + (float) Math.sin(time * 3.2f) * 0.12f;
        drawShape(cylinder, x, 0.09f, z, 4.8f * pulse, 0.18f, 4.8f * pulse, 0f, 0f, 0f, r, g, b, 1f);
        drawShape(sphere, x, 3.4f + (float) Math.sin(time * 4f) * 0.25f, z, 1.2f, 1.2f, 1.2f, 0f, 0f, 0f, r, g, b, 1f);
    }

    private void drawEvidence(float x, float z) {
        drawShape(cube, x, 0.85f, z, 1.7f, 1.1f, 1.25f, 0f, time, 0f, 1f, 0.72f, 0.16f, 1f);
        drawShape(cube, x, 1.6f, z, 1.2f, 0.18f, 1.35f, 0f, time, 0f, 1f, 0.95f, 0.42f, 1f);
        drawShape(sphere, x, 2.25f + (float) Math.sin(time * 5f) * 0.14f, z, 0.38f, 0.38f, 0.38f, 0f, 0f, 0f, 1f, 0.9f, 0.28f, 1f);
    }

    private void drawHero(float x, float z, float angle, boolean seated) {
        drawPerson(x, z, angle, 0.10f, 0.58f, 1.0f, true);
        drawShape(sphere, x, 2.55f, z + 0.03f, 0.32f, 0.20f, 0.20f, 0f, angle, 0f, 0.03f, 0.04f, 0.06f, 1f);
    }

    private void drawRafialdo(float x, float z, float angle) {
        drawPerson(x, z, angle, 0.92f, 0.08f, 0.09f, false);
        drawShape(cube, x, 2.78f, z - 0.13f, 0.92f, 0.18f, 0.64f, 0f, angle, 0f, 0.05f, 0.02f, 0.02f, 1f);
        drawShape(sphere, x + (float)Math.sin(angle) * 0.7f, 2.53f, z + (float)Math.cos(angle) * 0.7f, 0.20f, 0.20f, 0.20f, 0f, 0f, 0f, 1.0f, 0.07f, 0.07f, 1f);
    }

    private void drawPerson(float x, float z, float angle, float shirtR, float shirtG, float shirtB, boolean hero) {
        float bob = hero ? (float) Math.sin(walkBob) * 0.06f : 0f;
        float skinR = 0.84f, skinG = 0.62f, skinB = 0.42f;
        drawShape(cylinder, x, 1.34f + bob, z, 0.72f, 1.25f, 0.48f, 0f, angle, 0f, shirtR, shirtG, shirtB, 1f);
        drawShape(sphere, x, 2.25f + bob, z, 0.54f, 0.58f, 0.54f, 0f, angle, 0f, skinR, skinG, skinB, 1f);
        drawShape(sphere, x, 2.72f + bob, z - 0.02f, 0.58f, 0.24f, 0.58f, 0f, angle, 0f, 0.05f, 0.035f, 0.035f, 1f);
        drawShape(sphere, x - 0.17f * (float)Math.cos(angle), 2.28f + bob, z + 0.17f * (float)Math.sin(angle), 0.07f, 0.07f, 0.07f, 0f, angle, 0f, 0.02f, 0.02f, 0.02f, 1f);
        drawShape(sphere, x + 0.17f * (float)Math.cos(angle), 2.28f + bob, z - 0.17f * (float)Math.sin(angle), 0.07f, 0.07f, 0.07f, 0f, angle, 0f, 0.02f, 0.02f, 0.02f, 1f);
        drawLimb(x, z, angle, -0.55f, 1.25f, -0.18f, 0.20f, 0.98f, 0.20f, shirtR * 0.8f, shirtG * 0.8f, shirtB * 0.8f);
        drawLimb(x, z, angle, 0.55f, 1.25f, 0.18f, 0.20f, 0.98f, 0.20f, shirtR * 0.8f, shirtG * 0.8f, shirtB * 0.8f);
        drawLimb(x, z, angle, -0.25f, 0.45f, 0.10f, 0.24f, 0.95f, 0.24f, 0.05f, 0.05f, 0.08f);
        drawLimb(x, z, angle, 0.25f, 0.45f, -0.10f, 0.24f, 0.95f, 0.24f, 0.05f, 0.05f, 0.08f);
        drawShape(sphere, x - (float)Math.cos(angle) * 0.34f, 0.05f, z + (float)Math.sin(angle) * 0.34f, 0.26f, 0.12f, 0.42f, 0f, angle, 0f, 0.01f, 0.01f, 0.015f, 1f);
        drawShape(sphere, x + (float)Math.cos(angle) * 0.34f, 0.05f, z - (float)Math.sin(angle) * 0.34f, 0.26f, 0.12f, 0.42f, 0f, angle, 0f, 0.01f, 0.01f, 0.015f, 1f);
    }

    private void drawLimb(float x, float z, float angle, float localX, float y, float localZ, float sx, float sy, float sz, float r, float g, float b) {
        float wx = x + (float)Math.cos(angle) * localX + (float)Math.sin(angle) * localZ;
        float wz = z - (float)Math.sin(angle) * localX + (float)Math.cos(angle) * localZ;
        drawShape(cylinder, wx, y, wz, sx, sy, sz, 0f, angle, 0f, r, g, b, 1f);
    }

    private void drawVehicle(float x, float z, float angle) {
        drawShape(cube, x, 0.72f, z, 3.3f, 0.92f, 5.0f, 0f, angle, 0f, 0.02f, 0.28f, 0.92f, 1f);
        drawShape(cube, x, 1.34f, z - (float)Math.cos(angle) * 0.38f, 2.2f, 0.85f, 2.25f, 0f, angle, 0f, 0.08f, 0.72f, 1.0f, 1f);
        drawShape(cube, x + (float)Math.sin(angle) * 1.75f, 0.88f, z + (float)Math.cos(angle) * 1.75f, 0.18f, 0.45f, 0.75f, 0f, angle, 0f, 1f, 0.95f, 0.55f, 1f);
        drawShape(cube, x - (float)Math.sin(angle) * 2.48f, 0.82f, z - (float)Math.cos(angle) * 2.48f, 0.16f, 0.35f, 0.65f, 0f, angle, 0f, 1f, 0.06f, 0.04f, 1f);
        drawWheel(x, z, angle, -1.75f, -1.35f);
        drawWheel(x, z, angle, 1.75f, -1.35f);
        drawWheel(x, z, angle, -1.75f, 1.35f);
        drawWheel(x, z, angle, 1.75f, 1.35f);
        drawShape(sphere, x, 1.85f, z + (float)Math.cos(angle) * 1.0f, 0.28f, 0.28f, 0.28f, 0f, angle, 0f, 0.95f, 0.95f, 0.95f, 1f);
    }

    private void drawWheel(float x, float z, float angle, float localX, float localZ) {
        float wx = x + (float)Math.cos(angle) * localX + (float)Math.sin(angle) * localZ;
        float wz = z - (float)Math.sin(angle) * localX + (float)Math.cos(angle) * localZ;
        drawShape(cylinder, wx, 0.38f, wz, 0.72f, 0.48f, 0.72f, 90f, angle, 0f, 0.015f, 0.015f, 0.018f, 1f);
        drawShape(cylinder, wx, 0.38f, wz, 0.38f, 0.50f, 0.38f, 90f, angle, 0f, 0.58f, 0.62f, 0.68f, 1f);
    }

    private void drawShape(Mesh mesh, float x, float y, float z, float sx, float sy, float sz, float rotXDeg, float rotYRad, float rotZDeg, float r, float g, float b, float a) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        Matrix.rotateM(model, 0, rotXDeg, 1f, 0f, 0f);
        Matrix.rotateM(model, 0, (float) Math.toDegrees(rotYRad), 0f, 1f, 0f);
        Matrix.rotateM(model, 0, rotZDeg, 0f, 0f, 1f);
        Matrix.scaleM(model, 0, sx, sy, sz);
        Matrix.multiplyMM(mv, 0, view, 0, model, 0);
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0);

        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES20.glUniform4f(uColor, r, g, b, a);
        GLES20.glUniform3f(uLightDir, -0.25f, 0.82f, 0.52f);

        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glEnableVertexAttribArray(aNormal);
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, mesh.positions);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 0, mesh.normals);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indices);
        GLES20.glDisableVertexAttribArray(aNormal);
        GLES20.glDisableVertexAttribArray(aPosition);
    }

    private void setupMeshes() {
        cube = createCube();
        sphere = createSphere(18, 24);
        cylinder = createCylinder(24);
    }

    private void setupShader() {
        String vertex = "uniform mat4 uMvp; uniform mat4 uModel; uniform vec3 uLightDir; attribute vec3 aPosition; attribute vec3 aNormal; varying float vLight; void main(){ vec3 n = normalize(mat3(uModel) * aNormal); float d = max(dot(n, normalize(uLightDir)), 0.0); vLight = 0.35 + d * 0.65; gl_Position = uMvp * vec4(aPosition, 1.0); }";
        String fragment = "precision mediump float; uniform vec4 uColor; varying float vLight; void main(){ vec3 color = uColor.rgb * vLight; gl_FragColor = vec4(color, uColor.a); }";
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        aNormal = GLES20.glGetAttribLocation(program, "aNormal");
        uMvp = GLES20.glGetUniformLocation(program, "uMvp");
        uModel = GLES20.glGetUniformLocation(program, "uModel");
        uColor = GLES20.glGetUniformLocation(program, "uColor");
        uLightDir = GLES20.glGetUniformLocation(program, "uLightDir");
    }

    private int compileShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private Mesh createCube() {
        float[] p = new float[]{
                -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,  0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
                 0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f, 0.5f,-0.5f,  0.5f, 0.5f,-0.5f,
                -0.5f,-0.5f,-0.5f, -0.5f,-0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f,-0.5f,
                 0.5f,-0.5f, 0.5f,  0.5f,-0.5f,-0.5f,  0.5f, 0.5f,-0.5f,  0.5f, 0.5f, 0.5f,
                -0.5f, 0.5f, 0.5f,  0.5f, 0.5f, 0.5f,  0.5f, 0.5f,-0.5f, -0.5f, 0.5f,-0.5f,
                -0.5f,-0.5f,-0.5f,  0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f, -0.5f,-0.5f, 0.5f
        };
        float[] n = new float[]{
                 0,0,1, 0,0,1, 0,0,1, 0,0,1,
                 0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1,
                -1,0,0, -1,0,0, -1,0,0, -1,0,0,
                 1,0,0, 1,0,0, 1,0,0, 1,0,0,
                 0,1,0, 0,1,0, 0,1,0, 0,1,0,
                 0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0
        };
        short[] idx = new short[]{
                0,1,2, 0,2,3, 4,5,6, 4,6,7, 8,9,10, 8,10,11,
                12,13,14, 12,14,15, 16,17,18, 16,18,19, 20,21,22, 20,22,23
        };
        return new Mesh(p, n, idx);
    }

    private Mesh createSphere(int lat, int lon) {
        ArrayList<Float> p = new ArrayList<>();
        ArrayList<Float> n = new ArrayList<>();
        ArrayList<Short> idx = new ArrayList<>();
        for (int y = 0; y <= lat; y++) {
            float v = (float) y / lat;
            float theta = (float) (v * Math.PI);
            float sinT = (float) Math.sin(theta);
            float cosT = (float) Math.cos(theta);
            for (int x = 0; x <= lon; x++) {
                float u = (float) x / lon;
                float phi = (float) (u * Math.PI * 2.0);
                float sx = (float) Math.cos(phi) * sinT;
                float sy = cosT;
                float sz = (float) Math.sin(phi) * sinT;
                p.add(sx * 0.5f); p.add(sy * 0.5f); p.add(sz * 0.5f);
                n.add(sx); n.add(sy); n.add(sz);
            }
        }
        for (int y = 0; y < lat; y++) {
            for (int x = 0; x < lon; x++) {
                short i0 = (short) (y * (lon + 1) + x);
                short i1 = (short) (i0 + 1);
                short i2 = (short) (i0 + lon + 1);
                short i3 = (short) (i2 + 1);
                idx.add(i0); idx.add(i2); idx.add(i1);
                idx.add(i1); idx.add(i2); idx.add(i3);
            }
        }
        return new Mesh(toFloatArray(p), toFloatArray(n), toShortArray(idx));
    }

    private Mesh createCylinder(int seg) {
        ArrayList<Float> p = new ArrayList<>();
        ArrayList<Float> n = new ArrayList<>();
        ArrayList<Short> idx = new ArrayList<>();
        for (int i = 0; i <= seg; i++) {
            float a = (float) (i * Math.PI * 2.0 / seg);
            float x = (float) Math.cos(a) * 0.5f;
            float z = (float) Math.sin(a) * 0.5f;
            p.add(x); p.add(-0.5f); p.add(z); n.add((float)Math.cos(a)); n.add(0f); n.add((float)Math.sin(a));
            p.add(x); p.add(0.5f); p.add(z); n.add((float)Math.cos(a)); n.add(0f); n.add((float)Math.sin(a));
        }
        for (int i = 0; i < seg; i++) {
            short b = (short) (i * 2);
            idx.add(b); idx.add((short)(b + 1)); idx.add((short)(b + 2));
            idx.add((short)(b + 1)); idx.add((short)(b + 3)); idx.add((short)(b + 2));
        }
        short topCenter = (short) (p.size() / 3);
        p.add(0f); p.add(0.5f); p.add(0f); n.add(0f); n.add(1f); n.add(0f);
        short bottomCenter = (short) (p.size() / 3);
        p.add(0f); p.add(-0.5f); p.add(0f); n.add(0f); n.add(-1f); n.add(0f);
        for (int i = 0; i <= seg; i++) {
            float a = (float) (i * Math.PI * 2.0 / seg);
            float x = (float) Math.cos(a) * 0.5f;
            float z = (float) Math.sin(a) * 0.5f;
            p.add(x); p.add(0.5f); p.add(z); n.add(0f); n.add(1f); n.add(0f);
            p.add(x); p.add(-0.5f); p.add(z); n.add(0f); n.add(-1f); n.add(0f);
        }
        short capStart = (short) (bottomCenter + 1);
        for (int i = 0; i < seg; i++) {
            short top0 = (short) (capStart + i * 2);
            short bot0 = (short) (capStart + i * 2 + 1);
            short top1 = (short) (capStart + (i + 1) * 2);
            short bot1 = (short) (capStart + (i + 1) * 2 + 1);
            idx.add(topCenter); idx.add(top0); idx.add(top1);
            idx.add(bottomCenter); idx.add(bot1); idx.add(bot0);
        }
        return new Mesh(toFloatArray(p), toFloatArray(n), toShortArray(idx));
    }

    private float[] toFloatArray(ArrayList<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < list.size(); i++) a[i] = list.get(i);
        return a;
    }

    private short[] toShortArray(ArrayList<Short> list) {
        short[] a = new short[list.size()];
        for (int i = 0; i < list.size(); i++) a[i] = list.get(i);
        return a;
    }

    private void buildCity() {
        float[][] spots = new float[][]{
                {-68,-68,18,9,11,0.0f},{-52,-68,12,12,10,0.2f},{-32,-68,22,10,13,-0.1f},{-8,-68,16,12,10,0.0f},{20,-68,27,13,11,0.2f},{58,-68,16,12,12,-0.2f},
                {-70,-45,11,10,9,0.2f},{-28,-43,20,11,12,0.0f},{8,-43,12,9,11,-0.2f},{32,-43,24,11,12,0.1f},{66,-43,18,12,10,-0.2f},
                {-69,-20,28,12,11,-0.1f},{-32,-18,13,10,10,0.1f},{17,-18,17,11,11,0.0f},{58,-18,25,12,13,0.2f},
                {-67,19,16,11,10,0.1f},{-25,18,26,13,12,-0.2f},{17,17,15,11,10,0.1f},{65,17,21,12,11,-0.1f},
                {-68,61,15,12,12,0.2f},{-45,61,22,10,11,0.0f},{-18,61,11,12,9,-0.2f},{16,61,19,12,12,0.2f},{59,61,29,13,13,0.0f}
        };
        for (int i = 0; i < spots.length; i++) {
            float[] s = spots[i];
            float tint = (i % 5) * 0.035f;
            buildings.add(new Building(s[0], s[1], s[2], s[3], s[4], s[5], 0.14f + tint, 0.16f + (i % 3) * 0.04f, 0.24f + (i % 4) * 0.055f));
        }
        for (int i = -70; i <= 70; i += 20) {
            props.add(new Prop(i, -7f, 1));
            props.add(new Prop(i, 7f, 1));
            props.add(new Prop(-7f, i, 1));
            props.add(new Prop(7f, i, 1));
        }
        float[][] palms = new float[][]{{-72,-9},{-62,-9},{-52,-9},{-72,9},{-62,9},{-52,9},{50,-54},{58,-54},{66,-54},{43,-35},{49,-35},{55,-35},{-50,42},{-56,48},{-62,54}};
        for (float[] p : palms) props.add(new Prop(p[0], p[1], 0));
        for (int i = 0; i < 12; i++) {
            float x = -58f + i * 10f;
            float z = (i % 2 == 0) ? 22f : -23f;
            npcs.add(new Npc(x, z, 0.65f + (i % 3) * 0.08f, 0.18f + (i % 4) * 0.10f, 0.30f + (i % 5) * 0.10f));
        }
    }

    private float distance(float ax, float az, float bx, float bz) {
        float dx = ax - bx;
        float dz = az - bz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float angleDifference(float target, float current) {
        float a = target - current;
        while (a > Math.PI) a -= Math.PI * 2f;
        while (a < -Math.PI) a += Math.PI * 2f;
        return a;
    }

    private static class Mesh {
        final FloatBuffer positions;
        final FloatBuffer normals;
        final ShortBuffer indices;
        final int indexCount;
        Mesh(float[] p, float[] n, short[] idx) {
            positions = ByteBuffer.allocateDirect(p.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            positions.put(p).position(0);
            normals = ByteBuffer.allocateDirect(n.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            normals.put(n).position(0);
            indices = ByteBuffer.allocateDirect(idx.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
            indices.put(idx).position(0);
            indexCount = idx.length;
        }
    }

    private static class Building {
        final float x, z, h, w, d, rot, r, g, b;
        Building(float x, float z, float h, float w, float d, float rot, float r, float g, float b) {
            this.x = x; this.z = z; this.h = h; this.w = w; this.d = d; this.rot = rot; this.r = r; this.g = g; this.b = b;
        }
    }

    private static class Evidence {
        final float x, z;
        boolean collected;
        Evidence(float x, float z) { this.x = x; this.z = z; }
    }

    private static class Prop {
        final float x, z;
        final int type;
        Prop(float x, float z, int type) { this.x = x; this.z = z; this.type = type; }
    }

    private static class Npc {
        final float baseX, baseZ, r, g, b;
        float x, z, angle, phase;
        Npc(float x, float z, float r, float g, float b) {
            this.baseX = x; this.baseZ = z; this.x = x; this.z = z; this.r = r; this.g = g; this.b = b;
            this.phase = x * 0.11f + z * 0.03f;
        }
    }
}
