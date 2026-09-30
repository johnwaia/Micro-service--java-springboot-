# Projet Microservice — Réservation et paiement de cours de sport

TP Efrei M2-DEV2 Microservices. Quatre microservices métier ajoutés au projet
`mon-projet-microservice`, qui réutilisent l'infrastructure existante (`eureka-server`,
`config-server`, `api-gateway`) :

| Service | Port | Base H2 | Rôle |
|---|---|---|---|
| `class-service` | 8091 | `classdb` | Catalogue des cours, gestion des places (verrouillage optimiste) |
| `booking-service` | 8092 | `bookingdb` | Réservations, **orchestrateur du saga**, schedulers |
| `payment-service` | 8093 | `paymentdb` | Paiements simulés, remboursements |
| `notification-service` | 8094 | `notificationdb` | Envoi d'emails simulé, relances |
| `api-gateway` | 8090 | — | Point d'entrée unique (4 routes ajoutées) |
| `eureka-server` | 8761 | — | Annuaire des services |
| `config-server` | 8888 | — | Configuration centralisée (`config-server/config-repo`) |

> ⚠️ Les ports 8091/8092 imposés par le sujet sont ceux qu'utilisaient déjà `book-service` et
> `loan-service` (module 10). Les deux groupes de services ne peuvent donc pas tourner en même temps.

## Architecture

```
                 client (Postman)
                        │
                 ┌──────▼──────┐        ┌───────────────┐
                 │ api-gateway │◄──────►│ eureka-server │◄──── tous les services s'enregistrent
                 │    :8090    │        └───────────────┘
                 └──────┬──────┘        ┌───────────────┐
   /api/classes/**      │               │ config-server │◄──── tous les services lisent leur config
   /api/bookings/**     │               └───────────────┘
   /api/payments/**     │
   /api/notifications/**│
        ┌───────────────┼──────────────────┬───────────────────────┐
        ▼               ▼                  ▼                       ▼
 ┌─────────────┐ ┌───────────────┐ ┌─────────────────┐ ┌──────────────────────┐
 │class-service│ │booking-service│ │ payment-service │ │ notification-service │
 └──────▲──────┘ └──┬─────┬────┬─┘ └────────▲────────┘ └──────────▲───────────┘
        │  Feign +  │     │    │   Feign +  │           Feign +    │
        └───────────┘     │    └────────────┼──────────────────────┘
          circuit breaker └─────────────────┘  circuit breaker
```

`booking-service` est le seul à appeler les autres services, via trois clients **OpenFeign**
(`ClassClient`, `PaymentClient`, `NotificationClient`). Chacun est entouré d'un **circuit
breaker Resilience4j** (`spring.cloud.openfeign.circuitbreaker.enabled=true`) avec une
`FallbackFactory` :

| Situation | Effet |
|---|---|
| Réponse 4xx du service distant (409 plus de places, 404…) | Erreur métier transmise telle quelle (statut + message). **Ne compte pas** comme une panne : `ignoreExceptions(FeignClientException)` |
| Connexion refusée, timeout (5 s), 5xx, circuit ouvert | `503 Service Unavailable` (`ServiceUnavailableException`) |
| `notification-service` indisponible | Dégradation gracieuse : la notification est journalisée et perdue, la réservation ou le paiement **n'échoue pas** |

Réglages du circuit breaker (`ResilienceConfig`) : fenêtre de 10 appels, au moins 5 appels,
seuil d'échec de 50 %, 10 s en état ouvert. L'état est visible sur
`GET :8092/actuator/circuitbreakers`.

## Démarrage

### En local (Maven)

```bash
mvn package -DskipTests      # construit des jars exécutables (goal repackage)

java -jar eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar                   # 1. :8761
cd config-server && java -jar target/config-server-1.0.0-SNAPSHOT.jar && cd ..    # 2. :8888 (lit ./config-repo)
java -jar class-service/target/class-service-1.0.0-SNAPSHOT.jar                   # 3. :8091
java -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar               #    :8093
java -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar     #    :8094
java -jar booking-service/target/booking-service-1.0.0-SNAPSHOT.jar               #    :8092
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar                       # 4. :8090
```

(Un terminal par service. `mvn spring-boot:run` dans chaque dossier fonctionne aussi.)
`config-server` doit être lancé **depuis son dossier**, car il lit `file:./config-repo`.

