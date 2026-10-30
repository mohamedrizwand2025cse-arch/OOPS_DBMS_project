**Problem Statement: Lack of Transparency in Welfare and Opportunity Eligibility:**

Many students and individuals struggle to identify and access scholarships, internships, government welfare schemes, and other opportunities because eligibility criteria are complex, scattered across different sources, and difficult to understand. Manual verification can be time-consuming and inconsistent, potentially causing eligible applicants to miss valuable opportunities.
Proposed solution — RuleWeaver X: A configurable, explainable eligibility platform that evaluates applicants against clearly defined rules, provides transparent reasons for decisions, and maintains policy versions and evaluation history to support consistency and accountability.

**Social impact:**
- Improves access to scholarships and other opportunities.
- Reduces confusion about eligibility requirements.
- Makes decisions easier to understand and review.
- Helps organizations handle applications more consistently.**
  
# RuleWeaver — Dynamic Rule Engine & Management System

**Repository:** `DBMS_OOPS_PROJECT`  
**Root Package:** `com.ruleweaver`  
**Technology stack:**
| Layer                  | Technology                                       |
| ---------------------- | ------------------------------------------------ |
| UI                     | JavaFX 20.0.1,AtlantaFX            |
| Language               | Java 17                                          |
| Application Logic      | Decision Engine, Repositories, Policy Versioning |
| Database               | MongoDB Atlas                                    |
| Database Access        | MongoDB Java Driver                              |
| SQL Connectivity       | JDBC, MongoDB JDBC Driver 4.0.0                  |
| SQL Query Layer        | MongoDB Atlas SQL / Data Federation              |
| Build and Dependencies | Apache Maven                                     |
| Testing                | JUnit 5                                          | 

---

## 📖 About The Project

**RuleWeaver** is an Object-Oriented Database Application designed to automate complex eligibility assessment, rule management, and policy enforcement across dynamic datasets.

In traditional software systems, business rules (such as loan approvals, grant eligibility, or insurance qualification) are hardcoded into application logic. RuleWeaver solves this by decoupling business rules from code logic through a dynamic rule engine. Policies and evaluation conditions are stored dynamically in MongoDB and evaluated at runtime against incoming applicant data.

---

## ✨ Key Features

* **Dynamic Policy Engine:** Define, edit, and evaluate complex conditional logic without re-compiling or re-deploying application code.
* **NoSQL Persistence Layer:** Scalable document-based storage for flexible applicant structures, policy rules, and evaluation histories using MongoDB.
* **Modular Layered Architecture:** Strict separation of concerns between presentation (JavaFX), business logic (Services/Engine), domain models, and data access (Repositories).
* **Robust Domain Model:** Built using clean Object-Oriented Software Engineering principles (Encapsulation, Polymorphism, Abstraction, and Single Responsibility).
* **Automated Eligibility Processing:** Evaluates applicants against active policies and outputs pass/fail statuses with granular rule trace logs.
* **Transparent Decision Explanations:** Provides rule-level evaluation details to help users understand why an applicant passed or failed specific eligibility criteria.
* **Evaluation History:** Stores decision records for reviewing previous eligibility assessments and supporting traceability.

---
## Bidirectional Database Synchronization

RuleWeaver X supports synchronization of applicant data between MongoDB Atlas and MySQL.

### Synchronization flow

- **MongoDB → MySQL:** MongoDB Change Streams detect database changes and propagate applicant updates to MySQL.
- **MySQL → MongoDB:** MySQL outbox events are processed by a background worker, which applies corresponding changes to MongoDB.
- **Loop prevention:** Synchronization-origin metadata helps prevent changes written by one synchronization direction from being repeatedly propagated back.
- **Deletion handling:** Deletions are represented using soft-delete metadata so that deleted applicants can be excluded from normal application listings.

### Configuration

Configure the MongoDB connection using the project's supported configuration mechanism. Configure MySQL credentials through environment variables:

```powershell
$env:MYSQL_URL = "jdbc:mysql://localhost:3306/ruleweaver_sql?serverTimezone=UTC"
$env:MYSQL_USER = "your_mysql_username"
$env:MYSQL_PASSWORD = "your_mysql_password"
```

`MYSQL_URL` is optional; the value above is the default. If the MySQL console uses another host or schema, set `MYSQL_URL` to that same database URL. The application never logs the URL or credentials.

### MySQL schema and triggers

Run [`src/main/resources/db/mysql-sync-setup.sql`](src/main/resources/db/mysql-sync-setup.sql) against MySQL before starting synchronization. From PowerShell, in the project directory, run the script with a MySQL account permitted to create the database/tables and create/drop triggers:

```powershell
mysql --host=localhost --user=YOUR_SETUP_USER --password `
    --execute="source src/main/resources/db/mysql-sync-setup.sql"
```

The script creates `ruleweaver_sql`, `synced_applicants`, and `sync_outbox` only when absent; it replaces only the three named triggers when rerun. It does not drop or alter either table or delete applicant/outbox data. Ensure the application MySQL account can read/write `synced_applicants`, read/update `sync_outbox`, and that the trigger definer has permission to insert into `sync_outbox`.

The schema installer does not enqueue pre-existing applicants. To safely queue an existing active row once, without changing or deleting the applicant, run this after installing the triggers (replace the ID as needed):

```sql
INSERT INTO sync_outbox (mongo_id, operation)
SELECT a.mongo_id, 'UPSERT'
FROM synced_applicants AS a
WHERE a.mongo_id = 'MYSQL_TO_MONGO_TEST_001'
  AND NOT EXISTS (
      SELECT 1
      FROM sync_outbox AS pending
      WHERE pending.mongo_id = a.mongo_id
        AND pending.operation = 'UPSERT'
        AND pending.processed = FALSE
  );
