# 🎓 Master's Degree Portfolio — Computer Science @ Unimore

![Java](https://img.shields.io/badge/Java-ED8B00?logo=java&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?logo=python&logoColor=white)
![C++](https://img.shields.io/badge/C%2B%2B-00599C?logo=cplusplus&logoColor=white)
![ROS](https://img.shields.io/badge/ROS-22314E?logo=ros&logoColor=white)
![Security](https://img.shields.io/badge/Security-Offensive%20%26%20Defensive-C1121F)

The coursework, projects and study material from my **Laurea Magistrale in Informatica** at the
University of Modena and Reggio Emilia, with a specialisation built around **computer security** and
**distributed systems**.

Each directory is a self-contained course record: what I built, the techniques behind it, and the
written reports. Security course *labs* live in standalone repositories, listed at the bottom.

## 📊 Courses in this repository

| Course | CFU | Grade | What's inside |
|---|---|---|---|
| [Kernel Hacking](./kernel_hacking/) | 6 | **30/30 cum laude** | Two upstream Linux patches — an XFS lockdep fix for a syzkaller bug, and a WireGuard KCSAN data race — with the assembly-level analysis behind them |
| [Computer Security](./computer_security/) | 9 | **30/30 cum laude** | 109-page course handbook, exam questions, buffer overflow / SQLi / XSS labs |
| [Distributed Systems](./distributed_systems/) | 9 | **30/30** | Full theory notes + **DTIP**, a P2P threat intelligence platform in Java RMI |
| [Privacy & Data Protection](./privacy_data_protection/) | 6 | **30/30** | GDPR, DPIA, cybercrime notes and a regulatory quick-reference table |
| [Autonomous Driving](./autonomous_driving/) | 6 | **27/30** | 6 assignments: vehicle dynamics, MPC/Stanley/Pure Pursuit, PCL clustering, Kalman & Particle Filters |
| [Cryptography Algorithms](./applied_cryptography/) | 6 | **23/30** | Symmetric/asymmetric algorithm summaries and exam question bank |
| [CS Tutoring](./general_computer_science_tutoring/) | — | — | 9 lecture decks I authored as an academic tutor |

### Courses without a directory here

| Course | CFU | Grade | Where it lives |
|---|---|---|---|
| Sviluppo di Software Sicuro | 9 | **30/30** | [secure-software-development](https://github.com/NoFlyFre/secure-software-development) |
| IoT Systems | 6 | **30/30 cum laude** | Project material held back pending a coordinated vulnerability disclosure |
| Metodologie di Sviluppo Software | 6 | **30/30** | No archived material |
| High Performance Computing | 9 | **27/30** | No archived material |

## 🏆 Highlights

**[Kernel Hacking — two upstream Linux patches](./kernel_hacking/)**
A fix for syzkaller bug `5eb0d61dfb76ca12670c`: an inconsistent lock state on `inode->i_lock` in XFS,
where `igrab()` took a plain spinlock on a path reachable from softirq. Backed by a recursive expansion
of `igrab()` vs `ihold()` down to x86-64 assembly and a proof that `i_count > 0` holds at every call
site. Plus a WireGuard `READ_ONCE`/`WRITE_ONCE` annotation closing a KCSAN-reported data race.

**[DTIP — Distributed Threat Intelligence Platform](./distributed_systems/DTIP/)**
A peer-to-peer network for real-time sharing and analysis of Indicators of Compromise. Implements
Fidge/Mattern vector clocks, Ricart–Agrawala distributed mutual exclusion, pluggable threat analyzers
(heuristic, Gemini, Ollama) and a Python TUI dashboard on top of Java RMI. Ships with UML protocol
models, an architecture document and a full operator guide.

**[Autonomous Driving — Perception & Control](./autonomous_driving/)**
Six graded assignments in C++ and Python: a Frenet trajectory planner with static obstacle avoidance,
an MPC controller benchmarked against Pure Pursuit and Stanley at up to 25 m/s, a PCL/RANSAC Euclidean
clustering pipeline for LiDAR object detection, a Kalman Filter multi-object tracker, and a Particle
Filter localiser running on ROS.

**[Computer Security](./computer_security/)**
A handbook I wrote covering the entire course — from CVE/CVSS classification and memory corruption
through to PKI, intrusion detection and ISO 27000 risk management — including worked solutions for
every lab exercise.

## 🛡️ Dedicated security repositories

Practical security work is kept in standalone repositories so it stays immediately visible:

*   **[Secure Software Development](https://github.com/NoFlyFre/secure-software-development)** — secure coding and exploitation labs (Nebula, Protostar, WebForPentester). *Course grade: 30/30.*
*   **[Computer Security Labs](https://github.com/NoFlyFre/computer-security-labs)** — buffer overflow analysis with GDB, SQL injection and XSS against an OWASP Broken Web Applications VM. Theory notes for the same course are in [`computer_security/`](./computer_security/).
*   **[OliCyber CTF Writeups](https://github.com/NoFlyFre/olicyber-ctf-writeups)** — web security, cryptography and network analysis challenges.
*   **[HTB Writeups](https://github.com/NoFlyFre/htb-writeups)** — HackTheBox penetration testing walkthroughs.
*   **[BreachLab Writeups](https://github.com/NoFlyFre/breachlab-writeups)** — wargame and privilege escalation field notes.

## 🛠️ Tech stack & concepts

*   **Languages** — Java, Python, C++, C
*   **Frameworks & tools** — ROS, PCL (Point Cloud Library), OpenCV, Java RMI, GDB, sqlmap, Burp Suite
*   **Security** — OWASP Top 10, memory corruption, cryptography & PKI, intrusion detection, SIEM/SOC, ISO 27000 risk management, GDPR compliance
*   **Distributed systems** — consensus and Byzantine fault tolerance, vector clocks, distributed mutual exclusion, DHTs, leader election
*   **Robotics & control** — Kalman and Particle Filters, MPC, Frenet planning, point cloud segmentation

## 📝 Conventions

Files and directories use **`snake_case`** and are named in **English**, for consistency and
programmatic access. Repository topic: `unimore-informatica`.

---
*Personal academic archive — University of Modena and Reggio Emilia.*
