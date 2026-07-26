# Riassunto Modulo 04: OOPS e Sysfs

## 1. Kernel Log e Debugging
Il kernel scrive i messaggi in un buffer circolare (ring buffer).
- **dmesg**: Comando per leggere i log (`dmesg -H` -w per follow).
- **Console**: I messaggi critici vengono stampati su console.
- **Netconsole**: Modulo per inviare i log via rete UDP (utile se la macchina crasha e non si ha accesso fisico). Richiede configurazione IP logger.

## 2. Kernel OOPS
Un "Oops" è un errore non fatale (o quasi) del kernel (es. dereferenza NULL pointer, divisione per zero).
- **OOPS vs Panic**:
    - **Oops**: Uccide il processo colpevole, il sistema potrebbe restare instabile ma vivo.
    - **Panic**: Il sistema si blocca completamente (freeze/reboot necessario) se l'errore è irrecuperabile.
- **Analisi dell'Errore**:
    - **RIP**: Instruction Pointer (indirizzo istruzione fallita). Se compilato con debug info, gdb può dire la riga esatta: `list *(funzione+offset)`.
    - **Call Trace**: Stack delle chiamate.
    - **Code**: Dump esadecimale delle istruzioni.

**Debugging Proattivo**:
- `BUG_ON(condizione)`: Se vero, causa un OOPS intenzionale (kill processo). Utile per "offensive programming".
- `WARN_ON(condizione)`: Se vero, stampa stack trace ma non killa nulla.

## 3. Sysfs (`/sys`)
Filesystem virtuale che espone la gerarchia dei **kobject** (strutture dati interne del kernel) allo userspace.
- **Kobject**: Corrisponde a una directory in `/sys`.
- **Attributi**: Corrispondono a file in `/sys` (leggi/scrivi per interagire col kernel).
    - `show()`: Callback di lettura.
    - `store()`: Callback di scrittura.

## 4. Race Conditions (Esempio)
Se un kobject espone una variabile globale modificabile da più processi concorrenti, e l'operazione non è atomica (lettura-modifica-scrittura), si verificano **race conditions**.
- Esempio: incremento variabile. Senza locking, i valori letti/scritti saranno inconsistenti.
