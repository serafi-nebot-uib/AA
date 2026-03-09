# UIB Advanced Algorithms - Complete Guide for AI Agents

**Repository:** Advanced Algorithms Course (Algorismes Avançats)  
**Institution:** Universitat de les Illes Balears (UIB)  
**Academic Year:** 2025-2026  
**Version:** 1.0 (2026-03-09)

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Repository Overview](#repository-overview)
3. [Creating New Assignments](#creating-new-assignments)
4. [IEEE Template Guide](#ieee-template-guide)
5. [Java/Maven Project Setup](#javamaven-project-setup)
6. [LaTeX Examples](#latex-examples)
7. [Troubleshooting](#troubleshooting)
8. [For AI Assistants](#for-ai-assistants)

---

## Quick Reference

### TL;DR - Start a New Assignment

```bash
# Replace "2" with your assignment number
X=2

# One-command setup
mkdir -p P${X}/{src/{main,test}/java/com/serafinebot/p${X},memoria} && \
cp ieee-template/{IEEEtran.cls,uib-aa-template.tex} P${X}/memoria/ && \
mv P${X}/memoria/uib-aa-template.tex P${X}/memoria/memoria.tex && \
cp P1/{pom.xml,.gitignore} P${X}/ && \
echo "✅ P${X} structure created! Now edit pom.xml and memoria.tex"

# Then:
# 1. Edit P${X}/pom.xml (change artifactId and groupId)
# 2. Edit P${X}/memoria/memoria.tex (title, name, content)
# 3. Build: cd P${X} && mvn compile
# 4. Compile report: cd memoria && pdflatex memoria.tex (twice)
```

### Essential Commands

```bash
# Java compilation
cd PX/
mvn clean compile
mvn exec:java -Dexec.mainClass="com.serafinebot.pX.Main"

# LaTeX compilation
cd PX/memoria/
pdflatex memoria.tex
pdflatex memoria.tex  # Run TWICE for references
```

### Key Files

| File | Purpose |
|------|---------|
| `PX/pom.xml` | Maven build configuration |
| `PX/src/main/java/com/serafinebot/pX/Main.java` | Application entry point |
| `PX/memoria/memoria.tex` | Academic report (LaTeX source) |
| `PX/memoria/memoria.pdf` | Compiled report |
| `ieee-template/uib-aa-template.tex` | Template for new reports |

---

## Repository Overview

### Structure

```
/Users/serafi/Documents/uib/CS/Y3/AA/
├── AGENTS.md                           # This file
├── .gitignore                          # Repository-level ignores
├── ieee-template/                      # 🎨 Shared LaTeX template
│   ├── IEEEtran.cls                   # IEEE document class (REQUIRED)
│   ├── uib-aa-template.tex            # ⭐ Custom template (USE THIS!)
│   └── ...
├── P1/                                 # ✅ Assignment 1 (completed)
│   ├── pom.xml                        # Maven config
│   ├── src/main/java/com/serafinebot/p1/
│   └── memoria/
│       ├── memoria.tex
│       ├── memoria.pdf
│       └── IEEEtran.cls
├── P2/                                 # 🔜 Assignment 2 (future)
└── PX/                                 # 🔜 Assignment X (pattern)
```

### Technology Stack

- **Language:** Java (version 16)
- **Build Tool:** Maven
- **Documentation:** LaTeX (IEEE conference format)
- **IDE:** IntelliJ IDEA (project files included)

### Key Principles

1. **Project Independence:** Each `PX/` folder is a completely independent Java/Maven project
2. **No Shared Code:** Projects do not depend on each other
3. **Self-Contained Reports:** Each `memoria/` folder has its own copy of `IEEEtran.cls`
4. **Consistent Structure:** All projects follow the same directory pattern
5. **Standard Naming:** Package structure is always `com.serafinebot.pX`

---

## Creating New Assignments

### Option 1: One-Command Setup

```bash
X=2  # Replace with your assignment number
mkdir -p P${X}/{src/{main,test}/java/com/serafinebot/p${X},memoria} && \
cp ieee-template/{IEEEtran.cls,uib-aa-template.tex} P${X}/memoria/ && \
mv P${X}/memoria/uib-aa-template.tex P${X}/memoria/memoria.tex && \
cp P1/{pom.xml,.gitignore} P${X}/ && \
echo "✅ P${X} structure created!"
```

### Option 2: Step-by-Step Manual Setup

```bash
# Step 1: Create directory structure
mkdir -p P2/src/main/java/com/serafinebot/p2
mkdir -p P2/src/test/java
mkdir -p P2/memoria

# Step 2: Copy IEEE template files
cp ieee-template/IEEEtran.cls P2/memoria/
cp ieee-template/uib-aa-template.tex P2/memoria/memoria.tex

# Step 3: Copy build configuration
cp P1/pom.xml P2/pom.xml
cp P1/.gitignore P2/.gitignore

# Step 4: Edit P2/pom.xml
# Change:
#   <artifactId>P1</artifactId> → <artifactId>P2</artifactId>
#   <groupId>com.serafinebot.p1</groupId> → <groupId>com.serafinebot.p2</groupId>

# Step 5: Edit P2/memoria/memoria.tex
# Replace:
#   [Títol de la Pràctica] → Your project title
#   Pràctica X → Pràctica 2
#   [Nom de l'Estudiant] → Your name

# Step 6: Create Main.java
cat > P2/src/main/java/com/serafinebot/p2/Main.java << 'EOF'
package com.serafinebot.p2;

public class Main {
    public static void main(String[] args) {
        System.out.println("P2 - [Project Title]");
    }
}
EOF

# Step 7: Test Java build
cd P2/
mvn compile
mvn exec:java -Dexec.mainClass="com.serafinebot.p2.Main"

# Step 8: Test LaTeX compilation
cd memoria/
pdflatex memoria.tex
pdflatex memoria.tex  # Run twice for references
```

### Standard Directory Structure (per assignment)

```
PX/
├── pom.xml                             # Maven build configuration
├── .gitignore                          # Project-specific Git ignores
├── src/                                # Java source code
│   ├── main/
│   │   └── java/
│   │       └── com/serafinebot/pX/    # Package structure
│   │           ├── Main.java          # Entry point
│   │           ├── model/             # Data and business logic
│   │           ├── controller/        # Control flow
│   │           └── view/              # User interface
│   └── test/                           # Unit tests (optional)
│       └── java/
├── memoria/                            # Academic report
│   ├── memoria.tex                     # LaTeX source
│   ├── memoria.pdf                     # Compiled PDF
│   ├── IEEEtran.cls                    # IEEE document class copy
│   └── *.png / *.dot                   # Diagrams, screenshots
├── target/                             # Maven build output (gitignored)
├── .idea/                              # IntelliJ IDEA settings (gitignored)
└── video/                              # Demo videos (optional)
```

---

## IEEE Template Guide

### Template Overview

The `ieee-template/uib-aa-template.tex` file is a **custom IEEE conference template** with formatting from P1. It includes:

✅ Conference format (2 columns, Computer Society style)  
✅ Catalan language support  
✅ Bold italic abstract/keywords labels  
✅ Full-width abstract (strip environment)  
✅ Java code syntax highlighting  
✅ Professional table formatting (booktabs)  
✅ Precise figure positioning (float package)  
✅ Pre-included bibliography entries  

### Quick Start

```bash
# Always use the custom template
cp ieee-template/IEEEtran.cls PX/memoria/
cp ieee-template/uib-aa-template.tex PX/memoria/memoria.tex
```

### Document Class

```latex
\documentclass[conference,compsoc]{IEEEtran}
```

- `conference` → Two-column layout (not journal)
- `compsoc` → Computer Society style

### Catalan Language

The template automatically uses Catalan for abstract and keywords:

```latex
\renewcommand{\abstractname}{Resum}
\renewcommand{\IEEEkeywordsname}{Termes d'índex}
```

Catalan characters (à, è, í, ò, ú, ç) work automatically with UTF-8 encoding.

### Template Structure

```latex
\begin{document}

\title{Your Title \\ Pràctica X}
\author{\IEEEauthorblockN{Your Name}
\IEEEauthorblockA{Universitat de les Illes Balears (UIB)\\
Grau en Enginyeria Informàtica\\
Algorismes Avançats, Curs 2025--2026}}

\maketitle

\begin{strip}
\begin{abstract}
Your abstract text here.
\end{abstract}

\begin{IEEEkeywords}
keyword1, keyword2, keyword3
\end{IEEEkeywords}
\end{strip}

\IEEEpeerreviewmaketitle

\section{Introducció}
...

\section{Fonaments Teòrics}
...

\section{Disseny i Implementació}
...

\section{Resultats i Discussió}
...

\section{Conclusions}
...

\begin{thebibliography}{9}
\bibliographystyle{IEEEtran}
\bibitem{ref1} ...
\end{thebibliography}

\end{document}
```

### Standard Report Sections

1. **Introducció** - Introduction and objectives
2. **Fonaments Teòrics** - Theoretical foundations
   - Background theory
   - Related work
   - Mathematical foundations
3. **Disseny i Implementació** - Design and implementation
   - Architecture/design patterns
   - Component descriptions
   - Implementation details
4. **Resultats i Discussió** - Results and discussion
   - Experimental results
   - Analysis
   - Discussion of findings
5. **Conclusions** - Conclusions and future work
6. **Bibliografia** - References

### Code Listings

Pre-configured for Java with syntax highlighting:

```latex
\begin{lstlisting}[caption={Algorithm implementation.}]
public class Example {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
\end{lstlisting}
```

**Features:**
- Blue bold keywords
- Gray italic comments
- Red strings
- Line numbers on left
- Single frame border
- Automatic line breaking

To change language, modify `language=Java` in the preamble's `\lstset` configuration.

### Pre-Included Bibliography

Common references already in the template:

- **ref_cormen** - Cormen et al., Introduction to Algorithms (4th ed., 2022)
- **ref_sedgewick** - Sedgewick & Wayne, Algorithms (4th ed., 2011)
- **ref_knuth** - Knuth, The Art of Computer Programming (Vol. 1, 3rd ed., 1997)
- **ref_mvc** - Gamma et al., Design Patterns (GoF, 1994)
- **ref_observer** - Buschmann et al., POSA (1996)
- **ref_swing** - Java Swing (O'Reilly, 2002)
- **ref_java** - Oracle Java Tutorials - Concurrency

Cite with: `\cite{ref_cormen}` or multiple: `\cite{ref_cormen,ref_knuth}`

---

## Java/Maven Project Setup

### Maven Configuration (pom.xml)

Each project's `pom.xml` should define:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.serafinebot.pX</groupId>
    <artifactId>PX</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>16</maven.compiler.source>
        <maven.compiler.target>16</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <!-- Add dependencies here if needed -->
</project>
```

### Maven Commands

```bash
# Navigate to project
cd PX/

# Compile
mvn compile

# Run tests
mvn test

# Package
mvn package

# Clean build artifacts
mvn clean

# Run main class
mvn exec:java -Dexec.mainClass="com.serafinebot.pX.Main"

# Clean and compile
mvn clean compile
```

### Package Structure

Standard Java package structure:

```
com.serafinebot.pX/
├── Main.java              # Entry point
├── model/                 # Data and business logic
│   ├── AlgorithmType.java
│   ├── DataStructure.java
│   └── Model.java
├── controller/            # Control flow
│   └── Controller.java
└── view/                  # User interface
    ├── MainView.java
    └── ComponentPanel.java
```

### Main.java Template

```java
package com.serafinebot.pX;

public class Main {
    public static void main(String[] args) {
        System.out.println("PX - Project Title");
        
        // Initialize your application
        // Model model = new Model();
        // View view = new View(model);
        // Controller controller = new Controller(model, view);
        // view.setVisible(true);
    }
}
```

---

## LaTeX Examples

### Equations

```latex
\begin{equation}
\label{eq:complexity}
T(n) = c \cdot f(n)
\end{equation}
```

Reference: `segons l'equació~\eqref{eq:complexity}` or `(\ref{eq:complexity})`

**Multiple equations:**

```latex
\begin{align}
f(n) &= n \label{eq:linear} \\
g(n) &= n^2 \label{eq:quadratic} \\
h(n) &= n \log n \label{eq:nlogn}
\end{align}
```

### Tables

**Simple table:**

```latex
\begin{table}[!h]
  \caption{Comparison of algorithms.\label{tab:comparison}}
  \centering
  \begin{tabular}{lcc}
    \toprule
    Algorithm & Complexity & Time (ms) \\
    \midrule
    Linear & $O(n)$ & 10 \\
    Quadratic & $O(n^2)$ & 100 \\
    Cubic & $O(n^3)$ & 1000 \\
    \bottomrule
  \end{tabular}
\end{table}
```

Reference: `la Taula~\ref{tab:comparison}`

**Complex table:**

```latex
\begin{table}[!h]
  \caption{Growth rates for different n values.\label{tab:growth}}
  \centering
  \begin{tabular}{r|rrrr}
    \toprule
    $n$ & $n$ & $n \log_2 n$ & $n^2$ & $n^3$ \\
    \midrule
    10     & 10       & 33         & 100         & 1\,000 \\
    100    & 100      & 664        & 10\,000     & $10^6$ \\
    1\,000  & 1\,000  & 9\,966     & $10^6$      & $10^9$ \\
    10\,000 & 10\,000 & 132\,877   & $10^8$      & $10^{12}$ \\
    \bottomrule
  \end{tabular}
\end{table}
```

### Figures

**Single-column figure:**

```latex
\begin{figure}[!h]
  \centering
  \includegraphics[width=\columnwidth]{results-graph.png}
  \caption{Experimental results showing growth rates.\label{fig:results}}
\end{figure}
```

Reference: `la Fig.~\ref{fig:results} mostra que...`

**Full-width figure (both columns):**

```latex
\begin{figure*}[!h]
  \centering
  \includegraphics[width=\textwidth]{architecture-diagram.png}
  \caption{Complete system architecture.\label{fig:architecture}}
\end{figure*}
```

**Fixed-position figure:**

```latex
\begin{figure}[H]
  \centering
  \includegraphics[width=0.8\columnwidth]{screenshot.png}
  \caption{Application screenshot.\label{fig:screenshot}}
\end{figure}
```

**Subfigures:**

```latex
\begin{figure}[!h]
  \centering
  \subfloat[Linear scale]{\includegraphics[width=0.45\columnwidth]{graph-linear.png}}
  \hfill
  \subfloat[Log scale]{\includegraphics[width=0.45\columnwidth]{graph-log.png}}
  \caption{Comparison of scale types.\label{fig:scales}}
\end{figure}
```

### Code Listings

**Simple code:**

```latex
\begin{lstlisting}[caption={Linear algorithm.}]
for (long i = 0; i < n; i++) {
    sum += i * i;
}
\end{lstlisting}
```

**Code with label:**

```latex
\begin{lstlisting}[caption={Quadratic algorithm.}, label=lst:quadratic]
for (long i = 0; i < n; i++) {
    for (long j = 0; j < n; j++) {
        sum += i * j;
    }
}
\end{lstlisting}
```

Reference: `el Llistat~\ref{lst:quadratic}`

### Lists

**Itemized list:**

```latex
\begin{itemize}
  \item First item
  \item Second item
  \item Third item
\end{itemize}
```

**Enumerated list:**

```latex
\begin{enumerate}
  \item First step
  \item Second step
  \item Third step
\end{enumerate}
```

**Description list:**

```latex
\begin{description}
  \item[Model:] Encapsulates data and business logic
  \item[View:] Handles presentation and user input
  \item[Controller:] Manages application flow
\end{description}
```

### Bibliography

**Add a book:**

```latex
\bibitem{ref_mybook}
A. Author, B. Coauthor, and C. Thirdauthor, \textit{Title of the Book}.
City, State, Country: Publisher, Year.
```

**Add a journal article:**

```latex
\bibitem{ref_article}
D. Researcher, ``Title of the Article,'' \textit{Journal Name}, vol.~X,
no.~Y, pp.~123--456, Month Year.
```

**Add a web resource:**

```latex
\bibitem{ref_web}
E. WebAuthor, ``Title of Web Resource,'' Organization, Year. [Online].
Available: \url{https://example.com/resource}
```

---

## Troubleshooting

### LaTeX Issues

**Problem:** "File not found: image.png"  
**Solution:** Images must be in the same directory as `memoria.tex` (in `PX/memoria/`)

**Problem:** References show as [?]  
**Solution:** Run `pdflatex` **twice** - first pass collects, second resolves

**Problem:** Overfull/underfull hbox warnings  
**Solution:** Usually cosmetic, can be ignored unless severe (>10pt). Adjust text or use `\linebreak` if needed

**Problem:** Listings not showing correctly  
**Solution:** Ensure `listings` package is installed and language is correct in `\lstset`

**Problem:** Figure appears in wrong location  
**Solution:** Use `[H]` for exact positioning (requires `float` package), or `[!h]` for "here if possible"

**Problem:** Cannot compile with bibliography  
**Solution:** Run: `pdflatex → bibtex → pdflatex → pdflatex`

### Maven Issues

**Problem:** Maven can't find Main class  
**Solution:** Check package name matches directory structure (`com.serafinebot.pX`)

**Problem:** Compilation errors with Java version  
**Solution:** Check `maven.compiler.source` and `maven.compiler.target` in `pom.xml` match your Java version

**Problem:** Dependencies not downloading  
**Solution:** Run `mvn clean install -U` to force update

**Problem:** IntelliJ doesn't recognize Maven project  
**Solution:** Right-click `pom.xml` → "Add as Maven Project"

### Git Issues

**Problem:** Too many files being tracked  
**Solution:** Ensure `.gitignore` is in place:
```
target/
.idea/
*.class
*.log
*.aux
*.synctex.gz
```

**Problem:** Large PDF files in history  
**Solution:** Consider using Git LFS for PDF files, or regenerate them rather than committing

---

## For AI Assistants

### Understanding This Repository

**Key Facts:**
- This is a **university course repository** with multiple **independent Java/Maven projects**
- Each `PX/` folder = one complete assignment (completely independent)
- `ieee-template/` = shared LaTeX template (always use `uib-aa-template.tex`)
- No cross-project dependencies
- Each project: source (`src/`), build config (`pom.xml`), report (`memoria/`)

### When Helping Users

**Ask for context:**
- Which project? (P1, P2, P3, etc.)
- Java code or LaTeX report?
- Building or compiling?

**For new projects:**
- Always guide to use `uib-aa-template.tex`, not `bare_jrnl_new_sample4.tex`
- Emphasize: compile LaTeX **twice** for references
- Remind: update `pom.xml` artifactId and groupId

**For LaTeX help:**
- Reference this file's examples section
- Images go in `memoria/` folder (same as `.tex` file)
- Use `\columnwidth` for single-column, `\textwidth` for full-width
- Catalan language is built-in

**For Java help:**
- Package structure: `com.serafinebot.pX`
- Maven commands in this file
- Each project is independent

### Common User Questions

| Question | Answer |
|----------|--------|
| How to start P2? | See "Creating New Assignments" section (one-command or step-by-step) |
| How to add a figure? | See "LaTeX Examples > Figures" section |
| How to add code? | See "LaTeX Examples > Code Listings" section |
| Why compile twice? | LaTeX needs first pass to collect refs, second to resolve them |
| What template to use? | Always `uib-aa-template.tex` (has P1 formatting) |
| Where to put images? | In `PX/memoria/` (same directory as `memoria.tex`) |
| How to build Java? | `cd PX && mvn compile` |
| How to run Java? | `mvn exec:java -Dexec.mainClass="com.serafinebot.pX.Main"` |

### Best Practices for Assistance

1. **Be specific:** Provide exact commands, not generic advice
2. **Show examples:** Use code blocks from this file
3. **Verify structure:** Check they're in correct directory before commands
4. **Remind independence:** Each PX/ is separate, changes don't affect others
5. **Emphasize template:** Always use `uib-aa-template.tex` for consistency

### Code Generation Guidelines

**When generating Java code:**
- Use package `com.serafinebot.pX` (replace X with project number)
- Follow existing patterns from P1 if available
- Include proper comments
- Follow MVC pattern if applicable

**When generating LaTeX:**
- Use Catalan for text (Introducció, Resultats, etc.)
- Use proper labels (`fig:`, `tab:`, `eq:`, `lst:`)
- Include captions for all figures/tables
- Reference with `~\ref{}` (note the `~` for non-breaking space)

### Workflow Recommendations

**For starting new project:**
1. Show one-command setup
2. List what needs to be edited (pom.xml, memoria.tex)
3. Show how to verify (mvn compile, pdflatex)

**For debugging:**
1. Identify issue (Java or LaTeX?)
2. Check file locations
3. Provide specific solution from troubleshooting section

**For explaining concepts:**
1. Reference P1 as example
2. Point to specific files in repository
3. Show before/after code snippets

---

## Quick Reference Cheat Sheet

### Files to Copy for New Project

```bash
# Required
ieee-template/IEEEtran.cls → PX/memoria/IEEEtran.cls
ieee-template/uib-aa-template.tex → PX/memoria/memoria.tex

# Recommended
P1/pom.xml → PX/pom.xml (then edit artifactId/groupId)
P1/.gitignore → PX/.gitignore
```

### Files to Edit

| File | What to Change |
|------|---------------|
| `PX/pom.xml` | `<artifactId>`, `<groupId>` |
| `PX/memoria/memoria.tex` | Title, name, abstract, keywords, content |
| `PX/src/main/java/.../Main.java` | Package declaration, class implementation |

### Common Commands

```bash
# Java
mvn compile                                        # Compile
mvn exec:java -Dexec.mainClass="..."               # Run
mvn clean                                          # Clean

# LaTeX
pdflatex memoria.tex                               # Compile once
pdflatex memoria.tex && pdflatex memoria.tex       # Compile twice
```

### Do's and Don'ts

✅ **DO:**
- Use `uib-aa-template.tex` (has P1 formatting)
- Run `pdflatex` **twice** (for references)
- Place images in `memoria/` folder
- Update `pom.xml` artifactId and groupId
- Use `\columnwidth` for single-column figures

❌ **DON'T:**
- Use `bare_jrnl_new_sample4.tex` (wrong format)
- Forget to copy `IEEEtran.cls`
- Put images in other folders
- Forget to compile LaTeX twice
- Use `[journal]` document class (use `[conference,compsoc]`)

---

**Last Updated:** 2026-03-09  
**Version:** 1.0  
**For:** UIB Advanced Algorithms Course (2025-2026)  
**Purpose:** Comprehensive guide for AI agents and human developers
