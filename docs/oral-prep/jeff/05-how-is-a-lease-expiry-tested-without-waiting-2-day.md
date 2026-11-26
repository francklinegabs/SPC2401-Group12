# 05. How is a lease expiry tested without waiting 2 days?

*Topic: Test plan and unit tests*

TC14 uses a manual clock moved forward in 30 s steps: the sleeping student is reaped and not notified, the awake one is.
