# Hannyburg Deluxe Android Game

This is an original 3D Android prototype inspired by open-city action games, but it does not copy GTA assets, maps, characters, names, missions, music, or story.

## Game

- City: Hannyburg
- Hero: Hanny boi 123
- Villain: Rafialdo
- Version: Deluxe prototype v0.2

## What improved in this version

- Stylized human-like characters built from rounded 3D shapes
- Third-person camera with right-side drag rotation
- Better mobile controls
- Sports car with wheels, lights, and smoother driving
- Bigger city with roads, buildings, windows, palm trees, streetlights, and neon markers
- NPC citizens walking around
- Mission card and improved HUD
- Four mission story flow plus final Rafialdo confrontation
- Works offline as a native Android OpenGL app
- GitHub Actions workflow included to build APK online

## Controls

- Left joystick: move Hanny / drive car
- Right side drag: rotate camera
- ACTION: enter car, exit car, interact, attack Rafialdo
- RESTART: restart game

## Build APK with GitHub Actions

1. Create a new GitHub repository.
2. Upload the extracted project contents, not the ZIP file.
3. Make sure this file exists exactly here:

```text
.github/workflows/build-apk.yml
```

4. Open the Actions tab.
5. Run `Build Hannyburg APK`.
6. Download artifact `hannyburg-debug-apk`.
7. Extract it and install `app-debug.apk` on your Android phone.

## Important limitation

This is still a prototype made in pure Android/OpenGL so it can build through GitHub without needing Unity, Unreal, Godot, Blender assets, or paid asset packs. It looks much better than the first block version, but it is not a full AAA game. To reach true GTA-level graphics, the next step would be a proper Unity/Godot project with 3D character models, animations, textures, traffic AI, audio, and cutscenes.
