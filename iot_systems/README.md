# 📡 IoT Systems

![Grade](https://img.shields.io/badge/Grade-30%2F30%20cum%20laude-brightgreen)
![CFU](https://img.shields.io/badge/CFU-6-blue)
![Python](https://img.shields.io/badge/Python-3776AB?logo=python&logoColor=white)
![Wireshark](https://img.shields.io/badge/Wireshark-1679A7?logo=wireshark&logoColor=white)
![CVSS](https://img.shields.io/badge/CVSS-v3.1-C1121F)

*IoT Systems — 6 CFU · Prof. Luca Bedogni · A.Y. 2025/2026 · **30/30 cum laude***

Two halves: the architecture of IoT systems — edge, fog and cloud, low-power networking, context-aware
computing — and a hands-on security assessment of a real home IoT network.

## 🔍 Security assessment

**[`security_assessment/methodology_and_findings.md`](./security_assessment/methodology_and_findings.md)**

A vulnerability assessment of **13 consumer IoT devices** on a single flat subnet: smart TVs, media
players, speakers, a multifunction printer, smart plugs and relays, LED controllers and a mesh repeater.
Threat model: an unprivileged attacker already on the local network.

**26 findings** — 3 Critical, 12 High, 11 Medium — every one of them scored `AV:A/AC:L/PR:N` under
CVSS v3.1. That uniformity is the result: on this class of device the entire security model rests on
trusting the local network, and nothing found required a technique more sophisticated than a well-formed
request.

The published document groups findings **by vulnerability class** rather than by device: unauthenticated
control planes, firmware updates without provenance verification, persistent C2 through callback
injection, over-exposed telemetry, network topology disclosure, passive surveillance via discovery
protocols, replay without nonces, configuration disclosure, and unauthenticated denial of service. Each
class covers the design mistake, why vendors keep making it, and the remediation.

It also maps the findings onto the course's **seven privacy threats** framework and the **OWASP IoT
Top 10**, and argues the point I think matters most: the aggregation rule means individually low-severity
findings compose into a high-severity privacy outcome, so per-finding triage systematically
under-prioritises IoT privacy.

> ### ⚠️ On what is published here
>
> The document is a **redacted, class-based rewrite** — written from scratch, not derived from the
> original report. It contains no vendor or model names, firmware versions, endpoint paths, port
> numbers, proprietary protocol identifiers, network addresses, packet captures, or household data.
>
> One device family is under an active **coordinated vulnerability disclosure** with the manufacturer's
> PSIRT. The other findings have not been disclosed to their vendors — which is exactly why nothing
> identifying appears here. Proof-of-concept code, captures and evidence are not distributed.
>
> All testing was conducted on hardware I own, on my own network.

## 📗 Course theory

**[`course_theory.typ`](./course_theory.typ)** — my complete write-up of the course theory, in Typst.
Eleven chapters, from the IoT scenario through to privacy regulation. Compile with:

```bash
typst compile course_theory.typ
```

This is Part I of my exam notes. Part II — the technical deep dive on the assessment — is deliberately
omitted, for the reasons in the notice above.

## 📚 Topics covered

**IoT architecture** — the IoT scenario and its use cases; **ubiquitous computing** and
**context-aware systems**, with the acquisition → organisation → discrimination → adaptation loop;
edge, fog and cloud placement trade-offs.

**Hardware and networking** — IoT boards and their constraints; **LPWAN** technologies and where each
fits; communication protocols across the stack and the trade-off between energy budget, range and
throughput.

**Computation and intelligence** — **task offloading** decisions between device, edge and cloud;
time-series forecasting with **ARIMA** and **reinforcement learning** for adaptive policies; running
**AI on constrained devices**; visual orchestration with **Node-RED**.

**Privacy** — the seven privacy threats in IoT environments, data sensitivity classification
(sensitive / personal / non-sensitive / uncertain), and the aggregation rule that makes combinations of
harmless attributes harmful.

**Security practice** — VA/PT methodology for embedded and consumer devices: passive discovery before
active probing, per-device audit tooling, evidence handling, CVSS v3.1 scoring, and coordinated
disclosure.

## 🎯 Skills demonstrated

Taking a security assessment from packet capture through to a written report a vendor PSIRT will engage
with — including the part that isn't technical: deciding what can be published, when, and in how much
detail. The redaction in this directory is itself part of the work.
