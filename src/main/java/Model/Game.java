package Model;

import Game.Command;
import Game.GameEvent;
import Game.Parser;
import Game.UtilArray;
import View.View;
import com.sun.tools.internal.xjc.model.Model;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * @author Hasan Issa
 * <p>
 * This main class creates and initialises all the others. This class acts the Controller.
 * It create the map of all Countries and their neighbours.
 * It creates the parser and starts the game.
 * It also evaluates and executes the commands that the parser returns.
 */
public class Game {
    public String result;
    private Parser parser;
    private Player currentPlayer;
    private int currentPlayerIndex;
    private List<Player> players;
    private ModelUpdateListener viewer;
    private int numberOfPlayers;
    private int initialNumberOfTroops;
    private Map myMap = new Map();
    private InputStream inputStream;
    private Country attackingCountry;
    private Country defendingCountry;
    private boolean randomlyAllocateTroopsOnGameStart = false;

    public Game() {
        this.myMap = new Map();
        parser = new Parser();
        this.currentPlayerIndex = 0;
    }

    public static void main(String[] args) {
        Game game = new Game();
    }

    public boolean isRandomlyAllocateTroopsOnGameStart() {
        return randomlyAllocateTroopsOnGameStart;
    }

    public void setRandomlyAllocateTroopsOnGameStart(boolean randomlyAllocateTroopsOnGameStart) {
        this.randomlyAllocateTroopsOnGameStart = randomlyAllocateTroopsOnGameStart;
    }

    public Map getMyMap() {
        return myMap;
    }

    public void passTurn() {
        this.currentPlayerIndex = (this.currentPlayerIndex == (this.numberOfPlayers - 1)) ? 0 : this.currentPlayerIndex + 1;
        this.currentPlayer = this.players.get(this.currentPlayerIndex);
        update();
    }

    private void newTurn() {
        this.getCurrentPlayer().getMyPossibleTargets(myMap);
        //printListOfCurrentPlayerPossibleCountriesToAttack();
    }

    public void initializePlayers(int numberOfPlayers) {
        /**
         * @author Hasan Issa
         *
         * Ask the user for the number of players that will be playing, and then calls calculateTroops() to
         * determine how many troops each player will get.
         *
         */
        this.numberOfPlayers = numberOfPlayers;
        createPlayers(numberOfPlayers, calculateTroops(numberOfPlayers));
        this.currentPlayer = players.get(0);
        if(randomlyAllocateTroopsOnGameStart)
            randomizeMap();
        this.getCurrentPlayer().getMyPossibleTargets(myMap);
        //viewer.handleGameStartEvent(new GameEvent(this, myMap, numberOfPlayers));
        update();
    }

    private void update() {
        if(this.viewer != null)
            this.viewer.modelUpdated();
    }

    public int calculateTroops(int numberOfPlayers) {
        initialNumberOfTroops = 0;
        switch (numberOfPlayers) {
            case 2:
                initialNumberOfTroops = 50;
                break;
            case 3:
                initialNumberOfTroops = 35;
                break;
            case 4:
                initialNumberOfTroops = 30;
                break;
            case 5:
                initialNumberOfTroops = 25;
                break;
            case 6:
                initialNumberOfTroops = 20;
                break;
        }
        return initialNumberOfTroops;
    }

    private void createPlayers(int numberOfPlayers, int initialNumberOfTroops) {
        players = new ArrayList<Player>();
        for (int i = 1; i <= numberOfPlayers; i++) {
            players.add(new Player(i, initialNumberOfTroops));
        }
    }

    public List<Player> getPlayers() {
        return players;
    }

    public String getResult() {
        return result;
    }

    private void randomizeMap() {
        /**
         * @author Hasan Issa
         *
         * Iterate over each Country and assign them 1 troop from a random player. Iterate until each country has 1 troop.
         * Once all countries have 1 troop, randomize the allocation of the leftover troops. Only 1 player is allowed in a country.
         * Once all players have all their troops allocated, finish.
         *
         */
        firstPhaseOfDeployment();
        secondPhaseOfDeployment();

    }

