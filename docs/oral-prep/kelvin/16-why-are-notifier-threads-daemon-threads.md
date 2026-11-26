# 16. Why are notifier threads daemon threads?

*Topic: Notifier – the callback pool*

So a hung callback can never keep the server JVM from shutting down.
