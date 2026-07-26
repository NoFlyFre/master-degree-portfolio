# Home IoT Network — Security Assessment

**Methodology and findings by vulnerability class**

*IoT Systems — Università di Modena e Reggio Emilia, A.Y. 2025/2026*

---

## Disclosure notice

This document is a **redacted, class-based rewrite** of the original assessment report. It is not a
derived copy: it was written from scratch to preserve the analytical content while removing anything
that could function as an exploit or identify a product.

Specifically, it omits vendor and model names, firmware versions, endpoint paths, port numbers,
protocol-proprietary identifiers, network addresses, device UUIDs, packet captures, and all personal
data belonging to the household.

One device family is the subject of an ongoing **coordinated vulnerability disclosure** with the
manufacturer's PSIRT. The remaining findings have not been disclosed to their vendors, which is
precisely why no identifying detail appears here. Findings are described at the level of the *design
mistake*, not the *reproduction steps*.

All testing was performed on hardware I own, on my own network.

---

## 1. Scope and methodology

The assessment covered **13 consumer IoT devices** across seven categories — smart TVs, media players
and cast devices, smart speakers, a multifunction printer, smart plugs and relays, LED controllers, and
a WiFi mesh repeater — all on a single flat subnet, the default topology of an ISP-supplied router.

The threat model is the one that matters for consumer IoT and is almost always ignored by
manufacturers: **an unprivileged attacker already on the local network**. Not a remote attacker, not a
physically present one. A guest with the WiFi password. A compromised laptop. A device that was itself
the initial foothold.

### Passive first

The first phase sent **zero packets**. Consumer IoT devices are extremely talkative: they broadcast
their presence over zero-configuration multicast discovery protocols so that companion apps can find
them without setup. In one ten-second passive capture, a single television emitted twenty discovery
announcements in under a second — advertising its device class, the services it offered, and the URL at
which to reach them.

This matters more than it appears. Multicast discovery has no notion of *who* is listening. An attacker
who never transmits produces no logs, triggers no rate limits, and still leaves the capture session
with a complete service inventory of the network.

### Then active

The second phase used per-device audit scripts written in Python, one module per device family, each
emitting structured JSON and HTML output. Every finding was verified with a working proof of concept,
then re-verified after a device reboot to distinguish transient state from persistent configuration.

### Severity

All findings were scored with **CVSS v3.1**. The attack context is uniform across every one of them:

```
AV:A / AC:L / PR:N / UI:N
```

Adjacent network, low complexity, **no privileges required**, no user interaction. That uniformity is
itself the finding: on this class of device, the entire security model rests on the assumption that the
local network is trusted. Nothing here required a technique more sophisticated than a well-formed
request.

| Band | Count | Meaning |
|---|---|---|
| **Critical** (≥ 9.0) | 3 | Code execution or persistent backdoor |
| **High** (7.0–8.9) | 12 | Significant action on the device |
| **Medium** (4.0–6.9) | 11 | Data disclosure or limited action |

**26 findings.** Every device examined produced at least one, with two exceptions covered in §3.

---

## 2. Findings by class

### C1 — Unauthenticated control plane *(High to Critical)*

The most common failure by a wide margin. Devices expose a full command interface — power state,
playback, physical actuation — reachable from any host on the LAN with no credential whatsoever.

In the worst instance the manufacturer **had built an authentication mechanism**: a companion app pairs
over a WebSocket and the device displays a PIN the user must confirm. Sensible design. But the same
command parser was also reachable over a second, plaintext transport that knew nothing about pairing.
The lock existed; a service door had been left open beside it.

*Why it happens:* the pairing gate is treated as a UX affordance rather than a security boundary, and it
is enforced at one entry point instead of at the command handler.

*Remediation:* enforce authorisation at the command handler, not per-transport. Every path that reaches
the parser must pass the same gate.

### C2 — Firmware update without provenance verification *(Critical)*