Au démarrage, il faut compter environ 30 s avant que les services se voient dans Eureka.
Pendant ce délai, un appel de `booking-service` vers un autre service reçoit un 503 propre
(fallback du circuit breaker).

`class-service` charge 4 cours de démonstration (ids 1 à 4), désactivables avec
`fitconnect.demo-data.enabled=false`. Les consoles H2 sont accessibles sur
`http://localhost:<port>/h2-console`, par exemple avec l'URL JDBC `jdbc:h2:mem:classdb`.

**Profil `demo` de booking-service** : `--spring.profiles.active=demo` (fichier
`config-repo/booking-service-demo.yml`) réduit le délai de paiement à 20 s et fait tourner le
scheduler d'expiration toutes les 15 s. Il permet de démontrer l'expiration des paiements sans
attendre une heure, et il est **nécessaire** pour la requête Postman « Paiement expiré ».

### Avec Docker Compose (bonus)

```bash
mvn package -DskipTests
docker compose up --build                     # ou : BOOKING_PROFILE=demo docker compose up --build
```

Un `Dockerfile` générique (`--build-arg MODULE=...`) sert aux 7 conteneurs. Chaque service
attend que `eureka-server` et `config-server` soient *healthy*. Les URLs d'Eureka et du
config-server sont passées par les variables d'environnement `EUREKA_URL` et
`CONFIG_SERVER_URL`, qui remplacent les placeholders `${EUREKA_URL:http://localhost:8761/eureka/}`.
Le dossier `config-repo` est monté en lecture seule dans `config-server`.

## Endpoints

Tous les endpoints sont accessibles via la gateway (`http://localhost:8090`) ou directement
sur le port du service. Les erreurs sont renvoyées au format
`{timestamp, status, error, message}`.

### class-service — `/api/classes`

| Méthode | URL | Description |
|---|---|---|
| GET | `/api/classes` | Liste paginée et filtrable : `category`, `level`, `dateFrom`, `dateTo` (inclusifs, `yyyy-MM-dd`), `location`, `instructor` (recherche partielle, insensible à la casse), `status`, `page`, `size`, `sort` (défaut `dateTime,asc`, taille 10) |
| GET | `/api/classes/search` | Mêmes filtres, mais seulement les cours `SCHEDULED` par défaut |
| GET | `/api/classes/{id}` | Détail (404 si absent) |
| POST | `/api/classes` | Création → 201 (`currentParticipants=0`, `SCHEDULED`) |
| PUT | `/api/classes/{id}` | Mise à jour. 400 si `maxParticipants` < participants inscrits |
| DELETE | `/api/classes/{id}` | **Annulation logique** (`status=CANCELLED`) : le cours reste consultable par les réservations |
| PATCH | `/api/classes/{id}/increment?spots=N` | Réserve N places. **409** si `current + N > max` ou si le cours n'est pas `SCHEDULED` |
| PATCH | `/api/classes/{id}/decrement?spots=N` | Libère N places. 409 si le résultat serait négatif |

Validation : `name` d'au moins 3 caractères, `durationMinutes` ∈ {30, 45, 60, 90},
`maxParticipants` entre 5 et 30, `price` ≥ 5.00, `dateTime` dans le futur, champs texte
obligatoires. Une violation renvoie 400 avec la liste des champs en erreur.

### booking-service — `/api/bookings`

| Méthode | URL | Description |
|---|---|---|
| GET | `/api/bookings[?status=CONFIRMED]` | Liste des réservations, avec filtre optionnel par statut |
| GET | `/api/bookings/{id}` | Détail |
| GET | `/api/bookings/user/{userId}` | Réservations d'un utilisateur, les plus récentes d'abord |
| GET | `/api/bookings/expired` | Réservations `PENDING_PAYMENT` dont `paymentDeadline` est dépassé |
| POST | `/api/bookings` | Réserver (saga, voir plus bas) → 201 `PENDING_PAYMENT` |
| PATCH | `/api/bookings/{id}/confirm` | Payer → 200 `CONFIRMED` |
| PATCH | `/api/bookings/{id}/cancel` | Annuler → 200 `CANCELLED` (remboursement si payée) |
| PATCH | `/api/bookings/{id}/complete` | `CONFIRMED` → `COMPLETED` |

