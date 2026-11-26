# Concurrency, threads and synchronisation – study cards (Eunice Gikaru)

One question per card, answered from my own package (WP3) and report Sections 3 and 4, with numbers we measured.

## MarkStore – my thread-safe store

1. [Which three defects in the given class does my MarkStore fix?](01-which-three-defects-in-the-given-class-does-my-mar.md)
2. [Why is the Key a Java record?](02-why-is-the-key-a-java-record.md)
3. [What happens inside put() under the write lock?](03-what-happens-inside-put-under-the-write-lock.md)
4. [Why can getMark readers run at the same time?](04-why-can-getmark-readers-run-at-the-same-time.md)
5. [Why do I unlock in a finally block every time?](05-why-do-i-unlock-in-a-finally-block-every-time.md)
6. [What does publish() return for an unknown unit, and what does the service do with it?](06-what-does-publish-return-for-an-unknown-unit-and-w.md)

## WorkerPool – my thread architecture

7. [Why did I choose a worker pool over thread-per-request?](07-why-did-i-choose-a-worker-pool-over-thread-per-req.md)
8. [Why not thread-per-object?](08-why-not-thread-per-object.md)
9. [How does the RMI thread hand work to my pool?](09-how-does-the-rmi-thread-hand-work-to-my-pool.md)
10. [How are worker threads named and why?](10-how-are-worker-threads-named-and-why.md)
11. [Why is the CPU simulated with a busy loop and not sleep?](11-why-is-the-cpu-simulated-with-a-busy-loop-and-not.md)

## My Section 4 reproduction tests

12. [How does D01 make the original code deadlock in a test?](12-how-does-d01-make-the-original-code-deadlock-in-a.md)
13. [How does D01 detect the deadlock without hanging the test run?](13-how-does-d01-detect-the-deadlock-without-hanging-t.md)
14. [Why did D01 originally fail with IllegalThreadStateException?](14-why-did-d01-originally-fail-with-illegalthreadstat.md)
15. [What does D02 show?](15-what-does-d02-show.md)
16. [What does D03 compare?](16-what-does-d03-compare.md)

## Section 3 – OS types, processes and threads

17. [Rate a network OS for 'hides where GradeService runs'.](17-rate-a-network-os-for-hides-where-gradeservice-run.md)
18. [Rate a distributed OS for 'each campus server stays autonomous'.](18-rate-a-distributed-os-for-each-campus-server-stays.md)
19. [Rate a multiprocessor OS for 'survives one node failing'.](19-rate-a-multiprocessor-os-for-survives-one-node-fai.md)
20. [Rate each OS type for 'scales to 20,000 students'.](20-rate-each-os-type-for-scales-to-20-000-students.md)
21. [Which OS type did we choose for three servers, in one sentence?](21-which-os-type-did-we-choose-for-three-servers-in-o.md)
22. [Which transparency is violated when clients switch to a backup by hand?](22-which-transparency-is-violated-when-clients-switch.md)
