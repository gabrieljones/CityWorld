package me.daddychurchill.CityWorld.Support;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OddsTest {

    @Test
    public void testAlwaysGoingToHappen() {
        Odds odds = new Odds();
        assertTrue(odds.playOdds(Odds.oddsAlwaysGoingToHappen), "Always going to happen should return true");
    }

    @Test
    public void testNeverGoingToHappen() {
        Odds odds = new Odds();
        assertFalse(odds.playOdds(Odds.oddsNeverGoingToHappen), "Never going to happen should return false");
    }

    @Test
    public void testRandomIntRange() {
        Odds odds = new Odds();
        for (int i = 0; i < 100; i++) {
            int val = odds.getRandomInt(10);
            assertTrue(val >= 0 && val < 10, "Value " + val + " should be in range [0, 10)");
        }
    }

    @Test
    public void testRandomIntMinRange() {
        Odds odds = new Odds();
        for (int i = 0; i < 100; i++) {
            int val = odds.getRandomInt(5, 10);
            assertTrue(val >= 5 && val < 15, "Value " + val + " should be in range [5, 15)");
        }
    }

    @Test
    public void testCalcRandomRangeInt() {
        Odds odds = new Odds();
        for (int i = 0; i < 100; i++) {
            int val = odds.calcRandomRange(5, 10);
            assertTrue(val >= 5 && val <= 10, "Value " + val + " should be in range [5, 10]");
        }
    }
}
