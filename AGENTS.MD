Make sure all of the strings are in resources (not source code) (for future in18n)
Make sure executable code is not in composables but in ViewModel
Make sure all composables have @Preview
Make it cross-platform as much as possible.
Maintain separation of responsibilities between layers: compose, viewModel, database, location.




mkdirs() — create intermediate directories before writing
use { } — always close streams (use does this automatically)
try-catch — handle IOException
.buffered() — use buffering for large numbers of operations
Do not block the UI thread with file operations