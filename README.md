# Module 10 — book-service & loan-service

Ajout de deux microservices metier au projet `mon-projet-microservice` : `book-service`
(catalogue de livres) et `loan-service` (emprunts), en reutilisant `eureka-server`,
`config-server` et `api-gateway` deja construits.

## Demarrage

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

## Endpoints

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

## Tests

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

## Fichiers `.http`

- [`book-service/book-service.http`](book-service/book-service.http) et
  [`loan-service/loan-service.http`](loan-service/loan-service.http) : appels directs a
  chaque service.
- [`api-gateway/api-gateway.http`](api-gateway/api-gateway.http) : scenario complet via
  la gateway (port 8090) — creation d'un livre a un seul exemplaire, emprunt reussi,
  emprunt refuse (stock epuise, 409), emprunt refuse (livre inexistant, 400), retour,
  tentative de rendre deux fois le meme emprunt (409).

Ce scenario a ete verifie manuellement de bout en bout via la gateway (eureka-server,
config-server, book-service, loan-service, api-gateway demarres ensemble).
