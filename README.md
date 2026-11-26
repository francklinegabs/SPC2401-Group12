# SPC2401-Group12 – KYUCampusGrade (Java RMI)

A distributed grading service for the campus. A `GradeService` remote object stores marks per
student and unit. **Lecturer clients** call `submitMark` and `publishMarks`. **Student clients** call
`getMark` and subscribe to be told, by an RMI callback, when marks are published. Clients find the
service by name in the **RMI registry**.

Group: **Group 12** · see [TEAM.md](TEAM.md) · report: [docs/report/](docs/report/)

## What is implemented

| Requirement | Where |
|---|---|
| 6.1 Remote interfaces, listener, serialisable classes | `src/ke/ac/kyu/campusgrade/api/` (`GradeService`, `StudentListener`, `Lease`, `MarkRecord`, 6 exceptions) · class diagram: [docs/class-diagram.svg](docs/class-diagram.svg) |
| 6.1 Server exported and bound in the registry | `server/GradeServer.java` |
| 6.1 Lecturer and student clients calling every method | `client/LecturerClient.java`, `client/StudentClient.java` |
| 6.2 Subscriptions with a lease; dead listeners skipped and removed | `server/SubscriptionManager.java`, `server/Notifier.java` |
| 6.3 Concurrent requests, protected store, notifier thread pool, no lock held during a callback | `server/MarkStore.java` (read/write lock), `server/WorkerPool.java`, `server/Notifier.java`, `GradeServiceImpl.publishMarks` |
| 6.4 RemoteException handled on both sides, client timeout and retry, request ID applied once, timestamped failure log | `client/RetryingCaller.java`, `server/RequestDeduplicator.java`, `server/Journal.java`, `common/EventLog.java` |
| 6.5 No hard-coded host or port | `common/Config.java` + `config/kyu.properties` |

## Folder layout

```
src/        application source (package ke.ac.kyu.campusgrade)
tests/      JUnit 5 unit + integration tests, failure experiments, benchmark
docs/       report, diagrams, plotting script, work packages, GitHub workflow
evidence/   test output, logs, experiment results, performance CSVs and graphs
config/     kyu.properties (all hosts, ports and tunables)
scripts/    build and test scripts (PowerShell and sh)
```

## Requirements

* JDK 17 or newer (developed and tested with Temurin 21) on the `PATH`
* Internet once, so the build script can download the JUnit 5 console jar into `lib/` (not committed)
* Python 3 with matplotlib, only to redraw the graphs

## Build

Windows (PowerShell):

```powershell
.\scripts\build.ps1
```

Linux / macOS / Git Bash:

```sh
sh scripts/build.sh
```

Both compile `src/` into `out/classes` and `tests/` into `out/test-classes`.

## Run: registry, server and two clients

Open four terminals in the repository folder. Host and port come from `config/kyu.properties`
(`registry.host=localhost`, `registry.port=1099`); change them there or override any key with
`-Dkyu.<key>=value` or the environment variable `KYU_<KEY>`.

**1. RMI registry.** The registry must be able to load the remote interfaces, so give it the class path:

```powershell
rmiregistry -J-Djava.class.path=out/classes 1099
```

**2. Server**

```powershell
java -cp out/classes ke.ac.kyu.campusgrade.server.GradeServer
```

(Alternatively skip step 1 and let the server create its own registry:
`java -cp out/classes -Dkyu.registry.create=true ke.ac.kyu.campusgrade.server.GradeServer`.)

**3. Student client** (subscribes, renews its lease, and fetches the mark when notified)

```powershell
java -cp out/classes ke.ac.kyu.campusgrade.client.StudentClient KYU001 --units SPC2401
```

Commands: `get <unit>`, `quit`. A second student is just another terminal with another ID (`KYU002`).

**4. Lecturer client** (interactive, or commands separated by `;`)

```powershell
java -cp out/classes ke.ac.kyu.campusgrade.client.LecturerClient "submit KYU001 SPC2401 78; submit KYU002 SPC2401 64; publish SPC2401; list SPC2401"
```

Commands: `submit <student> <unit> <mark>`, `publish <unit>`, `get <student> <unit>`, `list <unit>`, `quit`.
The student terminals print `NOTIFIED marks published for SPC2401` followed by `MARK KYU001 SPC2401 = 78`.

### Running across machines

On the server machine set `server.hostname` to its LAN IP (the address written into stubs). On each
client set `registry.host` to that IP; on student machines also set `client.callback.hostname` to the
student's own IP so the server can call back. Firewalls must allow the registry port, the server
export port (`server.port`) and the student callback ports.

## Tests and evidence

```powershell
.\scripts\test.ps1                      # 31 JUnit tests, XML reports in evidence/test-reports
java -cp "out/classes;out/test-classes" ke.ac.kyu.campusgrade.experiments.FailureExperiments   # 7.3
java -cp "out/classes;out/test-classes" ke.ac.kyu.campusgrade.experiments.Benchmark 5          # 7.4
java -cp "out/classes;out/test-classes" ke.ac.kyu.campusgrade.experiments.SizingExperiments    # 2.2, 3.4
python docs/plot_perf.py                                                                         # graphs
```

On Linux/macOS replace `;` with `:` in the class path.

| Evidence | File |
|---|---|
| JUnit run (31 tests) | `evidence/test-output.txt`, `evidence/test-reports/` |
| 4-process integration test | `evidence/logs/integration/` |
| Concurrency and failure experiments | `evidence/experiments/summary.md` and one folder per experiment |
| Performance tables and graphs | `evidence/perf/*.csv`, `evidence/perf/*.png` |
| Terminal captures | `evidence/screenshots/` |

## Configuration keys

| Key | Default | Meaning |
|---|---|---|
| `registry.host` / `registry.port` | localhost / 1099 | where the registry is |
| `registry.create` | false | server starts its own registry |
| `server.hostname` | (unset) | address written into stubs |
| `server.port` | 0 | export port of GradeService (0 = any) |
| `server.workers` | 8 | worker-pool size |
| `notifier.threads` | 20 | callback thread pool |
| `lease.default.ms` / `lease.max.ms` | 600000 / 3600000 | subscription lease |
| `server.journal` | data/marks.journal | write-ahead journal (`none` disables) |
| `client.timeout.ms` / `client.retries` / `client.backoff.ms` | 5000 / 3 / 250 | client fault handling |
| `socket.timeout.ms` | 15000 | connect/read timeout on every RMI socket |
| `log.dir` | logs | where component logs go |
| `net.delay.ms`, `sim.*` | 0 | experiment knobs only |