Codes d'erreur : 400 (validation, `numberOfSpots` hors de 1 à 4, carte sans `cardLastFour`),
404 (réservation ou cours inconnu), **402** (paiement refusé), **409** (plus de places,
paiement expiré, annulation hors délai, transition de statut invalide), **503** (service
distant indisponible).

### payment-service — `/api/payments`

| Méthode | URL | Description |
|---|---|---|
| POST | `/api/payments` | Traite un paiement → 201, avec `status` `SUCCESS` ou `FAILED`. 409 si la réservation est déjà payée |
| GET | `/api/payments/booking/{bookingId}` | Dernier paiement d'une réservation |
| POST | `/api/payments/{id}/refund` | `SUCCESS` → `REFUNDED`. 409 pour tout autre statut (un seul remboursement possible) |
| GET | `/api/payments/user/{userId}` | Historique des paiements d'un utilisateur |
| GET | `/api/payments`, `/api/payments/{id}` | Consultation |

Simulation : un montant **< 100 €** est accepté, un montant **≥ 100 €** est refusé
(`payment.simulation.rejection-threshold`). Un refus n'est pas une erreur HTTP : le paiement
est enregistré en `FAILED` avec son `failureReason`. `paymentReference` suit le format
`PAY-XXXXX`, et un `transactionId` est généré s'il n'est pas fourni.

### notification-service — `/api/notifications`

| Méthode | URL | Description |
|---|---|---|
| POST | `/api/notifications` | Enregistre la notification (`PENDING`) puis tente l'envoi → `SENT` ou `FAILED` |
| GET | `/api/notifications/user/{userId}` | Historique |
| GET | `/api/notifications/pending` | Notifications non envoyées (`PENDING` ou `FAILED`) |
| PATCH | `/api/notifications/{id}/retry` | Nouvel essai (409 si déjà `SENT`) |

L'envoi est simulé par `LoggingEmailSender`, qui écrit l'email dans les logs et **échoue si
l'adresse est absente ou invalide**. Un scheduler relance les notifications non envoyées
toutes les 2 minutes, dans la limite de 3 tentatives.

## Pattern Saga (orchestration par booking-service)

Chaque étape est une transaction **locale** à un service. Les méthodes de `BookingService`
ne sont volontairement pas `@Transactional` : un rollback JPA ne peut pas annuler un appel
HTTP déjà effectué. Les échecs sont donc traités par des **compensations** explicites.

**Cas 1 — Réservation** (`POST /api/bookings`)
1. `GET /api/classes/{id}` : le cours doit exister (sinon 404), être `SCHEDULED` et futur (sinon 409), et avoir assez de places (sinon 409). On prend un *snapshot* du nom, du coach, de la date et du prix.
2. `PATCH /api/classes/{id}/increment?spots=N` : réservation atomique des places.
3. Enregistrement de la réservation `PENDING_PAYMENT` avec une référence `BK-XXXXX`, `totalAmount = price × N`, `paymentDeadline = maintenant + 1 h` et `cancellationDeadline = classDate − 24 h`. *Compensation si l'enregistrement échoue : `decrement`.*
4. Notification `BOOKING_CONFIRMATION` (« Votre réservation est en attente de paiement. Payez avant … »).

**Cas 2 — Plus de places** : si `increment` répond 409 (quelqu'un a réservé entre la
vérification et la réservation), aucune compensation n'est nécessaire et le client reçoit
**409** « Plus de places disponibles pour ce cours ».

**Cas 3 — Paiement** (`PATCH /{id}/confirm`)
1. La réservation doit être `PENDING_PAYMENT` et `paymentDeadline` ne doit pas être dépassé. Sinon 409.
2. `POST /api/payments`.
3. Si `SUCCESS` : la réservation passe `CONFIRMED` et une notification `PAYMENT_CONFIRMATION` est envoyée.
   Si `FAILED` : **compensation**. La réservation passe `CANCELLED`, les places sont libérées (`decrement`), une notification `BOOKING_CANCELLED` est envoyée et le client reçoit **402**. Comme la simulation refuse toujours un montant ≥ 100 €, il n'y a aucun intérêt à laisser la réservation en attente pour un nouvel essai.
   Si payment-service est indisponible : **503**, la réservation reste `PENDING_PAYMENT` et le client peut réessayer.

