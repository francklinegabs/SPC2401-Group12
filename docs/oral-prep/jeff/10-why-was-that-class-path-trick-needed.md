# 10. Why was that class-path trick needed?

*Topic: Integration tests*

The JUnit console launcher loads our classes itself, so java.class.path held only the JUnit jar; child JVMs could not find our classes.
