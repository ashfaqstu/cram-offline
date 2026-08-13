# Mobile AI Innovation Sectors (Project Scopes)

Based on the Track 3: Mobile AI requirements, here are the distinct technology sectors (branches) you can choose to focus your project on. Pick the sector that aligns best with your technical interests.

---

## Sector A: Conversational AI & NLP (Local LLMs)
* **Scope:** Running text-based Large Language Models directly on the device. This involves text generation, summarization, extraction, and conversational chat interfaces.
* **Core Challenge:** Managing the memory footprint (RAM) of LLMs and optimizing the "time to first token" latency.
* **Typical Workloads:** Chatbots, document summarization, offline translation, grammar correction.
* **Relevant Frameworks:** ExecuTorch (with XNNPACK/KleidiAI), `llama.cpp`.

## Sector B: Camera Intelligence & On-Device Vision
* **Scope:** Processing image or video feeds locally without sending data to a server. 
* **Core Challenge:** Achieving high frames-per-second (FPS) processing while minimizing battery drain during continuous camera usage.
* **Typical Workloads:** Object detection, Optical Character Recognition (OCR), facial recognition, image classification, augmented reality tracking.
* **Relevant Frameworks:** MediaPipe, LiteRT (formerly TensorFlow Lite), ONNX Runtime.

## Sector C: Speech & Audio Processing
* **Scope:** Processing audio inputs (like human speech or environmental sounds) locally in real-time.
* **Core Challenge:** Accurately transcribing or translating continuous audio streams with low latency.
* **Typical Workloads:** Real-time speech-to-text (transcription), text-to-speech generation, acoustic event detection (e.g., detecting a baby crying or glass breaking).
* **Relevant Frameworks:** `whisper.cpp`, LiteRT.

## Sector D: Multimodal Edge AI
* **Scope:** Combining two or more AI domains (e.g., Vision + Text) on the edge.
* **Core Challenge:** Running multiple models simultaneously (or one Vision-Language Model) on mobile hardware without exceeding thermal and memory limits.
* **Typical Workloads:** Snapping a picture and chatting with an LLM about the image, or voice-controlled image editing.
* **Relevant Frameworks:** ExecuTorch, ONNX Runtime.

## Sector E: Accessibility & Assistive Tech
* **Scope:** Using local AI specifically to aid users with physical, visual, or cognitive disabilities.
* **Core Challenge:** Ensuring the application is highly responsive and perfectly integrated with the mobile OS accessibility features.
* **Typical Workloads:** Live captioning for the deaf, environmental audio alerts, live visual description for the blind, text simplification.
* **Relevant Frameworks:** MediaPipe, LiteRT, ExecuTorch.

## Sector F: Windows on Arm / Desktop Productivity
* **Scope:** Targeting the "Laptops and PCs" requirement in the Track 3 guidelines by building local AI tools for Windows on Arm devices.
* **Core Challenge:** Deep integration with desktop workflows while proving the efficiency of the Arm64 architecture compared to x86.
* **Typical Workloads:** Local coding copilots, offline creative tools, intelligent file search, or background agent assistants.
* **Relevant Frameworks:** ONNX Runtime, DirectML.
