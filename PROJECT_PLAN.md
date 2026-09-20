# Centralized AI Shell App (Multi-Agent Assistant) - Project Plan

## 1. Executive Summary
The vision is to build a highly secure, centralized Android application that acts as a "shell" serving web-based UI modules (or Server-Driven UI) for various personal tracking services (e.g., Expense Tracking, Stock Portfolio). The core of the app is a **Multi-Agent AI Assistant** accessed via voice, combined with an automated background engine that reads Android notifications (like bank SMS) to proactively trigger agentic workflows.

## 2. Market Research & Similar Implementations

Based on GitHub and Reddit research, the concept of personal AI agents and automated trackers is rapidly evolving:

### GitHub Open Source References
*   **OpenDroid / Sanna / AIOPE:** These are existing open-source Android agents that utilize Accessibility Services and system APIs to control the phone, read screens, and act as autonomous assistants.
*   **AnythingLLM Mobile:** Focuses heavily on local-first privacy, allowing models to run on-device with agentic memory.

### Notification-Based Expense Trackers
*   Apps like **FinArt, AutoMoney AI**, and **Trackex** already perform the notification-reading capability you described. They use a combination of Regex (for known bank formats) and AI to parse unstructured SMS into structured expense logs.

### Reddit Community Consensus on Multi-Agent Systems
*   **"Lead Agent" / Router Architecture:** Reddit developers strongly advise against a "flat" mesh of agents. Instead, use a **Lead Agent** (Master Router) that takes the voice command, determines intent, and delegates to sub-agents (Expense Agent, Stock Agent).
*   **Human-in-the-Loop:** For tasks like logging stocks or modifying databases, the community recommends the agent *preparing* the draft and requiring a single-tap user confirmation, rather than silent execution.

---

## 3. Architecture & Technical Approach

To achieve a production-grade, secure, and offline-capable system, the following architecture is recommended:

### A. Android Client (The Shell)
While a pure WebView showing web pages is easy, it can feel sluggish and breaks easily offline. 
*   **Recommended Approach:** **Native Kotlin/Compose Shell** with **Server-Driven UI (SDUI)**. Instead of sending HTML, the server sends JSON defining the layout, which the native app renders. This gives a 60fps native feel while remaining completely dynamic and server-controlled.
*   **NotificationListenerService:** A native background service that listens to `onNotificationPosted`. When a bank notification (e.g., "₹2500 spent") arrives, it captures the text.
*   **Location Services:** `FusedLocationProviderClient` running in a foreground service (if continuous) or triggered on specific events to geotag expenses/actions.
*   **Voice Overlay:** A global native bottom-sheet or overlay using Android's `SpeechRecognizer` (or integrating a more robust library like Whisper) to allow chat from anywhere.

### B. Offline-First & Sync Strategy
*   **The Paradox:** You want server-driven but need offline support.
*   **The Solution:** 
    1. **Local Queueing:** If offline, voice transcripts and intercepted notifications are saved to a local SQLite/Room database.
    2. **Local Regex/Small Models:** Use lightweight local regex to parse simple bank notifications offline so the user sees the actionable notification immediately.
    3. **Background Sync:** Once online, WorkManager syncs the queued items to the cloud backend for the heavy LLM agent processing.
    4. **Offline UI Cache:** The SDUI JSON layouts are cached so the user can open the app and view their latest synced data even without internet.

### C. Backend Multi-Agent System
*   **Framework:** LangChain (LangGraph) or CrewAI hosted on a secure cloud (e.g., GCP Cloud Run).
*   **Workflow:**
    1. **Input:** Receives text (voice transcript or notification body).
    2. **Router Agent:** Analyzes text. If it mentions "Taj Restaurant ₹200", routes to `ExpenseAgent`. If it mentions "ITC shares 100000", routes to `StockAgent`.
    3. **Execution Agent:** `ExpenseAgent` formats a structured payload and interacts with the Expense Database API.
    4. **Response Agent:** Formats a conversational success response and an SDUI payload to update the app screen.

---

## 4. Feature Implementation Details

### Automated Notification Logging
1.  **Intercept:** Bank sends SMS -> NotificationListenerService reads "₹2500 credit".
2.  **Parse (Local/Cloud):** The AI extracts: `Type: Credit`, `Amount: 2500`.
3.  **Actionable Notification:** The app generates a *new* local notification: "Auto-logged ₹2500 credit. [Edit Details]".
4.  **Edit Flow:** Clicking "Edit Details" opens the Voice UI overlay: "I logged ₹2500. What was this for?" -> User speaks: "Salary bonus" -> Agent updates the record.

### Voice Assistant
*   Needs to be "AI Agnostic". The backend should abstract the LLM, allowing you to swap between Gemini, OpenAI, or Claude without changing the app.

---

## 5. Security & Production Grade Considerations

This app will handle highly sensitive financial data and personal notifications.

1.  **Google Play Policies:** Requesting `BIND_NOTIFICATION_LISTENER_SERVICE` requires a strict privacy policy and justification. You must prove the data is not being misused or sold.
2.  **On-Device Processing First:** Whenever possible, parse notifications locally. If sending to the cloud, strip PII (Personally Identifiable Information) before sending to the LLM, or use highly secure enterprise LLM endpoints (like Google Cloud Vertex AI) that do not use your data for training.
3.  **End-to-End Encryption (E2EE):** If this is for personal/internal use, consider encrypting logs on the device before syncing, though this makes cloud-side search harder.
4.  **Authentication:** Biometric login (Fingerprint/FaceID) whenever the app is opened or a sensitive agentic action is taken.

---

## 6. Alternative & Better Ideas to Consider

*   **Mini-App Architecture (Super App):** Instead of standard web pages, treat your services (Expense, Stocks) as "Mini-Apps" downloaded on demand by the shell. This is how WeChat operates. It provides better offline support than WebViews.
*   **SLMs (Small Language Models) on Device:** With devices getting powerful (Snapdragon 8 Gen 3), you can run models like Gemini Nano or Llama-3-8B locally using MLC LLM. This means your "Router Agent" and basic parsing could happen 100% offline and securely, only calling the cloud for complex data retrieval.
*   **Context-Aware Triggers:** Combine Location + Notifications. E.g., The notification says "₹200 spent", the Location Service says you are at "Taj Restaurant". The app automatically merges these facts: "Logged ₹200 expense at Taj Restaurant." No voice interaction needed!
