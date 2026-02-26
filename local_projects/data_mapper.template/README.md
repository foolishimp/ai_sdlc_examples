# Data Mapper: Project Genesis Reference Template

## Overview
This is the canonical template for the **Categorical Data Mapping & Computation Engine (CDME)**. It serves as the primary reference test for the evolving **Project Genesis** (AI SDLC v2.8+).

The project is complex, mathematically grounded in Category Theory, and requires strict adherence to invariants (Path validation, Grain safety, Type unification).

## Contents
*   `specification/`: The technology-agnostic "WHAT". Contains `INTENT.md`, `REQUIREMENTS.md`, and the domain-specific `mapper_requirements.md`.

## Genesis History
This project has been implemented across multiple dogfood runs to track methodology evolution:
*   **test02**: First complete run (v1.x pipeline).
*   **test04**: Best observability and gap detection (v2.1+).
*   **test06**: First uninterrupted single-shot build (v2.8).
*   **test07**: Best design depth and REQ traceability (v2.8 design-first).

## Usage
Use this folder to bootstrap new Genesis implementation tenants (`imp_<name>/`). 
1. Copy this template to a new `data_mapper.testXX` folder.
2. Initialize the Genesis workspace (`gemini init` or `claude -p gen-init`).
3. Traverse the Asset Graph from Intent -> Requirements -> Design -> Code.
