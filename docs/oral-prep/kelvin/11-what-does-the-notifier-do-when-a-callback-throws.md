# 11. What does the notifier do when a callback throws?

*Topic: Notifier – the callback pool*

It catches the RemoteException or RuntimeException, removes that subscription, writes a FAILURE line and carries on with the others.
