# Notification, leases and middleware – study cards (Kelvin Lemaitoi)

One question per card, answered from my own package (WP2) and report Section 2, with the numbers we measured.

## SubscriptionManager – leases

1. [What does a subscription hold?](01-what-does-a-subscription-hold.md)
2. [How is the lease length decided?](02-how-is-the-lease-length-decided.md)
3. [What happens when a student subscribes twice?](03-what-happens-when-a-student-subscribes-twice.md)
4. [What does renew() do when the lease has already run out?](04-what-does-renew-do-when-the-lease-has-already-run.md)
5. [Why does remove() take the Subscription object, not just the ID?](05-why-does-remove-take-the-subscription-object-not-j.md)
6. [How are expired leases cleaned up?](06-how-are-expired-leases-cleaned-up.md)
7. [Why is the map a ConcurrentHashMap?](07-why-is-the-map-a-concurrenthashmap.md)
8. [Why only notify 'active' subscriptions?](08-why-only-notify-active-subscriptions.md)

## Notifier – the callback pool

9. [How many notifier threads are there and why?](09-how-many-notifier-threads-are-there-and-why.md)
10. [What did we measure for 2,000 slow callbacks?](10-what-did-we-measure-for-2-000-slow-callbacks.md)
11. [What does the notifier do when a callback throws?](11-what-does-the-notifier-do-when-a-callback-throws.md)
12. [Why are the callbacks CompletableFutures?](12-why-are-the-callbacks-completablefutures.md)
13. [What is in a PublishReport?](13-what-is-in-a-publishreport.md)
14. [Why does the notifier never hold a lock while calling back?](14-why-does-the-notifier-never-hold-a-lock-while-call.md)
15. [What did experiment B show?](15-what-did-experiment-b-show.md)
16. [Why are notifier threads daemon threads?](16-why-are-notifier-threads-daemon-threads.md)

## StudentClient

17. [How does a student receive callbacks?](17-how-does-a-student-receive-callbacks.md)
18. [When is the lease renewed?](18-when-is-the-lease-renewed.md)
19. [What happens after a lost lease?](19-what-happens-after-a-lost-lease.md)
20. [Why fetch the mark inside markPublished?](20-why-fetch-the-mark-inside-markpublished.md)
