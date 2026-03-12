# Platforms and Algorithms for Autonomous Driving - Planning and Control Module

Questo repository contiene gli assignment svolti per il corso **Platforms and Algorithms for Autonomous Driving**, Modulo: **Planning and Control Module**. Ogni assignment è organizzato in cartelle dedicate che contengono il codice sorgente, i risultati delle simulazioni e altri file pertinenti.

---

## Assignment 1: Vehicle Modeling and Simulation

### Descrizione

L'**Assignment 1** si focalizza sulla modellazione e simulazione della dinamica di un veicolo. Sono stati implementati tre modelli matematici (cinematico, lineare e non lineare) e confrontati utilizzando vari scenari, tra cui comandi di sterzata sinusoidale e sterzata costante. Inoltre, è stato effettuato un confronto tra i metodi di integrazione numerica (Euler e RK4).

### Contenuto
- Implementazione dei modelli cinematico, lineare e non lineare.
- Analisi delle differenze negli angoli di slittamento tra i modelli.
- Studio delle traiettorie e delle forze laterali con sterzata costante.
- Confronto tra metodi di integrazione numerica con passi temporali diversi.

📄 Il report dettagliato è disponibile qui: [Report_Assignment1.pdf](./Assignment%201/Report_Assignment1.pdf)

---

## Assignment 2: Motion Control

### Descrizione

L'**Assignment 2** approfondisce il controllo longitudinale e laterale del veicolo, esplorando l'efficacia di vari metodi di controllo in diverse condizioni di velocità. Sono stati utilizzati controllori PID, Pure Pursuit e Stanley, con focus sulle basse e alte velocità.

### Contenuto
- **Exercise 1**: Controllo longitudinale con PID per tracciare una velocità target.
- **Exercise 2**: Controllo laterale a bassa velocità (10 m/s e 20 m/s) utilizzando Pure Pursuit e Stanley.
- **Exercise 3**: Controllo laterale ad alta velocità (23 m/s e 25 m/s) con curvature-based lookahead.

### Risultati
- Analisi degli errori laterali e delle oscillazioni del comando di sterzata.
- Confronto delle prestazioni tra controllori a diverse velocità.

📄 Il report dettagliato è disponibile qui: [Assignment2_Controllo_Caligiuri.pdf](./Assignment%202/Assignment2_Controllo_Caligiuri.pdf)

---

## Assignment 3: Motion Planning and Control

### Descrizione

L'**Assignment 3** si concentra sulla pianificazione e controllo del movimento del veicolo, con particolare attenzione a:
- Implementazione di un planner basato su Frenet per generare traiettorie in presenza di ostacoli statici.
- Uso di controllori longitudinali (PID) e laterali (Pure Pursuit, Stanley, MPC) per il tracciamento della traiettoria.

### Contenuto
- **Exercise 1**: Valutazione del controllo longitudinale e laterale a velocità di 10 m/s e 15 m/s.
- **Exercise 2**: Confronto delle prestazioni dei controllori laterali a velocità di 20 m/s, 25 m/s e oltre.

### Risultati
- Analisi delle traiettorie, errori laterali e slittamenti a diverse velocità.
- Confronto tra Pure Pursuit, Stanley e MPC in termini di precisione, stabilità e comfort.

📄 Il report dettagliato è disponibile qui: [Assignment3_Controllo.pdf](./Assignment%203/Assignment3_Controllo.pdf)

---

## Struttura della Repository

Ogni cartella contiene:
- **Codice sorgente**: Script per simulazioni e analisi.
- **Risultati**: Grafici e dati generati dalle simulazioni.
- **Report**: Documenti PDF con la spiegazione e i risultati dettagliati.

---