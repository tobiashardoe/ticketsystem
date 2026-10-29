# TicketSystem – API-dokumentation

API'et bruges til at oprette og behandle supporttickets.
Her vises alle 12 REST-endpoints, tre uddybede eksempler og tre fejlscenarier.

Lokal baseadresse: `http://localhost:7070/api`
Online baseadresse: `https://ticketsystem.tobiashardo.dk/api`

## Endpoints

| Metode | URL | Request Body (JSON) | Response (JSON) | Fejl |
|---|---|---|---|---|
| POST | /auth/register | name, email, password; se (1) | Login-svar (2), status 201 | 400, 409 |
| POST | /auth/login | Login (1) | Login-svar (2), status 200 | 400, 401 |
| GET | /tickets | Ingen | Liste af Ticket (4), status 200 | 401 |
| GET | /tickets/{id} | Ingen | Ticket (4), status 200 | 400, 401, 403, 404 |
| POST | /tickets | Ticket-input (3) | Ticket (4), status 201 | 400, 401, 403, 503 |
| PUT | /tickets/{id} | Alle felter i (3) | Ticket (4), status 200 | 400, 401, 403, 404 |
| DELETE | /tickets/{id} | Ingen | Ingen body, status 204 | 400, 401, 403, 404 |
| PUT | /tickets/{id}/assign?supporterId={supporterId} | Ingen | Ticket (4) med supporter og status IN_PROGRESS, status 200 | 400, 401, 403, 404 |
| GET | /tickets/{id}/comments | Ingen | Liste af Comment (5), status 200 | 400, 401, 403, 404 |
| POST | /tickets/{id}/comments | message; se (5) | Comment (5), status 201 | 400, 401, 403, 404 |
| POST | /supporters | name, email, password; se (6) | User (6), status 201 | 400, 401, 403, 409 |
| POST | /ip/check | IP-liste (7) | Liste af IP-svar (7), status 200 | 400, 401, 403, 503 |

POST /tickets kræver en indlogget bruger. PUT /tickets/{id}/assign kræver SUPPORT eller ADMIN.
Alle ticket-endpoints kræver JWT. USER må kun læse, opdatere, slette og kommentere
egne tickets; SUPPORT/ADMIN må gøre det på alle. POST /supporters kræver ADMIN,
og POST /ip/check kræver SUPPORT eller ADMIN. Registrering og login er offentlige.
Beskyttede requests skal indeholde headeren:

```http
Authorization: Bearer <token-fra-login>
```

## Request Body og Response-formater

### (1) Login og registrering – request

Bruges til POST /auth/login for en eksisterende bruger:

```json
{
  "email": "demo@example.com",
  "password": "Demo-password-2026!"
}
```

Begge felter skal være udfyldt. Serveren finder brugeren gennem e-mailen og
kontrollerer passwordet mod den gemte bcrypt-hash.

Ved POST /auth/register sendes også name:

```json
{
  "name": "Demo User",
  "email": "demo@example.com",
  "password": "Demo-password-2026!"
}
```

Navn skal være udfyldt, og e-mailen skal have et gyldigt format og være unik.
Passwordet skal være mindst 8 tegn og højst 72 UTF-8-bytes.
Brugeren får altid rollen USER. Svaret har format (2) med status 201 Created.

### (2) Login – response

Ved godkendt login returneres status 200 OK:

```json
{
  "token": "<JWT>",
  "user": {
    "id": 1,
    "name": "Demo User",
    "email": "demo@example.com",
    "role": "USER"
  }
}
```

JWT bruges til efterfølgende requests og gælder i én time.
Password og passwordhash sendes ikke med i svaret.

### (3) Oprettelse af ticket – request

Bruges til POST /tickets:

```json
{
  "title": "Kan ikke logge ind",
  "description": "Login viser en fejlmeddelelse.",
  "status": "OPEN",
  "priority": "MEDIUM"
}
```

- title og description skal være udfyldt og må ikke kun indeholde mellemrum.
- status skal være OPEN, IN_PROGRESS eller CLOSED.
- priority skal være LOW, MEDIUM eller HIGH.
- ID genereres automatisk, og opretteren findes gennem JWT.

Før lagring kontrolleres forbindelsens IP med AbuseIPDB.
Ved succes returneres status 201 Created og ticketen i format (4).

PUT /tickets/{id} bruger samme fire felter og returnerer den opdaterede ticket
med status 200. Opretter og tildelt supporter ændres ikke gennem dette endpoint.
GET /tickets returnerer en liste i formatet `[{...}, {...}]` eller `[]`.
GET /tickets/{id} returnerer én ticket. DELETE /tickets/{id} returnerer status
204 uden body ved succes.

### (4) Ticket – response og tildeling

Eksempel på en ny ticket:

