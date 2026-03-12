# UIB Advanced Algorithms - Agent Guide

**Course:** Algorismes Avançats, Universitat de les Illes Balears (UIB), 2025-2026

---

## What This Is

A university course repository containing multiple **independent** Java/Maven assignments. Each `PX/` folder is one complete, self-contained assignment with its own Java source, build config, and LaTeX report. There are no cross-project dependencies.

- **Language:** Java 16, build with Maven
- **Reports:** LaTeX (IEEE conference format, 2-column)
- **Package naming:** `com.serafinebot.pX`
- **Style:** Prefer simple, general solutions over complex or boilerplate-heavy ones

---

## Repository Structure

```
AA/
├── AGENTS.md
├── ieee-template/
│   ├── IEEEtran.cls            # IEEE document class (shared source)
│   └── uib-aa-template.tex     # Report template (USE THIS for new reports)
├── P1/                         # Assignment 1
├── P2/                         # Assignment 2
└── PX/                         # Pattern for all assignments
    ├── pom.xml
    ├── .gitignore
    ├── src/main/java/com/serafinebot/pX/
    │   ├── Main.java
    │   ├── model/
    │   ├── controller/
    │   └── view/
    └── memoria/
        ├── memoria.tex         # LaTeX source
        ├── memoria.pdf         # Compiled report
        ├── IEEEtran.cls        # Local copy (required for compilation)
        └── *.png               # Images/diagrams
```

---

## Starting a New Assignment

```bash
X=2  # Replace with assignment number
mkdir -p P${X}/{src/{main,test}/java/com/serafinebot/p${X},memoria}
cp ieee-template/{IEEEtran.cls,uib-aa-template.tex} P${X}/memoria/
mv P${X}/memoria/uib-aa-template.tex P${X}/memoria/memoria.tex
cp P1/{pom.xml,.gitignore} P${X}/
```

Then edit:
- `PX/pom.xml` — change `<artifactId>` and `<groupId>` to match PX
- `PX/memoria/memoria.tex` — update title, author, abstract, content

---

## Validation Commands

```bash
# Compile Java (run from PX/)
mvn clean compile

# Run main class
mvn exec:java -Dexec.mainClass="com.serafinebot.pX.Main"

# Compile LaTeX report (run from PX/memoria/) — must run TWICE for references
pdflatex memoria.tex && pdflatex memoria.tex
```

---

## Report Structure

Reports use `\documentclass[conference,compsoc]{IEEEtran}` (two-column, Computer Society style) and are written in Catalan. The template includes syntax-highlighted Java listings, booktabs tables, and a standard bibliography.

Standard sections:
1. **Introducció** — objectives and context
2. **Fonaments Teòrics** — theory, background, complexity analysis
3. **Disseny i Implementació** — architecture, MVC components, implementation
4. **Resultats i Discussió** — experiments, measurements, analysis
5. **Conclusions** — summary and future work
6. **Bibliografia** — references

Key LaTeX notes:
- Images go in `PX/memoria/` (same dir as `.tex`)
- Single-column figures: `\includegraphics[width=\columnwidth]{...}`
- Full-width figures: use `figure*` environment with `\textwidth`
- Labels: `fig:`, `tab:`, `eq:`, `lst:` — reference with `~\ref{}`

---

## pom.xml Template

```xml
<groupId>com.serafinebot.pX</groupId>
<artifactId>PX</artifactId>
<version>1.0-SNAPSHOT</version>
<properties>
    <maven.compiler.source>16</maven.compiler.source>
    <maven.compiler.target>16</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```
