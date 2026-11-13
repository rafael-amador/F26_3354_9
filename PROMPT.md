# The only build instruction given to the AI

Read every document in `spec/` (PDFs can be converted with `pdftotext -layout`).
Then build CometFit exactly as our deliverables describe it, following `CLAUDE.md`.

Goal: a working implementation of every use case (UC-1 to UC-4) and every
functional requirement (FR-1 to FR-8) in the spec, with the architecture,
classes and sequence of interactions the spec's diagrams describe.

Environment facts (not product requirements): this machine has Java 23,
Maven 3.9, Docker 29, Node 22 and npm. The app must run locally on a laptop
and be demoable in a recorded video, so any mobile client must also run in a
browser. Finish in this order: backend + database, then client screens, then
tests, then a root `app/README.md` with step-by-step instructions to install
dependencies, configure environment variables and run the project locally.

When finished, make sure `TRACEABILITY.md` maps every FR and UC to the code
and tests that implement it, and `ASSUMPTIONS.md` lists every gap you filled.