```json
{
  "id": 42,
  "title": "Kan ikke logge ind",
  "description": "Login viser en fejlmeddelelse.",
  "status": "OPEN",
  "priority": "MEDIUM",
  "createdBy": {
    "id": 1,
    "name": "Demo User",
    "email": "demo@example.com",
    "role": "USER"
  },
  "assignedTo": null
}
```

Ved PUT /tickets/42/assign?supporterId=2 sendes ingen JSON-body.
Begge ID'er skal være positive heltal, og modtageren skal have rollen SUPPORT.

Ved succes returneres status 200 OK med samme ticketformat, men status er
IN_PROGRESS, og assignedTo indeholder supporterens id, name, email og role.
Opretteren ændres ikke.

### (5) Kommentarformat

POST kræver et udfyldt message-felt, eksempelvis `{"message":"Vi undersøger sagen."}`.
Svaret indeholder id, message, ticket (4) og author (brugerformatet i (6)).
Forfatteren findes fra JWT, og ticketen fra URL'en. GET returnerer en liste eller `[]`.

### (6) Supporter og brugerformat

POST /supporters kræver name, email og password og sætter altid rollen SUPPORT.
Eksempel: `{"name":"Supporter","email":"support@example.com","password":"support-password"}`.
Navn og e-mail skal være udfyldt, e-mailen skal være gyldig og unik, og passwordet
skal følge reglen på 8 tegn til 72 UTF-8-bytes. Brud på passwordreglen kan aktuelt give 500.
User-svar indeholder id, name, email og role, som vist i (2), uden passwordhash.
Der findes ikke længere generelle bruger-CRUD-endpoints.

### (7) IP-format

Input er en JSON-liste med 1–20 ikke-tomme adresser, eksempelvis
`["8.8.8.8","1.1.1.1"]`. Resultatet er en liste i inputrækkefølge.
Hvert element indeholder data med felterne ipAddress (tekst), abuseConfidenceScore (heltal),
countryCode, isp, domain (tekst, kan være null), totalReports (heltal) og isTor (boolean).
Ét fejlet opslag giver 503 for hele requesten. API-nøglen hentes på serveren.


## Fejl

Almindelige API-fejl returneres som `{"message": "Beskrivelse af fejlen"}`.
Statuskoden står i HTTP-svaret. Her vises tre udvalgte fejlscenarier.

### 401 Unauthorized – login mangler eller er ugyldigt

Eksempel ved en beskyttet request uden token:

```json
{
  "message": "Login required"
}
```

Et ugyldigt eller udløbet token giver også 401, men beskeden
`Invalid or expired token`. Forkert e-mail eller password ved login giver
`Invalid email or password`.

### 400 Bad Request – ugyldige oplysninger

Eksempel ved POST /tickets med tom title:

```json
{
  "message": "title must not be empty"
}
```

Ved tildeling giver en valgt bruger uden rollen SUPPORT også 400.
Javalins validering af eksempelvis ugyldige ID'er giver et andet JSON-format
med message `Invalid request input` og et errors-objekt.

### 403 Forbidden – rollen giver ikke adgang

Eksempel når en USER forsøger at tildele en supporter:

```json
{
  "message": "Insufficient permissions"
}
```

Brugeren har et gyldigt login, men handlingen kræver SUPPORT eller ADMIN.

Dette er udvalgte fejleksempler, ikke en udtømmende fejlliste.
E-mail, som allerede findes, giver 409. Ukendt ticket eller supporter kan give 404,
IP-score mindst 80 giver 403 ved
oprettelse, et fejlet IP-opslag giver 503, og uventede serverfejl giver 500.
Ved IP-afvisning eller fejlet opslag gemmes ticketen ikke.

ID'er i sti og query-parametre skal være positive heltal. PUT /tickets/{id} kræver
alle felter i (3) og ændrer ikke opretter eller tildeling. GET-lister kan være tomme.
Sletning af en ticket med kommentarer eller attachments kan give 500 på grund af
databasereferencer; der er ikke automatisk cascading. Uventede serverfejl giver
`{"message":"Internal server error"}` med status 500.

## WebSockets

Lokalt bruges `ws://localhost:7070/ws/tickets` og online
`wss://ticketsystem.tobiashardo.dk/ws/tickets`. Klienten sender sit JWT som første
tekstbesked uden `Bearer`. Ved godkendelse svarer serveren med `CONNECTED`;
et ugyldigt token lukker forbindelsen med kode 1008.

SUPPORT og ADMIN får besked om nye tickets. Ejeren og SUPPORT/ADMIN får besked
om ændringer, tildeling, kommentarer og sletning. Beskederne indeholder `type`
og `ticketId` og sendes kun til tilsluttede klienter.

IP-kontrollen bruger forbindelsens adresse (`getRemoteAddr()`). Bag Caddy er
kontrol af den oprindelige besøgendes IP ikke verificeret; videresendte IP-headers
bruges ikke af den nuværende kode.
