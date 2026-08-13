link:/ https://arm-ai-optimization-challenge.devpost.com/

Welcome to the Arm AI Optimization Challenge 2026. We’re inviting developers to build and submit projects that show how AI can be optimized for Arm-powered platforms across three challenge tracks: 

Physical AI: Optimize AI for real-world systems, including robotics, embedded devices, sensors, simulation, autonomy, and edge environments.
Cloud AI: Optimize AI for scalable infrastructure, including Arm64 cloud, inference performance, frameworks, agents, and production-ready developer workflows.
Mobile AI: Optimize AI for on-device constraints, including performance, privacy, latency, battery efficiency, and local AI experiences on Arm-powered phones, tablets, and laptops.
Across all tracks, submissions should show clear optimization work and measurable improvements where possible.

Optimizations we will look for:

Model size: Reduce size on disk or in memory.
Model quality: Improve fine-tuning or output quality for a given model size.
Model speed: Improve tokens/sec, time to first token, or other relevant latency metrics.
Inference server speed: Improve throughput, latency, tokens/sec, or time to first token.
Developer experience: Improve tools, workflows, setup, documentation, or usability.
Arm-specific optimization: Implement optimizations in an existing framework, library, model, or application to run better on Arm.
Developers can use Arm Performix to get exact benchmarks of their Arm based performance and be able to clearly show their results.

Requirements
This year’s challenge has three tracks, so you can choose the path that best matches what you want to build.  For more details information for each Track, please visit the Track Details Tab.

Submissions to the Hackathon must meet the following requirements:
Include a Project built with the required developer tools and meets the above Project Requirements.
Provide a URL to your code repository for judging and testing. The repository must contain all necessary source code, assets, and instructions required for the project to be functional. The repository must be public and open source by including an open source license file. This license should be detectable and visible at the top of the repository page (in the About section).  
MIT or Apache 2.0
Include a text description that should explain the features and functionality of your Project.
Project Overview: A brief description of the project and its purpose. Also explain what makes it interesting and why it should win.
Functionality / Output: Explain what the project does and what the final output is (optimized model, migration example, scavenger deliverables, etc.).
Setup Instructions: Step-by-step instructions on how to build/run/validate on an Arm-powered device or Arm64 environment (as applicable to your track).
Optional:  Include a demonstration video of your Project. The video portion of the Submission:
should be less than three (3) minutes. Judges are not required to watch beyond three minutes 
should include footage that shows the Project functioning on the device for which it was built
must be uploaded to and made publicly visible on YouTube, Vimeo, or Youku, and a link to the video must be provided on the submission form on the Hackathon Website; and
must not include third party trademarks, or copyrighted music or other material unless the Entrant has permission to use such material.
Track 1 & Track 2: Each submission must include a copy of the project’s source code, either attached directly or linked to an open-source repository (e.g., GitHub).
Track 3: Each submission must include proof artifacts (links/screenshots) as described in the track requirements.
 
Prizes
$8,000 in prizes
Overall Winner
$3,000 in cash
1 winner
Project featured in the Arm Community Blog.

Overall Runner Up
$2,000 in cash
1 winner
Project featured in the Arm Community Blog.

Best in Category: Physical AI
$1,000 in cash
1 winner
Project featured in the Arm Community Blog.

Best in Category: Cloud AI
$1,000 in cash
1 winner
Project featured in the Arm Community Blog.

Best in Category: Mobile AI
$1,000 in cash
1 winner
Project featured in the Arm Community Blog.

Devpost Achievements
Submitting to this hackathon could earn you:


X Hackathons
 level 14

Hackathon Winner
 level 3
Judges
Avin Zarlez
Avin Zarlez
Arm Staff SW Engineer - Developer Evangelist

Michael Hall
Michael Hall
Arm Principal SW Engineer - Developer Evangelist

Gabriel Peterson
Gabriel Peterson
Arm Senior ML Engineer - Developer Evangalist

Rani Chowdary Mandepudi
Rani Chowdary Mandepudi
Software Engineer, Strategy & Ecosystems, Arm

Disha Patil
Disha Patil
Senior Developer Relations Engineer, Arm

Sicong Li
Sicong Li
Staff Sotware Engineer

Judging Criteria
Technological Implementation – 40 points
Does the submission demonstrate quality software development? Does it clearly leverage Arm-powered platforms (on-device, Arm64, efficiency-minded design)? Is the technical approach sound and well executed?
User Experience / Developer Experience – 15 points
Is it clear how to use, run, or validate the project? Is the documentation well structured? Could this be taken further or reused by other developers?
Potential Impact – 20 points
How useful is this to the developer community? Does it create reusable artifacts; optimized models, migration templates, prompt assets, or learning-ready content?
“WOW” factor – 25 points
How creative and compelling is the submission? Does it stand out in approach, usefulness, or clarity? Can it quickly capture attention and communicate value?



resources


To help spark your creativity and provide valuable resources for your projects, we’ve compiled a list of inspirations and examples. These resources will guide you in building projects on Arm-powered platforms: 

Arm Developer Program: Join to access technical documentation, development tools, and community support. https://developer.arm.com/ 
Arm Learning paths: Learn Arm architecture and AI topics through guided Learnig paths. https://learn.arm.com/ 
Arm Developer Ecosystem GitHub: Open-source projects and code examples for inspiration and reuse. https://github.com/ArmDeveloperEcosystem 
During the challenge we will run workshops and office hours. Details will be shared in the Arm Developer Program Discord, where you can reach Arm engineers and evangelists for support.

Good luck, and we can’t wait to see what you create!

---

## Track 3: Mobile AI 
Mobile AI is AI that runs locally on Arm-powered client devices such as smartphones, tablets, and laptops. Unlike Cloud AI, Mobile AI performs inference on the device in the user’s hands, enabling low-latency, private, and offline-capable experiences for real applications.

Eligible projects may include one or more of the following:

- AI inference running fully on-device in a mobile or client application, such as text, vision, speech, or multimodal use cases
- Applications optimized for mobile constraints such as model size, memory use, responsiveness, battery awareness, offline use, or time to first token
- Projects using mobile or client inference frameworks such as ExecuTorch, ONNX Runtime, LiteRT / TensorFlow Lite, MediaPipe, or similar runtimes
- Cross-platform AI applications that run on Android, iOS, or Windows on Arm devices, as long as inference runs locally on Arm-powered hardware

This track includes AI workloads for:

- Mobile phones and tablets, such as chat, summarization, translation, transcription, camera intelligence, and accessibility features
- Laptops and PCs, such as Windows on Arm systems running local copilots, creative tools, productivity features, or developer assistants
- Consumer on-device AI experiences that benefit from local execution, privacy, responsiveness, and reduced cloud dependence

Learning paths:

**On-device LLM applications (Android)**
- Build an Android chat app with Llama, KleidiAI, ExecuTorch, and XNNPACK 
- Build a customer support chatbot on Android with Llama and ExecuTorch 

**Mobile AI frameworks and runtimes**
- Run LLM inference on Android with KleidiAI, MediaPipe, and XNNPACK 

**Performance and optimization on-device**
- Measure LLM inference performance with KleidiAI and SME2 on Android