# 19. What happens after a lost lease?

*Topic: StudentClient*

renewLease throws NotSubscribedException, so the client subscribes again and pulls getMark for each unit in `--units` to catch missed publications.
