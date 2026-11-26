# 09. How many notifier threads are there and why?

*Topic: Notifier – the callback pool*

20 by default (`notifier.threads`). That turns 2,000 callbacks of 50 ms from 100 s one by one into about 5 s.
