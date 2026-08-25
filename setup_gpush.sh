#!/data/data/com.termux/files/usr/bin/bash
# MuwanChat: auto-sync git push helper installer
# Fixes: CI bot ka version-bump commit + manual push clash

RC_FILE="$HOME/.bashrc"
MARKER="# MuwanChat git push shortcut"

if grep -qF "$MARKER" "$RC_FILE" 2>/dev/null; then
    echo "[*] gpush already installed in $RC_FILE — kuch nahi kiya."
else
    cat << 'FUNC' >> "$RC_FILE"

# MuwanChat git push shortcut — auto pulls remote CI commits before pushing
gpush() {
    if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
        echo "[-] Yeh git repo nahi hai."
        return 1
    fi
    echo "[*] Pulling latest (rebase)..."
    if git pull --rebase; then
        echo "[*] Pushing..."
        git push
    else
        echo "[-] Pull/rebase mein conflict aaya. 'git status' check karo,"
        echo "    resolve karo, 'git rebase --continue', fir 'gpush' phir se chalao."
        return 1
    fi
}
FUNC
    echo "[+] gpush() add ho gaya $RC_FILE mein."
fi

source "$RC_FILE"
echo "[+] Done. Ab sirf 'gpush' chalao commit ke baad — 'git push' ki jagah."
