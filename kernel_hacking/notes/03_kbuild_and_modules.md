# Riassunto Modulo 03: Kbuild e Moduli

## 1. Aggiungere un Modulo al Kernel
Si può fare in due modi: creando una nuova directory o usando una esistente.

### Codice Sorgente Base (`hello_module.c`)
Ogni modulo ha almeno due funzioni e alcune macro:
```c
#include <linux/init.h>
#include <linux/module.h>
#include <linux/printk.h>

static __init int hello_init(void) {
    pr_info("Modulo inserito\n");
    return 0;
}

static __exit void hello_exit(void) {
    pr_info("Modulo rimosso\n");
}

module_init(hello_init);
module_exit(hello_exit);

MODULE_LICENSE("GPL");
```
- `__init`: Macro che indica che la funzione serve solo all'avvio (memoria liberata dopo).
- `__exit`: Macro per la rimozione (ingorata se built-in).

### Integrazione nel Build System (Kbuild)
**Opzione A: Nuova cartella (`drivers/modulo_custom/`)**
1.  **Kconfig Padre**: Aggiungere `source "drivers/modulo_custom/Kconfig"`
2.  **Makefile Padre**: Aggiungere `obj-$(CONFIG_HELLO_MODULE) += modulo_custom/`
3.  **Kconfig Locale**: Definire il simbolo `config HELLO_MODULE` (tristate).
4.  **Makefile Locale**: `obj-$(CONFIG_HELLO_MODULE) += hello_module.o`

**Opzione B: Cartella esistente**
1.  Aggiungere struct config al **Kconfig** esistente.
2.  Aggiungere la riga obj al **Makefile** esistente.

## 2. Caricamento Automatico
Il kernel può caricare automaticamente i moduli quando ne ha bisogno (ondemand).
Esempio: **I/O Scheduler** (bfq, kyber).
- Se configurati come moduli (`<M>`), non appaiono in `/sys/block/.../scheduler` finché non caricati.
- Se si prova a impostarli (es. `echo bfq > .../scheduler`), il kernel cerca e carica il modulo automaticamente.

## 3. Gestione Tempo e Tick (HZ)
Il kernel esegue servizi periodici (scheduler tick) scatenati da un interrupt timer.
- **HZ**: Costante che definisce la frequenza del tick (configurabile).
- **Jiffies**: Contatore globale incrementato ad ogni tick.
- **Compromesso**:
    - HZ alto (1000) = Più reattività, latenza minore.
    - HZ basso (100, 250) = Meno overhead, più throughput.

**Esercizio Avanzato**: Misurare overhead e latenza al variare di HZ usando tool come:
- `top` (per overhead CPU idle/sys).
- `cyclictest` (per latenza interrupt).
- `offwaketime` (per tempi di bloccaggio task).
