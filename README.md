# OmniTalk Edge

**Offline agentic speech translation on a mid-range Arm phone. No cloud, no NPU, no i8mm.**

> 🚧 Under active development for the Arm Create: AI Optimization Challenge 2026 (Mobile AI track).

You give it a goal — *"find out when the bus leaves, whether it has AC, and the price"* — and it
runs the conversation itself in a language you don't speak, tracks which facts it still needs,
asks its own follow-up questions, and hands you an English summary. Entirely on the CPU, in
airplane mode.

## Target hardware

Built and measured on phones most people actually own, not on flagships:

| | Dev / test | Hero |
|---|---|---|
| Device | Poco M2 Pro (2020) | Realme Narzo 50 Pro 5G (2021) |
| SoC | Snapdragon 720G | Dimensity 920 |
| CPU | 2x Cortex-A76 + 6x Cortex-A55 | 2x Cortex-A78 + 6x Cortex-A55 |
| ISA | Armv8.2-A, `asimddp`, **no i8mm / SVE / SME2** | Armv8.2-A, same |

## Status

Benchmarks, architecture notes and results are landing in `docs/`.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

**Built with Llama.** Model weights are not redistributed; `scripts/fetch_models.sh` downloads
and verifies them.
