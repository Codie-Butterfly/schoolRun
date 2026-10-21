# School Run backend

Java 17 + Maven, Spring Boot, JDBC. Local H2 file database; PostgreSQL JDBC driver included. This is a development build, not a production deployment.

## Run locally

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn spring-boot:run
```

The API listens on `127.0.0.1:9200/api`. Health: `GET /api/health`.
Configuration is read from environment variables listed in `.env.example`; Spring does not automatically source `.env`. Set variables through the service manager/container or export them in your shell. Keep credentials outside source control.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn test
```

Tests use isolated in-memory databases and a mocked payment gateway. They do not submit real payments.

## API groups

- `POST /api/auth/register`, `/api/auth/login`; `GET /api/me`; `POST /api/auth/logout`.
- `GET /api/drivers/{code}`; `GET/POST /api/links`; `GET/POST /api/children`.
- `POST /api/documents/licence` multipart `file`; `GET /api/documents/{id}` owner only.
- `GET/PUT /api/children/{id}/schedule`; `PUT/DELETE /api/children/{id}/days/{YYYY-MM-DD}`.
- `GET /api/route?day=YYYY-MM-DD`; `POST /api/trips/{id}/pickup|dropoff|gate`.
- `POST /api/position`; `GET /api/children/{id}/tracking` (own parent, onboard only).
- `GET /api/notifications` unread only; `POST /api/notifications/{id}/read`; `POST /api/broadcast`.
- `GET /api/subscriptions?month=YYYY-MM`; `GET/POST /api/payments`; `POST /api/payments/{id}/check`.
- Paynow callback `POST /api/paynow/result`, return `GET /api/paynow/return`.

Private requests require `Authorization: Bearer <token>`. Sessions expire after seven days and are stored as hashes; passwords use BCrypt. Phone uniqueness uses canonical international format. Every resource checks account ownership. Licence images are kept outside public web roots.

## Payment rules

Customer-level billing is provisional until the owner confirms the unit. Unsupported units fail closed. Configure an integration dedicated to School Run, an HTTPS public callback base, positive USD price, and a comma-separated test-driver ID allowlist before test initiation. Never reuse Chef Tarie's integration. Going live requires explicit `PAYNOW_TEST_MODE=false` plus payment-account readiness.

Amounts and selected customer ownership are server validated. Reservations persist before gateway calls; uncertain initiation is UNKNOWN and blocks overlapping retries. Reuse the same idempotency key after a client timeout. Callback signatures are checked and stored poll URLs independently polled; reference and amount must match before granting monthly coverage. Unknown initiation without a poll URL needs manual reconciliation; a 60-second background worker reconciles pending payments with known poll URLs. Refund/chargeback reconciliation remains launch work.

## Limits

The route planner is a capacity/precedence/time-window heuristic with straight-line distances at an assumed 25 km/h plus two minutes per stop. It flags estimated lateness, does not guarantee a globally optimal route and is not a road/traffic ETA service. Configure and field-test a road-routing provider before public use.

See `../school_run/docs/IMPLEMENTATION-STATUS.md` for remaining launch work. Back up both the database and private upload directory. Production schema migrations, PostgreSQL integration testing, rate limits, account recovery and verified contact ownership remain required before external deployment.
