# 0001. Initial System Architecture

* Status: accepted
* Date: 2026-08-17

## Context and Problem Statement

Need a clean, maintainable architecture for a personal quantitative trading dashboard supporting charting, AI signals, and manual/simulated trading.

## Decision Outcome

Chosen Option: Decoupled Single-Page Application (SPA) with a Java Spring Boot backend.

* Frontend: React + TypeScript + Vite
* Backend: Java 21 + Spring Boot
* Database: PostgreSQL
* Deployment: Docker Compose
