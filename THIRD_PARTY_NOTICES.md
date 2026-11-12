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

## Kuromoji and IPADIC

`dictionary/src/main/kotlin/com/elianiva/jisholens/dictionary/JapaneseTokenizer.kt`
uses Kuromoji, a Japanese morphological analyzer, and its IPADIC model:

- Kuromoji, <https://github.com/atilika/kuromoji>, `com.atilika.kuromoji:kuromoji-core` and
  `com.atilika.kuromoji:kuromoji-ipadic` version 0.9.0. Copyright 2010-2015
  Atilika Inc. and contributors. Licensed under the Apache License, Version
  2.0; the full text is in
  `dictionary/src/main/resources/deconjugation/LICENSE-APACHE-2.0.txt`.
- The IPADIC model is `mecab-ipadic-2.7.0-20070801`, obtained from
  <https://atilika.com/releases/mecab-ipadic/mecab-ipadic-2.7.0-20070801.tar.gz>.
  Nara Institute of Science and Technology (NAIST) holds the copyright. Use,
  reproduction, and distribution of the data is permitted, provided that any
  copy includes the NAIST and ICOT notices below.

NAIST disclaimer and ICOT Free Software conditions, as distributed with IPADIC:

> Nara Institute of Science and Technology (NAIST), the copyright holders,
> disclaims all warranties with regard to this software, including all implied
> warranties of merchantability and fitness, in no event shall NAIST be liable
> for any special, indirect or consequential damages or any damages whatsoever
> resulting from loss of use, data or profits, whether in an action of
> contract, negligence or other tortuous action, arising out of or in connection
> with the use or performance of this software.
>
> A large portion of the dictionary entries originate from ICOT Free Software.
> The following conditions for ICOT Free Software applies to the current
> dictionary as well.
>
> Each User may also freely distribute the Program, whether in its original form
> or modified, to any third party or parties, PROVIDED that the provisions of
> Section 3 ("NO WARRANTY") will ALWAYS appear on, or be attached to, the
> Program, which is distributed substantially in the same form as set out herein
> and that such intended distribution, if actually made, will neither violate or
> otherwise contravene any of the laws and regulations of the countries having
> jurisdiction over the User or the intended distribution itself.
>
> NO WARRANTY
>
> The program was produced on an experimental basis in the course of the research
> and development conducted during the project and is provided to users as so
> produced on an experimental basis. Accordingly, the program is provided
> without any warranty whatsoever, whether express, implied, statutory or
> otherwise. The term "warranty" used herein includes, but is not limited to,
> any warranty of the quality, performance, merchantability and fitness for a
> particular purpose of the program and the nonexistence of any infringement or
> violation of any right of any third party.
>
> Each user of the program will agree and understand, and be deemed to have
> agreed and understood, that there is no warranty whatsoever for the program
> and, accordingly, the entire risk arising from or otherwise connected with the
> program is assumed by the user.
>
> Therefore, neither ICOT, the copyright holder, or any other organization that
> participated in or was otherwise related to the development of the program and
> their respective officials, directors, officers and other employees shall be
> held liable for any and all damages, including, without limitation, general,
> special, incidental and consequential damages, arising out of or otherwise in
> connection with the use or inability to use the program or any product,
> material or result produced or otherwise obtained by using the program,
> regardless of whether they have been advised of, or otherwise had knowledge
> of, the possibility of such damages at any time during the project or
> thereafter. Each user will be deemed to have agreed to the foregoing by his or
> her commencement of use of the program. The term "use" as used herein
> includes, but is not limited to, the use, modification, copying and
> distribution of the program and the production of secondary products from the
> program.
>
> In the case where the program, whether in its original form or modified, was
> distributed or delivered to or received by a user from any person,
> organization or entity other than ICOT, unless it makes or grants independently
> of ICOT any specific warranty to the user in writing, such person,
> organization or entity, will also be exempted from and not be held liable to
> the user for any such damages as noted above as far as the program is
> concerned.

## Scan test fixture

`dictionary/src/test/resources/fixtures/term_bank_1.json` holds a small set of
term rows copied from Jitendex, <https://jitendex.org>, for the segmentation
tests. The rows keep Jitendex's expressions, readings, part-of-speech codes,
rules, scores, and sequence numbers; the glosses are shortened. Jitendex is
licensed under the Creative Commons Attribution-ShareAlike 4.0 International
License (CC BY-SA 4.0), <https://creativecommons.org/licenses/by-sa/4.0/>. The
fixture data remains under that license; the code that reads it stays MIT.

## Google Sans Flex

The app bundles a Latin subset of Google Sans Flex (weight and roundness axes) in `app/src/main/res/font/google_sans_flex.ttf`.

Copyright 2015 The Google Sans Flex Authors (https://github.com/googlefonts/googlesans-flex)

It is licensed under the SIL Open Font License, Version 1.1, available at https://openfontlicense.org. The full text is in the upstream repository: https://github.com/google/fonts/blob/main/ofl/googlesansflex/OFL.txt
