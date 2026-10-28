# ADR-0004: Spring Boot backend, Angular dashboard

## Status
Accepted

## Context
The backend and dashboard stacks were unset. For a POC whose HTTP surface
is one multipart upload and a map of Potholes, a single Python process
(FastAPI) plus a small map page is the smaller build.

The people building this already work in Java and Angular. A smaller stack
they do not know costs more than the extra framework.

## Decision
The Backend is a Spring Boot application. It serves the Client contract
(`POST /v1/sightings`) and the dashboard reads.

The dashboard is an Angular application.

The on-device Client is neither. It stays a separate program on the Pi
that POSTs Sightings to the Backend.

This repo houses all three. Each lives in a folder named after itself:
`backend/`, `frontend/`, and `client/`.

The database and the map library are not chosen here.

## Consequences

### Positive
- The team implements the API and the dashboard in stacks they already know.
- The Client contract stays plain HTTP, so the Pi does not need a Spring
  client.

### Negative
- More process and build machinery than a one-file POC backend.
- Two front-of-house codebases (Pi client and Angular dashboard) plus the
  Spring Boot service.