One device accepted an arbitrary firmware image over an unauthenticated update endpoint, with **no
digital signature, no checksum, and no provenance check**. An attacker on the LAN can replace the
running firmware with their own.

This is the most severe class present, because it is the only one that survives a factory reset. Every
other finding is remediated by rebooting or reconfiguring; this one persists at a layer below anything
the user can reach.

*Remediation:* signed images with on-device signature verification, anti-rollback version counters, and
authentication on the update endpoint. Non-negotiable, and a solved problem for over a decade.

### C3 — Persistent C2 via callback injection *(Critical)*

Several devices supported user-configurable **event callbacks** — fire an HTTP request when state
changes — a legitimate automation feature. With no authentication on the configuration interface, an
attacker can point those callbacks at their own server.

The result is a **persistent command-and-control channel that survives reboot and power cycle**,
implemented entirely with the vendor's own supported functionality. No malware, no exploit, nothing
anomalous in the device's behaviour: it is doing exactly what it was configured to do.

The exfiltrated data is deceptively mundane. A log of when the lights go on and off yields wake-up time,
evening return, and — most usefully to a burglar — extended absences.

*Remediation:* authenticate the configuration interface, and treat callback destinations as a
privileged setting requiring physical confirmation on the device.

### C4 — Excessive telemetry exposed on the LAN *(High)*

Diagnostic and telemetry interfaces designed for vendor support, or for enterprise fleet management,
shipped unchanged into consumer firmware and exposed without authentication.

The data available through them went far beyond diagnostics: network SSID and BSSID, service and
account state, and on one device a **complete history of jobs including document titles**. Document
titles are not metadata in any meaningful sense — bank statements, medical documents and travel
bookings are all self-describing.

*Why it happens:* one firmware base spans enterprise and consumer lines. In a managed enterprise
environment, exposing management endpoints to the local network is a defensible trade-off. Shipping the
same posture into a home network is not.

*Remediation:* separate the consumer build's exposure surface. Bind management endpoints to localhost or
require authentication; move telemetry to an authenticated outbound channel rather than an open inbound
one.

### C5 — Network topology disclosure *(High)*

A single unauthenticated management query against one infrastructure device returned the **complete
network inventory** — every connected host with address, hardware identifier, hostname and connection
state — in a few seconds.

Hostnames are the real payload. Consumer devices default to naming conventions that embed the owner's
given name, so a network map is also a household roster. In the assessed network this yielded dozens of
hostnames containing real personal names.

*Remediation:* authenticate management protocols by default, or disable them when unused. Most users do
not know these interfaces exist.

### C6 — Passive surveillance through discovery and state polling *(Medium)*

Beyond one-shot disclosure, several devices allowed **continuous polling** of state with no
authentication and no rate limiting: power state, volume, currently playing content, active
application. Repeated on a timer, this is a behavioural log.

A related channel needs no interaction with the device at all: unencrypted resolution queries leaving
the network reveal which broadcaster or streaming service is being consumed, minute by minute.

*Remediation:* rate-limit and authenticate state queries; encrypt name resolution at the gateway.

### C7 — Replay attacks with no nonce *(High)*

One protocol family used a fixed command encoding with **no nonce, sequence number or timestamp**. A
captured command replays indefinitely. Even where a weak obfuscation layer existed, it was static
across sessions, so capture-and-replay required no cryptographic work at all.

*Why it happens:* backward compatibility. Adding a nonce breaks older app versions, and vendors
consistently resolve that trade-off in favour of compatibility.

*Remediation:* sequence numbers with server-side replay rejection, versioned at the protocol level so
old clients degrade rather than force the weakness to persist.

### C8 — Configuration and credential-adjacent disclosure *(Medium)*

Unauthenticated configuration dumps returned network credentials context, service endpoints, and on one
device **the geographic coordinates of the installation** — stored for a scheduling feature that needs
sunrise and sunset times, and readable by anyone on the LAN.

*Remediation:* derive location-dependent features from a coarse timezone rather than precise
coordinates, and never return configuration state without authentication.

