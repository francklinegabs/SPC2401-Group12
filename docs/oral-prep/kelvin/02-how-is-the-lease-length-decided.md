# 02. How is the lease length decided?

*Topic: SubscriptionManager – leases*

A request of 0 or less gets the default of 10 minutes; any other request is capped at the maximum of 1 hour (test TC16).
