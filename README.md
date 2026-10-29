# TicketSystem

## Vision

Backend til et ticketsystem, hvor brugere opretter henvendelser, og supportere og
administratorer behandler dem. Formålet er at samle supporthenvendelser, svar og
tildeling i ét system med adgang baseret på brugerens rolle.

---

## Links

Deployed application (optional):  
https://ticketsystem.tobiashardo.dk/

Source code repository:  
https://github.com/tobiashardoe/ticketsystem

Portfolio website:  
tobiashardoe.github.io/Portfolio/

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
| Java | 17 (source/target i Maven) |
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

Baseadresse: `http://localhost:7070/api`.
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

## Lokal opstart

Brug JDK 17 eller nyere til programmet. Maven-testene bruger JUnit 6, som kræver JDK 17 eller nyere.
Opret PostgreSQL-databasen `ticketsystem`, og giv databasebrugeren rettigheder
til at oprette og ændre projektets tabeller.

Uden miljøvariablen `DEPLOYED` læses databasen fra
`src/main/resources/config.properties`:

```properties
CONNECTION_STR=jdbc:postgresql://localhost:5432/
DB_NAME=ticketsystem
DB_USERNAME=DIN_DATABASEBRUGER
DB_PASSWORD=DIT_DATABASEPASSWORD
JWT_SECRET=DIN_TILFAELDIGE_HEMMELIGE_NOEGLE
```

Vælg serveradressen i CONNECTION_STR, hvis databasen ligger på en anden maskine.
Adressen skal slutte med `/`, da DB_NAME tilføjes af koden.
JWT_SECRET skal være en tilfældigt genereret hemmelighed på mindst 32 UTF-8-bytes,
ikke teksten fra eksemplet. En miljøvariabel med JWT_SECRET har forrang over filen.

Hvis DEPLOYED er sat (også hvis teksten er `false`), læses DB_NAME, CONNECTION_STR,
DB_USERNAME, DB_PASSWORD og JWT_SECRET fra miljøvariabler i stedet.

Indstil desuden miljøvariablen ABUSEIPDB_API_KEY til de eksterne IP-opslag.
Valgfrit kan ADMIN_EMAIL og ADMIN_PASSWORD oprette den første administrator
ved opstart, hvis e-mailen ikke findes. Passwordet skal være mindst otte tegn og højst
72 UTF-8-bytes. Der er ingen standardadministrator eller standardadgangskode.

Kør `app.Main` i IntelliJ. API'et kører på `http://localhost:7070/api`. Registrering og login er offentlige;
øvrige endpoints kræver JWT. Der er ingen frontend eller health-route.

Hibernate bruger `update`, ikke `create`: opstart genskaber ikke tabellerne.
Det er ikke et migrationsværktøj; gamle kolonner fjernes ikke automatisk.
Kontrollér især den gamle version-kolonne, hvis databasen tidligere brugte @Version.

config.properties er ignoreret af Git og udelukkes fra JAR-filerne.
Den kan stadig ligge i target/classes ved lokal udvikling; del ikke denne mappe
eller medtag den i Docker-images med rigtige hemmeligheder.
Del aldrig passwords, API-nøgler eller JWT-hemmeligheder i repository eller screenshots.

## Build

Byg med Docker Desktop startet, så integrationstestene kan køre:

```bash
mvn clean package
```

Maven Shade Plugin 3.6.1 samler kode og biblioteker i `target/ticketsystem.jar`
med `app.Main` som startklasse. Brug denne fil til deployment, ikke
`original-ticketsystem.jar`. På serveren skal DEPLOYED og de nødvendige
miljøvariabler være sat, før programmet startes med `java -jar ticketsystem.jar`.

## Tests

Start Docker Desktop, og kør:

```bash
mvn test
```

Testene bruger separate PostgreSQL-containere og ikke den konfigurerede onlinedatabase.
Suite: 8 DAO-tests, 1 JSON/DTO-test, 7 user/security-tests og 10 ticket-tests:
i alt 26, herunder tre WebSocket-tests.

Route-testene bruger Rest Assured og Hamcrest med Given–When–Then-beskrivelser i
@DisplayName. De tester blandt andet login, CRUD, IP-afvisning og roller:
USER kan ikke tildele tickets, SUPPORT/ADMIN kan, og kun ADMIN kan oprette supportere.
Ticket-testene bruger faste IP-resultater i stedet for rigtige AbuseIPDB-kald.
Det eksterne kald og samtidigheden i checkMultiple er ikke automatisk testet.

WebSocket er manuelt afprøvet på den deployede backend. En supporter blev 
godkendt med JWT i Chrome og modtog TICKET_CREATED med korrekt ticket-ID, 
da en ticket blev oprettet gennem HTTP-filen i IntelliJ.

## Afgrænsning

Deployment er endnu ikke færdiggjort. Domæne og database på en server betyder ikke,
at selve Java-programmet er deployet.

Bag en proxy som Caddy skal klient-IP og sikker videresendelse af adressen afklares:
koden læser forbindelsens adresse og ikke vilkårlige IP-headers fra klienten.
Lokale/private adresser kan give fejl i AbuseIPDB-opslaget.

Der er endnu ingen frontend, offline-notifikationer, passwordreset eller token-refresh.
Sletning af refererede data kan give en databasefejl; de konkrete API-begrænsninger
er beskrevet i [API-dokumentationen](docs/api.md).

Navnet TicketDesk i Maven og mappen ResolveDesk er tekniske rester af tidligere navne.
JWT-udstedernavnet er TicketSystem.
