# 27. Name a real defect we found and fixed while testing.

*Topic: Defects and limits (7.5)*

The injected network delay had no effect because RMI reused pooled connections; giving the measured server its own export port fixed it.
