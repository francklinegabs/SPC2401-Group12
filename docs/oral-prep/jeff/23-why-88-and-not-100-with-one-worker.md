# 23. Why 88 and not 100 with one worker?

*Topic: Performance (7.4)*

On Windows Thread.sleep(6) really takes about 6.72 ms and RMI adds about 0.4 ms: 1,000 / 11.1 is about 90 requests/s.
