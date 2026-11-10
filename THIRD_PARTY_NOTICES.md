# Third-party notices

Jisho Lens is released under the MIT License (`LICENSE`). This file lists the
third-party material that the repository includes.

## Deconjugation rules

The rules in `dictionary/src/main/resources/deconjugation/deconjugator.json`
come from Jiten:

- Jiten, <https://github.com/Sirush/Jiten>, file
  `Shared/resources/deconjugator.json`, commit
  `6825e4965fed2819d71c2aa44d706d4ec77d72b4`. Licensed under the Apache License,
  Version 2.0.
- Jiten credits JL, <https://github.com/rampaa/JL>, for the updated rules and
  deconjugator. JL is licensed under the Apache License, Version 2.0.
- JL credits Nazeka, <https://github.com/wareya/nazeka>, for the original
  deconjugation rules. Nazeka's `dict/deconjugator.json` states: "This file is
  public domain data."

The rule data differs from the upstream file in one rule: the "do for someone"
rule marks あげる as godan (`v5r`), but あげる is ichidan, so this port uses
`v1`. The Kotlin deinflector in
`dictionary/src/main/kotlin/com/elianiva/jisholens/dictionary/Deinflector.kt`
is a port of Jiten's rule semantics, so the rule data and that port are both
distributed under the Apache License, Version 2.0. The rest of Jisho Lens
remains under the MIT License.

The Apache License, Version 2.0 text is in
`dictionary/src/main/resources/deconjugation/LICENSE-APACHE-2.0.txt`.

## Google Sans Flex

The app bundles a Latin subset of Google Sans Flex (weight and roundness axes) in `app/src/main/res/font/google_sans_flex.ttf`.

Copyright 2015 The Google Sans Flex Authors (https://github.com/googlefonts/googlesans-flex)

It is licensed under the SIL Open Font License, Version 1.1, available at https://openfontlicense.org. The full text is in the upstream repository: https://github.com/google/fonts/blob/main/ofl/googlesansflex/OFL.txt
