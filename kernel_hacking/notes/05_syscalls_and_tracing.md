# Riassunto Modulo 05: Syscall e Tracing

## 1. System Calls
Interfaccia fondamentale tra userspace e kernel.
### Aggiungere una nuova Syscall
Per aggiungere una syscall custom (es. `mysyscall`):
1.  **Definizione**: Usare la macro `SYSCALL_DEFINEn(nome, argomenti...)`.
    ```c
    SYSCALL_DEFINE0(mysyscall) {
        pr_info("Messaggio dal kernel\n");
        return 0;
    }
    ```
    Compilarla in un file (es. `mysyscall/mysyscall.c`) aggiunto al `obj-y` nel Makefile.
2.  **Prototipo**: Inserire in `include/linux/syscalls.h`: `asmlinkage long sys_mysyscall(void);`.
3.  **Tabella**: Registrare in `scripts/syscall.tbl`: `<numero> common mysyscall sys_mysyscall`.

**Userspace Test**:
```c
#include <sys/syscall.h>
#include <unistd.h>
// ...
syscall(470); // invocazione diretta tramite numero
```

## 2. Tracing
Strumenti per analizzare cosa succede nel sistema.

### Userspace
- **strace**: Traccia le system call invocate da un processo.
  `strace ls` -> mostra open, read, mmap, ecc.

### Kernel Tracing
- **ftrace**: Il tracer interno del kernel. Interfaccia fs in `/sys/kernel/tracing` (o `/sys/kernel/debug/tracing`).
- **trace-cmd**: Front-end userspace per ftrace.
  `trace-cmd record -e sched_switch ...` -> registra eventi.
  `trace-cmd report` -> legge il report.

### I/O Block Tracing
Analisi performance disco e I/O.
1.  **Preparazione**: Svuotare cache per test ripetibili: `echo 3 > /proc/sys/vm/drop_caches`.
2.  **Analisi Aggregata**: `iostat -x 1` (statistiche generali).
3.  **Analisi Dettagliata**:
    - **blktrace**: Registra eventi block layer a basso livello.
      `sudo blktrace -d /dev/nvme0n1 -o - | blkparse -i -`
    - **ftrace (blk tracer)**: Alternativa più ricca di info.
