# Kalman Filter Multi Object Tracker

## Build the project 

To build the project: 
```
mkdir build
cd build
cmake ..
make -j2
```
Download the dataset with the point cloud from here (https://drive.google.com/file/d/1TWfV1odleih6d0SG7q2Sbs1jbZ4oSPkP/view?usp=share_link) and place the ```log``` folder in the used build folder. 


Run the project by executing
```
./main
```

# Kalman Filter Multi-Object Tracker - Implementazione

## Funzionalità Implementate

### 1. Componenti Base (Richiesti)
- **KalmanFilter.cpp**:
  - Implementazione completa del filtro di Kalman
  - Inizializzazione ottimizzata della matrice di covarianza P
  - Implementazione delle funzioni predict() e update()

- **Tracker.cpp**:
  - Inizializzazione delle variabili nel costruttore
  - Implementazione della logica di tracking
  - Sistema di rimozione delle tracce basato su conteggio dei frame persi
  - Associazione dati tra cluster e tracklet

### 2. Funzionalità Opzionali
- **Tracciamento del Percorso più Lungo**:
  - Implementazione del calcolo della distanza totale per ogni traccia
  - Visualizzazione in tempo reale della traccia con il percorso più lungo
  - L'ID della traccia viene mostrato con un offset di 1000 per distinguerlo facilmente
  - Visualizzazione della distanza percorsa in centimetri

## Dettagli Implementativi

### Calcolo della Distanza
```cpp
void Tracklet::update(double x, double y, bool lidarStatus) {
  if (lidarStatus) {
    if (!first_update_) {
      double dx = x - last_x_;
      double dy = y - last_y_;
      total_distance_ += std::sqrt(dx*dx + dy*dy);
    }
    first_update_ = false;
    last_x_ = x;
    last_y_ = y;
    // ...
  }
}
```

### Visualizzazione
```cpp
auto longest = tracker.getLongestTrack();
if (longest.id != -1) {
    renderer.addText(0.0, -4.0, longest.id + 1000);  // ID offset di 1000
    renderer.addText(0.2, -2.0, static_cast<int>(longest.distance * 100));  // Distanza in cm
}
```