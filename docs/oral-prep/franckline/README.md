# RMI core and the whole design – study cards (Franckline Were)

One question per card, answered from my own package (WP1), report Section 1 and the design as a whole.

## RMI in our design

1. [What is a remote object in KYUCampusGrade?](01-what-is-a-remote-object-in-kyucampusgrade.md)
2. [Why does GradeService extend java.rmi.Remote?](02-why-does-gradeservice-extend-java-rmi-remote.md)
3. [Why does every remote method declare RemoteException?](03-why-does-every-remote-method-declare-remoteexcepti.md)
4. [What is the stub in Java RMI today?](04-what-is-the-stub-in-java-rmi-today.md)

## Section 1 – Remote invocation

5. [Step 1 of the trace: what does the lecturer's code do?](05-step-1-of-the-trace-what-does-the-lecturer-s-code.md)
6. [Step 2: what does the client stub do?](06-step-2-what-does-the-client-stub-do.md)
7. [Step 3: what does the communication module do?](07-step-3-what-does-the-communication-module-do.md)
8. [Step 4: what does the dispatcher (skeleton) do?](08-step-4-what-does-the-dispatcher-skeleton-do.md)
9. [Steps 5 and 6: what happens in the implementation and on the reply path?](09-steps-5-and-6-what-happens-in-the-implementation-a.md)
10. [Why is submitMark at-most-once?](10-why-is-submitmark-at-most-once.md)

## Section 2 – Notification and middleware

11. [What are our three notification event types?](11-what-are-our-three-notification-event-types.md)
12. [Why push the event but pull the mark?](12-why-push-the-event-but-pull-the-mark.md)
13. [What lease did we choose and how is it renewed?](13-what-lease-did-we-choose-and-how-is-it-renewed.md)

## Section 3 – OS types, processes, threads

14. [Hard-coded host name broke every client. Which transparency, and what fix?](14-hard-coded-host-name-broke-every-client-which-tran.md)
15. [Two lecturers' updates overwrote each other. Which transparency?](15-two-lecturers-updates-overwrote-each-other-which-t.md)
16. [A replica shows old marks after an update. Which transparency?](16-a-replica-shows-old-marks-after-an-update-which-tr.md)

## Section 4 – Synchronisation and invocation cost

17. [What is wrong with lines (1)-(3) of the given submitMark?](17-what-is-wrong-with-lines-1-3-of-the-given-submitma.md)
18. [How big was the lost update in practice?](18-how-big-was-the-lost-update-in-practice.md)
19. [Why is the key sid + unit a bug?](19-why-is-the-key-sid-unit-a-bug.md)
20. [Explain the deadlock in 4.2 in one breath.](20-explain-the-deadlock-in-4-2-in-one-breath.md)
21. [Which of the four deadlock conditions does our fix break?](21-which-of-the-four-deadlock-conditions-does-our-fix.md)
22. [How did we prove the deadlock and the fix?](22-how-did-we-prove-the-deadlock-and-the-fix.md)

## Section 5 – Amoeba

23. [Map the lecturer's PC and the server host onto Amoeba.](23-map-the-lecturer-s-pc-and-the-server-host-onto-amo.md)
24. [Map the registry and the spare CPUs onto Amoeba.](24-map-the-registry-and-the-spare-cpus-onto-amoeba.md)
