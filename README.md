# TicketSystem

## Vision

Backend til et ticketsystem, hvor brugere opretter henvendelser, og supportere og
administratorer behandler dem. Formålet er at samle supporthenvendelser, svar og
tildeling i ét system med adgang baseret på brugerens rolle.

---

## Links

Deployed application (optional):  
[TicketSystem API](https://ticketsystem.tobiashardo.dk/api/tickets)

Beskyttede endpoints svarer 401 uden JWT.

Source code repository:  
[GitHub-repository](https://github.com/tobiashardoe/ticketsystem)

Portfolio website:  
[Portfolio](https://tobiashardoe.github.io/Portfolio/)

---

# Architecture

## System Overview

Systemet er opdelt i:

- Routes, der forbinder URL'er med controller-metoder.
- Controllere, der håndterer HTTP-requests, validering og adgangskontrol.
- AbuseIpService, der håndterer kald til AbuseIPDB.
- DAO-klasser, der håndterer databaseadgangen gennem JPA.

Teknologier og versioner fra `pom.xml` og testopsætningen:

| Teknologi | Version |
|---|---|
| Java | 21 (lokalt, Maven release og deployment) |
| Javalin | 6.5.0 |
| Hibernate ORM (JPA) | 7.2.3.Final |
| PostgreSQL JDBC-driver | 42.7.7 |
| PostgreSQL i tests | 16 (`postgres:16-alpine`) |
| Jackson Databind | 2.19.2 |
| Lombok | 1.18.42 |
| TokenSecurity (JWT) | 1.0.4 |
| jBCrypt | 0.4 |
| Jakarta Validation API | 3.1.1 |
| JUnit | 6.0.2 |
| Rest Assured | 5.5.6 |
| Hamcrest | 3.0 |
| Testcontainers | 2.0.3 |
| Logback Classic | 1.5.27 |
| SLF4J | 2.0.17 |

Maven bruges som buildværktøj, men versionen er ikke fastlåst i projektet.
PostgreSQL-serverens version afhænger af databaseinstallationen; JDBC-driverens
version er ikke det samme som serverens version.

---

## Architecture Diagram

```text
Klient → Routes / Controller → DAO → PostgreSQL
                    ↓
              AbuseIpService → AbuseIPDB
```

Controllerne kalder DAO'erne direkte til databasearbejdet.
AbuseIpService bruges ved IP-kontrol; det er ikke et fælles servicelag for alle requests.

---

## Key Design Decisions

- Databasearbejdet ligger i DAO-klasser, så det er adskilt fra HTTP-håndteringen.
- Passwords gemmes som bcrypt-hashes. Efter login bruges JWT til at identificere
  brugeren, mens rolle og ejerskab afgør adgangen til tickets.
- AbuseIPDB kontrollerer forbindelsens IP før oprettelse. En score på mindst 80
  giver 403, og fejl i opslaget giver 503. Ticketen gemmes ikke i de tilfælde.
- Flere IP-opslag udføres med ExecutorService, Callable og Future.
- WebSockets giver tilsluttede supportere besked om nye tickets og ejeren besked
  om ændringer og svar, uden at klienten hele tiden skal hente listen igen.

---

# Data Model

## ERD

Se [ER-diagrammet](docs/er-diagram.puml) og [domænemodellen](docs/domain-model.puml).

---

## Important Entities

### User

En registreret bruger, supporter eller administrator.

Felter:

- id
- name
- email
- passwordHash
- role

### Ticket

En supporthenvendelse med en opretter og eventuelt en tildelt supporter.
Begge brugerrelationer er ManyToOne.

Felter:

- id
- title
- description
- status
- priority
- createdBy
- assignedTo

### Comment

Et svar på en ticket med ManyToOne-relationer til ticket og forfatter.

Felter:

- id
- message
- ticket
- author

### Attachment

Filoplysninger knyttet til en ticket med ManyToOne.
Entiteten og DAO'en er bevaret, men der er ikke REST-endpoints til filer.

Felter:

- id
- fileName
- fileType
- filePath
- ticket

En bruger kan altså oprette flere tickets, og en ticket kan have flere kommentarer.
Relationerne er mappet fra ticketen eller kommentaren med ManyToOne.

---

# API Documentation

API'et har 12 REST-endpoints og én separat WebSocket-forbindelse.

Lokal baseadresse: `http://localhost:7070/api`.
Online baseadresse: `https://ticketsystem.tobiashardo.dk/api`.
Disse seks endpoints viser login og det centrale forløb fra oprettelse til behandling af en ticket.

Alle undtagen login kræver `Authorization: Bearer <token>`.
USER arbejder kun med egne tickets. SUPPORT og ADMIN kan behandle alle tickets,
og tildeling kræver SUPPORT eller ADMIN.

Se [den fulde API-dokumentation](docs/api.md) for alle 12 endpoints,
JSON-formater, validering, fejleksempler og WebSockets.

## Example Endpoints

### Login

```http
POST /api/auth/login
```

Request body:

```json
{"email":"demo@example.com","password":"Demo-password-2026!"}
```

Response: `200 OK`.

```json
{"user":{"id":1,"name":"Demo User","email":"demo@example.com","role":"USER"},"token":"<JWT>"}
```

---

### Get Tickets

```http
GET /api/tickets
```

Ingen request body. USER får egne tickets; SUPPORT og ADMIN får alle.

Response: `200 OK`. Eksempel uden tickets:

```json
[]
```

Med tickets returneres en liste af objekter som i Create Ticket nedenfor.

---

### Create Ticket

```http
POST /api/tickets
```

Request body:

```json
{"title":"Kan ikke logge ind","description":"Login viser en fejl.","status":"OPEN","priority":"MEDIUM"}
```

Response: `201 Created`.

```json
{"id":42,"title":"Kan ikke logge ind","description":"Login viser en fejl.","status":"OPEN","priority":"MEDIUM","createdBy":{"id":1,"name":"Demo User","email":"demo@example.com","role":"USER"},"assignedTo":null}
```

---

### Update Ticket

```http
PUT /api/tickets/42
```

Request body:

```json
{
  "title": "Kan ikke logge ind",
  "description": "Problemet er løst.",
  "status": "CLOSED",
  "priority": "MEDIUM"
}
```

Response: `200 OK` med den opdaterede ticket i samme format som ved oprettelse.
Alle fire felter skal sendes. Opretter og tildelt supporter ændres ikke.

---

### Assign Supporter

```http
PUT /api/tickets/42/assign?supporterId=2
```

Ingen request body. Kræver SUPPORT eller ADMIN, og den valgte bruger skal være SUPPORT.

Response: `200 OK` med ticketen. Status sættes til IN_PROGRESS, og assignedTo
indeholder den valgte supporters id, name, email og role.

---

### Delete Ticket

```http
DELETE /api/tickets/42
```

Ingen request body.

Response: `204 No Content` uden body.

---

## Error Examples

- **400:** Tom title giver `{"message":"title must not be empty"}`.
- **401:** Manglende token giver `{"message":"Login required"}`.
- **403:** En USER, der forsøger at tildele en supporter, får
  `{"message":"Insufficient permissions"}`.

---

# User Stories

- Som bruger vil jeg kunne registrere mig og logge ind, så mine tickets er knyttet til mig.
- Som bruger vil jeg kunne oprette, se, opdatere og slette mine egne tickets.
- Som bruger vil jeg kunne læse svar og få besked om ændringer på mine tickets.
- Som supporter vil jeg kunne se alle tickets, tildele en supporter og svare på henvendelser.
- Som administrator vil jeg kunne oprette supportere, så de kan behandle tickets.

---

# Development Notes

## Build og tests

- `mvn clean package` bygger den projektet
- `mvn test` kører testene. Docker Desktop skal være startet, da testene bruger
  separate PostgreSQL-containere og ikke onlinedatabasen.

Rest Assured og Hamcrest bruges til HTTP-tests med Given–When–Then-beskrivelser. 
Ticket-testene bruger faste IP-resultater; det eksterne API-kald og samtidigheden
er ikke automatisk testet.

Login, CRUD og centrale roller er manuelt afprøvet online.
WebSocket er manuelt afprøvet på den deployede backend. En supporter blev
godkendt med JWT i Chrome og modtog `TICKET_CREATED` med korrekt ticket-ID,
da en ticket blev oprettet gennem HTTP-filen i IntelliJ.

## Afgrænsning

IP-kontrollen læser forbindelsens adresse, ikke den oprindelige klient-IP bag Caddy.
Det er en kendt begrænsning. Der er ingen frontend, offline-notifikationer,
passwordreset eller token-refresh. Sletning af en ticket med kommentarer eller
attachments kan give en databasefejl; se [API-dokumentationen](docs/api.md).