```

The worker starts after MongoDB connects successfully and polls every five seconds on its own scheduled background thread. A MySQL or MongoDB failure is logged with event/database error context (without logging connection strings); failed events remain unprocessed and are retried on later polls.

### Repeatable synchronization verification

Use a disposable test database and run the following steps while the application is running and connected to MongoDB Atlas. Do not reuse a test ID that already exists. The generated ID is a string ID, which is supported by the application.

1. **Trigger / insert:** In the MySQL console, generate and insert a unique ID:

   ```sql
   SET @rwx_test_id = CONCAT('RWX_SYNC_TEST_', REPLACE(UUID(), '-', ''));
   INSERT INTO synced_applicants
       (mongo_id, program_id, name, email, gpa, interview_score,
        skills, custom_attributes, version, is_deleted)
   VALUES
       (@rwx_test_id, 'TEST_PROGRAM', 'MySQL Sync Test',
        'rwx-sync-test@example.com', 8.50, 85,
        JSON_ARRAY('Java', 'SQL'),
        JSON_OBJECT('source', 'mysql_sync_test'), 1, FALSE);
   SELECT @rwx_test_id;
   ```

   Save the returned ID. Confirm that `sync_outbox` contains a new event with this ID and `operation = 'UPSERT'`:

   ```sql
   SELECT id, mongo_id, operation, processed, created_at, processed_at
   FROM sync_outbox
   WHERE mongo_id = 'PASTE_RETURNED_ID'
   ORDER BY id;
   ```

2. **Worker:** Check the application log for the `Applying MySQL sync event` entry and successful processed-event count. In Atlas, confirm `applicants` contains `_id` equal to the test ID and the inserted values. Confirm the outbox row has `processed = 1` and non-null `processed_at`. The worker marks it processed only after its MongoDB replace/soft-delete call succeeds.

3. **Update:** Change a MySQL field and verify a second `UPSERT` is queued, then wait for the worker and confirm Atlas reflects the changed field:

   ```sql
   UPDATE synced_applicants
   SET name = 'MySQL Sync Test Updated'
   WHERE mongo_id = 'PASTE_RETURNED_ID';
   ```

4. **Delete:** Hard-delete only this disposable test row. The MySQL `AFTER DELETE` trigger queues `DELETE`; the worker follows the application convention by marking the MongoDB document `_sync_deleted = true` rather than physically deleting it:

   ```sql
   DELETE FROM synced_applicants
   WHERE mongo_id = 'PASTE_RETURNED_ID';
   ```

   Confirm a `DELETE` outbox event is processed and the Atlas document is soft-deleted/excluded from normal applicant listings.

5. **Regression / failure handling:** Create or edit an applicant through the application and verify the MongoDB-to-MySQL listener updates `synced_applicants` without adding outbox rows (it sets `@ruleweaver_sync_origin = 'mongo'`). Verify MySQL-originated updates add outbox rows but are ignored by the MongoDB change-stream listener via `_sync_origin = 'mysql'`. In a non-production environment only, make MongoDB temporarily unavailable while a disposable event is pending: confirm the worker logs the failed event and it remains `processed = 0`; restore MongoDB and confirm a later poll succeeds. Never modify or clear production outbox data to conduct this check.

Check installed triggers and schema directly when diagnosing an existing database:

```sql
SHOW TRIGGERS FROM ruleweaver_sql;
SHOW CREATE TABLE ruleweaver_sql.synced_applicants;
SHOW CREATE TABLE ruleweaver_sql.sync_outbox;
```

An attempted duplicate-key `INSERT` does not fire `AFTER INSERT`; it failed before inserting. If that row predates trigger installation and has no pending event, use the safe one-row UPSERT enqueue above rather than deleting/reinserting it.

Start the application with:

```powershell
mvn javafx:run
```

Set the credentials in the same PowerShell session used to launch the application. Do not commit database passwords, connection secrets, or local configuration files.

### Verification

Run the automated test suite with:

```powershell
mvn clean test
```

Verify database synchronization separately by creating, updating, and deleting a test applicant, then checking the corresponding records in both databases.

## 🏗️ System Architecture

### Project Structure

The project follows the standard Maven directory layout and organizes Java classes into packages based on their responsibilities.

```text
src/
└── main/
    ├── java/
    │   └── com/
    │       └── ruleweaver/
    │           ├── config/       # MongoDB and MySQL configuration
    │           ├── model/        # Domain entities (Applicant, Policy, etc.)
    │           ├── repository/   # Data access and persistence operations
    │           ├── service/      # Business logic and domain services
    │           ├── sync/         # Bidirectional MongoDB–MySQL synchronization
    │           ├── engine/       # Rule evaluation and decision logic
    │           ├── ui/           # JavaFX interface and application context
    │           └── Main.java     # Application entry point (if located here)
    └── test/
        └── java/                 # Automated tests
```

**Architecture overview**

- **Config:** Database connections and application configuration.
- **Model:** Core domain objects such as applicants, policies, and evaluation results.
- **Repository:** Database access and persistence operations.
- **Service:** Business operations and domain-level workflows.
- **Sync:** Background workers and change listeners that synchronize applicant data between MongoDB Atlas and MySQL.
- **Engine:** Policy evaluation and decision-making logic.
- **UI:** JavaFX views, navigation, and application interaction.
- **Tests:** Automated checks for core logic and application behavior.
