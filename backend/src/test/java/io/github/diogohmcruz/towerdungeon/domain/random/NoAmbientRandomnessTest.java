package io.github.diogohmcruz.towerdungeon.domain.random;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every random draw of a Campaign comes from its {@link RandomSource}. This reads the game's source
 * and fails on any way of drawing game randomness from outside it, so a new draw cannot slip past
 * review. (A human Campaign's seed comes from {@code SeedSource}, and the file sealer takes nonces
 * from {@code SecureRandom}; neither is a draw of the game.)
 */
class NoAmbientRandomnessTest {
  private static final Path MAIN = Path.of("src/main/java");
  private static final Pattern FORBIDDEN =
      Pattern.compile(
          "ThreadLocalRandom|Math\\.random\\(|new Random\\(\\)|new Faker\\(\\)"
              + "|new SplittableRandom\\(\\)");

  private static String withoutComments(String source) {
    return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
  }

  @Test
  void noSourceFileDrawsFromAnythingButTheCampaignsRandomSource() throws IOException {
    var offences = new ArrayList<String>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        var matcher = FORBIDDEN.matcher(withoutComments(Files.readString(file)));
        while (matcher.find()) {
          offences.add(MAIN.relativize(file) + ": " + matcher.group());
        }
      }
    }

    assertThat(offences).isEmpty();
  }
}
