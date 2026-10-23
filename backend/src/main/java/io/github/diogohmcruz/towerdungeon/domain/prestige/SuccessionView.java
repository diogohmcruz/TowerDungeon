package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.util.ArrayList;
import java.util.List;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;

/**
 * What the Succession screen needs: the Legacy to spend, every Inheritance with whether it is held
 * and, if not, why it cannot be bought, the Courses and their gates, and the choices open with the
 * answer given so far. Built from the meta and the Campaign in OFFER; null at any other time.
 *
 * @param generation the Generation that ended
 * @param legacy Legacy available to spend
 * @param spent Legacy spent on what the Roll holds
 * @param courses the four Courses
 * @param inheritances all seventeen Inheritances, in Course order
 * @param choices the choices open, one entry for each of their options
 */
public record SuccessionView(
    int generation,
    int legacy,
    int spent,
    List<Course> courses,
    List<Node> inheritances,
    List<Option> choices) {

  /**
   * @param course 1 to 4
   * @param gate Legacy that must be spent in the Courses below
   * @param spentBelow Legacy spent there now
   * @param open whether the gate is met
   */
  public record Course(int course, int gate, int spentBelow, boolean open) {}

  /**
   * @param id the stable id
   * @param name the name to show
   * @param course 1 to 4
   * @param kind {@code POWER}, {@code DECISION} or {@code QUALITY}
   * @param cost Legacy it costs now
   * @param available whether its effect exists; an unavailable one is listed, never bought
   * @param held whether the Roll holds it
   * @param paid Legacy paid for it, which a refund returns; 0 when not held
   * @param whyNot why it cannot be bought now ({@link RollRefusal} name); null when it can, or when
   *     it is held
   * @param refundable whether it can be given back now
   */
  public record Node(
      String id,
      String name,
      int course,
      String kind,
      int cost,
      boolean available,
      boolean held,
      int paid,
      String whyNot,
      boolean refundable) {}

  /**
   * @param id the choice id to send
   * @param group the question, {@code DOWRY} or {@code HEIR}
   * @param selected whether it is the answer now
   * @param isDefault whether it is what is taken when none is given
   */
  public record Option(String id, String group, boolean selected, boolean isDefault) {}

  /** The view for a Campaign waiting in OFFER; null while it is played. */
  public static SuccessionView of(PlayerMeta meta, GameState state) {
    if (meta == null || state == null || state.getPhase() != CampaignPhase.OFFER) {
      return null;
    }
    GameProperties.Prestige.Roll config = state.getConfig().getPrestige().getRoll();
    var courses = new ArrayList<Course>();
    for (var course = 1; course <= 4; course++) {
      courses.add(
          new Course(
              course,
              config.gateOf(course),
              RollRules.spentBelow(meta.roll(), course),
              RollRules.courseOpen(meta.roll(), course, config)));
    }
    var nodes = new ArrayList<Node>();
    for (var type : InheritanceType.values()) {
      var held = meta.holds(type);
      var paid =
          meta.roll().stream()
              .filter(entry -> entry.id().equals(type.name()))
              .mapToInt(RollEntry::paid)
              .sum();
      var whyNot = held ? null : RollRules.whyNotBuy(meta, type, config);
      nodes.add(
          new Node(
              type.name(),
              type.displayName(),
              type.course(),
              type.kind().name(),
              config.costOf(type),
              type.isAvailable(),
              held,
              paid,
              whyNot == null ? null : whyNot.name(),
              held && RollRules.whyNotRefund(meta, type.name(), config) == null));
    }
    var effects = RollEffects.of(meta.roll(), state.getConfig());
    var options = new ArrayList<Option>();
    for (var group : SuccessionChoice.Group.values()) {
      if (!effects.holds(group.requires())) {
        continue;
      }
      var answer = state.getChoices().get(group.name());
      for (var choice : SuccessionChoice.values()) {
        if (choice.group() == group) {
          options.add(
              new Option(
                  choice.name(),
                  group.name(),
                  choice.name().equals(answer),
                  choice == group.fallback()));
        }
      }
    }
    return new SuccessionView(
        meta.generation(), meta.legacy(), meta.spent(), courses, nodes, options);
  }
}
