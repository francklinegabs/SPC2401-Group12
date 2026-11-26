# 12. Why are the callbacks CompletableFutures?

*Topic: Notifier – the callback pool*

Each callback runs independently on the pool, and `allOf` tells us when all are done so the server can log one PublishReport.
