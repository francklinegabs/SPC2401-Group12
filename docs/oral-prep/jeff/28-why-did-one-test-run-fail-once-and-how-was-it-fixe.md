# 28. Why did one test run fail once, and how was it fixed?

*Topic: Defects and limits (7.5)*

Some tests waited fixed times that a cold first start could miss; they now wait for the condition itself, and five cold rebuilds then passed.
