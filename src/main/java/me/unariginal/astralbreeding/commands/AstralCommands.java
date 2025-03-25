package me.unariginal.astralbreeding.commands;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.unariginal.astralbreeding.AstralBreeding;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;
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

                    Species baby_Species = getEgg(first_Pokemon, second_Pokemon);
                    player.sendMessage(Text.literal("Baby Species: " + baby_Species.getName()));
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

    private Species getEgg(Pokemon first_Pokemon, Pokemon second_Pokemon) {
        Pokemon mother = (first_Pokemon.getGender() == Gender.FEMALE) ? first_Pokemon : second_Pokemon;
        Pokemon father = (first_Pokemon.getGender() == Gender.MALE) ? first_Pokemon : second_Pokemon;

        Species baby_Species = mother.getSpecies();
        PreEvolution preEvolution = baby_Species.getPreEvolution();
        while (preEvolution != null) {
            baby_Species = preEvolution.getSpecies();
            preEvolution = baby_Species.getPreEvolution();
        }

        return baby_Species;
    }

    private boolean rarePokemon(Pokemon pokemon) {
        Set<String> labels = pokemon.getForm().getLabels();

        return labels.contains(CobblemonPokemonLabels.LEGENDARY)
                || labels.contains(CobblemonPokemonLabels.ULTRA_BEAST)
                || labels.contains(CobblemonPokemonLabels.PARADOX)
                || labels.contains(CobblemonPokemonLabels.MYTHICAL);
    }
}
