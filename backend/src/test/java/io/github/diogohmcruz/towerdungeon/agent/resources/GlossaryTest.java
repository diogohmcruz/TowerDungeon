package io.github.diogohmcruz.towerdungeon.agent.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.observe.Observations;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolFailure;

/** The glossary an agent may read, and the resources built on top of the observations. */
class GlossaryTest {
  private final Glossary glossary = Glossary.load();

  @Test
  void theFilteredGlossaryCarriesShippedTermsAndDefinitionsOnly() {
    var text = glossary.filtered();

    assertThat(text)
        .contains("**Campaign**", "**Credits**", "**Extraction**", "**Reinforcement Wave**");
    assertThat(text).doesNotContain("_Avoid_");
  }

  @Test
  void plannedTermsAndTheUnitDepthSectionAreLeftOut() {
    var text = glossary.filtered();

    assertThat(glossary.plannedTerms()).isNotEmpty();
    for (var planned : glossary.plannedTerms()) {
      assertThat(text).as(planned).doesNotContain("**" + planned + "**");
    }
    for (var depth : List.of("Phobia", "Sanity", "Body Part", "Maimed", "Affinity")) {
      assertThat(text).as(depth).doesNotContain("**" + depth + "**");
    }
  }

  @Test
  void noAvoidedTermIsOfferedAsATermAndNoAvoidedListIsCopied() {
    var text = glossary.filtered();
    var headings =
        Pattern.compile("\\*\\*(.+?)\\*\\*")
            .matcher(text)
            .results()
            .map(m -> m.group(1).toLowerCase(Locale.ROOT))
            .toList();

    var avoided = glossary.avoidedTerms();
    assertThat(avoided).contains("game", "boss", "retreat", "credit", "run", "hint");
    assertThat(headings).doesNotContainAnyElementsOf(avoided);
    // The lists of avoided words are the source's own business: none of them is copied across.
    assertThat(glossary.avoidLines())
        .isNotEmpty()
        .allSatisfy(line -> assertThat(text).doesNotContain(line));
  }

  @Test
  void aTermIsOnlyAvoidedOnItsOwn() {
    assertThat(glossary.avoidedIn("A Reinforcement Wave can follow")).isEmpty();
    assertThat(glossary.avoidedIn("a wave of enemies")).contains("wave");
    assertThat(glossary.avoidedIn("The Boss floor")).contains("boss");
    assertThat(glossary.avoidedIn("Loot is carried")).isEmpty();
    assertThat(glossary.avoidedIn("Returns the state")).isEmpty();
  }

  @Test
  void theResourceCataloguesReadsAreTheObservationsAndAnUnknownAddressIsNone() {
    var observations = mock(Observations.class);
    when(observations.state(any(), any(), any(), any())).thenReturn(Map.of("seq", 5));
    when(observations.floor(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenThrow(new ToolFailure("NO_SUCH_FLOOR", "That Floor has not been reached."));
    var catalogue = new ResourceCatalogue(observations);

    assertThat(catalogue.read(Principal.dev(), "td://state").orElseThrow().text())
        .isEqualTo("{\"seq\":5}");
    assertThat(catalogue.read(Principal.dev(), "td://glossary").orElseThrow().mimeType())
        .isEqualTo("text/markdown");
    assertThat(catalogue.read(Principal.dev(), "td://nope")).isEmpty();
    assertThat(catalogue.read(Principal.dev(), "td://tower/floors/abc")).isEmpty();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> catalogue.read(Principal.dev(), "td://tower/floors/9"))
        .isInstanceOf(ToolFailure.class);
    assertThat(catalogue.fixed()).extracting(ResourceCatalogue.Def::uri).doesNotHaveDuplicates();
    assertThat(catalogue.fixed())
        .allSatisfy(
            d ->
                assertThat(glossary.avoidedIn(d.uri() + " " + d.name() + " " + d.description()))
                    .as(d.uri())
                    .isEmpty());
    assertThat(
            Arrays.asList(
                catalogue.templates().stream()
                    .map(ResourceCatalogue.Template::uriTemplate)
                    .toArray()))
        .containsExactly("td://tower/floors/{n}", "td://ledger/{n}");
  }
}
