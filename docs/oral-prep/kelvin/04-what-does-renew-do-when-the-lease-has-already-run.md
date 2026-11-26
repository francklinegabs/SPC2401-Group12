# 04. What does renew() do when the lease has already run out?

*Topic: SubscriptionManager – leases*

It removes the stale entry and throws NotSubscribedException, which tells the client to subscribe again (TC15).
