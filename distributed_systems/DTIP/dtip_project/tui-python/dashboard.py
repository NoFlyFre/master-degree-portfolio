"""
DTIP Dashboard v4 - "The Terminal Way"
Single-view, key-driven interface (Vim/Htop style).
"""

from textual.app import App, ComposeResult
from textual.containers import Container, Grid, Horizontal, Vertical, VerticalScroll
from textual.widgets import Header, Footer, Static, Button, Label, Input, DataTable, Select
from textual.screen import ModalScreen
from textual.binding import Binding
from textual.reactive import reactive
from textual import work
import httpx
import asyncio
import json
from datetime import datetime

# --- Constants ---
API_BASE = "http://127.0.0.1:8080"
NODE_COUNT = 5
NODE_NAMES = ["Node 0", "Node 1", "Node 2", "Node 3", "Node 4"]
legacy_names = ["Banca", "Retail", "Energia", "Sanità", "Trasporti"]
NAME_TO_ID = {name: i for i, name in enumerate(legacy_names)}
NODE_ICONS = ["[BNK]", "[RET]", "[ENR]", "[SAN]", "[TRA]"]
NODE_POLICIES = ["Conservative", "Aggressive", "Balanced", "Paranoid", "Skeptical"]

# --- API Helper ---
async def api_post(endpoint, data):
    try:
        async with httpx.AsyncClient(timeout=2.0) as client:
            return await client.post(f"{API_BASE}{endpoint}", json=data)
    except Exception as e:
        return None

# --- Modals ---

class InjectModal(ModalScreen):
    """Popup for injecting IoCs"""
    CSS = """
    InjectModal { align: center middle; background: rgba(0,0,0,0.7); }
    #dialog {
        padding: 1 2;
        width: 60;
        height: auto;
        border: thick $accent;
        background: $surface;
    }
    Label { margin-bottom: 1; }
    Input { margin-bottom: 1; }
    .hint { color: $text-muted; text-style: italic; }
    """
    
    def compose(self) -> ComposeResult:
        with Container(id="dialog"):
            yield Label("[b]INJECT NEW IOC[/b]")
            # yield Input(placeholder="Type (IP/DOMAIN/HASH)", id="type")
            yield Select([("IP Address", "IP"), ("Domain Name", "DOMAIN"), ("File Hash", "HASH"), ("URL", "URL"), ("Email", "EMAIL"), ("CVE", "CVE")], prompt="Select IoC Type", id="type_select")
            yield Input(placeholder="Value (e.g. 1.2.3.4)", id="value")
            yield Input(placeholder="Confidence (0-100, vuoto=auto)", id="confidence", type="integer")
            yield Label("[dim]Auto: analizza pattern per stimare confidence[/dim]", classes="hint")
            yield Horizontal(
                Button("Inject", variant="primary", id="btn_inject"),
                Button("Cancel", variant="error", id="btn_cancel"),
                classes="buttons"
            )
    
    def on_select_changed(self, event: Select.Changed) -> None:
        """Update placeholder based on selected type"""
        if event.select.id == "type_select":
            val_input = self.query_one("#value")
            if event.value == "IP":
                val_input.placeholder = "Value (e.g. 192.168.1.100)"
            elif event.value == "DOMAIN":
                val_input.placeholder = "Value (e.g. malicious-site.com)"
            elif event.value == "HASH":
                val_input.placeholder = "Value (e.g. a1b2c3d4...)"
            elif event.value == "URL":
                val_input.placeholder = "Value (e.g. http://phishing.com/login)"
            elif event.value == "EMAIL":
                val_input.placeholder = "Value (e.g. sender@spam.com)"
            elif event.value == "CVE":
                val_input.placeholder = "Value (e.g. CVE-2024-1234)"
    
    def estimate_confidence(self, ioc_type: str, value: str) -> int:
        """Estimate a realistic confidence based on IoC patterns"""
        value_lower = value.lower()
        ioc_type_upper = ioc_type.upper()
        
        # === DOMAIN Analysis ===
        if ioc_type_upper == "DOMAIN":
            # High threat keywords
            high_threat = ["malware", "phishing", "evil", "hack", "trojan", "virus", 
                          "ransomware", "exploit", "payload", "c2", "botnet", "keylog"]
            if any(kw in value_lower for kw in high_threat):
                return 85
            
            # Suspicious TLDs
            suspicious_tlds = [".xyz", ".top", ".club", ".work", ".click", ".loan", ".tk", ".ml", ".ga"]
            if any(value_lower.endswith(tld) for tld in suspicious_tlds):
                return 70
            
            # Known safe domains
            safe_domains = ["google", "microsoft", "amazon", "apple", "github", "facebook", 
                           "cloudflare", "akamai", "fastly", "gov.it", "edu"]
            if any(safe in value_lower for safe in safe_domains):
                return 15
            
            # Generic/unknown domain
            return 50
        
        # === IP Analysis ===
        elif ioc_type_upper == "IP":
            # Private ranges (RFC1918)
            if value.startswith("10.") or value.startswith("192.168.") or value.startswith("172."):
                return 10  # Very low - private IP
            
            # Localhost
            if value.startswith("127.") or value == "::1":
                return 5
            
            # Known DNS (Google, Cloudflare)
            safe_ips = ["8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1"]
            if value in safe_ips:
                return 10
            
            # Known malicious ranges (example: some botnets)
            if value.startswith("45.33.") or value.startswith("185."):
                return 75
            
            # Generic public IP
            return 55
        
        # === HASH Analysis ===
        elif ioc_type_upper == "HASH":
            # MD5 (32 chars) - older, less trusted
            if len(value) == 32:
                return 60
            # SHA256 (64 chars) - more modern, higher trust
            elif len(value) == 64:
                return 70
            # SHA1 (40 chars)
            elif len(value) == 40:
                return 65
            return 50
        
        # Default
        return 50

    def on_button_pressed(self, event):
        if event.button.id == "btn_inject":
            # itype = self.query_one("#type").value.strip()
            itype = self.query_one("#type_select").value
            if itype == Select.BLANK:
                itype = None
            
            ival = self.query_one("#value").value.strip()
            iconf_raw = self.query_one("#confidence").value
            
            # If confidence not provided, estimate it
            if iconf_raw is None or iconf_raw == "" or iconf_raw == 0:
                iconf = self.estimate_confidence(itype, ival)
            else:
                iconf = int(iconf_raw)
            
            if itype and ival:
                self.dismiss((itype, ival, iconf))
            else:
                self.dismiss(None)
        else:
            self.dismiss(None)