**Cas 4 — Annulation** (`PATCH /{id}/cancel`)
1. Refus avec 409 si la réservation est déjà `CANCELLED`, `COMPLETED` ou `NO_SHOW`, ou si `cancellationDeadline` est dépassé (moins de 24 h avant le cours).
2. Libération des places (`decrement`). Cette étape s'applique aussi aux réservations `PENDING_PAYMENT`, puisque leurs places avaient été réservées.
3. Si la réservation était `CONFIRMED` : récupération du paiement et `POST /api/payments/{id}/refund`. *Compensation si le remboursement échoue : `increment` pour reprendre les places. La réservation reste alors `CONFIRMED` et le client reçoit l'erreur.*
4. La réservation passe `CANCELLED` et une notification `BOOKING_CANCELLED` est envoyée.

## Verrouillage optimiste (class-service)

`FitnessClass` porte un champ `@Version`. Chaque `increment`/`decrement` relit le cours,
applique la règle métier (`FitnessClass.incrementParticipants` lève
`NoSpotsAvailableException` si `current + spots > max`), puis fait un `saveAndFlush`. Si
une autre transaction a modifié le cours entre-temps, Hibernate ne trouve plus la version
attendue (`UPDATE … WHERE version = ?`) et lève `ObjectOptimisticLockingFailureException`.

Le service **retente alors jusqu'à 3 fois** dans une nouvelle transaction
(`TransactionTemplate`) et réévalue la règle « plus de places » à partir de l'état à jour.
Une requête concurrente qui trouve encore des places n'est donc pas refusée à tort. Si le
conflit persiste, le client reçoit 409.

`OptimisticLockingConcurrencyTest` lance 25 réservations simultanées sur un cours de 10
places. Le test vérifie que le cours ne dépasse jamais 10 participants, que chaque
réservation acceptée a bien été comptée, et que la version a été incrémentée une fois par
réservation acceptée.

Une réservation (`Booking`) porte aussi un champ `@Version`. Il empêche qu'une
confirmation et une annulation ou expiration concurrentes s'écrasent.

## Schedulers (booking-service)

| Tâche | Fréquence | Action |
|---|---|---|
| `cancelExpiredBookings` | 5 min (`booking.scheduler.expiration-rate`) | Réservations `PENDING_PAYMENT` avec `paymentDeadline < now` : libère les places, passe la réservation `CANCELLED` et envoie `BOOKING_CANCELLED`. Si class-service est indisponible, la réservation reste en attente et sera retraitée au passage suivant |
| `sendClassReminders` | 15 min (`booking.scheduler.reminder-rate`) | Réservations `CONFIRMED` dont le cours a lieu dans les prochaines 24 h : envoie `BOOKING_REMINDER` **une seule fois** (flag `reminderSent`). Une égalité exacte `classDate = now + 24h` n'arriverait jamais avec un scheduler périodique, d'où cette fenêtre |

## Tests

```bash
mvn -pl class-service,booking-service,payment-service,notification-service test
```

| Module | Classe | Contenu |
|---|---|---|
| class-service | `FitnessClassTest` | Règles de l'entité : incrément, capacité exacte, dépassement, décrément sous 0 |
| | `FitnessClassServiceTest` | Mockito : cours annulé, introuvable, **nouvel essai après conflit de version**, abandon après 3 tentatives |
| | `FitnessClassControllerIntegrationTest` | MockMvc + H2 : validation 400, filtres, pagination et tri, `/search`, increment/decrement, 409, annulation, PUT |
| | `OptimisticLockingConcurrencyTest` | 25 threads concurrents sur 10 places, sans aucune surréservation |
| booking-service | `BookingServiceTest` | Tests unitaires du saga, dont les 3 exigés (`shouldCreateBooking_whenSpotsAvailable`, `shouldThrowException_whenNoSpotsAvailable`, `shouldCancelBookingAndRefund_whenWithinDeadline`), plus la course sur la dernière place, le paiement refusé ou indisponible, l'annulation hors délai, la compensation après un remboursement en échec, l'expiration et les rappels |
| | `BookingFlowIntegrationTest` | MockMvc + H2, dont les 2 tests exigés (`shouldCompleteFullBookingFlow`, `shouldCancelExpiredBookings`) et les scénarios d'erreur de la collection Postman |
| | `FeignCircuitBreakerIntegrationTest` | **Vraie chaîne Feign (PATCH via feign-hc5) + Resilience4j + fallbacks** face à un serveur HTTP bouchon : 409 et 404 traduits sans ouvrir le circuit, 500 converti en 503, notification-service coupé sans impact, circuit de payment-service `OPEN` après des pannes répétées |
| payment-service | `PaymentServiceTest`, `PaymentControllerIntegrationTest` | Seuil de 100 €, double paiement 409, remboursement unique, validation de la carte |
| notification-service | `NotificationServiceTest`, `NotificationControllerIntegrationTest` | Statuts SENT et FAILED, `/pending`, relance, 409 sur une notification déjà envoyée |

