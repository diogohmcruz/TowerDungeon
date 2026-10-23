package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.text.Normalizer;

/**
 * The name a Contender goes by in a table. It is typed by a stranger and shown to others, so it is
 * cut down to letters, digits, spaces and a few marks of punctuation: no markup, no control or
 * direction-changing characters, no stacked accents, no emoji. What is left is capped in length;
 * nothing left at all reads {@value #ANONYMOUS}.
 */
public final class Alias {
  public static final String ANONYMOUS = "Anonymous";
  public static final int MAX_LENGTH = 24;

  private static final String PUNCTUATION = "-_.'";

  private Alias() {}

  public static String clean(String raw) {
    if (raw == null) {
      return ANONYMOUS;
    }
    var text = Normalizer.normalize(raw, Normalizer.Form.NFKC);
    var kept = new StringBuilder();
    var count = 0;
    var pendingSpace = false;
    for (var i = 0; i < text.length() && count < MAX_LENGTH; ) {
      var cp = text.codePointAt(i);
      i += Character.charCount(cp);
      if (Character.isLetterOrDigit(cp) || PUNCTUATION.indexOf(cp) >= 0) {
        if (pendingSpace && count > 0) {
          kept.append(' ');
          count++;
          if (count >= MAX_LENGTH) {
            break;
          }
        }
        pendingSpace = false;
        kept.appendCodePoint(cp);
        count++;
      } else if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
        pendingSpace = true;
      }
    }
    var cleaned = kept.toString().strip();
    return cleaned.isEmpty() ? ANONYMOUS : cleaned;
  }
}
