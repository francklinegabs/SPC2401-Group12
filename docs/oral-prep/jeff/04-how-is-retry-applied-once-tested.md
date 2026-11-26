# 04. How is 'retry applied once' tested?

*Topic: Test plan and unit tests*

TC07 sends the same request ID twice and checks the count stays 1; a late duplicate also cannot overwrite a newer correction to 80.
