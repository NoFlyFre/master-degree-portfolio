# DTIP Architecture Documentation

**Visual diagrams of system architecture and component interactions**

---

## 1. System Overview

### 1.1 High-Level P2P Network

```mermaid
graph TB
    subgraph "DTIP Distributed Network"
        N0[Node 0: Banca<br/>CONSERVATIVE<br/>threshold: 70]
        N1[Node 1: Retail<br/>AGGRESSIVE<br/>threshold: 30]
        N2[Node 2: Energia<br/>BALANCED<br/>threshold: 50]
        N3[Node 3: Sanità<br/>PARANOID<br/>threshold: 10]
        N4[Node 4: Trasporti<br/>SKEPTICAL<br/>threshold: 80]
    end

    N0 <--> N1
    N0 <--> N2
    N0 <--> N3
    N0 <--> N4
    N1 <--> N2
    N1 <--> N3
    N1 <--> N4
    N2 <--> N3
    N2 <--> N4
    N3 <--> N4

    DASH[TUI Dashboard<br/>Python Textual]

    WB[WebBridge<br/>HTTP: 8080]

    DASH -->|HTTP REST| WB
    WB -->|RMI Lookup| N0
    WB -->|RMI Lookup| N1
    WB -->|RMI Lookup| N2
    WB -->|RMI Lookup| N3
    WB -->|RMI Lookup| N4

    style N0 fill:#3b82f6
    style N1 fill:#10b981
    style N2 fill:#f59e0b
    style N3 fill:#ef4444
    style N4 fill:#8b5cf6
    style DASH fill:#ec4899
```

**Communication Patterns:**
- **P2P RMI:** Nodes communicate directly via Java RMI
- **RMI Callbacks:** Event-driven notifications
- **HTTP REST:** Dashboard fetches state via WebBridge API
- **Fully Connected Mesh:** Each node has direct connection to all others

---

## 2. Component Architecture

### 2.1 Single Node Internal Architecture

```mermaid
graph TB
    subgraph "DTIPNode (Java)"
        VC[VectorClock]
        RA[RicartAgrawalaManager]
        GL[GossipListener]
        DB[(Local IoC Database)]
        API[ThreatIntelAPI]
        REG[Local RMI Registry]
    end

    subgraph "Interfaces"
        RMI_IF[DTIPNodeInterface]
    end

    subgraph "External"
        PEERS[Other Nodes]
    end

    PEERS -->|RMI:1099+ID| RMI_IF
    RMI_IF --> VC
    RMI_IF --> RA
    RMI_IF --> GL
    GL --> DB
    RA --> PEERS
    
    REG -.-> RMI_IF

    style VC fill:#06b6d4
    style RA fill:#8b5cf6
    style GL fill:#f59e0b
    style DB fill:#10b981
```

**Key Components:**
- **VectorClock:** Tracks causal ordering
- **RicartAgrawalaManager:** Coordinates mutual exclusion (with 5s timeout)
- **Local RMI Registry:** Each node hosts its own registry for autonomy

---

## 6. Deployment Architecture

### 6.1 Process Topology (Single Machine)

```mermaid
graph TB
    subgraph "Host Machine (localhost)"
        
        subgraph "Node Processes (Java)"
            P0[DTIPNode 0<br/>RMI: 1099]
            P1[DTIPNode 1<br/>RMI: 1100]
            P2[DTIPNode 2<br/>RMI: 1101]
            P3[DTIPNode 3<br/>RMI: 1102]
            P4[DTIPNode 4<br/>RMI: 1103]
        end

        subgraph "Client Processes"
            WEB[WebBridge<br/>HTTP: 8080]
        end

        WEB -.->|RMI Lookup| P0
        WEB -.->|RMI Lookup| P1
        WEB -.->|RMI Lookup| P2
        WEB -.->|RMI Lookup| P3
        WEB -.->|RMI Lookup| P4
    end

    subgraph "Terminal"
        TUI[TUI Dashboard<br/>Python Textual]
    end

    TUI --> WEB

    style P0 fill:#3b82f6
    style P1 fill:#10b981
    style P2 fill:#f59e0b
    style P3 fill:#ef4444
    style P4 fill:#8b5cf6
    style WEB fill:#8b5cf6
```

**Port Allocation:**
- **RMI Ports:**
  - Node 0: 1099
  - Node 1: 1100
  - Node 2: 1101
  - Node 3: 1102
  - Node 4: 1103
- **WebBridge API:** 8080
- **Sensor Listeners:** 9000-9005

---

*Generated with Mermaid.js*
*Updated: Jan 2026*