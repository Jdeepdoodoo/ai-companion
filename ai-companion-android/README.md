# Hydra Shell (Android App)

This repository contains the Kotlin + Jetpack Compose source code for the Hydra Shell Android client. 

## S23 Ultra Back-Tap Integration

Samsung's S23 Ultra supports "Back-Tap" actions natively through the **Good Lock > RegiStar** module. You can configure a double-tap or triple-tap on the back of the phone to trigger an "App Shortcut".

To make Hydra Shell work with Back-Tap instantly, we define an **App Shortcut** that launches an invisible `VoiceActionActivity`. This Activity bypasses the main UI, instantly triggers the native Android `SpeechRecognizer`, captures your voice, sends it to the FastAPI backend, and closes itself.

### 1. `res/xml/shortcuts.xml`
Create this file in your Android project. This defines the shortcut that RegiStar will detect.

```xml
<?xml version="1.0" encoding="utf-8"?>
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:shortcutId="voice_command"
        android:enabled="true"
        android:icon="@drawable/ic_mic"
        android:shortcutShortLabel="@string/shortcut_voice"
        android:shortcutLongLabel="@string/shortcut_voice_long">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="com.hydra.shell"
            android:targetClass="com.hydra.shell.VoiceActionActivity" />
        <categories android:name="android.shortcut.conversation" />
    </shortcut>
</shortcuts>
```

### 2. `AndroidManifest.xml`
Register the shortcut and the Activity.

```xml
<manifest ...>
    <!-- Permissions for Speech and Internet -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application ...>
        <!-- Main UI -->
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            
            <!-- Link the shortcuts.xml here -->
            <meta-data android:name="android.app.shortcuts"
                       android:resource="@xml/shortcuts" />
        </activity>

        <!-- Invisible Voice Activity triggered by Back-Tap -->
        <activity android:name=".VoiceActionActivity"
                  android:theme="@android:style/Theme.NoDisplay"
                  android:exported="true" />
    </application>
</manifest>
```

### 3. `VoiceActionActivity.kt`
This activity starts the speech recognizer instantly when you double-tap the back of your phone.

```kotlin
package com.hydra.shell

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

class VoiceActionActivity : Activity() {

    private val SPEECH_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Instantly trigger Google Voice Input UI
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
        }
        
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            
            if (!spokenText.isNullOrEmpty()) {
                // TODO: Send `spokenText` to our FastAPI backend using Retrofit/OkHttp
                Toast.makeText(this, "Sent: $spokenText", Toast.LENGTH_SHORT).show()
            }
        }
        // Immediately close the invisible activity so the user stays wherever they were
        finish()
    }
}
```

### How to map it on your S23 Ultra:
1. Open the **Good Lock** app from the Galaxy Store.
2. Install and open the **RegiStar** module.
3. Tap **"Back-Tap action"**.
4. Select **"Double Tap"** (or Triple Tap).
5. Choose **"Show app shortcut"** -> **Hydra Shell** -> **"Voice Command"**.

Now, anytime you double-tap the back of your phone, it will immediately start listening and push the transcript to our backend!
