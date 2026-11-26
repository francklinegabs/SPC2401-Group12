# 05. Why does remove() take the Subscription object, not just the ID?

*Topic: SubscriptionManager – leases*

`remove(key, value)` only removes that exact entry, so a failed callback on an old listener cannot delete a newer subscription the student just made.
