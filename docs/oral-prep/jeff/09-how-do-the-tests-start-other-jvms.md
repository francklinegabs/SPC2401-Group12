# 09. How do the tests start other JVMs?

*Topic: Integration tests*

The Proc helper uses ProcessBuilder with the same java binary and a class path taken from where GradeServer was loaded.
