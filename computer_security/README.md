# 🔐 Computer Security (Sicurezza Informatica)

Study materials for the **Computer Security** course (9 CFU, Prof. Mirco Marchetti — A.Y. 2024/2025).
Course completed with a final grade of **30/30 cum laude**.

The course is organised in three parts: offensive techniques, defensive mechanisms, and the design of
secure network architectures, each backed by hands-on lab sessions.

## 📄 Contents

*   **`course_handbook.pdf`**: A complete 109-page handbook I compiled from the course slides, covering
    all three parts plus fully worked solutions for the buffer overflow, SQL injection and XSS labs,
    and a solved mock exam.
*   **`exam_questions.pdf`**: Recurring oral exam questions with worked answers (25 pages).

## 📚 Topics Covered

**Part I — Attacks**
Software, human and organisational vulnerabilities · vulnerability life cycle and classification
(CVE, CVSS v4, CPE, OVAL, CWE, MITRE ATT&CK) · incomplete mediation and TOCTTOU · code and OS command
injection · buffer overflow and shellcode injection · SQL injection (UNION-based, blind, file access) ·
Cross-Site Scripting (reflected, stored, DOM) · OWASP Top 10 · wireless protocol vulnerabilities ·
spoofing, sniffing and fingerprinting · malware taxonomy, polymorphic/metamorphic evasion, ransomware,
cyber-weapons and APTs · DoS/DDoS and botnets.

**Part II — Defenses**
Cryptosystems, unconditional vs computational security and cryptanalysis · classical ciphers ·
symmetric cryptography (Feistel, DES, 3DES, AES) and block cipher operation modes · stream ciphers ·
hash functions and MACs · asymmetric cryptography (RSA, Diffie-Hellman) · digital signatures ·
Certification Authorities and PKI · secure protocols (IPsec, SSL/TLS) · secure application services
(HTTPS, S/MIME, PGP, SSH) · digital identity, AAA, multi-factor authentication, SSO and passkeys.

**Part III — Secure Architecture Design**
The six principles of technical countermeasures · risk-to-countermeasure mapping · web application
firewalls, VPN, NAC and Data Loss Prevention · security monitoring of network and system events ·
threat intelligence · Intrusion Detection Systems (performance metrics, analysis models,
NIDS/HIDS classification) · SIEM and SOC · cybersecurity management: checklist vs risk-based
approaches, the Deming/PDCA cycle, the ISO 27000 family, risk assessment and treatment.

## 🧪 Lab Sessions

Exercises were run against a local **OWASP Broken Web Applications** VM:

*   **Buffer overflow**: stack layout analysis with GDB, overwriting local variables, hijacking
    function pointers and return addresses.
*   **SQL injection**: vulnerability detection, authentication bypass via tautology, UNION-based
    exfiltration, blind SQLi, and the corresponding defensive fixes.
*   **XSS**: reflected and persistent payloads, plus output-encoding countermeasures.

Tooling: GDB, `sqlmap`, Burp Suite.

> The lab code and exploit scripts live in the dedicated
> [Computer Security Labs](https://github.com/NoFlyFre/computer-security-labs) repository, in line with
> the convention of keeping security course work in standalone repositories.