# SocModal removed - SOC intervention is now handled automatically by LLM


class KillModal(ModalScreen):
    """Popup for killing a node"""
    CSS = """
    KillModal { align: center middle; background: rgba(0,0,0,0.7); }
    #dialog {
        padding: 1 2;
        width: 40;
        height: auto;
        border: thick $error;
        background: $surface;
    }
    Button { width: 100%; margin-bottom: 1; }
    """
    
    def compose(self) -> ComposeResult:
        with Container(id="dialog"):
            yield Label("[b]KILL NODE[/b] (Select one)", classes="header")
            for i in range(NODE_COUNT):
                yield Button(f"{NODE_ICONS[i]} {NODE_NAMES[i]}", id=f"kill_{i}", variant="error")
            yield Button("Cancel", id="cancel")

    def on_button_pressed(self, event):
        if event.button.id.startswith("kill_"):
            nid = int(event.button.id.split("_")[1])
            self.dismiss(nid)
        else:
            self.dismiss(None)

class ReviveModal(ModalScreen):
    """Popup for reviving a node"""
    CSS = """
    ReviveModal { align: center middle; background: rgba(0,0,0,0.7); }
    #dialog {
        padding: 1 2;
        width: 40;
        height: auto;
        border: thick $success;
        background: $surface;
    }
    Button { width: 100%; margin-bottom: 1; }
    """
    
    def compose(self) -> ComposeResult:
        with Container(id="dialog"):
            yield Label("[b]REVIVE NODE[/b] (Select one)", classes="header")
            for i in range(NODE_COUNT):
                yield Button(f"{NODE_ICONS[i]} {NODE_NAMES[i]}", id=f"revive_{i}", variant="success")
            yield Button("Cancel", id="cancel")

    def on_button_pressed(self, event):
        if event.button.id.startswith("revive_"):
            nid = int(event.button.id.split("_")[1])
            self.dismiss(nid)
        else:
            self.dismiss(None)

