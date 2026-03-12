# README

## Assignment #1: Rilevamento di oggetti mediante clustering euclideo

### Anno Accademico: 2024/2025

### Studente: Francesco Caligiuri

### Matricola: 146666

---

## Introduzione

L’obiettivo di questo assignment è implementare un programma di clustering euclideo utilizzando la libreria PCL per segmentare e rilevare auto e pedoni presenti sulla strada. Il programma deve:

1. **Implementare il clustering euclideo** come studiato durante le lezioni.
2. **Assegnamento opzionale**: Implementare l’algoritmo che esegue il clustering euclideo utilizzando le funzioni `proximity` ed `euclideanCluster`, visualizzare la distanza di ogni cluster rispetto al veicolo ego, colorare in rosso i veicoli che sono sia davanti che a 5 metri di distanza dal veicolo ego e testare l’approccio sul dataset 2.
3. **Far funzionare la soluzione sul dataset 2**.

---

## Dettagli dell’Implementazione

### 1. Pre-elaborazione dei Dati

- **Downsampling**: Ho utilizzato il filtro `VoxelGrid` per ridurre la densità della nuvola di punti, impostando una dimensione del voxel di `0.1f` per le tre dimensioni. Questo migliora le prestazioni riducendo il numero di punti da elaborare.

- **Cropping**: Ho applicato un filtro `CropBox` per limitare l’area di interesse della nuvola di punti, utilizzando i seguenti limiti:

  - Min: `(-20, -6, -2)`
  - Max: `(30, 7, 5)`

  Questo consente di concentrarsi sugli oggetti rilevanti davanti al veicolo ego.

### 2. Segmentazione del Piano (Strada)

- **RANSAC Plane Segmentation**: Ho utilizzato l’algoritmo RANSAC per segmentare il piano della strada dalla nuvola di punti. Ho impostato una soglia di distanza di `0.2` per determinare quali punti appartengono al piano.

- **Estrazione degli Inlier e Outlier**: Dopo la segmentazione, ho estratto gli inlier (piano) e gli outlier (oggetti) utilizzando `ExtractIndices`.

### 3. Clustering Euclideo

- **Impostazione dei Parametri basati sul Dataset 2**: Ho inizialmente impostato i parametri per il clustering in base alle esigenze del **dataset 2**, poiché richiedeva una maggiore granularità rispetto al dataset 1. Una volta ottimizzati i parametri sul dataset 2, li ho mantenuti invariati anche per il dataset 1.

- **Utilizzo della Libreria PCL**: Ho utilizzato la classe `EuclideanClusterExtraction` per eseguire il clustering sugli oggetti estratti. I parametri impostati sono:

  - **Tolleranza di Cluster**: `0.2`
  - **Dimensione Minima del Cluster**: `50`
  - **Dimensione Massima del Cluster**: `25000`

- **Assegnamento Opzionale**: Ho implementato le funzioni `proximity` ed `euclideanCluster` per eseguire il clustering senza utilizzare le funzioni PCL predefinite. Questo è controllato tramite il flag `#define USE_PCL_LIBRARY`. Se commentato, il programma utilizza la mia implementazione personalizzata.

### 4. Visualizzazione e Rendering

- **Rendering dei Cluster**: Ogni cluster rilevato viene visualizzato con un colore diverso. Ho utilizzato un vettore di colori per differenziare i cluster.

- **Calcolo della Distanza**: Per ogni cluster, ho calcolato la distanza rispetto al veicolo ego utilizzando il centroide del cluster.

- **Visualizzazione della Distanza**: La distanza calcolata viene stampata sulla console e visualizzata nella scena come testo 3D vicino al cluster.

- **Colorazione dei Veicoli Vicini**: I veicoli che sono sia davanti al veicolo ego che entro una distanza di 5 metri vengono evidenziati con una bounding box rossa.

---

## Assegnamento Opzionale

