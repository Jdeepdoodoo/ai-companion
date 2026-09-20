# MASTER PLAN: Centralized AI Shell App Architecture
**Phase 1: Solo Usage | Infrastructure: VPS (Dockerized) | Focus: Visual Agent Orchestration**

This document serves as the master blueprint for building a highly scalable, AI-driven personal assistant shell app. It is designed to run entirely on a low-cost VPS for Phase 1, using free-tier APIs and open-source tools, while maintaining a strict microservices ("segregated apps") architecture to allow seamless migration to enterprise cloud (AWS/GCP) in the future.

---

## 1. Infrastructure: The "Free & VPS-Ready" Stack

To run this on your VPS without cloud lock-in, **everything must be containerized using Docker & Docker Compose**. This ensures that migrating to AWS ECS or Google Kubernetes Engine later requires zero code changes.

*   **Compute:** Your existing VPS (minimum recommended: 2 vCPU, 4GB RAM).
*   **Reverse Proxy:** **Traefik** or **Nginx Proxy Manager** (Free, handles auto-SSL via Let's Encrypt and routes traffic between your segregated apps).
*   **LLM Intelligence (Free Tier):** 
    *   **Google Gemini API:** Excellent generous free tier (15 RPM) with massive context windows.
    *   **Groq API:** Blazing fast Llama-3 endpoints, excellent for real-time voice conversations (Free tier available).
    *   *Note on Local LLMs:* Running local LLMs (Ollama) is possible but will likely choke a standard VPS. Using free cloud APIs keeps VPS resource usage low.

---

## 2. The "Segregated Apps" Architecture

The system is split into independent, segregated modules.

### Module A: The Android Shell (Client)
*   **Tech:** Native Kotlin + Jetpack Compose.
*   **Role:** The dumb terminal. It handles OS-level permissions (Microphone, `NotificationListenerService`, `LocationManager`).
*   **Voice Flow:** Uses Android's native Speech-to-Text (free/on-device) to convert voice to text -> sends text payload to Backend API.
*   **UI Engine:** Renders JSON received from the backend into visual components (Server-Driven UI).

### Module B: The API Gateway & State Manager (Backend)
*   **Tech:** Python (FastAPI).
*   **Role:** The central nervous system.
*   **Function:** 
    *   Receives requests from the Android app (voice text, intercepted notifications).
    *   Handles authentication and sessions.
    *   Forwards the prompts to the *Agent Orchestrator* (Module C) via REST/Webhooks.
    *   Translates the Agent's JSON output into Server-Driven UI JSON for the Android app.

### Module C: The Visual Agent Organizer (Orchestrator)
*   **Tech:** **Flowise** (Node.js) OR **Langflow** (Python) OR **n8n**. 
*   *(Flowise/Langflow are open-source drag-and-drop UIs for LangChain/LangGraph).*
*   **Role:** This solves your requirement for a **"Lead-Subagent visual architecture"** and **"Rolldown style chaining"**.
*   **Function:**
    *   Provides a Web UI (accessed via your browser on your VPS IP) where you can visually drag and drop agents.
    *   **Rolldown Chaining:** You can visually plug the output of `Agent A` (e.g., Notification Parser) into the input prompt of `Agent B` (e.g., Expense Categorizer), and pass that to `Agent C` (Database Writer).
    *   **Lead/Supervisor Setup:** You can visually define a "Supervisor Agent" that looks at the prompt and decides which sub-agent chain to trigger.
    *   Exposes the final workflow as a simple REST API endpoint for Module B to call.

### Module D: The Data Layer
*   **Tech:** **PostgreSQL** (with `pgvector` extension) + **Redis**.
*   **Role:** 
    *   **Postgres:** Stores structured data (Expenses, Stocks) and Vector data (AI memory, embeddings of past conversations).
    *   **Redis:** Acts as a message queue (Celery) for background processing if the VPS gets overloaded, and caches frequent data.

---

## 3. Workflow: How Voice & Notifications Traverse the Stack

### Scenario 1: Voice Command ("Log ₹200 for lunch")
1.  **Android:** User taps mic, speaks. Android converts to text -> POST request to FastAPI.
2.  **FastAPI (Gateway):** Receives `"Log ₹200 for lunch"`. Forwards to Flowise API endpoint.
3.  **Flowise (Agent Organizer):**
    *   *Node 1 (Lead Agent):* Identifies intent -> "Expense Tracking".
    *   *Node 2 (Data Extractor Agent):* Extracts JSON `{amount: 200, category: food, context: lunch}`.
    *   *Node 3 (DB Agent):* Writes to Postgres.
    *   *Node 4 (Response Agent):* Generates friendly text: *"Done! Logged ₹200 for lunch."*
4.  **FastAPI:** Receives Flowise response, wraps it in SDUI JSON (e.g., `{type: "success_card", message: "..."}`).
5.  **Android:** Renders the success card and uses Text-to-Speech to read it out loud.

### Scenario 2: Background Notification ("₹10,000 debited for ITC shares")
1.  **Android (Background Service):** Intercepts SMS. Saves to local Room DB queue. Sends POST to FastAPI in background.
2.  **FastAPI:** Puts the task in Redis Queue (so as not to block operations).
3.  **FastAPI Worker:** Pops task, sends to Flowise API.
4.  **Flowise:** Lead Agent routes to `StockAgent`. It logs the trade in Postgres.
5.  **FastAPI:** Pushes a notification back to Android via Firebase Cloud Messaging (FCM) or WebSockets: "Stock trade auto-logged. Tap to view."

---

## 4. Open Source References for Validation

When running this plan by other AI agents, reference these GitHub repos as your foundational tools:

1.  **[Flowise](https://github.com/FlowiseAI/Flowise):** Drag & drop UI to build customized LLM flows. Perfect for visual Lead/Subagent building and exposing them as APIs.
2.  **[Langflow](https://github.com/langflow-ai/langflow):** A highly customizable Python-based alternative to Flowise. Excellent for LangChain power users.
3.  **[n8n](https://github.com/n8n-io/n8n):** Advanced workflow automation tool. Excellent for plugging agents into 3rd party APIs (e.g., pulling real-time stock prices from Yahoo Finance before logging).
4.  **[Supabase](https://github.com/supabase/supabase):** An open-source Firebase alternative powered by Postgres. You can self-host this on your VPS to handle Authentication, PostgreSQL, and instant REST APIs out of the box, completely replacing the need to write heavy CRUD code in FastAPI.

## 5. Scalability & Future Migration Path
Because everything runs in isolated Docker containers on your VPS:
*   When your VPS runs out of RAM, you simply point your `docker-compose.yml` to a managed PostgreSQL database (like AWS RDS).
*   When traffic spikes, you move the FastAPI container to Google Cloud Run (Serverless, scales to 1000s instantly).
*   The Android App never changes its endpoints; it just points to your domain name behind Cloudflare.
