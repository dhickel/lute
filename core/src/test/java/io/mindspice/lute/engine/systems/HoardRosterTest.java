package io.mindspice.lute.engine.systems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.mindspice.lute.engine.systems.HoardSystem.RosterEntry;

public final class HoardRosterTest {
    public static void main(String[] args) {
        var expected = List.of(
            new RosterEntry("brainless_undead", 1, 2, 5),
            new RosterEntry("undead_archer", 1, 1, 5),
            new RosterEntry("bone_soldier", 5, 2, 10),
            new RosterEntry("cultist", 10, 2, 15),
            new RosterEntry("cultist_archer", 10, 1, 15),
            new RosterEntry("viper", 20, 1, 20),
            new RosterEntry("reaper", 25, 2, 25),
            new RosterEntry("raider", 30, 2, 30)
        );
        if (!HoardSystem.WAVE_ROSTER.equals(expected)) {
            throw new AssertionError("Unexpected roster (key, unlock round, weight, base XP): " + HoardSystem.WAVE_ROSTER);
        }
        var tickets = new ArrayList<RosterEntry>();
        for (int round = 1; round <= 35; round++) {
            for (var entry : expected) {
                if (entry.unlockRound() == round) { tickets.addAll(Collections.nCopies(entry.weight(), entry)); }
            }
            var actual = HoardSystem.spawnTickets(round);
            if (!actual.equals(tickets)) {
                throw new AssertionError("Wrong ordered tickets at round " + round + ": " + actual + " != " + tickets);
            }
        }
        System.out.println("Hoard roster and ordered tickets passed for rounds 1–35");

        checkStarterCycle("basic_sword", List.of("basic_sword", "basic_bow"));
        checkStarterCycle("basic_bow", List.of("basic_bow", "basic_sword"));
        checkStarterCycle("unarmed", List.of("unarmed", "basic_sword", "basic_bow"));
        checkStarterCycle("Basic_Sword", List.of("Basic_Sword", "basic_sword", "basic_bow"));
        System.out.println("Hoard starter weapon order and exact-ID deduplication passed");
    }

    private static void checkStarterCycle(String equippedWeapon, List<String> expected) {
        var actual = HoardSystem.starterWeaponCycle(equippedWeapon);
        if (!actual.equals(expected)) {
            throw new AssertionError("Wrong starter cycle with " + equippedWeapon + ": " + actual + " != " + expected);
        }
    }
}