Ho implementato le funzioni `proximity` ed `euclideanCluster` seguendo il pseudocodice fornito. Questo mi ha permesso di comprendere meglio l’algoritmo di clustering euclideo e di personalizzare ulteriormente il processo di clustering.

---

## Dataset 2

Ho testato la mia soluzione sul **dataset 2**, impostando i parametri di clustering per ottenere la maggiore granularità necessaria. Il dataset 2 presenta una maggiore complessità e richiede parametri più precisi per una segmentazione accurata. Una volta ottimizzati i parametri sul dataset 2, li ho lasciati invariati anche per il dataset 1.

- **Adattamento dei Parametri**: Il dataset 2 richiedeva una regolazione dei parametri per gestire correttamente la segmentazione degli oggetti. Ho affinato la **tolleranza di cluster** e le dimensioni minime e massime dei cluster per ottenere risultati ottimali.

- **Risultati Consistenti**: Dopo aver settato i parametri sul dataset 2, la soluzione funziona correttamente anche sul dataset 1 senza necessità di ulteriori modifiche, dimostrando la robustezza dell'approccio adottato.

- **Performance**: Il programma è stato in grado di segmentare correttamente il piano e rilevare gli oggetti presenti sulla strada in entrambi i dataset, mantenendo prestazioni elevate.

---

## Istruzioni per la Compilazione e l’Esecuzione

### Requisiti di Sistema

- **Sistema Operativo**: macOS Sequoia
- **Gestore Pacchetti**: [Homebrew](https://brew.sh/)
- **Libreria PCL**: Installare con `brew install pcl`
- **CMake**: Installare con `brew install cmake`
- **Boost Libraries**: Installare con `brew install boost`
- **Altre Dipendenze**:

  - Assicurarsi di avere installate le dipendenze richieste da PCL e Boost, che dovrebbero essere gestite automaticamente da Homebrew.

### Compilazione

1. **Clonare il Repository o Scaricare il Codice**

   Assicurarsi di avere il codice sorgente nella directory desiderata.

2. **Creare la Directory di Build**

   ```bash
   mkdir build
   cd build
   ```

3. **Configurare il Progetto con CMake**

   ```bash
   cmake ..
   ```

   Assicurarsi che CMake trovi correttamente le librerie PCL e Boost. Se necessario, specificare manualmente i percorsi utilizzando opzioni di configurazione di CMake.

4. **Compilare il Progetto**

   ```bash
   make
   ```

   Questo genererà l’eseguibile `cluster_extraction`.

### Esecuzione

```bash
./cluster_extraction
```

**Nota**: Assicurarsi di modificare il percorso del dataset nel codice sorgente:

```cpp
namespace fs = boost::filesystem;
std::vector<fs::path> stream(fs::directory_iterator{"/percorso/al/dataset"},
                             fs::directory_iterator{});
```

Sostituire `"/percorso/al/dataset"` con il percorso corretto dove sono memorizzati i file PCD del dataset sul vostro sistema.

**Esempio**:

Se i file del dataset si trovano nella directory `/Users/username/Desktop/dataset_1`, allora modificare come segue:

```cpp
std::vector<fs::path> stream(fs::directory_iterator{"/Users/username/Desktop/dataset_1"},
                             fs::directory_iterator{});
```

Per utilizzare il **dataset 2**, modificare il percorso di conseguenza:

```cpp
std::vector<fs::path> stream(fs::directory_iterator{"/Users/username/Desktop/dataset_2"},
                             fs::directory_iterator{});
```

---

## Funzionalità Aggiuntive

- **Visualizzazione della Distanza**: Il programma visualizza la distanza di ogni cluster rispetto al veicolo ego sia sulla console che nella visualizzazione grafica.

- **Colorazione Dinamica**: I cluster vengono colorati dinamicamente, e i veicoli vicini vengono evidenziati in rosso per una rapida identificazione.

- **Interfaccia Utente Migliorata**: Ho aggiunto commenti e messaggi di log per migliorare la comprensibilità e la tracciabilità durante l’esecuzione del programma.