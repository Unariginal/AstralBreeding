package me.unariginal.astralbreeding.commands;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import kotlin.Pair;
import me.unariginal.astralbreeding.AstralBreeding;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.Item;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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

                    properties.setSpecies(baby_Form.getSpecies().showdownId());
                    properties.setForm(baby_Form.formOnlyShowdownId());
                    properties.setIvs(ivs);

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

    private FormData getEgg(Pokemon mother) {
        FormData form = mother.getForm();

        Species baby_Species = mother.getSpecies();
        while (baby_Species.getPreEvolution() != null) {
            baby_Species = baby_Species.getPreEvolution().getSpecies();
        }

        for (FormData formData : baby_Species.getForms()) {
            if (formData.formOnlyShowdownId().contains(form.formOnlyShowdownId())) {
                return formData;
            }
        }

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
