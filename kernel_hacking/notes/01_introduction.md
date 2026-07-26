# Riassunto Modulo 01: Introduzione al Kernel Hacking

## 1. Introduzione e Risorse (dalle slide)
Il corso introduce al mondo del Kernel Linux, un progetto in continua evoluzione dove i libri di testo diventano presto obsoleti.

**Fonti principali di informazione:**
- **Il Codice**: "Il codice è la documentazione". Spesso è l'unica fonte di verità.
- **Web**: kernel.org, mailing list, forum. Attenzione però all'obsolescenza e all'inesattezza. Bisogna incrociare le fonti.
- **Documentazione ufficiale**: https://docs.kernel.org/ (utile ma non sempre organica).

**Filosofia del corso:**
- Livello di difficoltà iniziale basso per padroneggiare le basi.
- Approccio pratico: imparare a cavarsela con gli imprevisti (Legge di Murphy molto attiva nel kernel hacking).

**Esplorare il sistema (`/boot`):**
La directory `/boot` contiene i file essenziali per l'avvio:
- `vmlinuz-<versione>`: Immagine compressa del kernel.
- `initrd-<versione>`: Initial RAM Disk (file system temporaneo per l'avvio).
- `config-<versione>`: Configurazione con cui è stato compilato quel kernel.
- `System.map-<versione>`: Tabella dei simboli (indirizzi di memoria delle funzioni).

Comando utile: `uname -a` per vedere versione e data di compilazione del kernel in esecuzione.

---

## 2. Compilazione del Kernel (dagli appunti)

La procedura consigliata prevede l'uso di una **Macchina Virtuale** (VirtualBox o KVM/QEMU) con circa 30GB di disco e una distro Linux (es. Ubuntu/Debian o Fedora).

### Passi Principali:
1.  **Installare dipendenze**:
    *   Debian/Ubuntu: `git build-essential libncurses5-dev wget bzip2 libssl-dev libelf-dev flex bison dwarves`
    *   Fedora: `dnf groupinstall "Development Tools"`, `ncurses-devel`, `openssl-devel`, ecc.
2.  **Ottenere i sorgenti**:
    *   `git clone https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git`
3.  **Configurazione**:
    *   `make defconfig`: crea una config di default.
    *   `./scripts/kconfig/streamline_config.pl > .config`: genera un config ottimizzato per la macchina corrente (abilita solo i moduli in uso).
4.  **Compilazione**:
    *   `make -j $(nproc + 1)`: compila kernel e moduli usando tutti i core + 1.
    *   *Nota*: Non usare `sudo` per compilare!
5.  **Installazione**:
    *   `sudo make modules_install`: installa i moduli in `/lib/modules/<version>`.
    *   `sudo make install`: copia `vmlinuz`, `config`, `System.map` in `/boot` e aggiorna il bootloader (GRUB).
6.  **Post-installazione**:
    *   Aggiornare GRUB se necessario (`update-grub` o `grub2-mkconfig`).
    *   Riavviare e selezionare il nuovo kernel.

### Risoluzione Problemi Comuni:
-   **Errore OpenSSL**: installare `libssl-dev` o `openssl-devel`.
-   **Spazio esaurito / Corruzione**: `make mrproper` (pulisce tutto), rigenerare `.config` e ricompilare.
-   **Kernel Panic / Blocco al boot**: Spesso manca un driver fondamentale (es. driver del disco) nell'initramfs o nel kernel (renderlo *builtin* `y` invece di modulo `m`). Rimuovere `quiet` e `splash` da GRUB per vedere i log di errore.

### Comandi Utili per la gestione:
-   Rimuovere vecchi kernel: cancellare i file vmlinuz/initrd/config da `/boot` e la cartella in `/lib/modules`.
-   Modifica config kernel running: `zcat /proc/config.gz` (se abilitato).
