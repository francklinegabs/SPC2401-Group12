# 14. Why does the notifier never hold a lock while calling back?

*Topic: Notifier – the callback pool*

It only receives a snapshot list from publishMarks, after all locks are released, so a student calling getMark from inside the callback cannot deadlock the server.
