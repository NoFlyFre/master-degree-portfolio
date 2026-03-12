
# Implementazione di un Particle Filter per la Localizzazione di un Carrello Elevatore

## Descrizione del Progetto

In questo progetto, è stato implementato un Particle Filter per la localizzazione di un carrello elevatore utilizzando dati LiDAR e landmark come riferimento. L’obiettivo è sviluppare un filtro in grado di stimare con precisione la posizione del veicolo durante l’intera simulazione, analizzando l’effetto di diversi parametri sul suo comportamento.

## Struttura del Progetto

Il progetto contiene i seguenti file, che son stati modificati e creati per l'assignment:
- **src/particle_filter/src/particle_filter.cpp**: Implementazione del Particle Filter con tutte le funzioni necessarie.
- **src/particle_filter/src/main.cpp**: File principale che esegue il filtro utilizzando i dati di input.
- **assignment3.pdf**: Report dettagliato del progetto, contenente l’analisi e i risultati degli esperimenti.
- **README.md**: Questo file, contenente la descrizione del progetto e le istruzioni.

## Istruzioni per l’Esecuzione

Per eseguire il progetto, seguire i seguenti passaggi:

### 1. Prerequisiti
- Sistema operativo Ubuntu 18.04 o 20.04.
- ROS (Robot Operating System) installato e configurato.
- ROS package `rosbag` per la riproduzione dei dati.
- Eseguire `source /opt/ros/melodic/setup.sh` in ogni terminale che si andrà ad aprire.
- Scaricare il file `out.bag`

### 2. Compilazione del Codice
- Posizionarsi nella cartella root del progetto.
- Eseguire il comando `catkin_make` per compilare il progetto.

### 3. Esecuzione
- Avviare ROS eseguendo, in un terminale separato:
  ```bash
  roscore
  ```
- Lanciare il nodo ROS eseguendo:
  ```bash
  ./devel/lib/particle/particle_node
  ```
- In un altro terminale, riprodurre il rosbag con i dati utilizzando:
  ```bash
  rosbag play --clock --hz=10 out.bag
  ```
  
## Esperimenti e Risultati

Sono stati eseguiti tre esperimenti variando il numero di particelle e i parametri di rumore, per osservare l’effetto su precisione e stabilità del filtro. I risultati dettagliati, inclusi i grafici e le analisi, sono disponibili nel report **assignment3.pdf**.

---

## Nota al Professore

Gentile Professore,

Desidero informarla che ho caricato il progetto con un leggero ritardo rispetto alla scadenza prevista. Ho incontrato diverse difficoltà nel configurare l’ambiente ROS a causa del mio sistema operativo (macOS) e dell’architettura Apple Silicon (ARM). 

Inizialmente, ho provato a utilizzare ROS tramite Docker su Ubuntu 18, forwardando la GUI con XQuartz, ma senza successo. Successivamente, ho tentato con una macchina virtuale, installando Ubuntu 18 Server e aggiungendo manualmente l’ambiente desktop, ma anche questa soluzione non ha funzionato. 

Infine, ho dovuto utilizzare un PC con architettura x86, dove sono riuscito a installare Ubuntu e avviare ROS. Tuttavia, queste difficoltà tecniche mi hanno sottratto tempo prezioso, riducendo quello disponibile per lavorare sul codice.

Attualmente, il codice è funzionante e sono riuscito a completare tutti i punti richiesti, inclusi gli esperimenti con le varie configurazioni.

Mi scuso per il ritardo nella consegna e spero che possa tenerne conto.

Cordiali saluti,  
**Francesco Caligiuri**
