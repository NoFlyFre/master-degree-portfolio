# Riassunto Modulo 06: Code Management (Git)

## 1. Configurazione Iniziale
```bash
git config --global user.name "Nome Cognome"
git config --global user.email "you@example.com"
git config --global core.editor /usr/bin/vim
```
> ⚠️ Scrivere `email`, non `mail`!

## 2. Comandi Base Git
- **Branch**: `git branch`, `git checkout <nome>`, `git checkout -b <nuovo>`, `git checkout -` (precedente)
- **Tag**: `git tag`, `git log <tag>`, `git tag --contains <hash>`
- **Stato**: `git status`, `git log`, `git log -p`, `git log --oneline`, `git show <commit>`
- **Modifiche**: `git add <file>`, `git add -p`, `git commit`, `git commit -a`, `git commit -as`
- **Diff**: `git diff` (non staged), `git diff --cached` (staged)
- **Reset**:
    - `git reset --hard HEAD`: Scarta modifiche locali
    - `git reset --hard <commit>`: Torna a un commit (DISTRUTTIVO)
    - `git reset HEAD~`: Elimina ultimo commit mantenendo modifiche
    - `git reflog`: Cronologia movimenti HEAD (per recuperare)
- **Pulizia**: `git clean -fd`

## 3. Repository Remoti
```bash
git remote                   # Lista repo remoti
git remote -v                # Con URL
git remote add <nome> <URL>  # Aggiungi remoto
git pull                     # Scarica e merge
git fetch                    # Scarica senza merge
git push origin <branch>     # Push
git push origin <hash>:<branch>  # Push parziale
git checkout --track origin/<branch>  # Checkout con tracking
```

## 4. Tecniche Avanzate
- **Stash**: `git stash`, `git stash list`, `git stash pop`, `git stash apply`
- **Amend**: `git commit --amend` (modifica ultimo commit)
- **Spezzare un commit**:
    1. `git reset HEAD~` (mantiene modifiche)
    2. `git add -p` (aggiungi hunk)
    3. `git commit`
    4. Ripeti per altri commit
- **Rebase Interattivo**: `git rebase -i HEAD~5`
    - `pick`, `reword`, `edit`, `squash`, `drop`
- **Cherry-pick**: `git cherry-pick <commit>`, `git cherry-pick <hash1>~..<hash2>`
- **Revert**: `git revert <commit>` (annulla mantenendo storia)

## 5. Merge
```bash
git checkout branch_dest
git merge branch_src
# Se conflitti:
git diff                     # Vedi conflitti
git add <file>               # Segna risolto
git commit                   # Completa merge
```
**Alternative**: cherry-pick, rebase

## 6. Patch
```bash
# Creare
git format-patch HEAD~1
git format-patch --cover-letter HEAD~3

# Applicare
git am <patch.patch>
git am -3 <patch.patch>      # Con aiuto conflitti
git am --continue            # Dopo risoluzione
git am --abort               # Abbandona
```

## 7. Workflow Linux Kernel
```bash
./scripts/checkpatch.pl <patch>       # Verifica stile
./scripts/get_maintainer.pl <patch>   # Destinatari
git send-email *.patch --to="..." --cc=linux-kernel@vger.kernel.org
```

## 8. Lavorare su Due Branch
```bash
git show altro_branch:file.c          # Vedere file senza cambiare
git diff altro_branch                 # Diff con altro branch
git diff branch1:file1 branch2:file2 > diff.patch
git apply diff.patch
```

## 9. Best Practices Commit Message
```
<sottosistema>: <descrizione imperativa>

Contesto del problema.
Motivazione della soluzione.

Signed-off-by: Nome Cognome <email>
```
✅ `block, bfq: fix memory leak in merge`
❌ `Fixed bug`

## 10. Compilazione Incrementale
- **Problema**: Cambio branch → timestamp cambiano → make ricompila tutto
- **Soluzione 1**: `make -t O=<build_dir>` (touch fittizio)
- **Soluzione 2**: `git worktree add ../dir branch` (directory separate)
> ⚠️ Non funziona se `CONFIG_STACK_VALIDATION` è abilitato
