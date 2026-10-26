# Desktop navigation measurement

Measured on Windows, JDK 24, against 22 visible personal/public desktop items.
Both paths use the same DesktopCatalog listing. The previous path additionally
resolves up to 96 native Windows shell icons before publishing any result. The
new path publishes the listing and draws folder/file-type badges directly.

| Run | Previous listing + shell icons | Current listing |
| --- | ---: | ---: |
| First process run | 5823 ms | 1172 ms |
| Repeat 1 | 1014 ms | 28 ms |
| Repeat 2 | 895 ms | 25 ms |
| Repeat 3 | 972 ms | 22 ms |

These are sequential elapsed-time samples, not a randomized benchmark. The first
run includes JVM/native desktop initialization; OS caches were not flushed.
Painting, hover delay and event dispatch are outside the measurement. CPU time,
peak process-family memory and token costs were not measured. No listing cache
was added: each expansion reads current filesystem metadata. Slow/offline/cloud
folders may still delay listing. The launcher no longer waits for shell icons.
