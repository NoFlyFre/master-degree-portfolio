# 🔐 Computer Security

![Grade](https://img.shields.io/badge/Grade-30%2F30%20cum%20laude-brightgreen)
![CFU](https://img.shields.io/badge/CFU-9-blue)
![GDB](https://img.shields.io/badge/GDB-Exploitation-red)
![OWASP](https://img.shields.io/badge/OWASP-Top%2010-000000)

*Sicurezza Informatica — 9 CFU · Prof. Mirco Marchetti · A.Y. 2024/2025 · **30/30 cum laude***

The flagship security course of the degree: three modules covering how systems get broken, how they get
defended, and how you architect a network so the first is hard and the second is measurable.

## 📄 What's here

*   **[`course_handbook.pdf`](./course_handbook.pdf)** — a 109-page handbook I wrote from the ground up
    covering all three modules, with fully worked solutions for every lab exercise and a solved mock
    exam. Written to be studied from, not just skimmed.
*   **[`exam_questions.pdf`](./exam_questions.pdf)** — 25 pages of recurring oral exam questions with
    worked answers.

## 💣 Part I — Attacks

Vulnerability life cycle and the classification ecosystem that makes it tractable: **CVE**, **CVSS v4**,
CPE, OVAL, **CWE**, **MITRE ATT&CK**.

Application-level flaws — incomplete mediation, **TOCTTOU** race conditions, code and OS command
injection. **Buffer overflow** end to end: process memory layout, stack frame anatomy, shellcode
injection, relative addressing with JMP/CALL/POP, NOP sleds, and the countermeasures that broke each
technique. **SQL injection** in all its shapes: tautology-based authentication bypass, UNION-based
exfiltration, blind SQLi, and file access leading to web server compromise. **XSS** reflected, stored
and DOM-based. The **OWASP Top 10** — IDOR, sensitive data exposure, insecure design, security
misconfiguration, broken authentication, insecure deserialization, SSRF.

Then the threat landscape: wireless protocol vulnerabilities, spoofing, sniffing and fingerprinting;
malware taxonomy by propagation and payload; polymorphic and metamorphic evasion; ransomware business
models; state-sponsored spyware and cyber-weapons; **APTs**; **DoS/DDoS** and botnet infrastructure.

## 🛡️ Part II — Defenses

Cryptosystem fundamentals, unconditional vs computational security, cryptanalysis and steganography.
Classical ciphers as a way in — substitution, frequency analysis, Vigenère, one-time pad, transposition,
product ciphers.

Modern **symmetric** cryptography: Feistel networks, **DES**, 2DES/3DES and the meet-in-the-middle
attack, **AES**, block cipher operation modes, stream ciphers and RC4. Integrity through hash functions
and **MACs**. **Asymmetric** cryptography: the key distribution problem, **RSA**, **Diffie-Hellman**.
**Digital signatures**, **Certification Authorities** and the trust model behind **PKI**.

Secure protocols in practice: **IPsec**, **SSL/TLS**, **HTTPS**, **S/MIME**, **PGP**, **SSH**.
Digital identity and **AAA** — authentication factors, two-factor and OTP, SSO and passkeys, privileged
identity management, **DLP**.

## 🏗️ Part III — Secure architecture design

The six principles behind technical countermeasures, and the mapping from risk to control.
**Web application firewalls**, network segmentation, **VPN**, **NAC**, **DLP**.

Continuous **security monitoring** of network and system events, threat intelligence feeds.
**Intrusion Detection Systems** in depth: performance metrics and the false-positive trade-off,
signature vs anomaly analysis models, NIDS/HIDS classification, and how **SIEM** and a **SOC** turn
alerts into response.

**Cybersecurity management**: checklist vs risk-based approaches, the **Deming/PDCA** cycle, the
**ISO 27000** family, risk as threat × vulnerability, the full risk management pipeline from asset
identification through the risk matrix to treatment decisions.

## 🧪 Labs

Run against a local **OWASP Broken Web Applications** VM, with `GDB`, `sqlmap` and Burp Suite:

| Lab | What I did |
|---|---|
| **Buffer overflow** | Stack layout analysis in GDB, overwriting local variables, hijacking a function pointer, overwriting the return address |
| **SQL injection** | Detection, tautology authentication bypass, UNION-based exfiltration, blind SQLi, then closing the hole with prepared statements |
| **XSS** | Reflected and persistent payloads, output encoding countermeasures |

> Lab code and exploit scripts live in
> **[computer-security-labs](https://github.com/NoFlyFre/computer-security-labs)**, following the
> convention of keeping practical security work in standalone repositories.
