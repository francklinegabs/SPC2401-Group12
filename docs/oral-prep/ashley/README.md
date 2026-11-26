# Fault handling and Amoeba – study cards (Ashley Odiwuor)

One question per card, answered from my own package (WP4) and report Section 5, with numbers from our experiments.

## Config – no hard-coded host or port

1. [In what order does Config look up a value?](01-in-what-order-does-config-look-up-a-value.md)
2. [How is a key turned into an environment variable name?](02-how-is-a-key-turned-into-an-environment-variable-n.md)
3. [Which properties file does Config read, and can it be changed?](03-which-properties-file-does-config-read-and-can-it.md)

## EventLog – the timestamped failure log

4. [What does one EventLog line look like?](04-what-does-one-eventlog-line-look-like.md)
5. [Where does EventLog write?](05-where-does-eventlog-write.md)

## KyuClientSocketFactory – timeouts and simulated delay

6. [What does KyuClientSocketFactory do on every new socket?](06-what-does-kyuclientsocketfactory-do-on-every-new-s.md)
7. [In which JVM does the socket factory run?](07-in-which-jvm-does-the-socket-factory-run.md)
8. [Why does the factory need equals and hashCode?](08-why-does-the-factory-need-equals-and-hashcode.md)

## RetryingCaller – timeout, retry, back-off

9. [What does RetryingCaller.call do, in one sentence?](09-what-does-retryingcaller-call-do-in-one-sentence.md)
10. [How is the per-attempt timeout implemented?](10-how-is-the-per-attempt-timeout-implemented.md)
11. [Why does a timed-out attempt not stop the server?](11-why-does-a-timed-out-attempt-not-stop-the-server.md)
12. [Which errors are never retried, and why?](12-which-errors-are-never-retried-and-why.md)
13. [Why drop the cached stub after a failure?](13-why-drop-the-cached-stub-after-a-failure.md)
14. [What back-off do we use?](14-what-back-off-do-we-use.md)

## RequestDeduplicator – at-most-once

15. [What problem does RequestDeduplicator solve?](15-what-problem-does-requestdeduplicator-solve.md)
16. [How does begin() guarantee only one winner?](16-how-does-begin-guarantee-only-one-winner.md)
17. [What does a duplicate do while the original is still running?](17-what-does-a-duplicate-do-while-the-original-is-sti.md)
18. [What happens if the original fails before storing?](18-what-happens-if-the-original-fails-before-storing.md)

## Journal – surviving a crash

19. [What is the journal's format?](19-what-is-the-journal-s-format.md)
20. [What does write-ahead mean here?](20-what-does-write-ahead-mean-here.md)
21. [Does flush() survive every kind of crash?](21-does-flush-survive-every-kind-of-crash.md)
22. [How does replay handle a half-written last line?](22-how-does-replay-handle-a-half-written-last-line.md)

## LecturerClient

23. [What commands does the lecturer client accept?](23-what-commands-does-the-lecturer-client-accept.md)
24. [Where is the request ID created?](24-where-is-the-request-id-created.md)

## Invocation semantics and failures

25. [Define the three outcomes a client can see after a remote call.](25-define-the-three-outcomes-a-client-can-see-after-a.md)
26. [Why is at-most-once harder than at-least-once?](26-why-is-at-most-once-harder-than-at-least-once.md)
27. [Why is exactly-once impossible to promise in general?](27-why-is-exactly-once-impossible-to-promise-in-gener.md)