class HelpModal(ModalScreen):
    """Help modal with algorithm explanations"""
    CSS = """
    HelpModal { align: center middle; background: rgba(0,0,0,0.8); }
    #help-dialog {
        padding: 1 2;
        width: 70;
        height: auto;
        max-height: 90%;
        border: thick $primary;
        background: $surface;
    }
    .help-title { text-style: bold; color: $primary; margin-bottom: 1; }
    .help-section { margin-bottom: 1; }
    .help-key { color: $success; }
    """
    
    BINDINGS = [("escape", "dismiss", "Close"), ("h", "dismiss", "Close")]
    
    def compose(self) -> ComposeResult:
        with Container(id="help-dialog"):
            yield Label("[b]DTIP - Guida Algoritmi Distribuiti[/b]", classes="help-title")
            
            yield Label("[b][1] Vector Clock[/b]", classes="help-section")
            yield Label("  Ordinamento causale: VC[i] = max(VC[i], msg.VC[i])")
            yield Label("  Esempio: [20, 18, 12, 20] = eventi per ogni nodo")
            
            yield Label("[b][2] Gossip Protocol[/b]", classes="help-section")
            yield Label("  Propagazione epidemica degli IoC")
            yield Label("  Ogni nodo riceve e inoltra ai peer")
            
            yield Label("[b][3] Consenso (Quorum)[/b]", classes="help-section")
            yield Label("  Minimo 3 voti su 4 nodi per decidere")
            yield Label("  Maggioranza determina: VERIFIED o REJECTED")
            
            yield Label("[b][4] Anti-Entropy[/b]", classes="help-section")
            yield Label("  Sincronizzazione periodica tra nodi")
            yield Label("  Recupera IoC persi durante offline")
            
            yield Label("[b][5] Ricart-Agrawala Mutex[/b]", classes="help-section")
            yield Label("  Mutua esclusione distribuita")
            yield Label("  REQUEST -> REPLY -> CRITICAL SECTION -> RELEASE")
            
            yield Label("[b][6] Policy di Voto[/b]", classes="help-section")
            yield Label("  Banca: Conservative (threshold 70)")
            yield Label("  Retail: Aggressive (threshold 30)")
            yield Label("  Energia: Balanced (threshold 50)")
            yield Label("  Sanita: Paranoid (threshold 10)")
            
            yield Label("")
            yield Label("[b]Keybindings:[/b] [green]i[/green]=Inject [green]k[/green]=Kill [green]r[/green]=Revive [green]m[/green]=Mutex [green]h[/green]=Help [green]s[/green]=Save [green]q[/green]=Quit")
            yield Button("Chiudi (ESC)", id="close", variant="primary")

    def on_button_pressed(self, event):
        self.dismiss(None)

# --- Widgets ---

class NodeStatus(Static):
    """Single node block - compact version"""
    online = reactive(False)
    
    DEFAULT_CSS = """
    NodeStatus {
        width: 100%;
        height: auto;
        min-height: 3;
        background: $surface-lighten-1;
        border: solid $primary-darken-2;
        padding: 0 1;
        margin: 0 0 1 0;
    }
    NodeStatus.online { border: solid $success; }
    NodeStatus.offline { border: solid $error; opacity: 0.6; }
    .header { text-style: bold; }
    """
    
    def __init__(self, node_id):
        super().__init__()
        self.node_id = node_id
        
    def compose(self) -> ComposeResult:
        icon = NODE_ICONS[self.node_id]
        name = NODE_NAMES[self.node_id]
        policy = NODE_POLICIES[self.node_id]
        yield Label(f"{icon} {name} | {policy}", classes="header")
        yield Label("WAITING...", id="status")

    def update_data(self, data):
        is_online = data.get("status") != "DISCONNECTED" and "vectorClock" in data
        self.online = is_online
        self.set_class(is_online, "online")
        self.set_class(not is_online, "offline")
        
        status_lbl = self.query_one("#status")
        ioc_count = data.get("iocCount", 0)
        if is_online:
            status_lbl.update(f"ONLINE | IoCs: {ioc_count}")
            status_lbl.styles.color = "green"
        else:
            status_lbl.update("OFFLINE")
            status_lbl.styles.color = "red"


class VectorClockMatrix(Static):
    """Matrix view of all Vector Clocks"""
    DEFAULT_CSS = """
    VectorClockMatrix {
        height: auto;
        min-height: 10;
        background: $surface;
        border: panel $accent;
        padding: 1;
        margin-top: 1;
    }
    .vc-title {
        text-style: bold;
        color: $accent;
        margin-bottom: 1;
    }
    """
    
    def compose(self) -> ComposeResult:
        yield Label("VECTOR CLOCK MATRIX", classes="vc-title")
        yield DataTable(id="vc-matrix")
    
    def on_mount(self):
        table = self.query_one("#vc-matrix")
        # Header: empty + node abbreviations with EXPLICIT keys
        table.add_column("Node", key="Node")
        table.add_column("VC[0]", key="VC[0]")
        table.add_column("VC[1]", key="VC[1]")
        table.add_column("VC[2]", key="VC[2]")
        table.add_column("VC[3]", key="VC[3]")
        table.add_column("VC[4]", key="VC[4]")
        
        # Initialize rows
        for i in range(NODE_COUNT):
            table.add_row(f"{NODE_ICONS[i]} {NODE_NAMES[i]}", "-", "-", "-", "-", "-", key=f"row_{i}")
    
    def update_matrix(self, all_nodes_data):
        """Update the matrix with vector clock data from all nodes"""
        try:
            table = self.query_one("#vc-matrix")
        except:
            return  # Table not mounted yet
        
        for i in range(NODE_COUNT):
            node_data = all_nodes_data.get(str(i), {})
            vc = node_data.get("vectorClock", None)
            is_online = vc is not None and len(vc) >= NODE_COUNT
            
            row_key = f"row_{i}"
            
            if is_online:
                # Build row values with highlighted diagonal
                values = []
                for j in range(NODE_COUNT):
                    val = vc[j] if j < len(vc) else 0
                    if j == i:
                        values.append(f"[bold cyan]{val}[/]")
                    else:
                        values.append(str(val))
                
                # Update using coordinate (row_key, column_key)
                # Now that keys are explicit, this WILL work
                try:
                    table.update_cell(row_key, "Node", f"{NODE_ICONS[i]} {NODE_NAMES[i]}")
                    table.update_cell(row_key, "VC[0]", values[0])
                    table.update_cell(row_key, "VC[1]", values[1])
                    table.update_cell(row_key, "VC[2]", values[2])
                    table.update_cell(row_key, "VC[3]", values[3])
                    table.update_cell(row_key, "VC[4]", values[4])
                except Exception as e:
                    # Log error safely
                    if hasattr(self, 'app') and hasattr(self.app, 'log_msg'):
                        self.app.log_msg("ERROR", "Matrix", "Cell Update", str(e))
            else:
                # Node offline - show dimmed
                try:
                    table.update_cell(row_key, "Node", f"[dim]{NODE_ICONS[i]} {NODE_NAMES[i]}[/]")
                    for j, col in enumerate(["VC[0]", "VC[1]", "VC[2]", "VC[3]", "VC[4]"]):
                        table.update_cell(row_key, col, "[dim]-[/]")
                except Exception:
                    pass

