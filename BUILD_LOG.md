# Build log

1. Read `PROMPT.md`, `CLAUDE.md`, listed `spec/01-canvas-submitted/` (2 PDFs).
2. Extracted text of `Team_9_SE3354_F26_ProjectProposal.pdf` and `Team_09_SE3354_F26_Deliverable_1.pdf` with `pdftotext -layout` (printed to stdout, nothing written into `spec/`).
3. Tried to render diagram pages: `pdftoppm` not installed and Read-tool PDF rendering also needs poppler -> failed. Fix: wrote scratch tool `app/.spec-render/extract-images.js` (Node, zlib only) that pulls embedded image XObjects out of the PDF and re-encodes them as PNG. 10 images extracted (git-ignored).
4. Viewed all 10 images: Fig 4.3 use case diagrams UC-1..UC-4, SD-1..SD-4 sequence diagrams (§5.1), class diagram (§5.2), client-server architecture diagram (§5.3.2).
5. Checked environment: Java 23.0.1, Maven 3.9.11, Node 22.23, npm 10.9. Docker CLI present but Docker Engine not running -> PostgreSQL via docker-compose is provided for the README, and an embedded H2 profile is used to build/test/run here (see A-2).
