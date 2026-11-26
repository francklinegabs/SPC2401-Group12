# 07. Why is the map a ConcurrentHashMap?

*Topic: SubscriptionManager – leases*

Subscribe, renew, publish and housekeeping run on different threads at the same time; the concurrent map keeps each operation safe without one global lock.
