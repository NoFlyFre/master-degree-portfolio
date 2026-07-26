# igrab vs ihold — Analisi ricorsiva fino all'assembly

**Kernel:** for-next ~7.0-rc1 (commit cd7a5651db26)  
**Arch:** x86-64  
**Config rilevanti:** `CONFIG_PREEMPT_DYNAMIC=y` (PREEMPT_FULL attivo), `CONFIG_LOCKDEP=y`, `CONFIG_QUEUED_SPINLOCKS=y`

---

## Indice

1. [igrab — espansione completa](#1-igrab--espansione-completa)
2. [ihold — espansione completa](#2-ihold--espansione-completa)
3. [Tabella comparativa assembly](#3-tabella-comparativa-assembly)
4. [Perché il lockdep warning scatta dentro igrab e non ihold](#4-perché-il-lockdep-warning-scatta-dentro-igrab-e-non-ihold)

---

## 1. igrab — espansione completa

### Livello 0: igrab (fs/inode.c)

```c
struct inode *igrab(struct inode *inode)
{
    spin_lock(&inode->i_lock);                              // [A]
    if (!(inode->i_state & (I_FREEING|I_WILL_FREE)))       // [B]
        __iget(inode);                                      // [C]
    else
        inode = NULL;
    spin_unlock(&inode->i_lock);                            // [D]
    return inode;
}
```

---

### [A] spin_lock(&inode->i_lock) — espansione completa

#### Livello 1: spin_lock (include/linux/spinlock.h)

```c
static __always_inline void spin_lock(spinlock_t *lock)
{
    raw_spin_lock(&lock->rlock);
    // spinlock_t.rlock è di tipo raw_spinlock_t
}
```

#### Livello 2: raw_spin_lock (include/linux/spinlock.h)

```c
#define raw_spin_lock(lock)   _raw_spin_lock(lock)
```

#### Livello 3: _raw_spin_lock (kernel/locking/spinlock.c)

```c
void __lockfunc _raw_spin_lock(raw_spinlock_t *lock)
{
    __raw_spin_lock(lock);
}
```

#### Livello 4: __raw_spin_lock (include/linux/spinlock_api_smp.h)

```c
static inline void __raw_spin_lock(raw_spinlock_t *lock)
{
    preempt_disable();                                      // [A1]
    spin_acquire(&lock->dep_map, 0, 0, _RET_IP_);          // [A2]  ← LOCKDEP
    LOCK_CONTENDED(lock, do_raw_spin_trylock, do_raw_spin_lock);  // [A3]
    // LOCK_CONTENDED si espande a:
    //   do_raw_spin_lock(lock)  (quando !CONFIG_LOCK_STAT)
}
```

---

#### [A1] preempt_disable() (include/linux/preempt.h) — PREEMPT_FULL

```c
static __always_inline void preempt_disable(void)
{
    preempt_count_inc();
    barrier();
}

static __always_inline void preempt_count_inc(void)
{
    __preempt_count_add(1);
}

static __always_inline void __preempt_count_add(int val)
{
    raw_cpu_add_4(__preempt_count, val);
    // raw_cpu_add_4 su x86 diventa:
}
```

**Assembly x86-64 di preempt_disable():**
```asm
addl   $1, __preempt_count(%rip)   ; incrementa il contatore di preemption (per-CPU)
; barrier() = compiler barrier, nessuna istruzione generata
```

> **Effetto:** segnala al kernel "sono in una sezione critica, non deschedulare questo thread".

---

#### [A2] spin_acquire — lockdep (include/linux/lockdep.h)

```c
// Con CONFIG_LOCKDEP attivo:
#define spin_acquire(l, s, t, i)  lock_acquire_exclusive(l, s, t, NULL, i)
#define lock_acquire_exclusive(l, s, t, n, i) \
    lock_acquire(l, s, t, 0, 1, n, i)
```

**lock_acquire (kernel/locking/lockdep.c):**
```c
void lock_acquire(struct lockdep_map *lock, unsigned int subclass,
                  int trylock, int read, int check,
                  struct lockdep_map *nest_lock, unsigned long ip)
{
    // ...
    __lock_acquire(lock, subclass, trylock, read, check,
                   irqs_disabled(), nest_lock, ip, 0, 0);
}
```

**__lock_acquire (kernel/locking/lockdep.c) — funzione critica:**
```c
static int __lock_acquire(struct lockdep_map *lock, ...)
{
    // ...
    hlock->irqs_off = (curr->hardirqs_enabled == 0);

    // Verifica contesto corrente: siamo in softirq?
    if (softirq_count()) {                    // ← in_softirq() > 0 ?
        hlock->hardirq_context = 0;
        hlock->softirq_context = 1;           // sì: siamo IN-SOFTIRQ
    }

    // Chiama mark_lock per aggiornare il grafo e verificare consistenza:
    if (!mark_lock(curr, hlock, LOCK_USED))
        return 0;

    // mark_lock → mark_lock_irq → print_usage_bug se inconsistente
    // ↑ QUI SCATTA IL WARNING {SOFTIRQ-ON-W} → {IN-SOFTIRQ-W}
}
```

**mark_lock (kernel/locking/lockdep.c) — dove scatta il warning:**
```c
static int mark_lock(struct task_struct *curr,
                     struct held_lock *this,
                     enum lock_usage_bit new_bit)
{
    // Controlla se il nuovo usage (IN-SOFTIRQ-W) è compatibile
    // con gli usage già registrati su questa lock class ({SOFTIRQ-ON-W})
    if (!valid_state(curr, this, new_bit, excl))
        return 0;  // ← qui stampa print_usage_bug = il WARNING che vediamo
    // ...
}
```

> **Effetto:** lockdep percorre il grafo delle dipendenze tra lock e rileva
> che `i_lock_key` è stata acquisita con softirq abilitati (SOFTIRQ-ON-W,
> registrata in `unlock_new_inode`) e ora viene acquisita DA DENTRO un softirq
> (IN-SOFTIRQ-W). Stampa il WARNING e il backtrace.
>
> **Questo è puramente software, zero assembly machine-code utile.
> È un checker a runtime che aggiunge decine di migliaia di istruzioni
> machine code per ogni acquisizione di lock.**

---

#### [A3] do_raw_spin_lock (include/linux/spinlock.h)

```c
static inline void do_raw_spin_lock(raw_spinlock_t *lock)
{
    __acquire(lock);                    // annotazione per sparse, nessun codice
    arch_spin_lock(&lock->raw_lock);   // [A3a]
}
```

#### [A3a] arch_spin_lock → queued_spin_lock (include/asm-generic/qspinlock.h)

```c
static __always_inline void arch_spin_lock(arch_spinlock_t *lock)
{
    queued_spin_lock(lock);
}

static __always_inline void queued_spin_lock(struct qspinlock *lock)
{
    int val = 0;

    // Fast path: il lock è libero (val==0)?
    // Prova a settarlo atomicamente a _Q_LOCKED_VAL (1)
    if (likely(atomic_try_cmpxchg_acquire(&lock->val, &val, _Q_LOCKED_VAL)))
        return;   // ← fast path: lock acquisito in 1 istruzione

    // Slow path: qualcuno tiene già il lock → spin (MCS queue)
    queued_spin_lock_slowpath(lock, val);
}
```

**atomic_try_cmpxchg_acquire su x86-64:**
```c
static __always_inline bool
atomic_try_cmpxchg_acquire(atomic_t *v, int *old, int new)
{
    // si espande a:
    return try_cmpxchg_acquire(&v->counter, old, new);
}
// che genera:
```

**Assembly x86-64 di queued_spin_lock (fast path):**
```asm
; input: rdi = &lock->val
xor    %eax, %eax              ; val = 0 (valore atteso)
mov    $0x1, %edx              ; _Q_LOCKED_VAL = 1 (valore da scrivere)
lock   cmpxchgl %edx, (%rdi)   ; se *lock==0: *lock=1, ZF=1
                                ; se *lock!=0: eax=*lock, ZF=0
jne    .slowpath               ; se ZF=0: qualcuno tiene il lock → slow path
; fast path: ritorno (lock acquisito)
```

> **Effetto concreto:** se il lock è libero, una singola istruzione `LOCK CMPXCHG`
> atomicamente testa-e-setta il lock. Se occupato, entra nella MCS queue (slowpath).

---

### [B] Controllo inode->i_state

```c
if (!(inode->i_state & (I_FREEING|I_WILL_FREE)))
```

Su x86-64, `inode->i_state` è ora di tipo `struct inode_state_flags`
(kernel 7.0-rc1). Acceduto tramite `inode_state_read()` che usa `READ_ONCE`.

**Assembly:**
```asm
; offset di __i_state dentro struct inode → chiamiamolo OFF_STATE
mov    OFF_STATE(%rdi), %eax     ; legge i_state (READ_ONCE = load normale + barrier)
test   $0x...,%eax               ; I_FREEING | I_WILL_FREE
jne    .set_null                 ; se uno dei due è settato: inode = NULL
```

---

### [C] __iget(inode) (fs/inode.c)

```c
static inline void __iget(struct inode *inode)
{
    atomic_inc(&inode->i_count);
}
```

**atomic_inc (arch/x86/include/asm/atomic.h):**
```c
static __always_inline void atomic_inc(atomic_t *v)
{
    asm volatile(LOCK_PREFIX "incl %0"
                 : "+m" (v->counter)
                 :: "cc");
}
// LOCK_PREFIX = "lock " su SMP
```

**Assembly x86-64:**
```asm
; offset di i_count dentro struct inode → OFF_COUNT
lock   incl OFF_COUNT(%rdi)    ; incremento atomico di i_count
```

> **Effetto:** incrementa `i_count` di 1 in modo atomico. Una sola istruzione.

---

### [D] spin_unlock(&inode->i_lock) — espansione completa

#### Livello 1 → 4: percorso speculare

```c
spin_unlock(spinlock_t *lock)
  raw_spin_unlock(&lock->rlock)
    __raw_spin_unlock(raw_spinlock_t *lock)
      spin_release(&lock->dep_map, _RET_IP_)   // [D1] LOCKDEP release
      do_raw_spin_unlock(lock)                  // [D2]
        arch_spin_unlock(&lock->raw_lock)
          queued_spin_unlock(lock)              // [D3]
      preempt_enable()                          // [D4]
```

#### [D1] spin_release — lockdep

```c
// Lockdep: rimuove il lock dallo stack dei lock tenuti da questo thread.
// Software puro, nessun assembly machine utile.
lock_release(&lock->dep_map, ip);
```

#### [D2] do_raw_spin_unlock → queued_spin_unlock

```c
static __always_inline void queued_spin_unlock(struct qspinlock *lock)
{
    // Rilascia il lock con release semantics:
    // garantisce che tutte le store precedenti siano visibili
    // PRIMA che il lock venga rilasciato
    smp_store_release(&lock->val.counter, 0);
}
```

**Assembly x86-64:**
```asm
movb   $0x0, (%rdi)     ; store di 0 nel byte del lock (release semantics)
                         ; su x86 una plain store È già un release store
                         ; (TSO = Total Store Order)
```

#### [D4] preempt_enable()

```c
static __always_inline void preempt_enable(void)
{
    if (unlikely(preempt_count_dec_and_test()))
        __preempt_schedule();
    // preempt_count_dec_and_test:
    //   decrementa preempt_count, ritorna true se è tornato a 0
    //   se true: potrebbe esserci un thread con priorità più alta → reschedule
}
```

**Assembly x86-64:**
```asm
subl   $1, __preempt_count(%rip)   ; decrementa preemption counter (per-CPU)
jne    .done                         ; se != 0: siamo ancora in sezione critica
call   __preempt_schedule            ; se == 0: potrebbe servire reschedule
.done:
```

---

### igrab — riepilogo assembly completo (fast path, lock libero, inode vivo)

```asm
;; ============================================================
;; igrab(struct inode *rdi)
;; ============================================================

;; --- spin_lock: preempt_disable ---
addl   $1, __preempt_count(%rip)

;; --- spin_lock: lockdep spin_acquire ---
;; [decine di migliaia di istruzioni del checker lockdep]
;; QUI PUÒ SCATTARE IL WARNING {SOFTIRQ-ON-W}→{IN-SOFTIRQ-W}

;; --- spin_lock: arch_spin_lock (fast path) ---
xor    %eax, %eax
mov    $0x1, %edx
lock   cmpxchgl %edx, OFF_LOCK(%rdi)   ; acquisisce i_lock

;; --- controllo I_FREEING | I_WILL_FREE ---
mov    OFF_STATE(%rdi), %ecx
test   $FLAG_MASK, %ecx
jne    .set_null

;; --- __iget: atomic_inc(i_count) ---
lock   incl OFF_COUNT(%rdi)
jmp    .do_unlock

.set_null:
mov    $0, %rdi                        ; inode = NULL

.do_unlock:
;; --- spin_unlock: queued_spin_unlock ---
movb   $0x0, OFF_LOCK(%rdi_orig)      ; rilascia i_lock

;; --- spin_unlock: lockdep spin_release ---
;; [decine di istruzioni del checker lockdep]

;; --- spin_unlock: preempt_enable ---
subl   $1, __preempt_count(%rip)
jne    .done
call   __preempt_schedule
.done:

;; return rdi (inode o NULL)
```

---

---

## 2. ihold — espansione completa

### Livello 0: ihold (include/linux/fs.h)

```c
static inline void ihold(struct inode *inode)
{
    WARN_ON(atomic_inc_return(&inode->i_count) < 2);   // [E]
}
```

---

### [E] WARN_ON(atomic_inc_return(...) < 2)

#### Livello 1: atomic_inc_return (include/linux/atomic/atomic-instrumented.h)

```c
static __always_inline int atomic_inc_return(atomic_t *v)
{
    return arch_atomic_inc_return(v);
    // (con KCSAN/instrumentation off: chiamata diretta)
}
```

#### Livello 2: arch_atomic_inc_return (arch/x86/include/asm/atomic.h)

```c
static __always_inline int arch_atomic_inc_return(atomic_t *v)
{
    return arch_atomic_add_return(1, v);
}
```

#### Livello 3: arch_atomic_add_return (arch/x86/include/asm/atomic.h)

```c
static __always_inline int arch_atomic_add_return(int i, atomic_t *v)
{
    return i + xadd(&v->counter, i);
}

// xadd è:
#define xadd(ptr, inc) \
({                                              \
    typeof(*(ptr)) __ret = (inc);              \
    asm volatile(LOCK_PREFIX "xaddl %0, %1"   \
                 : "+r" (__ret), "+m" (*(ptr)) \
                 :: "cc");                      \
    __ret;                                      \
})
```

**Assembly x86-64 di atomic_inc_return:**
```asm
; input: rdi = &inode->i_count
mov    $0x1, %eax                    ; i = 1
lock   xaddl %eax, OFF_COUNT(%rdi)   ; fetch-and-add:
                                      ;   tmp = *i_count
                                      ;   *i_count += 1
                                      ;   eax = tmp (valore VECCHIO)
add    $0x1, %eax                    ; return old + 1 = nuovo valore
```

> **Nota chiave:** `XADD` (exchange-and-add) è diverso da `INC`:
> - `INC` incrementa e basta
> - `XADD` incrementa E ritorna il vecchio valore
> Serve `XADD` perché `atomic_inc_return` deve ritornare il nuovo valore.
> Funzionalmente identico a `INC` per quanto riguarda il contatore.

#### Livello 4: WARN_ON (include/asm-generic/bug.h)

```c
#define WARN_ON(condition)  ({                          \
    int __ret_warn_on = !!(condition);                  \
    if (unlikely(__ret_warn_on))                        \
        __WARN();                                       \
    unlikely(__ret_warn_on);                            \
})
```

**Assembly x86-64 di WARN_ON:**
```asm
; eax contiene il nuovo valore di i_count (dopo l'incremento)
cmp    $0x2, %eax               ; i_count_new < 2 ?
jge    .no_warn                  ; se >= 2: tutto ok, nessun warning
; se < 2: i_count era 0 prima dell'incremento → caller bug
call   __WARN                    ; stampa backtrace (slowpath rarissimo)
.no_warn:
```

> **Quando scatta:** SOLO se `i_count` era 0 prima della chiamata, cioè
> se il caller sta passando un inode senza tenere una reference valida.
> In condizioni corrette (il caller tiene una reference): **mai**.
> Non è un lock check, è un sanity check del contatore.

---

### ihold — riepilogo assembly completo

```asm
;; ============================================================
;; ihold(struct inode *rdi)
;; ============================================================

;; --- atomic_inc_return(&inode->i_count) ---
mov    $0x1, %eax
lock   xaddl %eax, OFF_COUNT(%rdi)    ; incrementa i_count, salva vecchio valore
add    $0x1, %eax                      ; calcola nuovo valore

;; --- WARN_ON(result < 2) ---
cmp    $0x2, %eax
jge    .done                           ; caso normale: nessun warning
call   __WARN                          ; rarissimo: i_count era 0, caller bug

.done:
;; return (void)
```

---

---

## 3. Tabella comparativa assembly

| Operazione | igrab (fast path) | ihold |
|---|---|---|
| preempt_disable | `addl $1, __preempt_count` | **assente** |
| lockdep acquire | ~migliaia di istruzioni, può WARN | **assente** |
| acquisizione lock | `lock cmpxchgl $1, i_lock` | **assente** |
| lettura i_state | `mov i_state, reg; test FLAGS` | **assente** |
| incremento i_count | `lock incl i_count` | `lock xaddl $1, i_count` |
| check result | assente | `cmp $2, reg; jge done` |
| rilascio lock | `movb $0, i_lock` | **assente** |
| lockdep release | ~decine di istruzioni | **assente** |
| preempt_enable | `subl $1, __preempt_count` | **assente** |
| **istruzioni totali (fast path)** | **~20 machine + migliaia lockdep** | **~4 machine** |
| **acquisisce i_lock** | **SÌ** | **NO** |
| **interagisce con lockdep** | **SÌ** | **NO** |

---

## 4. Perché il lockdep warning scatta dentro igrab e non ihold

### Il punto esatto nel codice

Il warning scatta dentro `__lock_acquire()` → `mark_lock()`, chiamato da
`spin_acquire()` dentro `__raw_spin_lock()`, che è il passo [A2] di igrab.

**Non scatta durante l'istruzione macchina `LOCK CMPXCHG`.**  
Scatta durante il **software checker** che lockdep esegue PRIMA dell'istruzione
macchina, per aggiornare e verificare il suo grafo delle dipendenze tra lock.

### Cosa controlla lockdep in quel momento

Lockdep mantiene per ogni **lock class** (non per ogni istanza di lock, ma per
la classe `i_lock_key`) un bitmask degli "usage": in quali contesti è stato
acquisito questo lock nella storia del sistema.

Quando `unlock_new_inode()` (Process 1, mount) chiama `spin_lock(&inode->i_lock)`
con softirq abilitati, lockdep segna: `{SOFTIRQ-ON-W}` sulla classe `i_lock_key`.

Quando `ksoftirqd/0` (Process 2) chiama `igrab()` → `spin_lock(&inode->i_lock)`
dall'interno di un softirq, lockdep sta per segnare `{IN-SOFTIRQ-W}` sulla stessa
classe. Prima di farlo, controlla: "è compatibile?"

La risposta è **no**: se una CPU tiene `i_lock_key` con softirq abilitati
e un softirq su quella stessa CPU prova ad acquisirlo, la CPU non può
rilasciare il lock finché il softirq non finisce, ma il softirq non può
finire finché non acquisisce il lock. **DEADLOCK.**

```
Possible unsafe locking scenario:
  CPU0
  ----
  lock(i_lock_key);           ← mount process con softirq abilitati
  <softirq fires on CPU0>
    lock(i_lock_key);         ← ksoftirqd/0 da dentro il softirq
  *** DEADLOCK ***
```

### Perché ihold non triggera niente

`ihold` non chiama `spin_lock`, quindi:
- **non entra mai in lockdep** — nessun `spin_acquire`, nessun `mark_lock`,
  nessun grafo da verificare
- **non acquisisce `i_lock`** — nessuna possibilità di deadlock con chi la tiene
- **non disabilita il preempt** — nessun side effect sullo scheduler
- Fa solo `LOCK XADDL` — un'istruzione atomica della CPU, sicura in qualsiasi
  contesto: processo, softirq, hardirq, NMI

### La sostituzione è sicura perché

A runtime, al call site di `fserror_report()`:
- `i_count >= 1` (il caller tiene una reference) → confermato dal log: `i_count=1`
- `I_FREEING == 0`, `I_WILL_FREE == 0` → confermato: `i_state=0x0`

Poiché `I_FREEING` e `I_WILL_FREE` vengono settati **solo** in `iput_final()`,
e `iput_final()` è raggiungibile **solo** quando `i_count` scende a zero,
e il caller tiene una reference (`i_count >= 1`), la condizione
`!(i_state & (I_FREEING|I_WILL_FREE))` in igrab è **invariabilmente vera**.

Quindi il branch `__iget(inode)` di igrab è **sempre** preso, e il branch
`inode = NULL` è **mai** preso. igrab si riduce a:

```
spin_lock → atomic_inc(i_count) → spin_unlock
```

Il `spin_lock/unlock` non aggiunge alcun valore funzionale (la condizione
è sempre vera), ma aggiunge: latenza, interazione con lockdep, e il rischio
di deadlock da softirq. `ihold` fa lo stesso `atomic_inc(i_count)` senza
nessuno di questi costi.

**`igrab ≡ ihold` in questo contesto, e `ihold` è la scelta corretta.**