### C9 — Unauthenticated denial of service *(High)*

Reboot and reset endpoints reachable without credentials. Individually a nuisance; combined with C2 it
becomes the delivery mechanism for a firmware replacement, and combined with a security device it
becomes a way to blind a system before acting.

---

## 3. What was done right

Two findings from the negative results are worth more than the positive ones, because they establish
that the failures above are **choices, not constraints**.

**A €10 smart bulb** implemented a proprietary protocol with mutual authentication, a challenge-response
handshake deriving session keys from account credentials and exchanged nonces, AES encryption for every
subsequent command, HMAC integrity protection, and monotonic sequence numbers rejecting replays. An
attacker on the same network can neither issue commands nor decrypt traffic. Cost was evidently not the
obstacle for the vendors that got this wrong.

**Two premium-brand devices** — a television and a family of smart speakers — required authenticated,
encrypted channels for all control operations, leaking only minor device metadata. Larger vendors with
mature product security programmes performed measurably better, which is unsurprising but worth
documenting.

The uncomfortable corollary: within the assessed set, security correlated with **vendor maturity**, not
with device price. Two LED controllers at nearly identical price points had opposite security postures.

---

## 4. Mapping to the course framework

### 4.1 The seven privacy threats

The course frames IoT privacy through seven threat categories. All seven were observed:

| Threat | Observed as |
|---|---|
| **Identification** | Hostnames embedding personal names; document titles; device names and hardware identifiers |
| **Tracking** | State polling on a timer; real-time application and channel identification |
| **User profiling** | Injected callbacks logging every actuation, yielding daily routine and absence patterns |
| **Inventory attack** | Full network inventory in seconds, plus network identifiers and installation coordinates |
| **Interaction** | Remote actuation of televisions and lighting; a physical printed artefact |
| **Lifecycle transition** | Arbitrary firmware replacement; callbacks persisting across reboot and power cycle |
| **Linkage** | Correlation of the above into a household behavioural profile |

**Linkage is the finding that matters.** The course teaches an aggregation rule: attributes individually
classified as *non-sensitive* become *sensitive* in combination. A hardware address is non-sensitive. A
lamp's switching time is non-sensitive. A device model is non-sensitive. A hostname is at most personal.

But address + schedule + model + hostname + document history + viewing history yields a detailed profile
of an entire family's habits — from findings that a triage process scoring each one individually would
have classified as Medium and deprioritised. This is the strongest argument in the whole assessment for
why "low severity, low priority" is the wrong reflex in IoT.

### 4.2 OWASP IoT Top 10

Eight of the ten categories were observed: weak or absent passwords, insecure network services,
insecure ecosystem interfaces, lack of secure update mechanism, insecure or outdated components,
insufficient privacy protection, insecure data transfer and storage, and insecure default settings.

---

## 5. Conclusions

Not one finding required a novel technique. There was no memory corruption, no cryptanalysis, no
timing side channel. Twenty-six issues, three of them critical, all reachable with well-formed requests
against interfaces the vendors deliberately built and left open.

The single shared root cause is an **assumption of local network trust** that stopped being reasonable
the moment homes acquired a dozen internet-connected devices of wildly differing quality. In a flat
subnet, the security of every device is bounded by the weakest one — and the weakest one accepts
unsigned firmware.

Three conclusions I would defend beyond this specific network:

1. **Network segmentation is the only user-side control that generalises.** Every other mitigation is
   per-device and depends on a vendor caring. A separate IoT VLAN limits the damage regardless.
2. **Passive discovery is undervalued as an attack surface.** The most complete reconnaissance in this
   assessment cost zero transmitted packets and would appear in no log.
3. **Aggregate before you triage.** Severity scoring per finding systematically undercounts privacy
   impact, because the impact emerges from correlation and no individual score can represent it.

---

*Redacted for publication. The unredacted report, proof-of-concept code, packet captures and evidence
are not distributed.*
