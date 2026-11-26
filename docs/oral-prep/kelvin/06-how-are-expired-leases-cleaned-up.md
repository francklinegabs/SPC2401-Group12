# 06. How are expired leases cleaned up?

*Topic: SubscriptionManager – leases*

The housekeeping thread calls reapExpired(), which removes every subscription past its expiry and logs each one.
