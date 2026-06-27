# Vision Assistance App

A real-time mobile application designed to assist visually impaired users in navigating their surroundings using object detection and depth estimation.

## Overview

Most navigation aids for visually impaired users rely on simple obstacle detection. This app goes further — it not only detects objects in real time but also estimates how far they are, giving users meaningful spatial awareness through audio feedback.

## Features

- Real-time object detection using YOLOv8s
- Depth estimation using Depth Anything V2
- Live frame streaming from Android to host system via Socket.IO
- Lightweight on-device experience with server-side inference
- Audio feedback for detected objects and their distances

## Tech Stack

| Component | Technology |
|---|---|
| Mobile App | Android (Kotlin) |
| Object Detection | YOLOv8s |
| Depth Estimation | Depth Anything V2 |
| Communication | Socket.IO |
| Backend | Python |
| IDE | Android Studio |

## System Architecture
Android Device

│

│  (Live frames via Socket.IO)

▼

Python Backend Server

│

├── YOLOv8s → Object Detection

└── Depth Anything V2 → Depth Estimation

│
## How It Works

1. Android app captures live camera frames
2. Frames are streamed to the Python backend via Socket.IO
3. YOLOv8s detects objects in each frame
4. Depth Anything V2 estimates distance to detected objects
5. Results are sent back to the Android app
6. App provides audio feedback to the visually impaired user

## Project Structure
VisionAssist/

├── app/

│   ├── manifests/       # Android manifest

│   ├── kotlin+java/     # App source code

│   ├── assets/          # Static assets

│   └── res/             # UI resources

├── gradle/

├── build.gradle

└── settings.gradle

▼

Results sent back to Android → Audio Feedback to User
