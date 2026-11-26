# 08. Why only notify 'active' subscriptions?

*Topic: SubscriptionManager – leases*

activeFor() skips expired leases, so publish never wastes a notifier thread on a student who has not renewed in over 10 minutes.