    private void firstPhaseOfDeployment() {
        Country[] remainingCountries = myMap.getAllCountries().toArray(new Country[0]);
        Random randomize = new Random();
        int i = 0;
        while (remainingCountries.length != 0) {
            int randomNumber = randomize.nextInt(remainingCountries.length);

            remainingCountries[randomNumber].setPlayer(players.get(i));
            players.get(i).decrementUndeployedNumberOfTroops();
            remainingCountries[randomNumber].incrementNumberOfTroops();
            remainingCountries = UtilArray.removeTheElement(remainingCountries, randomNumber);
            i++;
            if (i == numberOfPlayers) i = 0;
        }
        for (Player player : players) {
            for (Country country : myMap.getAllCountries()) {
                if (country.getPlayer().equals(player)) {
                    player.getMyCountries().add(country);
                }
            }
        }
    }

    private void secondPhaseOfDeployment() {
        //For each Player, iterate over all the countries that they own, and randomly increment the number of troops in their countries until they have no more troops left to allocate
        Random randomize = new Random();
        for (int i = 0; i < players.size(); i++) {
            while (players.get(i).getUndeployedTroops() > 0) {
                int randomNumber = randomize.nextInt(players.get(i).getMyCountries().size());
                players.get(i).getMyCountries().get(randomNumber).incrementNumberOfTroops();
                players.get(i).decrementUndeployedNumberOfTroops();
            }
        }
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public int getInitialNumberOfTroops() {
        return initialNumberOfTroops;
    }

    private void printListOfCurrentPlayerPossibleCountriesToAttack() {
        this.getCurrentPlayer().getMyPossibleTargets(myMap);
    }

    public Country getAttackingCountry() {
        return attackingCountry;
    }

    public void setAttackingCountry(Country attackingCountry) {
        this.attackingCountry = attackingCountry;
    }

    public Country getDefendingCountry() {
        return defendingCountry;
    }

    public void setDefendingCountry(Country defendingCountry) {
        this.defendingCountry = defendingCountry;
    }

    /**
     * Checks the syntax of the command passed, and makes sure it is Attack followed by AttackingCountry DefendingCountry #OfTroopsAttacking
     * Ensures that the AttackingCountry is owned by the current player, the DefendingCountry is a neighbour of his, and that he has atleast 1 troop left in the country
     *
     * @author Hasan Issa
     */
    public boolean initiateAttack(Command command) {

        if (!checkAttackCommandSyntax(command)) {
            System.out.println("Attack syntax error ");
            return false;
        }

        String attackCountryName = command.getSecondWord().toLowerCase();
        setAttackingCountry(myMap.getCountryByName(attackCountryName));
        ;
        if (!checkAttackCountry(attackingCountry)) {
            System.out.println("Check attack failed");
            return false;
        }

        String defenceCountryName = command.getThirdWord();
        setDefendingCountry(myMap.getCountryByName(defenceCountryName));
        if (!checkDefenceCountry(attackingCountry, defendingCountry)) {
            System.out.println("Check defence failed");
            return false;
        }

        int numberOfTroopsAttacking = command.getFourthWord();
        if (checkNumberOfTroopsAttacking(attackCountryName, numberOfTroopsAttacking)) {
            attackAlgorithm(numberOfTroopsAttacking, getDefendingCountry().getNumberOfTroops());

        }
        update();
        return true;
    }

    private String attackAlgorithm(int numberOfTroopsAttacking, int numberOfTroopsDefending) {
        /**
         * @author Hasan Issa
         *
         * Rolls dice based on the number of troops attacking and defending
         * Checks the highest attack dice with the highest defence dice, the higher number wins. A tie is a Defender win.
         * Checks the second highest attack dice with the second highest defence dice, the higher number wins. A tie is a Defender win.
         * Decrements the number of troops for the loser side.
         * If the attacker has no troops left, he loses the attack.
         * If the defender has no troops left, the attacker wins the attack.
         *
         */
        List<Integer> attackersDiceResults = new ArrayList<>();
        List<Integer> defendersDiceResults = new ArrayList<>();
        List<Integer> troopsLost = new ArrayList<>();
        int currentNumberOfAttackingTroops = numberOfTroopsAttacking;
        int currentNumberOfDefendingTroops = numberOfTroopsDefending;
        while (currentNumberOfAttackingTroops > 0 && currentNumberOfDefendingTroops > 0) {
            if (currentNumberOfAttackingTroops >= 3) {
                attackersDiceResults = rollDice(3);
                if (currentNumberOfDefendingTroops == 1) {
                    defendersDiceResults = rollDice(1);
                } else {
                    defendersDiceResults = rollDice(2);
                }
                troopsLost = checkOutcomeOfBattle(attackersDiceResults, defendersDiceResults);
                currentNumberOfAttackingTroops = currentNumberOfAttackingTroops - troopsLost.get(0);
                attackingCountry.setNumberOfTroops(attackingCountry.getNumberOfTroops() - troopsLost.get(0));
                currentNumberOfDefendingTroops = currentNumberOfDefendingTroops - troopsLost.get(1);
                defendingCountry.setNumberOfTroops(defendingCountry.getNumberOfTroops() - troopsLost.get(1));
            }
            if (currentNumberOfAttackingTroops > 0 && currentNumberOfAttackingTroops < 3) {
                attackersDiceResults = rollDice(currentNumberOfAttackingTroops);
                if (currentNumberOfDefendingTroops == 1) {
                    defendersDiceResults = rollDice(1);
                } else {
                    defendersDiceResults = rollDice(2);
                }
                troopsLost = checkOutcomeOfBattle(attackersDiceResults, defendersDiceResults);
                currentNumberOfAttackingTroops = currentNumberOfAttackingTroops - troopsLost.get(0);
                attackingCountry.setNumberOfTroops(attackingCountry.getNumberOfTroops() - troopsLost.get(0));
                currentNumberOfDefendingTroops = currentNumberOfDefendingTroops - troopsLost.get(1);
                defendingCountry.setNumberOfTroops(defendingCountry.getNumberOfTroops() - troopsLost.get(1));
            }
        }
        if (currentNumberOfAttackingTroops <= 0) {
            this.result = "Sadly, you have lost the attack.\n";
            attackingCountry.getPlayer().setTotalNumberOftroops(attackingCountry.getPlayer().getTotalNumberOftroops() - (numberOfTroopsAttacking - currentNumberOfAttackingTroops));
            defendingCountry.getPlayer().setTotalNumberOftroops(defendingCountry.getPlayer().getTotalNumberOftroops() - (numberOfTroopsDefending - currentNumberOfDefendingTroops));
            newTurn();
            if (attackingCountry.getPlayer().getTotalNumberOftroops() == 0) {
                players.remove(attackingCountry.getPlayer());
            }
            if (defendingCountry.getPlayer().getTotalNumberOftroops() == 0) {
                players.remove(defendingCountry.getPlayer());
            }
        }
        if (currentNumberOfDefendingTroops <= 0) {
            this.result = "Congratulations! You have won the attack!\n";
            attackingCountry.getPlayer().setTotalNumberOftroops(attackingCountry.getPlayer().getTotalNumberOftroops() - (numberOfTroopsAttacking - currentNumberOfAttackingTroops));
            defendingCountry.getPlayer().setTotalNumberOftroops(defendingCountry.getPlayer().getTotalNumberOftroops() - (numberOfTroopsDefending - currentNumberOfDefendingTroops));
            newTurn();
            if (attackingCountry.getPlayer().getTotalNumberOftroops() == 0) {
                players.remove(attackingCountry.getPlayer());
            }
            if (defendingCountry.getPlayer().getTotalNumberOftroops() == 0) {
                players.remove(defendingCountry.getPlayer());
            }
        }
        return result;
    }

    private List<Integer> checkOutcomeOfBattle(List<Integer> attackersDiceResults, List<Integer> defendersDiceResults) {
        List<Integer> troopsLost = new ArrayList<>();
        int troopsLostFromAttacker = 0;
        int troopsLostFromDefender = 0;
        int highestAttackRoll = 0;
        int secondHighestAttackRoll = 0;
        int highestDefenceRoll = 0;
        int secondHighestDefenceRoll = 0;

        for (Integer i : attackersDiceResults) {
            if (i.intValue() >= highestAttackRoll) {
                secondHighestAttackRoll = highestAttackRoll;
                highestAttackRoll = i.intValue();
            }
            if (i.intValue() > secondHighestAttackRoll && i.intValue() != highestAttackRoll)
                secondHighestAttackRoll = i.intValue();
        }
        for (Integer i : defendersDiceResults) {
            if (i.intValue() >= highestDefenceRoll) {
                secondHighestDefenceRoll = highestDefenceRoll;
                highestDefenceRoll = i.intValue();
            }
            if (i.intValue() > secondHighestDefenceRoll && i.intValue() != highestDefenceRoll)
                secondHighestDefenceRoll = i.intValue();
        }

        if (highestAttackRoll <= highestDefenceRoll) {
            troopsLostFromAttacker++;
        } else {
            troopsLostFromDefender++;
        }

        if (secondHighestAttackRoll <= secondHighestDefenceRoll && secondHighestAttackRoll != 0 && secondHighestDefenceRoll != 0) {
            troopsLostFromAttacker++;
        }
        if (secondHighestAttackRoll > secondHighestDefenceRoll && secondHighestAttackRoll != 0 && secondHighestDefenceRoll != 0) {
            troopsLostFromDefender++;
        }

        troopsLost.add(troopsLostFromAttacker);
        troopsLost.add(troopsLostFromDefender);
        return troopsLost;
    }

    private List<Integer> rollDice(Integer numberOfDiceToRoll) {
        List<Integer> diceRolls = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < numberOfDiceToRoll; i++) {
            int diceRoll = random.nextInt(6) + 1;
            diceRolls.add(diceRoll);
        }
        return diceRolls;
    }

