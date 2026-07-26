# 🌐 Distributed Systems

![Grade](https://img.shields.io/badge/Grade-30%2F30-brightgreen)
![CFU](https://img.shields.io/badge/CFU-9-blue)
![Java](https://img.shields.io/badge/Java-11%2B-ED8B00?logo=java&logoColor=white)
![RMI](https://img.shields.io/badge/Distributed-RMI-blue)

*Algoritmi Distribuiti — 9 CFU · A.Y. 2025/2026 · **30/30***

Distributed algorithms from the theory up, then a full system built on top of them.

## 💻 DTIP — Distributed Threat Intelligence Platform

**[`DTIP/`](./DTIP/)** — the final project: a peer-to-peer network for real-time sharing and analysis of
**Indicators of Compromise** across independent nodes, with no central coordinator.

**What it implements**

*   **Fidge/Mattern vector clocks** for causal ordering of IoC propagation across the mesh
*   **Ricart–Agrawala** distributed mutual exclusion for coordinated access to shared state
*   **Pluggable threat analyzers** — heuristic scoring, plus LLM-backed analysis via Gemini and Ollama
*   **Java RMI** for inter-node communication, with a TCP listener for external sensor injection
*   **Python TUI dashboard** for live network and threat visualisation
*   Test clients: a WebBridge and an AutomatedSOC simulator

**Documentation** — [`architecture.md`](./DTIP/architecture.md) ·
[`code_flow.md`](./DTIP/code_flow.md) ·
[`uml_protocols.md`](./DTIP/uml_protocols.md) ·
[`dtip_complete_guide.md`](./DTIP/dtip_complete_guide.md) (setup and operation) ·
[`project_theory_study.md`](./DTIP/project_theory_study.md) and
[`theory_reference.md`](./DTIP/theory_reference.md) (the theory behind each design choice) ·
[`exam_speech.md`](./DTIP/exam_speech.md) (project defence notes)

Source code and build instructions: [`DTIP/dtip_project/`](./DTIP/dtip_project/).

## 📄 Theory

**[`distributed_algorithms_complete_theory.pdf`](./distributed_algorithms_complete_theory.pdf)** — full
course notes covering leader election, broadcast and convergecast, distributed MST and routing, logical
and vector clocks, mutual exclusion, consensus and its impossibility results, Byzantine fault tolerance,
distributed hash tables and blockchain fundamentals.

## 🎯 Skills demonstrated

Reasoning about systems with no global clock and no trusted centre: designing protocols that stay
correct under message reordering, node failure and adversarial participants — then implementing them in
Java and showing they hold up.