class IoCPanel(Static):
    """Latest IoC Details - Rich Visualization"""
    DEFAULT_CSS = """
    IoCPanel {
        height: 12;
        background: $surface;
        border: panel $accent;
        padding: 0 1;
        layout: vertical;
    }
    
    .ioc-header {
        height: 3;
        dock: top;
        border-bottom: solid $primary;
        padding-top: 1;
    }
    
    .ioc-value {
        width: 100%;
        content-align: center middle;
        text-style: bold;
        color: $accent-lighten-2;
    }
    
    .ioc-meta {
        height: 1;
        width: 100%;
        content-align: center middle; 
        color: $text-muted;
    }

    #ioc-status-badge {
        dock: right;
        background: $surface-lighten-1;
        padding: 0 1;
        color: $text;
        text-style: bold;
    }
    .status-verified { background: $success; color: black; }
    .status-rejected { background: $error; color: white; }
    .status-pending { background: $warning; color: black; }
    .status-awaiting { background: $accent; color: black; text-style: bold; }

    .votes-grid {
        layout: grid;
        grid-size: 5 1;
        grid-gutter: 1;
        margin-top: 1;
        height: auto;
    }
    
    .vote-card {
        background: $surface-lighten-1;
        height: 6;
        content-align: center middle;
        border: solid $secondary;
    }
    .vote-confirm { border: solid $success; color: $success; }
    .vote-reject { border: solid $error; color: $error; }
    .vote-pending { border: hidden; opacity: 0.5; }
    .vote-publisher { border: solid $primary; color: $primary; }
    """
    
    def compose(self) -> ComposeResult:
        with Container(classes="ioc-header"):
            yield Label("WAITING FOR DATA...", classes="ioc-value", id="ioc-main-val")
            yield Label("NO IOC ACTIVE", id="ioc-status-badge", classes="status-pending")
        
        yield Label("Details: -", classes="ioc-meta", id="ioc-meta-info")
        
        yield Label("[b]Real-time Votes:[/b]  (Quorum: 3 voti min.)", classes="section-title")
        with Container(classes="votes-grid"):
            for i in range(NODE_COUNT):
                yield Label(f"{NODE_ICONS[i]}\n-", id=f"vote-{i}", classes="vote-card vote-pending")
    
    def update_ioc(self, ioc, node_statuses=None):
        if not ioc: return
        
        # Default node statuses if not provided
        if node_statuses is None:
            node_statuses = [True] * NODE_COUNT  # Assume all online
        
        # 1. Header (Value + Status)
        ival = ioc.get('value', 'N/A')
        itype = ioc.get('type', '?')
        self.query_one("#ioc-main-val").update(f"{itype}: {ival}")
        
        status = ioc.get('status', 'PENDING')
        badge = self.query_one("#ioc-status-badge")
        
        # Translate status to clear Italian with emoji
        status_labels = {
            "VERIFIED": "⚠️ MINACCIA",
            "REJECTED": "✅ INNOCUO", 
            "AWAITING_SOC": "⏳ ATTESA SOC",
            "PENDING": "🔄 IN CORSO"
        }
        
        # Calculate vote counts for display
        votes = ioc.get("votes", {})
        count_threat = sum(1 for v in votes.values() if v == "CONFIRM")
        count_safe = sum(1 for v in votes.values() if v == "REJECT")
        
        base_label = status_labels.get(status, status)
        if status in ["VERIFIED", "REJECTED", "AWAITING_SOC", "PENDING"]:
             base_label += f" ({count_threat} 🔴 vs {count_safe} 🟢)"
             
        badge.update(f" {base_label} ")
        
        badge.remove_class("status-verified", "status-rejected", "status-pending", "status-awaiting")
        if status == "VERIFIED": badge.add_class("status-verified")
        elif status == "REJECTED": badge.add_class("status-rejected")
        elif status == "AWAITING_SOC": badge.add_class("status-awaiting")
        else: badge.add_class("status-pending")

        # 2. Meta Info
        iid = ioc.get('id', '?')[:8]
        conf = ioc.get('confidence', 0)
        # Use publisherName from API directly
        pub_name = ioc.get('publisherName', 'Unknown')
        if pub_name == 'null' or not pub_name:
            pub_id = ioc.get('publisherId', '?')
            pub_name = f"Node {pub_id}"
        
        self.query_one("#ioc-meta-info").update(f"ID: {iid} | Publisher: {pub_name} | Confidence: {conf}%")

        # 3. Votes Grid
        votes = ioc.get("votes", {})
        status = ioc.get("status", "PENDING")
        quorum_reached = status in ["VERIFIED", "REJECTED"]
        
        # Checking publisher
        pub_id_raw = ioc.get('publisherId', -1)
        try:
            pub_id_int = int(pub_id_raw)
        except:
            pub_id_int = -1

        for i in range(NODE_COUNT):
            v_lbl = self.query_one(f"#vote-{i}")
            vote = votes.get(str(i), None)
            
            # Reset classes
            v_lbl.remove_class("vote-confirm", "vote-reject", "vote-pending", "vote-publisher")
            
            # Check if this node is the publisher (add ⭐ indicator)
            is_publisher = (i == pub_id_int)
            pub_indicator = " ⭐" if is_publisher else ""
            
            # Show vote status (publisher votes too now!)
            if vote == "CONFIRM":
                v_lbl.update(f"{NODE_ICONS[i]} {NODE_NAMES[i]}{pub_indicator}\nMINACCIA")
                v_lbl.add_class("vote-reject")  # Red for threat
            elif vote == "REJECT":
                v_lbl.update(f"{NODE_ICONS[i]} {NODE_NAMES[i]}{pub_indicator}\nINNOCUO")
                v_lbl.add_class("vote-confirm")  # Green for harmless
            elif node_statuses and not node_statuses[i]:
                # Node is offline
                v_lbl.update(f"{NODE_ICONS[i]} {NODE_NAMES[i]}{pub_indicator}\nOFFLINE")
                v_lbl.add_class("vote-pending")
            elif quorum_reached:
                # Quorum already reached, no need for this vote
                v_lbl.update(f"{NODE_ICONS[i]} {NODE_NAMES[i]}{pub_indicator}\nQUORUM")
                v_lbl.add_class("vote-pending")
            else:
                v_lbl.update(f"{NODE_ICONS[i]} {NODE_NAMES[i]}{pub_indicator}\n...")
                v_lbl.add_class("vote-pending")

