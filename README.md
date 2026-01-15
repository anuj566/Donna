Donna V7: The "Juggernaut" AI Agent 🤖📱
Donna is an advanced Android Accessibility Agent designed to push the limits of on-device automation without relying on paid Cloud APIs. Unlike standard voice assistants that simply open apps, Donna acts as a Ghost in the Machine—navigating UI, scrolling lists, typing text, and clicking buttons just like a human user would.

Status: V7 (Juggernaut Edition) - Peak Android Accessibility Automation.

🚀 Features
👻 Ghost UI: No main activity. Donna lives as a lightweight, 1-pixel overlay service that is always listening but never intrusive.

🧠 Intelligent Context: Understands complex, multi-step commands in Hinglish (Hindi + English).

"WhatsApp pe Papa ko msg bhejo main aa raha hu"

"YouTube pe Arijit Singh ke gaane lagao"

🕵️‍♂️ Hunter-Seeker Navigation:

If she can't find a button, she doesn't give up. She scrolls aggressively to hunt for it.

Uses a "Heuristic Search" to find elements by Text, Content Description, or View ID.

🔁 Smart Loops: Detects if the keyboard is blocking a view, handles "Back" navigation automatically, and retries actions if the internet is slow.

🗣️ Two-Way Communication: Features Text-to-Speech (TTS) to give vocal feedback ("Searching for Papa...", "Opening YouTube...").

🇮🇳 Indian English Optimized: Fine-tuned speech recognition for Indian names (e.g., "Anu", "Neha", "Papa") and accents.

🛠️ How It Works
Donna is not a traditional app; she is an Accessibility Service.

Speech Layer: Uses Android's SpeechRecognizer with en-IN locale to convert voice to text.

Parser Brain: Analyzes the command using Keyword Logic to determine the Intent (WhatsApp, YouTube, Chrome) and Payload (Message body, Search query).

Action Layer:

Root Access (UI): Uses rootInActiveWindow to scan the current screen's XML tree.

Node Interaction: Finds AccessibilityNodeInfo elements (Buttons, EditTexts) and performs ACTION_CLICK or ACTION_SET_TEXT.

Gestures: Uses dispatchGesture to perform swipes and scrolls if elements are off-screen.

🗣️ Supported Commands
Donna supports flexible command structures. You don't need to speak like a robot.

WhatsApp Automation
"Donna WhatsApp pe [Name] ko msg bhejo [Message]"

"[Name] ko [Message] bolo WhatsApp par"

Example: "Donna WhatsApp pe Papa ko msg bhejo dinner ready hai"

YouTube Automation
"YouTube pe [Song/Topic] chalao"

"YouTube pe [Song/Topic] play karo"

Example: "Donna YouTube pe Neha Kakkar ke song lagao"

Chrome / Search
"Chrome pe [Query] search karo"

"Google pe [Query] dhundo"

Global Utilities
"Scroll down" / "Niche karo"

"Go Home" / "Ghar"

"Go Back" / "Piche"

⚙️ Installation & Setup
Since Donna uses sensitive permissions, she cannot be on the Play Store. You must build her yourself.

Clone the Repo:

Bash

git clone https://github.com/anuj566/Donna.git

Build & Run: Install the APK on your device.

Grant Permissions (Crucial):

Go to Settings > Accessibility > Installed Apps.

Find Donna V7.

Turn ON. (Allow full control).

Note: If the mic doesn't start, ensure "Display over other apps" permission is also granted if prompted.

⚠️ Privacy & Security
Offline First: Donna V7 processes logic on-device.

No Cloud Storage: Voice data is handled by Android's internal Speech Recognizer. No audio is recorded or uploaded to private servers.

Accessibility Warning: Because Donna uses Accessibility Services, she has technically capable of reading everything on your screen. This project is open-source so you can verify the code does not steal data.

🚧 Known Limitations (The "Wall")
This project represents the limit of what is possible using standard Android APIs without integrating heavy AI models (LLMs) or paid Cloud Vision APIs.

She cannot "see" images or interpret video content.

She relies on app layouts (WhatsApp/YouTube) remaining somewhat consistent. If an app completely redesigns its UI, Donna's "blind" search might fail.

👨‍💻 Author
Anuj

Vision: To build a sentient, Jarvis-like AI agent for Android.

Project Status: Completed (Hit the API Limit).
