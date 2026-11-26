# 16. What did killing the server after storing show (A2)?

*Topic: Concurrency and failure experiments (7.3)*

The restarted server recovered the request ID from the journal and ignored the retry: one journal entry, at-most-once across a crash.