    private boolean checkAttackCommandSyntax(Command command) {
        if (!command.hasSecondWord()) {
            return false;
        }
        if (!command.hasThirdWord()) {
            return false;
        }
        if (!command.hasFourthWord()) {
            return false;
        }
        return true;
    }

    private boolean checkAttackCountry(Country attackCountry) {
        if (attackCountry == null) {
            return false;
        }
        if (this.getCurrentPlayer().getMyCountries().contains(attackCountry)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean checkDefenceCountry(Country attackCountry, Country defenceCountry) {
        if (defenceCountry == null) {
            return false;
        }
        if (this.getCurrentPlayer().getNeighbours(myMap, attackCountry).contains(defenceCountry)) {
            if (!attackCountry.getPlayer().equals(defenceCountry.getPlayer())) {
                return true;
            } else {
                return false;
            }

        }
        return false;
    }

    private boolean checkNumberOfTroopsAttacking(String attackCountryName, int numberOfTroopsAttacking) {
        if (this.getCurrentPlayer().getACountry(attackCountryName).getNumberOfTroops() <= numberOfTroopsAttacking) {
            return false;

        }
        return true;
    }

    public void setViewer(ModelUpdateListener viewer) {
        this.viewer = viewer;
    }

    @Deprecated
    private void updateViewer() {
        //this.viewer.handleGameStartEvent(new GameEvent(this));
    }

    public void startGame(int numberOfPlayers) {
        initializePlayers(numberOfPlayers);
        newTurn();

    }

    public void quitGame() {
        System.exit(0);
    }


}
