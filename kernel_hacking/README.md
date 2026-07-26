# 🐧 Kernel Hacking

![Grade](https://img.shields.io/badge/Grade-30%2F30%20cum%20laude-brightgreen)
![CFU](https://img.shields.io/badge/CFU-6-blue)
![C](https://img.shields.io/badge/C-A8B9CC?logo=c&logoColor=black)
![Linux](https://img.shields.io/badge/Linux-Kernel-FCC624?logo=linux&logoColor=black)
![syzkaller](https://img.shields.io/badge/syzkaller-bug%20triage-red)

*Kernel Hacking — 6 CFU · Prof. Paolo Valente · A.Y. 2025/2026 · **30/30 cum laude***

The course where the abstraction ends. Building and instrumenting the Linux kernel, then closing a real
bug in mainline — not a toy exercise, an open **syzkaller** report on XFS.

## 🔧 Upstream patches

Two real fixes, in [`patches/`](./patches/):

**[`0001-fserror-fix-lockdep-igrab-softirq.patch`](./patches/0001-fserror-fix-lockdep-igrab-softirq.patch)**
Fixes syzkaller bug `5eb0d61dfb76ca12670c` — an **inconsistent lock state** on `inode->i_lock` in XFS.
`fserror_report()` called `igrab()`, which takes `i_lock` with a plain `spin_lock()`. Safe from process
context, but the function is also reachable from **softirq** through the iomap read-completion path:

```
ksoftirqd → blk_complete_reqs → lo_complete_rq → blk_mq_end_request
          → bio_endio → iomap_read_end_io → iomap_finish_folio_read
          → fserror_report → igrab   ← takes inode->i_lock
```

Since XFS mount acquires the same lock class from process context with softirqs enabled, lockdep flags
the `SOFTIRQ-ON-W → IN-SOFTIRQ-W` transition as a potential IRQ-inversion deadlock. The fix replaces
`igrab()` with `ihold()`, which bumps the reference count without touching the lock — valid because the
call site already holds a reference.

**[`0001-wireguard-fix-data-race-timer_handshake_attempts.patch`](./patches/0001-wireguard-fix-data-race-timer_handshake_attempts.patch)**
Annotates `peer->timer_handshake_attempts` with `READ_ONCE`/`WRITE_ONCE` to close a **KCSAN**-reported
data race between `wg_expired_retransmit_handshake()` in softirq context and
`wg_packet_send_queued_handshake_initiation()` running on a work queue.

## 🔬 The analysis behind the fix

The interesting part of the project wasn't the one-line change — it was proving it was the *right*
one-line change. In [`project/`](./project/):

*   **[`igrab_vs_ihold_analysis.md`](./project/igrab_vs_ihold_analysis.md)** — a recursive expansion of
    both functions down to **x86-64 assembly** under `CONFIG_PREEMPT_DYNAMIC`, `CONFIG_LOCKDEP` and
    `CONFIG_QUEUED_SPINLOCKS`, establishing exactly which locks each path touches.
    ([PDF version](./project/igrab_vs_ihold_analysis.pdf))
*   **[`icount_precondition.pdf`](./project/icount_precondition.pdf)** — the correctness argument for
    substituting `ihold()`: proving `i_count > 0` holds at every reachable call site, which is the
    precondition `ihold()` requires and `igrab()` does not.
*   **[`lockdep_warning.txt`](./project/lockdep_warning.txt)** — the raw lockdep splat from the
    reproduction run.
*   **[`project_report.pdf`](./project/project_report.pdf)** — the full report, with
    **[a 2-page summary](./project/project_report_summary.pdf)** covering bug, reproduction, root cause
    and fix.

**Reproduction environment** — a deliberately awkward stack that turned out to matter: macOS on Apple
Silicon → Debian ARM64 VM under UTM → QEMU x86-64 inside it, running syzbot's pre-built `bzImage` and C
reproducer for the exact reported commit. Getting a bit-identical repro of an x86 kernel bug on ARM
hardware was half the work.

## 📚 Course material

**[`course_handbook.pdf`](./course_handbook.pdf)** — handbook covering the full course.

Module notes in [`notes/`](./notes/):

| # | Notes | Topic |
|---|---|---|
| 01 | [`01_introduction.md`](./notes/01_introduction.md) | Kernel architecture, build and boot |
| 02 | [`02_core_concepts.md`](./notes/02_core_concepts.md) | Memory, processes, synchronisation primitives |
| 03 | [`03_kbuild_and_modules.md`](./notes/03_kbuild_and_modules.md) | Kbuild and loadable kernel modules |
| 04 | [`04_oops_and_sysfs.md`](./notes/04_oops_and_sysfs.md) | Reading an OOPS, exposing state via sysfs |
| 05 | [`05_syscalls_and_tracing.md`](./notes/05_syscalls_and_tracing.md) | Adding system calls, tracing and profiling |
| 06 | [`06_code_management.md`](./notes/06_code_management.md) | Patch workflow and upstream contribution |

> Lecture slides are not included: they are the instructor's material, not mine to redistribute.

## 🎯 Skills demonstrated

Reading kernel code at the level where concurrency bugs actually live — lock classes, softirq context,
IRQ inversion — and backing a change with a correctness argument rather than "the warning went away".
Plus the unglamorous half: reproducing someone else's crash from a bug tracker before touching a line.