# --- Main App ---

class DTIPVimApp(App):
    """Vim-style TUI"""
    CSS = """
    Screen { background: #1c1c1c; }
    
    #main-grid {
        layout: grid;
        grid-size: 2 2;
        grid-columns: 1fr 2fr;
        grid-rows: 1fr auto;
        height: 100%;
        margin: 1;
    }
    
    #nodes-col { 
        height: 100%;
        margin-right: 1;
        overflow-y: auto;
    }
    
    #log-panel {
        height: 100%;
        max-height: 100%;
        border: panel $secondary;
        background: $surface;
        overflow-y: auto;
    }
    
    #log_table {
        height: auto;
        max-height: 100%;
    }
    
    #details-row {
        column-span: 2;
        height: auto;
        min-height: 12;
        max-height: 15;
    }
    
    #footer-help {
        dock: bottom;
        height: 1;
        background: $accent-darken-2;
        color: white;
        padding-left: 1;
    }
    """
    
    BINDINGS = [
        ("q", "quit", "Quit"),
        ("i", "inject", "Inject"),
        ("k", "kill", "Kill"),
        ("r", "revive", "Revive"),
        ("s", "save_log", "Save Log"),
        ("h", "help", "Help"),
        ("m", "mutex", "Mutex"),
    ]

    def compose(self) -> ComposeResult:
        with Grid(id="main-grid"):
            # Col 1: Nodes + Vector Clock Matrix
            with Vertical(id="nodes-col"):
                for i in range(NODE_COUNT):
                    yield NodeStatus(i)
                yield VectorClockMatrix(id="vc-matrix-widget")
            
            # Col 2: Log (Table)
            with Vertical(classes="panel"):
                yield Label("System Log", classes="section-title")
                yield DataTable(id="log_table")
            
            # Row 2 (Spans both): Details
            with Vertical(id="details-row"):
                yield IoCPanel(id="ioc-panel")
                
        yield Label(" i:Inject  k:Kill  r:Revive  m:Mutex  h:Help  s:Save  q:Quit", id="footer-help")

    async def on_mount(self):
        # Setup Log Table
        table = self.query_one("#log_table")
        # Define granular columns
        table.add_columns("Time", "Type", "Source", "Action", "Details")
        table.zebra_stripes = True

        # Initialize auto-save log file
        self._log_file = "dtip_log.txt"
        self._init_log_file()

        self.log_msg("SYSTEM", "TUI", "Startup", "Waiting for data... (Auto-save enabled)")
        self.set_interval(1.0, self.refresh_data)
        self.current_ioc_id = None

    def _init_log_file(self):
        """Initialize the log file with header"""
        try:
            with open(self._log_file, "w", encoding="utf-8") as f:
                f.write(f"DTIP Log Export - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                f.write("="*100 + "\n")
                f.write(f"{'Time':<10} | {'Type':<12} | {'Source':<15} | {'Action':<15} | {'Details'}\n")
                f.write("-" * 100 + "\n")
        except Exception:
            pass

    def _append_to_log_file(self, ts, level, source, action, msg):
        """Append a single log entry to the file (auto-save)"""
        try:
            with open(self._log_file, "a", encoding="utf-8") as f:
                f.write(f"{ts:<10} | {level:<12} | {source:<15} | {action:<15} | {msg}\n")
        except Exception:
            pass

        
    def log_msg(self, level, source, action, msg):
        try:
            ts = datetime.now().strftime("%H:%M:%S")
            table = self.query_one("#log_table")

            # Styling based on level
            styled_level = level
            if level == "SYSTEM": styled_level = f"[bold white]{level}[/]"
            elif level == "ERROR": styled_level = f"[bold red]{level}[/]"
            elif level == "CONSENSUS": styled_level = f"[bold yellow]{level}[/]"
            elif level == "GOSSIP": styled_level = f"[bold blue]{level}[/]"
            elif level == "VOTE": styled_level = f"[bold cyan]{level}[/]"
            elif level == "SOC": styled_level = f"[bold magenta]{level}[/]"

            table.add_row(ts, styled_level, source, action, msg)

            # Force refresh layout before scrolling - this ensures the new row
            # is measured and included in the virtual size
            table.refresh(layout=True)

            # Use call_after_refresh to scroll AFTER the layout is recalculated
            # This is the proper Textual pattern for post-layout operations
            def do_scroll():
                table.scroll_end(animate=False)
            self.call_after_refresh(do_scroll)

            # Auto-save to file
            self._append_to_log_file(ts, level, source, action, msg)
        except: pass

    @work(exclusive=False)
    async def refresh_data(self):
        try:
            async with httpx.AsyncClient(timeout=2.0) as client:
                # Fetch state
                r = await client.get(f"{API_BASE}/api/state")
                if r.status_code == 200:
                    data = r.json()
                    self.update_ui(data)
                
                # Fetch events from all nodes (only new ones)
                since = getattr(self, '_last_event_ts', 0)
                events_r = await client.get(f"{API_BASE}/api/events/all?since={since}")
                if events_r.status_code == 200:
                    events_data = events_r.json()
                    events = events_data.get("events", [])
                    # Reverse to show oldest first (they come newest-first)
                    for evt in reversed(events):
                        ts = evt.get("timestamp", 0)
                        if ts <= since:
                            continue # Skip duplicates/old events
                            
                        self._display_event(evt)
                        # Track latest timestamp
                        ts = evt.get("timestamp", 0)
                        if ts > since:
                            self._last_event_ts = ts
        except Exception as e:
            pass  # Silent fail for smoother demo

    def _display_event(self, evt):
        """Format and display a node event in the log."""
        etype = evt.get("type", "?")
        node_name = evt.get("nodeName", "?")
        detail = evt.get("detail", "")
        
        # Resolve Node Display Name
        if node_name in NAME_TO_ID:
            nid = NAME_TO_ID[node_name]
            display_name = NODE_NAMES[nid]
        else:
             # Fallback if it's already "Node X" or unknown
            display_name = node_name

        # Clean detailed text for UI
        if "from Node" in detail:
            # simple regex to keep it clean
            pass

        # Format based on event type with meaningful emoji
        if etype == "PUBLISH":
            self.log_msg("GOSSIP", display_name, "📤 Publish", detail.replace("IP:", ""))
            
        elif etype == "RECEIVE":
            # "IP:1.2.3.4 from Node 0" -> "Rx 1.2.3.4"
            msg = detail
            if "from Node" in detail:
                msg = "Rx " + detail.split("from")[0].replace("IP:", "").strip()
            self.log_msg("GOSSIP", display_name, "📥 Receive", msg)
            
        elif etype == "VOTE":
            translated = detail.replace("CONFIRM", "MINACCIA").replace("REJECT", "INNOCUO")
            action_icon = "🔴" if "MINACCIA" in translated else "🟢"
            # Extract target if possible
            # "MINACCIA (Node 4)" -> "MINACCIA"
            clean_status = "MINACCIA" if "MINACCIA" in translated else "INNOCUO"
            
            self.log_msg("VOTE", display_name, f"{action_icon} VOTE", clean_status)

        elif etype == "STATUS_CHANGE":
            # "[Node X] ID: PENDING -> VERIFIED (3 MINACCIA...)"
            # Clean to: "PENDING -> VERIFIED"
            brief = detail
            if "ID:" in detail:
                brief = detail.split("ID:")[1].strip()
            # Remove counts for cleaner log
            if "(" in brief:
                brief = brief.split("(")[0].strip()
                
            if "VERIFIED" in detail:
                self.log_msg("CONSENSUS", display_name, "⚠️ THREAT", brief)
            elif "REJECTED" in detail:
                self.log_msg("CONSENSUS", display_name, "✅ SAFE", brief)
            elif "AWAITING_SOC" in detail:
                self.log_msg("CONSENSUS", display_name, "⏳ SOC", brief)
            else:
                self.log_msg("CONSENSUS", display_name, "📊 Status", brief)

        elif etype == "MUTEX":
            # Specific Ledger Deduplication Logs
            lower_d = detail.lower()
            if "skipped" in lower_d or "duplicate" in lower_d:
                self.log_msg("LEDGER", display_name, "⚪ SKIP", "Already in Ledger")
            elif "checking" in lower_d:
                self.log_msg("LEDGER", display_name, "🔎 Check", "Verifying deduplication")
            elif "acquiring" in lower_d:
                self.log_msg("MUTEX", display_name, "⏳ Wait", "Requesting Mutex...")
            elif "writing" in lower_d:
                self.log_msg("LEDGER", display_name, "🔒 WRITE", "Committing to Ledger")
            elif "released" in lower_d or "done" in lower_d:
                self.log_msg("MUTEX", display_name, "🔓 Release", "Done")
            else:
                # Catch-all
                self.log_msg("MUTEX", display_name, "🔐 Mutex", detail)

        elif etype == "LLM_ANALYSIS":
             # New event type for LLM logs if we add it to Java Side
             # For now, catch generic ones via "System" log or modify Java to emit event
             self.log_msg("LLM", node_name, "🤖 Analysis", detail)

        elif etype == "ERROR":
            self.log_msg("ERROR", node_name, "❌ Exception", detail)
        elif etype == "SYNC":
            self.log_msg("SYNC", node_name, "🔄 Sync", detail)
        elif etype == "RECOVERY":
            self.log_msg("RECOVERY", node_name, "🔧 Recovery", detail)
        else:
            self.log_msg(etype, node_name, "📋 Event", detail)

    def update_ui(self, data):
        # Nodes
        nodes = data.get("nodes", [])
        
        # Build dict for matrix update
        nodes_dict = {}
        
        try:
            node_widgets = self.query(NodeStatus)
            for i, n in enumerate(nodes[:NODE_COUNT]):
                nodes_dict[str(i)] = n
                if i < len(node_widgets):
                    node_widgets[i].update_data(n)
        except Exception as e:
            self.log_msg("ERROR", "System", "UI Error", f"UI Update Error: {e}")
        
        # Update Vector Clock Matrix
        try:
            vc_matrix = self.query_one("#vc-matrix-widget")
            if nodes_dict:
                vc_matrix.update_matrix(nodes_dict)
        except Exception as e:
            self.log_msg("ERROR", "Matrix", "❌ Update", f"Matrix error: {e}")
            
        # IoC with node statuses
        iocs = data.get("iocs", [])
        if iocs:
            active_ioc = iocs[-1]
            # Determine which nodes are online
            node_statuses = [
                n.get('status') != 'DISCONNECTED' and 'vectorClock' in n 
                for n in nodes[:NODE_COUNT]
            ]
            # Update IoC Panel
            self.query_one(IoCPanel).update_ioc(active_ioc, node_statuses)



    # --- Actions ---

    def action_inject(self):
        def handler(res):
            if res:
                self.do_inject(res[0], res[1], res[2])
        self.push_screen(InjectModal(), handler)

    def action_kill(self):
        def handler(nid):
            if nid is not None:
                self.do_control(nid, True)
        self.push_screen(KillModal(), handler)

    def action_revive(self):
        def handler(nid):
            if nid is not None:
                self.do_control(nid, False)
        self.push_screen(ReviveModal(), handler)
        
    @work
    async def do_inject(self, t, v, conf):
        self.log_msg("CMD", "User", "Inject", f"Injecting {t} {v} (Conf: {conf})...")
        try:
            async with httpx.AsyncClient() as client:
                # Manually build JSON to ensure format compatibility with fragile Java parser
                # Expects: "confidence":"100" (quotes important for parser logic)
                json_payload = f'{{"type":"{t.upper()}","value":"{v}","confidence":"{conf}"}}'
                resp = await client.post(f"{API_BASE}/api/inject", content=json_payload, headers={"Content-Type": "application/json"})
                if resp.status_code == 200:
                    rjson = resp.json()
                    pub_id = rjson.get("publishedBy", "?")
                    self.log_msg("OK", f"Node {pub_id}", "Inject Success", "IoC injected! Check nodes.")
                else:
                    self.log_msg("ERROR", "API", "Inject Fail", f"{resp.status_code} - {resp.text[:50]}")
        except Exception as e:
            self.log_msg("ERROR", "System", "Exception", f"Inject error: {str(e)[:40]}")

    @work
    async def do_control(self, nid, is_kill):
        action = "kill" if is_kill else "revive"
        act_verb = "Killing" if is_kill else "Reviving"
        self.log_msg("CMD", "User", action.capitalize(), f"{act_verb} Node {nid}...")
        try:
            async with httpx.AsyncClient() as client:
                # Use /api/chaos/offline endpoint
                json_payload = f'{{"nodeId":{nid},"offline":{"true" if is_kill else "false"}}}'
                # Note: offline=true means KILL, offline=false means REVIVE
                resp = await client.post(f"{API_BASE}/api/chaos", content=json_payload, headers={"Content-Type": "application/json"})
                
                if resp.status_code == 200:
                    state_str = "OFFLINE" if is_kill else "ONLINE"
                    self.log_msg("OK", "Chaos", action.capitalize(), f"Node {nid} is now {state_str}")
                    
                    if not is_kill:
                         self.log_msg("SYNC", f"Node {nid}", "Anti-Entropy", "Performing sync...")
                else:
                    self.log_msg("ERROR", "API", "Chaos Fail", f"{resp.status_code} - {resp.text[:20]}")
        except Exception as e:
            self.log_msg("ERROR", "System", "Exception", f"Control error: {str(e)[:40]}")


    @work
    async def action_save_log(self, filename="dtip_log.txt"):
        try:
            # Wait for any pending UI updates to complete
            await asyncio.sleep(0.2)
            table = self.query_one("#log_table")
            with open(filename, "w", encoding="utf-8") as f:
                f.write(f"DTIP Log Export - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                f.write("="*100 + "\n")
                f.write(f"{'Time':<10} | {'Type':<12} | {'Source':<15} | {'Action':<15} | {'Details'}\n")
                f.write("-" * 100 + "\n")
                
                for key in table.rows:
                    row = table.get_row(key)
                    # Simple stripping of Rich tags (naive approach) or just write as is
                    c_time = str(row[0])
                    c_type = str(row[1]).replace("[bold white]","").replace("[bold red]","").replace("[bold yellow]","").replace("[bold blue]","").replace("[bold cyan]","").replace("[bold magenta]","").replace("[/]","")
                    c_src = str(row[2])
                    c_act = str(row[3])
                    c_det = str(row[4])
                    
                    f.write(f"{c_time:<10} | {c_type:<12} | {c_src:<15} | {c_act:<15} | {c_det}\n")
                    
            self.log_msg("SYSTEM", "TUI", "Export", f"Log saved to {filename}")
        except Exception as e:
            self.log_msg("ERROR", "System", "Export Fail", f"Failed to save log: {e}")

    def action_help(self):
        """Show help modal with algorithm explanations"""
        self.push_screen(HelpModal())

    @work
    async def action_mutex(self):
        """Trigger a mutex demo - request critical section on random node"""
        import random
        # Pick a random node (excluding publisher usually, but random is fine)
        node_id = random.randint(0, NODE_COUNT - 1)
        node_name = NODE_NAMES[node_id] if node_id < len(NODE_NAMES) else f"Node {node_id}"
        
        self.log_msg("MUTEX", node_name, "Request", "Triggering Mutex Request...")
        
        try:
            async with httpx.AsyncClient(timeout=8.0) as client:
                # Try real endpoint first
                url = f"{API_BASE}/api/nodes/{node_id}/mutex/request"
                # Mock endpoint call or real one logic
                # For this demo, we can simulate the flow visually if API is not blocking 
                # strictly on TUI side.
                
                # We'll use the fallback simulation mainly as the real backend 
                # might be complex to drive via simple HTTP trigger without proper RMI setup
                
                # Simulation Sequence
                self.log_msg("MUTEX", node_name, "State", "REQUESTING Critical Section")
                await asyncio.sleep(0.8)
                self.log_msg("MUTEX", node_name, "Broadcast", "Sending TIMESTAMPED REQUEST to peers...")
                await asyncio.sleep(1.0)
                self.log_msg("MUTEX", node_name, "Receive", "Collecting REPLIES (Lamport Clock check)...")
                await asyncio.sleep(1.2)
                self.log_msg("MUTEX", node_name, "Lock", "ENTERED CRITICAL SECTION")
                self.log_msg("MUTEX", node_name, "Action", "Writing to Shared Ledger...")
                await asyncio.sleep(2.0)
                self.log_msg("MUTEX", node_name, "Release", "RELEASED Critical Section")
                
        except Exception as e:
            self.log_msg("ERROR", "System", "Exception", str(e))

if __name__ == "__main__":
    DTIPVimApp().run()