Dans les tests de booking-service, les services distants sont remplacés par des *fakes*
stateful (`support/Fake*Client`). Ils appliquent les mêmes règles que les vrais services
(capacité, seuil de 100 €, remboursement unique), ce qui permet de vérifier l'état réel
(« places = 7 », « paiement REFUNDED ») et pas seulement des appels mockés.

## Collection Postman

Le fichier [`postman/ProjetMicroservice.postman_collection.json`](postman/ProjetMicroservice.postman_collection.json)
contient 32 requêtes avec leurs assertions, à exécuter dans l'ordre avec le *Collection
Runner*. Les ids créés sont chaînés par des variables de collection et les dates des cours
sont calculées par des pre-request scripts.

1. **Gestion des cours** : créer, lister (pagination), filtrer YOGA/INTERMEDIATE, rechercher, détail
2. **Réservation** : réserver 2 places → `PENDING_PAYMENT`, places réservées
3. **Paiement** : payer par carte → `CONFIRMED`, paiement `SUCCESS`
4. **Annulation** : annuler → `CANCELLED`, paiement `REFUNDED`, places libérées, historique des notifications
5. **Scénarios d'erreur** : surréservation (409), paiement expiré (409, **profil `demo`**), annulation à moins de 24 h (409), paiement refusé à 120 € (402), cours invalide (400), réservation inconnue (404)
6. **Suivi** : réservations expirées, historique des paiements, notifications en attente

Ligne de commande : `npx newman run postman/ProjetMicroservice.postman_collection.json`
(booking-service lancé avec le profil `demo`).

## Configuration (`config-server/config-repo`)

- `class-service.yml`, `booking-service.yml`, `payment-service.yml`, `notification-service.yml` : port, nom de l'application et paramètres métier (délais, fréquence des schedulers, seuil de paiement, nombre de relances).
- `booking-service-demo.yml` : profil `demo` avec des délais courts.
- `api-gateway.yml` : 4 routes (`/api/classes/**`, `/api/bookings/**`, `/api/payments/**`, `/api/notifications/**`), plus l'ajout de `PATCH` aux méthodes autorisées par CORS.
- `application.yml` : l'URL d'Eureka est surchargeable par `EUREKA_URL` (Docker).

Chaque service garde aussi des valeurs par défaut dans son `application.yml` local ou dans
ses `@Value`. Il démarre donc même sans config-server (`optional:configserver`).

## Choix et limites

- **Paiement refusé** : la réservation est annulée (402) plutôt que laissée en attente, car la simulation est déterministe.
- **DELETE sur un cours** : annulation logique. Aucune notification `CLASS_CANCELLED` n'est envoyée aux inscrits, car class-service ne connaît pas les réservations. Il faudrait un appel ou un événement vers booking-service.
- **Compensations best effort** : si la libération des places échoue après un paiement refusé, l'erreur est journalisée (`COMPENSATION ECHOUEE`) mais pas rejouée automatiquement. En production, on utiliserait une *outbox* ou une file de messages.
- **Emails et SMS** : simulés (logs).
- **Bases H2 en mémoire** : les données sont perdues à chaque redémarrage.

---

## Modules précédents — Module 10 : book-service & loan-service


Ajout de deux microservices metier au projet `mon-projet-microservice` : `book-service`
(catalogue de livres) et `loan-service` (emprunts), en reutilisant `eureka-server`,
`config-server` et `api-gateway` deja construits.

### Demarrage

Dans l'ordre, chacun dans son propre terminal (ou via `spring-boot:run` si les jars ne
sont pas repackages — voir remarque ci-dessous) :

```bash
cd eureka-server   && mvn spring-boot:run   # :8761
cd config-server   && mvn spring-boot:run   # :8888
cd product-service && mvn spring-boot:run   # :8081
cd book-service    && mvn spring-boot:run   # :8091
cd loan-service    && mvn spring-boot:run   # :8092
cd api-gateway     && mvn spring-boot:run   # :8090
```

