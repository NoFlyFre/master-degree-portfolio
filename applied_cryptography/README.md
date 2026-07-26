# 🔑 Cryptography Algorithms

![Grade](https://img.shields.io/badge/Grade-23%2F30-yellow)
![CFU](https://img.shields.io/badge/CFU-6-blue)
![Math](https://img.shields.io/badge/Focus-Number%20Theory-8A2BE2)

*Algoritmi di Crittografia — 6 CFU · A.Y. 2024/2025 · **23/30***

The mathematical course, not the engineering one: number theory, probabilistic algorithms and
correctness proofs behind the primitives everyone else treats as black boxes. Examined orally on proofs.

## 📄 What's here

*   **[`cryptography_algorithms_summary_23_24.pdf`](./cryptography_algorithms_summary_23_24.pdf)** —
    47-page summary of the full syllabus, from one-way functions through to TLS certificate chains.
*   **[`algorithm_questions.pdf`](./algorithm_questions.pdf)** — 15-page oral exam question bank.

> **Attribution** — both documents are **shared course material circulated among students**, not my own
> notes. The question bank is credited to **Bruno Pesole**; the summary predates my enrolment in the
> course. They are kept here as the reference set I studied from. If you are an author and want
> attribution changed or the file removed, open an issue.

## 📚 Topics covered

**Preliminaries** — one-way and trapdoor functions.

**Symmetric cryptography** — Caesar cipher, frequency cryptanalysis, Vigenère, one-time pad; the
security goals of a cipher; block ciphers and their attack models; **Feistel networks** and the design
of the round function F; modes of operation (ECB, CBC).

**Modular arithmetic** — the rings ℤₙ and why modular arithmetic is computationally attractive;
**Euclid's algorithm** and its efficiency; the **Chinese Remainder Theorem** with proof; groups and
cyclic groups; safe primes; modular exponentiation, the **discrete logarithm** problem and the
baby-step giant-step algorithm.

**Key exchange** — the **Diffie-Hellman** protocol: efficiency, security, the nature of the shared
secret g^ab, the authentication gap, and ephemeral DH keys.

**Randomness and probabilistic algorithms** — Monte Carlo vs Las Vegas algorithms and why bounded error
matters; **primality testing** (Fermat's little theorem, Solovay-Strassen); **integer factorisation**
with **Pollard's ρ**.

**Asymmetric protocols** — **RSA**: correctness proof, efficiency, and its attack surface (malleability,
small exponent *e*, Fermat factorisation, reuse of p and q), implementation details including CRT-based
decryption speedup, and **OAEP** padding. **Rabin**: modular square roots and the proof of equivalence
to factorisation. **ElGamal**, including the consequences of reusing the ephemeral value.

**Applications** — digital signatures, hashing, and the certification authority trust chain up to the
root CAs installed in the operating system.

## 🎯 Skills demonstrated

Working comfortably with the algebra that cryptography rests on — enough to read a proof of security
rather than trusting a library's README. The applied counterpart is in
[Computer Security](../computer_security/), which covers the same primitives from the deployment side:
TLS, IPsec, PKI and key management in production.
