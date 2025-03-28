package me.unariginal.astralbreeding.commands;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.ShinyChanceCalculationEvent;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeature;
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.cobblemon.mod.common.pokemon.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import kotlin.Pair;
import kotlin.Unit;
import me.unariginal.astralbreeding.AstralBreeding;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.Item;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class AstralCommands {
    private final AstralBreeding ab = AstralBreeding.INSTANCE;

    public AstralCommands() {
        CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> commandDispatcher.register(
                CommandManager.literal("breed")
                        .then(
                                CommandManager.argument("first", IntegerArgumentType.integer(1,6))
                                        .then(
                                                CommandManager.argument("second", IntegerArgumentType.integer(1,6))
                                                        .executes(this::breed)
                                        )
                        )
        )));
    }

    private int breed(CommandContext<ServerCommandSource> ctx) {
        if (!ctx.getSource().isExecutedByPlayer()) {
            ctx.getSource().sendMessage(Text.literal("[Astral] This command can only be executed by a player!"));
            return 0;
        }

        ServerPlayerEntity player = ctx.getSource().getPlayer();
        int first = IntegerArgumentType.getInteger(ctx, "first");
        int second = IntegerArgumentType.getInteger(ctx, "second");

        if (player != null) {
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

            Pokemon first_Pokemon = party.get(first - 1);
            Pokemon second_Pokemon = party.get(second - 1);

            if (first_Pokemon != null && second_Pokemon != null) {
                Species first_Species = first_Pokemon.getSpecies();
                Gender first_Gender = first_Pokemon.getGender();
                Set<EggGroup> first_eggGroups = first_Pokemon.getForm().getEggGroups();
                boolean first_Ditto = first_eggGroups.contains(EggGroup.DITTO);

                Species second_Species = second_Pokemon.getSpecies();
                Gender second_Gender = second_Pokemon.getGender();
                Set<EggGroup> second_eggGroups = second_Pokemon.getForm().getEggGroups();
                boolean second_Ditto = second_eggGroups.contains(EggGroup.DITTO);

                boolean dittoBreeding = first_Ditto || second_Ditto;

                if (first_Species.getLabels().contains(CobblemonPokemonLabels.BABY) || second_Species.getLabels().contains(CobblemonPokemonLabels.BABY)) {
                    player.sendMessage(Text.literal("[Astral] Cannot Breed Baby Pokemon!"));
                    return 0;
                }

                if (first_Ditto && second_Ditto) {
                    player.sendMessage(Text.literal("[Astral] Cannot Breed Two Dittos!"));
                    return 0;
                }

                if (rarePokemon(first_Pokemon) || rarePokemon(second_Pokemon)) {
                    player.sendMessage(Text.literal("[Astral] Cannot Breed Rare Pokemon!"));
                    return 0;
                }

                if (dittoBreeding) {
                    player.sendMessage(Text.literal("[Astral] Ditto Breeding!"));
                    return 1;
                }

                if (first_Gender != second_Gender) {
                    boolean eggGroupMatch = false;
                    for (EggGroup eggGroup : first_eggGroups) {
                        if (second_eggGroups.contains(eggGroup)) {
                            eggGroupMatch = true;
                            break;
                        }
                    }

                    if (!eggGroupMatch) {
                        player.sendMessage(Text.literal("[Astral] These Pokemon Don't Have Matching Egg Groups!"));
                        return 0;
                    }

                    Pokemon mother = (first_Pokemon.getGender() == Gender.FEMALE) ? first_Pokemon : second_Pokemon;
                    Pokemon father = (first_Pokemon.getGender() == Gender.MALE) ? first_Pokemon : second_Pokemon;

                    PokemonProperties properties = new PokemonProperties();
                    FormData baby_Form = getEgg(mother);
                    IVs ivs = getIVs(mother, father);
                    Nature nature = getNature(mother, father);
                    PokeBall ball = getPokeball(mother, father);
                    boolean shiny = getShiny(mother, father, player);

                    properties.setSpecies(baby_Form.getSpecies().showdownId());
                    properties.setForm(baby_Form.formOnlyShowdownId());
                    properties.setIvs(ivs);
                    properties.setNature(nature.getName().toString());
                    properties.setAbility(getAbility(mother, baby_Form));
                    properties.setPokeball(ball.getName().toString());
                    properties.setShiny(shiny);
                    properties.setAspects(new HashSet<>(getAspects(mother)));

                    if (properties.getSpecies() != null) {
                        for (FormData form : PokemonSpecies.INSTANCE.getByName(properties.getSpecies()).getForms()) {
                            if (form.formOnlyShowdownId().equalsIgnoreCase(properties.getForm())) {
                                for (String aspect : properties.getAspects()) {
                                    ab.logInfo("[AstralBreeding] Aspect: " + aspect);
                                    properties.getCustomProperties().add(new FlagSpeciesFeature(aspect, true));

                                    String[] split = aspect.split("-");
                                    ab.logInfo("[AstralBreeding] Split: " + split[split.length - 1]);
                                    String region = split[split.length - 1];

                                    if (region.equalsIgnoreCase("alolan")) {
                                        region = "alola";
                                        properties.getCustomProperties().add(new StringSpeciesFeature("region_bias", region));
                                    }
                                    if (region.equalsIgnoreCase("galarian")) {
                                        region = "galar";
                                        properties.getCustomProperties().add(new StringSpeciesFeature("region_bias", region));
                                    }
                                    if (region.equalsIgnoreCase("hisuian")) {
                                        region = "hisui";
                                        properties.getCustomProperties().add(new StringSpeciesFeature("region_bias", region));
                                    }

                                    if (aspect.contains("striped")) {
                                        properties.getCustomProperties().add(new StringSpeciesFeature("fish_stripes", aspect.substring(0, aspect.indexOf("striped"))));
                                    }
                                    ab.logInfo("[AstralBreeding] Aspect Added");
                                }
                            }
                        }
                    }

                    Pokemon baby = properties.create();
                    baby.setFriendship(120, true);
                    party.add(baby);

                    player.sendMessage(Text.literal("Baby Species: " + baby.getSpecies().getName()));
                } else {
                    player.sendMessage(Text.literal("[Astral] These Pokemon Are The Same Gender!"));
                    return 0;
                }
            } else {
                player.sendMessage(Text.literal("[Astral] No Pokemon Found!"));
                return 0;
            }
        }

        return 1;
    }

    /* The logic...
     *
     * In Gen 2, IVs are not used, instead DVs are used, so I will be skipping implementation of this generation for the time being.
     *
     * In Gen 3 (Not Emerald), baby will inherit 3 different stats, at least 1 from both parents.
     *
     * As Of Gen 3 (Emerald), baby gets first random iv from random parent. Then, another stat (NOT HP) from a random parent
     *   so long as it's not the SAME stat from the SAME parent. If it's the same stat from the OTHER parent,
     *   the previously selected IV gets overwritten. Finally, the third stat is chosen (NOT HP OR DEFENSE) still overriding
     *   previously chosen ivs if it's not from the same parent. The remaining stats (could be 3-5 stats) are randomly generated.
     *
     * As of Gen 4 (Not D/P/PL), baby will inherit 3 ivs of different stats from either parent, no repeats or overriding. If a
     *   parent is holding a power item, that power item's corresponding stat will be inherited from that parent, and the
     *   remaining stats are selected randomly from either parent as stated before. If both parents hold a power item, only
     *   one of the power items is used, chosen at random.
     *
     * Currently as of Gen 6, if at least one parent is holding a destiny knot, the baby will inherit 5 ivs instead of 3.
     *   If the other parent is holding a power item, the baby will still only inherit a total of 5 stats, but one of them
     *   will be of the power item's corresponding stat from the power item parent.
     *
     * No plans to implement swap breeding currently.
     */
    private IVs getIVs(Pokemon mother, Pokemon father) {
        IVs toReturn = IVs.createRandomIVs(0);
        Pair<Pokemon, Pokemon> parents = new Pair<>(mother, father);

        // HG/SS (Gen 4) [Introduced the usage of Power Items to retain 1 specific IV from a parent.]
        Map<Stats, Integer> final_stats = new HashMap<>();

        Stats power_stat = getPowerItemIV(mother);
        if (power_stat != null) {
            ab.logInfo("[AstralBreeding] Setting " + power_stat.getShowdownId() + " iv from mother to " + mother.getIvs().get(power_stat));
            final_stats.put(power_stat, mother.getIvs().get(power_stat));
        }

        power_stat = getPowerItemIV(father);
        if (power_stat != null) {
            ab.logInfo("[AstralBreeding] Setting " + power_stat.getShowdownId() + " iv from father to " + father.getIvs().get(power_stat));
            final_stats.put(power_stat, father.getIvs().get(power_stat));
        }

        if (final_stats.size() > 1) {
            Map.Entry<Stats, Integer> power_iv = final_stats.entrySet().stream().toList().get(new Random().nextInt(final_stats.size()));
            final_stats.clear();
            ab.logInfo("[AstralBreeding] Both parents have power items!");
            ab.logInfo("[AstralBreeding] Setting " + power_iv.getKey().getShowdownId() + " iv from random to " + power_iv.getValue());
            final_stats.put(power_iv.getKey(), power_iv.getValue());
        }

        if (final_stats.isEmpty()) {
            Stats stat = Stats.getEntries().get(new Random().nextInt(6));
            Pokemon parent = getRandomParent(parents);
            ab.logInfo("[AstralBreeding] Setting " + stat.getShowdownId() + " iv from " + parent.getSpecies().getName() + " to " + parent.getIvs().get(stat));
            final_stats.put(stat, parent.getIvs().get(stat));
        }

        // X/Y (Gen 6) [Introduced the use of Destiny Knot in breeding to retain 5 IVs instead of 3.]
        for (int ivsRemaining = (mother.heldItem().getItem().equals(CobblemonItems.DESTINY_KNOT) || father.heldItem().getItem().equals(CobblemonItems.DESTINY_KNOT)) ? 4 : 2; ivsRemaining > 0; ivsRemaining--) {
            Stats stat = Stats.getEntries().get(new Random().nextInt(6));
            while (final_stats.containsKey(stat)) {
                stat = Stats.getEntries().get(new Random().nextInt(6));
            }

            Pokemon parent = getRandomParent(parents);
            ab.logInfo("[AstralBreeding] Setting " + stat.getShowdownId() + " iv from " + parent.getSpecies().getName() + " to " + parent.getIvs().get(stat));
            final_stats.put(stat, parent.getIvs().get(stat));
        }

        for (Map.Entry<Stats, Integer> entry : final_stats.entrySet()) {
            toReturn.set(entry.getKey(), entry.getValue());
        }

        return toReturn;
    }

    private Stats getPowerItemIV(Pokemon pokemon) {
        Item held_item = pokemon.heldItem().getItem();

        if (held_item.equals(CobblemonItems.POWER_WEIGHT)) {
            return Stats.HP;
        } else if (held_item.equals(CobblemonItems.POWER_BRACER)) {
            return Stats.ATTACK;
        } else if (held_item.equals(CobblemonItems.POWER_BELT)) {
            return Stats.DEFENCE;
        } else if (held_item.equals(CobblemonItems.POWER_LENS)) {
            return Stats.SPECIAL_ATTACK;
        } else if (held_item.equals(CobblemonItems.POWER_BAND)) {
            return Stats.SPECIAL_DEFENCE;
        } else if (held_item.equals(CobblemonItems.POWER_ANKLET)) {
            return Stats.SPEED;
        }

        return null;
    }

    /* The logic...
     *
     * In Gen 3 (Emerald), if the mother or ditto is holding an Everstone, the baby has a 50% chance of inheriting
     *   that pokemon's nature.
     *
     * In Gen 4 (HG/SS), whichever pokemon is holding an Everstone will have a chance of passing down its nature,
     *   regardless of gender or ditto.
     *
     * As of Gen 5 (B2/W2), a pokemon holding an everstone will always pass down its nature. If both parents are holding
     *   an everstone, the nature is picked at random from either parent.
     */
    private Nature getNature(Pokemon mother, Pokemon father) {
        Nature nature = Natures.INSTANCE.getRandomNature();
        Pair<Pokemon, Pokemon> parents = new Pair<>(mother, father);

        List<Nature> possible_natures = new ArrayList<>();
        if (mother.heldItem().getItem().equals(CobblemonItems.EVERSTONE)) {
            possible_natures.add(mother.getNature());
        }
        if (father.heldItem().getItem().equals(CobblemonItems.EVERSTONE)) {
            possible_natures.add(father.getNature());
        }

        if (!possible_natures.isEmpty()) {
            nature = possible_natures.get(new Random().nextInt(possible_natures.size()));
        }

        return nature;
    }

    /* The logic...
     *
     * In gen 3 and 4, abilities cannot be inherited by breeding. Instead, the egg will get a random ability from
     *   its possible abilities.
     *
     * As of gen 5, parents have a chance to pass down their ability in certain circumstances. When male/female breeding,
     *   only the female's ability is relevant; when with a ditto, only the non-ditto parent's. (Might have to do something
     *   special for Rockruff)
     *
     ************************************************
     * Regular Abilities
     *
     * In gen 5 (B2/W2), if a female is bred with a male, but not a ditto, there's an 80% chance the baby will have the
     *   mother's nature. Pokemon bred with a ditto will not pass down an ability.
     *
     * In gen 6 onward, if the female has a non-hidden ability, there's an 80% chance the baby will have the mother's nature regardless
     *   of ditto being involved.
     *
     * Hidden Abilities
     *
     * In gen 5 (B/W), if the female pokemon has a hidden ability, and is bred with a male (not ditto), there's a 60% chance of
     *   passing down the hidden ability. In B2/W2, this chance is instead 80%. Male and genderless pokemon cannot pass down their
     *   abilities in these games.
     *
     * From gen 6 onward, if a female or any pokemon bred with a ditto has a hidden ability, there's a 60% chance that the baby
     *   will have it's hidden ability.
     */
    private String getAbility(Pokemon mother, FormData baby) {
        Ability ability = mother.getAbility();
        Priority priority = ability.getPriority();

        List<AbilityTemplate> nonHAAbilities = new ArrayList<>();
        baby.getAbilities().getMapping().values().forEach(mapping -> {
            mapping.forEach(potentialAbility -> {
                if (!potentialAbility.getPriority().equals(Priority.LOW)) {
                    nonHAAbilities.add(potentialAbility.getTemplate());
                }
            });
        });

        int chance = 8;
        if (priority.equals(Priority.LOW)) {
            chance = 6;
        }

        int random = new Random().nextInt(10);
        if (random < chance) {
            return ability.getName();
        }

        return nonHAAbilities.get(new Random().nextInt(nonHAAbilities.size())).getName();
    }

    /* The logic...
     *
     * As of Gen 7, pokemon breeding with different species will result in the female's or non-ditto's pokeball being
     *   passed down. If they're the same species, regardless of form, it will pick between both parent's pokeballs.
     *
     * The master ball, cherish ball, and strange ball count as a normal pokeball.
     */
    private PokeBall getPokeball(Pokemon mother, Pokemon father) {
        PokeBall ball = mother.getCaughtBall();

        if (mother.getSpecies().equals(father.getSpecies())) {
            List<PokeBall> balls = new ArrayList<>();
            balls.add(filterBall(mother.getCaughtBall()));
            balls.add(filterBall(father.getCaughtBall()));
            ball = balls.get(new Random().nextInt(balls.size()));
        }

        return ball;
    }

    private PokeBall filterBall(PokeBall ball) {
        if (ball.equals(PokeBalls.INSTANCE.getMASTER_BALL()) || ball.equals(PokeBalls.INSTANCE.getCHERISH_BALL())) {
            return PokeBalls.INSTANCE.getPOKE_BALL();
        }

        return ball;
    }

    /* The Logic...
     *
     * Gen 3, standard shiny rate
     *
     * Gen 4, Masuda adds 4 personality values, effectively 5/shiny rate
     *
     * Gen 5, Masuda method now adds 5 personality values (6/shiny rate). Shiny charm introduced,
     *   if the player has a shiny charm when an egg is generated, 2 more personality values (3/shiny rate).
     *   These methods can be combined for a total of 7 personality values (8/shiny rate)
     *
     * Gen 6, shiny rate changed to 1/4096. This doesn't matter, servers set what they want.
     *
     * Gen 8, the initial personality value is skipped if bonus rolls are applied. Masuda method now
     *   adds 6 personality values, however the first 1 is skipped, so it is effectively the same.
     *   Shiny charm's first personality value is skipped, making it 2/shiny rate instead of 3. But it still
     *   does add 3 personality values. Combining these methods totals (6 + 3 - 1)/shiny rate.
     */
    private boolean getShiny(Pokemon mother, Pokemon father, ServerPlayerEntity player) {
        ab.logInfo("[AstralBreeding] Getting Shiny");
        AtomicReference<Float> atomicShinyRate = new AtomicReference<>(Cobblemon.config.getShinyRate());
        CobblemonEvents.SHINY_CHANCE_CALCULATION.post(new ShinyChanceCalculationEvent[]{new ShinyChanceCalculationEvent(atomicShinyRate.get(), mother)}, event -> {
            atomicShinyRate.set(event.calculate(player));
            return Unit.INSTANCE;
        });

        float shinyRate = atomicShinyRate.get();
        ab.logInfo("[AstralBreeding] Shiny Rate: " + shinyRate);
        int pValues = 1;

        String mother_owner = (mother.getOriginalTrainer() != null) ? mother.getOriginalTrainer() : Objects.requireNonNull(mother.getOwnerPlayer()).getUuidAsString();
        String father_owner = (father.getOriginalTrainer() != null) ? father.getOriginalTrainer() : Objects.requireNonNull(father.getOwnerPlayer()).getUuidAsString();

        if (!mother_owner.equalsIgnoreCase(father_owner)) {
            pValues--;
            pValues += 6;
        }
        ab.logInfo("[AstralBreeding] P values: " + pValues);

        shinyRate /= pValues;
        ab.logInfo("[AstralBreeding] New Shiny Rate: " + shinyRate);

        return new Random().nextInt(Math.round(shinyRate)) == 0;
    }

    private List<String> getAspects(Pokemon mother) {
        List<String> motherAspects = new ArrayList<>(mother.getAspects());
        motherAspects.remove("shiny");
        motherAspects.remove("male");
        motherAspects.remove("female");
        return motherAspects;
    }

    private Pokemon getRandomParent(Pair<Pokemon, Pokemon> parents) {
        int randomNum = new Random().nextInt(0, 2);
        ab.logInfo("[AstralBreeding] Random Parent: " + randomNum);
        if (randomNum == 0) {
            ab.logInfo("[AstralBreeding] Selecting first parent.");
            return parents.getFirst();
        }
        ab.logInfo("[AstralBreeding] Selecting second parent.");
        return parents.getSecond();
    }

    private FormData getEgg(Pokemon mother) {
        FormData form = mother.getForm();

        Species baby_Species = mother.getSpecies();
        while (baby_Species.getPreEvolution() != null) {
            baby_Species = baby_Species.getPreEvolution().getSpecies();
        }

        for (FormData formData : baby_Species.getForms()) {
            if (formData.formOnlyShowdownId().contains(form.formOnlyShowdownId())) {
                ab.logInfo("[AstralBreeding] Mother Form: " + form.formOnlyShowdownId());
                ab.logInfo("[AstralBreeding] Baby Form: " + formData.formOnlyShowdownId());
                return formData;
            }
        }

        ab.logInfo("[AstralBreeding] Mother Form: " + form.formOnlyShowdownId());
        ab.logInfo("[AstralBreeding] Baby Form: " + baby_Species.getStandardForm().formOnlyShowdownId());
        return baby_Species.getStandardForm();
    }

    private boolean rarePokemon(Pokemon pokemon) {
        Set<String> labels = pokemon.getForm().getLabels();

        return labels.contains(CobblemonPokemonLabels.LEGENDARY)
                || labels.contains(CobblemonPokemonLabels.ULTRA_BEAST)
                || labels.contains(CobblemonPokemonLabels.PARADOX)
                || labels.contains(CobblemonPokemonLabels.MYTHICAL);
    }
}
