# Enlistment Enterprise

The application validates its PostgreSQL schema at startup. Run Liquibase separately through Gradle before starting the application against a database that needs migrations.

## Connect to UAT

Set the same connection variables for both Gradle and Spring Boot. Obtain the JDBC URL and credentials for your assigned UAT database before running any command below. Keep credentials out of source control.

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://<uat-host>/<database>'
export SPRING_DATASOURCE_USERNAME='<username>'
export SPRING_DATASOURCE_PASSWORD='<password>'
```

In PowerShell, use `$env:SPRING_DATASOURCE_URL = '...'` and the corresponding assignments for username and password. Use `.\gradlew.bat` in place of `./gradlew`. Run the commands from the repository root.

## Baseline UAT once

1. Check that you are connected to your assigned database. For an empty UAT database that needs sample data, start the app once with Hibernate's `create` override. Wait for startup and the `import.sql` inserts, then stop the app. **The override drops and recreates mapped tables at startup.** If UAT already contains data, skip this command and verify that its existing tables and constraints match the baseline SQL before proceeding.

   ```bash
   ./gradlew bootRun --args='--spring.jpa.hibernate.ddl-auto=create'
   ```

2. Clear any persistent Hibernate DDL override, including `SPRING_JPA_HIBERNATE_DDL_AUTO`, so subsequent startups use `validate`.
3. Run `status` and verify that it lists only the initial changesets as pending. Run `changelogSync` only after confirming that every pending change is already present in the database. It records all pending changesets. Use this procedure on the baseline revision, before later migrations are added.

   ```bash
   ./gradlew status
   ./gradlew changelogSync
   ./gradlew status
   ```

4. Confirm that the second status reports no pending changesets and that `databasechangelog` contains an entry for each baseline changeset.

   ```sql
   SELECT id, author, exectype FROM databasechangelog ORDER BY orderexecuted;
   ```

5. Run `./gradlew bootRun` with the default validation setting. Restart it and verify that the existing rows are still present.

## Apply later migrations

Review the pending changes, apply them, and verify the result before starting the app.

```bash
./gradlew status
./gradlew update
./gradlew status
```

Use the same `update` workflow to build the schema on an empty database that does not need sample data. The baseline creates the schema. Sample data is loaded by Hibernate's one-time `create` procedure above.

## Tests

Run `./gradlew test integrationTest`. Integration tests require Docker. Controller tests create and seed their schema with Hibernate. The schema migration test uses an empty PostgreSQL container, applies Liquibase migrations, and starts Spring with Hibernate validation.
