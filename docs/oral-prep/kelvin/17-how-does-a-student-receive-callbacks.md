# 17. How does a student receive callbacks?

*Topic: StudentClient*

StudentClient implements StudentListener, exports itself with UnicastRemoteObject and sends its own stub to subscribe.
