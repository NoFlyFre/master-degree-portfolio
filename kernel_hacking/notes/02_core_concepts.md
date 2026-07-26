# Riassunto Modulo 02: Concetti Base

## 1. Struttura del Kernel nel Filesystem
Il kernel in esecuzione risiede principalmente in `/boot`.
- **vmlinuz**: Immagine compressa del kernel (bzImage).
- **initrd/initramfs**: Archivio cpio compresso che viene scompattato in un ramfs (tmpfs) all'avvio. Contiene i driver necessari per montare il vero root filesystem.
- **config**: File di configurazione usato per compilare quel kernel.

## 2. Initramfs (Initial RAM Filesystem)
Essenziale perché il kernel spesso non ha i driver del disco/filesystem compilati staticamente (built-in), ma come moduli. L'initramfs fornisce questi moduli.
- **Contenuto**: Moduli essenziali, programma `/init`.
- **Funzionamento**:
    1. Kernel scompatta initramfs in RAM.
    2. Esegue `/init`.
    3. `/init` carica i moduli e monta il vero root filesystem.
    4. Passa il controllo all'init del sistema (es. systemd).
- **Comandi Utili**:
    - Lista contenuto: `lsinitramfs /boot/initrd.img-...`
    - Scompattare: `unmkinitramfs <file> <destinazione>`
    - Creare/Aggiornare: `update-initramfs` o `mkinitramfs`.

## 3. Moduli del Kernel
- **Built-in (Statici)**: Parte integrante dell'immagine `vmlinuz`. Lista in `/lib/modules/$(uname -r)/modules.builtin`.
- **Loadable (Dinamici)**: File `.ko` caricati a richiesta. Risiedono in `/lib/modules/$(uname -r)/kernel/`.
- **Memoria**: Sia statici che dinamici condividono lo stesso spazio di indirizzamento, stack e heap del kernel.

## 4. Processo di Avvio (Semplificato)
1. **Bootloader** (GRUB) carica vmlinuz e initrd in memoria.
2. **Start Kernel**: Decompressione e inizializzazione hardware di base.
3. **Initramfs**: Caricamento moduli essenziali.
4. **Mount Root**: Montaggio del disco di sistema.
5. **Userspace Init**: Avvio dei servizi di sistema.

## 5. Esplorazione Sorgenti
Strumento consigliato: **Elixir Cross Referencer** (elixir.bootlin.com) per navigare il codice del kernel.
Funzioni chiave di avvio: `start_kernel` -> `rest_init` -> `cpu_startup_entry` (idle loop).
