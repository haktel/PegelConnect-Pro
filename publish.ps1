$ErrorActionPreference = "Stop"

$repo = "https://github.com/haktel/PegelConnect-Pro.git"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git ist nicht installiert oder nicht im PATH."
}

if (-not (Test-Path ".git")) {
    git init
    git branch -M main
}

$remote = git remote 2>$null
if ($remote -notcontains "origin") {
    git remote add origin $repo
} else {
    git remote set-url origin $repo
}

git add .
git commit -m "feat: initial PegelConnect Pro application"
git push -u origin main
