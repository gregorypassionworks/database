# In-Memory Key–Value Store with Nested Transactions

A Scala project providing two implementations of a 
simple key→value store (String keys, BigDecimal values) 
with full nested transaction support (BEGIN/ROLLBACK/COMMIT):
- Mutable Map version (database.normal.Database)
- ZIO STM version (database.stmt.DatabaseSTM + database.stmt.app.DemoApp)

---

## Features

- SET key value
- GET key (prints value or NULL)
- DELETE key
- COUNT value (number of keys holding that value)
- BEGIN / ROLLBACK / COMMIT with nested transactions
- Two interchangeable backends:
  - Classic mutable maps
  - ZIO STM with atomic TRef snapshots

---

## Prerequisites

- Java 17 or newer
- scala 2.13.12
- sbt 1.7.x or later

---

## Getting Started

1. Clone the repository  
   Replace `<owner>` with the GitHub account or organization name hosting this repo (the part before `/database` in the URL):
   ```bash
   git clone https://github.com/<owner>/database.git

## Example REPL Session
```text
> SET a 10
> GET a
10

> BEGIN
> SET a 20
> GET a
20

> ROLLBACK
> GET a
10

> BEGIN
> SET b 30
> COUNT 30
1

> COMMIT
> GET b
30

> DELETE a
> COMMIT
> GET a
NULL