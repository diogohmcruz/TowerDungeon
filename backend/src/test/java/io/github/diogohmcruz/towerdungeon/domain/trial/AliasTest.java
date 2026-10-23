package io.github.diogohmcruz.towerdungeon.domain.trial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AliasTest {
  @Test
  void anOrdinaryNameIsKept() {
    assertThat(Alias.clean("Anaxagoras")).isEqualTo("Anaxagoras");
    assertThat(Alias.clean("Zoë O'Neil-2")).isEqualTo("Zoë O'Neil-2");
  }

  @Test
  void markupIsStripped() {
    assertThat(Alias.clean("<script>alert(1)</script>")).isEqualTo("scriptalert1script");
    assertThat(Alias.clean("<img src=x onerror=\"x\">")).doesNotContain("<", ">", "\"", "=");
  }

  @Test
  void controlAndDirectionCharactersAreDropped() {
    assertThat(Alias.clean("a‮b\u0000c\n\td​e")).isEqualTo("abc de");
  }

  @Test
  void stackedAccentsAndEmojiAreDropped() {
    var zalgo = "z̀́̂̃̄ỳ́l̀go";
    // Only an accent that composes into a letter survives; the stack is gone.
    assertThat(Alias.clean(zalgo)).isEqualTo("zỳlgo");
    assertThat(Alias.clean("hero 😀")).isEqualTo("hero");
  }

  @Test
  void spacesCollapseAndLengthIsCapped() {
    assertThat(Alias.clean("  a   b  ")).isEqualTo("a b");
    assertThat(Alias.clean("x".repeat(100))).hasSize(Alias.MAX_LENGTH);
  }

  @Test
  void nothingLeftIsAnonymous() {
    assertThat(Alias.clean(null)).isEqualTo(Alias.ANONYMOUS);
    assertThat(Alias.clean("")).isEqualTo(Alias.ANONYMOUS);
    assertThat(Alias.clean("<>/\\;")).isEqualTo(Alias.ANONYMOUS);
  }
}