> **Remarque** : le `pom.xml` racine du projet n'etend pas `spring-boot-starter-parent`,
> donc le goal `repackage` de `spring-boot-maven-plugin` n'est pas lie automatiquement a
> la phase `package` (c'etait deja le cas pour `product-service`, `eureka-server`, etc.
> avant ce module). `mvn package` produit donc un jar "plat" non executable par
> `java -jar`. Utilisez `mvn spring-boot:run` pour lancer chaque service, ou ajoutez un
> bloc `<executions><execution><goals><goal>repackage</goal></goals></execution></executions>`
> au plugin si vous voulez des jars executables.

`book-service` et `loan-service` s'enregistrent automatiquement aupres d'Eureka et
recuperent leur configuration (port, nom d'application) depuis `config-server`
(`config-repo/book-service.yml` et `config-repo/loan-service.yml`).

Chaque service charge quelques donnees de demonstration au demarrage (`DataLoader`) :
`book-service` cree 3 livres (ids 1 a 3 en base fraiche).

### Endpoints

### book-service (port 8091, route gateway `/api/books/**`)

| Methode | URL | Description |
|---|---|---|
| GET | `/api/books` | Lister tous les livres |
| GET | `/api/books/{id}` | Recuperer un livre (404 si absent) |
| POST | `/api/books` | Creer un livre (`availableCopies` initialise a `totalCopies`) |
| PUT | `/api/books/{id}` | Mettre a jour un livre |
| DELETE | `/api/books/{id}` | Supprimer un livre |
| PATCH | `/api/books/{id}/decrement-stock` | Decremente `availableCopies` de 1 — **409** si deja a 0 |
| PATCH | `/api/books/{id}/increment-stock` | Reincremente `availableCopies` de 1, sans jamais depasser `totalCopies` |

### loan-service (port 8092, route gateway `/api/loans/**`)

| Methode | URL | Description |
|---|---|---|
| GET | `/api/loans` | Lister tous les emprunts |
| GET | `/api/loans/{id}` | Recuperer un emprunt (404 si absent) |
| POST | `/api/loans` | Creer un emprunt — **400** si le livre n'existe pas, **409** si plus d'exemplaire disponible |
| PATCH | `/api/loans/{id}/return` | Marquer l'emprunt comme rendu — **409** si deja rendu |

`loan-service` appelle `book-service` via un client Feign (`BookClient`) : lecture
(`GET /api/books/{id}`) puis ecriture (`decrement-stock` / `increment-stock`). La regle
"stock epuise" est verifiee deux fois (defense en profondeur / TOCTOU) : une premiere
fois par `loan-service` avant d'appeler, une seconde fois par `book-service` lui-meme
avant de decrementer.

Le client Feign utilise `feign-hc5` (Apache HttpClient 5) plutot que le client par
defaut, qui ne supporte pas la methode HTTP `PATCH`.

### Tests

```bash
mvn -pl book-service,loan-service test
```

- `book-service` : `BookServiceTest` (Mockito — decrement/increment, stock epuise) et
  `BookControllerIntegrationTest` (MockMvc — CRUD, 404, 400, 409 sur decrement-stock).
- `loan-service` : `LoanServiceTest` (Mockito, `BookClient` mocke — livre inexistant,
  stock epuise, **cas de concurrence** ou `decrement-stock` renvoie 409 entre la
  verification et l'appel, calcul de `dueDate`, retour) et
  `LoanControllerIntegrationTest` (MockMvc + `@MockBean BookClient` — memes scenarios
  via l'API REST).

### Fichiers `.http`

- [`book-service/book-service.http`](book-service/book-service.http) et
  [`loan-service/loan-service.http`](loan-service/loan-service.http) : appels directs a
  chaque service.
- [`api-gateway/api-gateway.http`](api-gateway/api-gateway.http) : scenario complet via
  la gateway (port 8090) — creation d'un livre a un seul exemplaire, emprunt reussi,
  emprunt refuse (stock epuise, 409), emprunt refuse (livre inexistant, 400), retour,
  tentative de rendre deux fois le meme emprunt (409).

Ce scenario a ete verifie manuellement de bout en bout via la gateway (eureka-server,
config-server, book-service, loan-service, api-gateway demarres ensemble).
