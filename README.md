# BirdVue_AI “Gains” — Hackathon Upgrade 🐦🚀

**TL;DR**: We took Daniel's existing college bird-sighting Android app and **decked it out with AI**:

1. **Hugging Face** image classifier to identify species,
2. **OpenAI** micro-summaries & Q\&A,
3. **Amazon Rekognition** image verification and content moderation (is it a bird?),
4. **Amazon Lex** chatbot with Lambda fulfillment.
   We expose clean APIs via **AWS App Runner** and front some calls with **API Gateway**. Logs flow to **CloudWatch**.

---

## Why “Gains”?

Because every sighting should give you **knowledge gains**: fast identifications, crisp descriptions, and focused answers that make you a better birder with each upload. Our app also **gained multiple advanced features** through AI services and AI assisted development workflows and tools.

---

## How We Meet the Judging Criteria

### KNOWLEDGE

* We turn raw photos into **actionable knowledge**: content moderation → verified bird → bird name and concise summary → focused Q\&A.
* Lex + `/qa` keep context (species, summary) to deliver **better responses to user questions about community bird sightings** (confirm ID, where to find, diet, voice).

### DESIGN

* Clean separation of concerns: **Classify/Verify** service vs **Chat/Q\&A** service; both stateless behind App Runner.
* **Textbox-safe** outputs (no truncation mid-sentence), consistent JSON, and mobile-friendly endpoints.
* Smart defaults + caching for snappy UX.
* Modern material design language.
* Included dark mode with the use of themes.

### MODERNISATION

* We **modernized an existing app** with managed AI (HF, Rekognition, OpenAI, Lex) and infra-as-service (App Runner, API Gateway).
* Concrete real-world value: faster bird identification, guardrails against bad uploads, autofilling bird data for user uploads, and teachable chats.

### GREENFIELDS

* A fresh **Lex + Q\&A** pattern blends rules (utterances/slots) with **LLM fallback**, opening paths for richer domain bots.
* Extensible pipeline for future features (sound ID, geofenced tips, community embeddings).
* Our app completely changes how users interact with birdwatching. Users can rely on AI to fill gaps in knowledge, and quickly access new information when uploading or exploring bird posts.

### SPEED / EXECUTION

* Built and deployed **fast** with managed services (no servers to wrangle).
* Caching, retries, and uniform responses keep performance responsive and integration simple.
* Our app temporarily caches api calls with the same bird name, ensuring less calls to the api, lower cost, and greater responsiveness
* Gaining knowledge and identifying birds is made much faster with the use of our app. 

---

## Non-AI related features added during the hackathon

* Updated dependencies and versions
* Fixed bugs
* Added ability to search the maps
* Fixed UI display issues
* Added usernames to the community posts
* Other

---

## Other Repos

* **Android-facing Bird Chat (App Runner, OpenAI)**: [https://github.com/janro13806/BirdChatVoel](https://github.com/janro13806/BirdChatVoel)
  Provides `/ask` and `/qa` endpoints for summaries and Q\&A.
* **Bird Classification & Validation (App Runner, HF + Rekognition)**: [https://github.com/janro13806/BirdApp](https://github.com/janro13806/BirdApp)
  Provides `/predict` (Hugging Face image classifier) and `/VerifyBirdImage` (Rekognition label check for content moderation).

---

## Architecture Diagram

<img width="3368" height="1527" alt="image" src="https://github.com/user-attachments/assets/1e87a287-6477-4f9d-b09d-8aac26a71d23" />

---

## AI Features Gained (4 pillars)

### 1) Hugging Face (Bird Classifier)

* **Endpoint**: `POST /predict` (multipart `file`)
  Returns `predicted_class`, `confidence`, and `topK`.
* Handles model cold starts with retries & backoff.

### 2) OpenAI (Summaries & Q\&A)

* **Endpoint**: `GET /ask?prompt=<species or free text>`
  Returns a **3-line** “fits-textbox” summary (no mid-sentence truncation).
* **Endpoint**: `GET /qa?species=<name>&q=<question>&summary=<optional>`
  Returns a **concise, direct** answer (≤3 lines, ≤200 chars).

### 3) Amazon Rekognition (Content Moderation)

* **Endpoint**: `POST /VerifyBirdImage` (multipart `file`)
  Uniform JSON with **`ok` (true/false), `label`, `confidence`, `message`, `error`**.
  Always **200**—your client logic keys off `ok`, not HTTP code.

### 4) Amazon Lex (Chatbot)

* **Bot**: *BirdBot* (English US)
* **Intents**: `BirdInfo` (+ fallback).
* **Fulfillment**: **BirdBotFulfillment Lambda** calls **/qa** with the current `species` and the user’s question.
* **Proxy** (optional for mobile): API Gateway → Lambda → `lex:RecognizeText`.

---

## Ai features End-to-End Flow

1. **Android app** uploads a photo → `POST /predict` → gets species + confidence.
2. **Verify on submit and autofill** → `POST /VerifyBirdImage` → `ok:true|false` to guard uploads.
3. **Get bird name and description on autofill** → `GET /ask?prompt=<species>` → 3 tidy lines for the UI textbox.
4. **Chat** → Android calls API Gateway `/lex/text/{sessionId}` with `text` and `sessionState.sessionAttributes` (`species`, `summary`).

   * Lex → BirdBotFulfillment Lambda → **/qa** → returns tailored answer.
   * If utterance matches a known pattern (e.g., “what does it eat”), we still pass a targeted question to **/qa** for consistent answers.

---

## Logging & Monitoring

* **CloudWatch Logs**: App Runner service logs, both Lambdas.
* **Lex conversation logs**: Enable on the **Alias** to capture intents, slots, and returned messages (great for demo/traceability).
* **API Gateway**: Execution logs for the `/lex/text/{sessionId}` method.

---

## Security Notes

* Rekognition verify returns **200** with `ok:false` to avoid surfacing infra to clients.
* API keys/usage plans can be applied on API Gateway resources (e.g., Lex proxy) as needed.
* No secrets in code; use App Runner env vars/Secrets Manager and Android local properties.

---

## Credits

Team Voël 🐦 — thanks to the hackathon organizers and the open-source/model providers!

Contact us for local files and other things if you would like to run the app.
