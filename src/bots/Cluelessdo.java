package bots;

import gameengine.*;
import gameengine.Map;
import java.util.*;

public class Cluelessdo implements BotAPI {

    // The public API of Bot must not change
    // This is ONLY class that you can edit in the program
    // Rename Bot to the name of your team. Use camel case.
    // Bot may not alter the state of the board or the player objects
    // It may only inspect the state of the board and the player objects

    private Player player;
    private PlayersInfo playersInfo;
    private Map map;
    private Dice dice;
    private Log log;
    private Deck deck;

    private String path;
    private Room pathEndRoom;

    private int questionCount = 0;

    //cards we were dealt at the start
    //most likely using Token and Room arrays are incorrect
    //just leaving it as that for now
    private ArrayList<String> suspectsDealt;
    private ArrayList<String> weaponsDealt;
    private ArrayList<String> roomsDealt;

    //cards we do not have any info on
    //i.e. haven't seen them through a question
    //or if we were not dealt them
    private ArrayList<String> unseenSuspects;
    private ArrayList<String> unseenWeapons;
    private ArrayList<String> unseenRooms;

    //booleans used to only initialise the Arraylists once
    private boolean suspectsInitialised;
    private boolean weaponsInitialised;

    private boolean hasQuestioned; // boolean to see if the bot has questioned in the current move
    private boolean hasMoved; // boolean to see if the bot has moved in the current move
    private Room prevRoomQuestioned; // the room that the bot questioned in last

    private ArrayList<String> shownCards; // cards that have been shown to other players

    //suspected murderer,weapon and room
    private String murderer = "";
    private String weapon = "";
    private String room = "";

    public Cluelessdo(Player player, PlayersInfo playersInfo, Map map, Dice dice, Log log, Deck deck) {
        this.player = player;
        this.playersInfo = playersInfo;
        this.map = map;
        this.dice = dice;
        this.log = log;
        this.deck = deck;
        this.path = "";
        this.pathEndRoom = map.getRoom("Ballroom");
        prevRoomQuestioned = null;
        hasMoved = false;
        hasQuestioned = false;
        weaponsInitialised = false;
        suspectsInitialised = false;
        unseenRooms = new ArrayList<>();
        unseenWeapons = new ArrayList<>();
        unseenSuspects = new ArrayList<>();
        roomsDealt = new ArrayList<>();
        suspectsDealt = new ArrayList<>();
        weaponsDealt = new ArrayList<>();
        shownCards = new ArrayList<>();
    }

    /*
     Find a path between the start and the end coordinates through the corridor
     saves path (string representing directions) in path
      */
    private void pathFind(Coordinates start, Coordinates end) {
        Queue<Coordinates> unseenTiles = new PriorityQueue<Coordinates>(Comparator.comparingInt(c -> heuristic(c, end))); // queue of tiles that need to be checked
        HashMap<Coordinates, ArrayList<Coordinates>> paths = new HashMap<>(); // hashmap of paths from the start coordinate to specific coordinates
        unseenTiles.add(start);
        paths.put(start, new ArrayList<>());

        // while there are still tiles to be looked at
        while (!unseenTiles.isEmpty()) {
            Coordinates curr = unseenTiles.remove(); // remove removed
            ArrayList<Coordinates> currPath = paths.get(curr); // get the path from the start node to the coordinate just removed

            if (curr.equals(end)) {
                ArrayList<Coordinates> endPath = paths.get(curr);

                // get the strings representing the directions needed to move from current
                String pathStr = getDirection(start, endPath.get(0));
                for (int i = 0; i < endPath.size()-1; i++) {
                    pathStr += getDirection(endPath.get(i), endPath.get(i+1));
                }

                this.path = pathStr;
                return;
            }

            for (String dir : getDirections(curr, end)) { // loop through directions to neighboring tiles
                if (map.isValidMove(curr, dir)) {
                    Coordinates neighbor = map.getNewPosition(curr, dir);

                    // path to neighboring tile
                    ArrayList<Coordinates> newPath = new ArrayList<Coordinates>(currPath);
                    newPath.add(neighbor);

                    // if neighbor is not in the path to curr coordinate and either theres no path to the neighbor coordinate or the new path to the neighbor coordinate is shorter than the path in the hashmap to it
                    if (!containsCoord(currPath, neighbor) && (!paths.containsKey(neighbor) || newPath.size() < paths.get(neighbor).size())) {
                        paths.put(neighbor, newPath);
                        if (!unseenTiles.contains(neighbor)) { // if not in queue add it to the queue
                            unseenTiles.add(neighbor);
                        }
                    }
                }
            }
        }
        return;
    }

    // get directions of neighbouring tiles, displaying the direction of the end tile in relation to the current tile before the other directions
    private String[] getDirections(Coordinates curr, Coordinates end) {
        String[] dirs = new String[4];
        if (curr.getCol() - end.getCol() < 0) { // if the end node is to the right of the current tile
            dirs[0] = "r";
            dirs[2] = "l";
        } else {
            dirs[0] = "l";
            dirs[2] = "r";
        }

        if (curr.getRow() - end.getRow() < 0) { // if the end node is below the current tile
            dirs[1] = "d";
            dirs[3] = "u";
        } else {
            dirs[1] = "u";
            dirs[3] = "d";
        }
        return dirs;
    }

    // get the direction of a move from one coordinate to another
    private static String getDirection(Coordinates prev, Coordinates next) {
        if (prev.getCol() == next.getCol()) { // if it was a vertical move
            if (prev.getRow() < next.getRow()) { // if the previous node is above the next node
                return "d";
            }
            return "u";
        } else {
            if (prev.getCol() < next.getCol()) { // if the previous node is to the left of the next node
                return "r";
            }
            return "l";
        }
    }

    /*
      returns a boolean
      true if the coordinate is in the arraylist otherwise false
     */
    private boolean containsCoord(ArrayList<Coordinates> list, Coordinates c) {
        for (Coordinates e : list) {
            if (e.equals(c)) {
                return true;
            }
        }
        return false;
    }

    // returns the manhattan distnace between the start and end coordinates
    private int heuristic(Coordinates start, Coordinates end) {
        return Math.abs(start.getRow()-end.getRow()) + Math.abs(start.getCol()-end.getCol());
    }

    public String getName() {
        return "Cluelessdo";
    }

    public String getCommand() {
        // if the player is in a room
        if (player.getToken().isInRoom()) {
            Room currRoom = player.getToken().getRoom();
            if (currRoom.toString().equals("Cellar")) { // if the player is in the cellar
                updateUnseenRooms();
                return "accuse"; // accuse
            } if (decideToAsk(currRoom)) { // is the bot going to ask
                hasQuestioned = true;
                hasMoved = true;
                prevRoomQuestioned = currRoom;
                updateUnseenRooms();
                return "question"; // question
            } else if (currRoom.hasPassage() && !hasMoved) {
                if (decideToTakePassage(currRoom)) {
                    hasMoved = true;
                    updateUnseenRooms();
                    return "passage"; // move through the passage
                }
            }
        }
        if (hasQuestioned || hasMoved) {
            hasQuestioned = false;
            hasMoved = false;
            updateUnseenRooms();
            return "done"; // finish turn
        }
        hasMoved = true;
        return "roll"; // roll
    }

    public String getMove() {
        // if there is no path
        if (path.equals("")) {
            updateUnseenRooms();
            if(!isRoomKnown() || !isSuspectKnown() || !isWeaponKnown()) { // if were not trying to make an accusation
               pathFind(this.player.getToken().getPosition(), nearestRoom(unseenRooms).getDoorCoordinates(getClosestDoor(this.player.getToken().getPosition(), nearestRoom(unseenRooms))));
            }
            /*if(room.equals("Library") || !isSuspectKnown() || !isWeaponKnown()) { // if were not trying to make an accusation
                pathFind(this.player.getToken().getPosition(), nearestRoom(unseenRooms).getDoorCoordinates(getClosestDoor(this.player.getToken().getPosition(), nearestRoom(unseenRooms))));
            }*/
            else{
                pathFind(this.player.getToken().getPosition(), map.getRoom("Cellar").getDoorCoordinates(0)); // find path to the cellar
            }
        }
        String dir = Character.toString(path.charAt(0)); // get the first move
        path = path.substring(1, path.length()); // remove the first move
        return dir;
    }

    public String getSuspect() {
        if(!suspectsInitialised) {
            initializeSuspectsDealt();
            suspectsInitialised = true;
        }
        /* If the bot is in a room asking a question */
        if(!this.player.getToken().getRoom().toString().equals(Names.ROOM_NAMES[9])) {
            if (isSuspectKnown()) {
                /*
                 * return a suspect we were dealt at the start,
                 * or if none were received at the start return the murderer.
                 **/
                if(suspectsDealt.size() != 0){
                    Random random = new Random();
                    return suspectsDealt.get(random.nextInt(suspectsDealt.size()));
                }
                else{
                    return murderer;
                }
            } else if (!isSuspectKnown()) {
                /*
                 * return a suspect which we do not know anything about
                 */
                Random random = new Random();
                updateUnseenSuspects();
                return unseenSuspects.get(random.nextInt(unseenSuspects.size()));
            }
        }
        //in the cellar
        else{
            return murderer;
        }
        return null;
    }

    public String getWeapon() {
        if(!weaponsInitialised) {
            initializeWeaponsDealt();
            weaponsInitialised = true;
        }

        if(!this.player.getToken().getRoom().toString().equals(Names.ROOM_NAMES[9])) {
            if (isWeaponKnown()) {
                /*
                 * return a weapon we were dealt at the start,
                 * or if none were received at the start return the murder weapon.
                 **/
                if(weaponsDealt.size() != 0){
                    Random random = new Random();
                    return weaponsDealt.get(random.nextInt(weaponsDealt.size()));
                }
                else{
                    return weapon;
                }
            } else if (!isWeaponKnown()) {
                /*
                 * return a weapon which we do not know anything about
                 */
                Random random = new Random();
                updateUnseenWeapons();
                return unseenWeapons.get(random.nextInt(unseenWeapons.size()));
            }
        }

        return weapon;

    }

    public String getRoom() {
        return room;
    }

    /* finds the door that is closest to a coordinate
        returns an index to the door in the array
     */
    public int getClosestDoor(Coordinates start, Room endRoom) {
        int endRoomDoorNum = endRoom.getNumberOfDoors();
        int endIndex = 0; // index of the door for the end of the path

        for (int j = 0; j < endRoomDoorNum; j++) {
            if (heuristic(start, endRoom.getDoorCoordinates(j)) < heuristic(start, endRoom.getDoorCoordinates(endIndex))) { // if less than current minimum
                endIndex = j;
            }
        }

        return endIndex;
    }

    public String getDoor() {
        Room startRoom = player.getToken().getRoom();
        int startRoomDoorNum = startRoom.getNumberOfDoors();

        pathEndRoom = nearestRoom(unseenRooms); // desired room is the unseen room that is closest to the players position

        int startIndex = 0; // index of the door for the start of the path
        int endIndex = 0; // index of the door for the end of the path

        // loop through the doors of the start room and the end room to find a pair with the shortest heuristics
        for (int i = 0; i < startRoomDoorNum; i++) {
            int currEndIndex = getClosestDoor(startRoom.getDoorCoordinates(i), pathEndRoom); // get closest door from the end room from the door at the start door
            //check if its a shorter path
            if (heuristic(startRoom.getDoorCoordinates(i), pathEndRoom.getDoorCoordinates(currEndIndex)) < heuristic(startRoom.getDoorCoordinates(startIndex), pathEndRoom.getDoorCoordinates(endIndex))) { // if less than current minimum
                startIndex = i;
                endIndex = currEndIndex;
            }
        }

        if(!isRoomKnown() || !isSuspectKnown() || !isWeaponKnown()) { // if were not trying to make an accusation
            pathFind(startRoom.getDoorCoordinates(startIndex), pathEndRoom.getDoorCoordinates(endIndex));
        }
            else{
            pathFind(startRoom.getDoorCoordinates(startIndex), map.getRoom("Cellar").getDoorCoordinates(0));
        }

        this.pathEndRoom = null; // set the desired room to null
        return Integer.toString(startIndex+1);
    }

    public String getCard(Cards matchingCards) {
        /*
         *   If we can we should try to show the same card to the
         *   same person if we have shown it to them before
         */
        if (matchingCards.count() == 0) { // if matchingCards is empty
            return matchingCards.get().toString(); // return matchingCards
        } else if (matchingCards.count() == 1) { // if matchingCards has one element in it
            if (!shownCards.contains(matchingCards.get().toString())) { // if shown cards does not contain the matchingCards
                shownCards.add(matchingCards.get().toString()); // add the matchingCards to the shownCards List
            }
            return matchingCards.get().toString(); // return the matchingCards
        }
        for (Card currCard : matchingCards) { // this is when matchingCards is of size 2 or more
            for (int i = 0; i < shownCards.size(); i++) {
                if (currCard.hasName(shownCards.get(i).toString())) { // if one of the matchingCards is found in the shownCards arrayList, return that card
                    return shownCards.get(i).toString();
                }
            }
        }

        Card card = matchingCards.get(); // get the first card from the matchingCards List
        shownCards.add(card.toString()); // add it to the matchingCards

        return card.toString();
    }

    public void notifyResponse(Log response) {
        // Add your code here
    }

    private boolean isRoomKnown() {
        updateUnseenRooms();
        if(unseenRooms.size() == 1){
            room = unseenRooms.get(0);
            return true;
        }
        else {
            return false;
        }
    }

    private boolean isWeaponKnown() {
        updateUnseenWeapons();
        if(unseenWeapons.size() == 1){
            weapon = unseenWeapons.get(0);
            return true;
        }
        else {
            return false;
        }
    }

    private boolean isSuspectKnown() {
        updateUnseenSuspects();
        if(unseenSuspects.size() == 1){
            murderer = unseenSuspects.get(0);
            return true;
        }
        else {
            return false;
        }}

    //update info on weapons we have no info on
    private void updateUnseenWeapons(){
        for(int i = 0 ; i < Names.WEAPON_NAMES.length ; i++){
            if(!this.player.hasCard(Names.WEAPON_NAMES[i]) && !this.player.hasSeen(Names.WEAPON_NAMES[i]) && !deck.getSharedCards().contains(Names.WEAPON_NAMES[i]) && !unseenWeapons.contains(Names.WEAPON_NAMES[i])){
                unseenWeapons.add(Names.WEAPON_NAMES[i]);
            }
            else if(this.player.hasSeen(Names.WEAPON_NAMES[i])){
                unseenWeapons.remove(Names.WEAPON_NAMES[i]);
            }
        }
    }


    //update info on suspects we have no info on
    private void updateUnseenSuspects(){
        for(int i = 0 ; i < Names.SUSPECT_NAMES.length ; i++){
            if(!this.player.hasCard(Names.SUSPECT_NAMES[i]) && !this.player.hasSeen(Names.SUSPECT_NAMES[i]) && !deck.getSharedCards().contains(Names.SUSPECT_NAMES[i]) && !unseenSuspects.contains(Names.SUSPECT_NAMES[i])){
                unseenSuspects.add(Names.SUSPECT_NAMES[i]);
            }
            else if(this.player.hasSeen(Names.SUSPECT_NAMES[i])){
                unseenSuspects.remove(Names.SUSPECT_NAMES[i]);
            }
        }
    }

    //update info on rooms we have no info on
    private void updateUnseenRooms(){
        for(int i = 0 ; i < Names.ROOM_NAMES.length-1 ; i++){
            if(!this.player.hasCard(Names.ROOM_NAMES[i])&& !this.player.hasSeen(Names.ROOM_NAMES[i]) && !deck.getSharedCards().contains(Names.ROOM_NAMES[i]) && !unseenRooms.contains(Names.ROOM_NAMES[i])){
                unseenRooms.add(Names.ROOM_NAMES[i]);
            }
            else if(this.player.hasSeen(Names.ROOM_NAMES[i])){
                unseenRooms.remove(Names.ROOM_NAMES[i]);
            }
        }
    }

    //initialize suspectsDealt
    private void initializeSuspectsDealt(){
        for(int i = 0 ; i < Names.SUSPECT_NAMES.length ; i++){
            if(this.player.hasCard(Names.SUSPECT_NAMES[i])){
                suspectsDealt.add(Names.SUSPECT_NAMES[i]);
            }
        }

    }

    //initialize weaponsDealt
    private void initializeWeaponsDealt() {
        for (int i = 0; i < Names.WEAPON_NAMES.length; i++) {
            if (this.player.hasCard(Names.WEAPON_NAMES[i])) {
                weaponsDealt.add(Names.WEAPON_NAMES[i]);
            }
        }
    }

    //initialize roomsDealt
    private void initializeRoomsDealt(){
        for(int i = 0 ; i < Names.ROOM_CARD_NAMES.length ; i++){
            if(this.player.hasCard(Names.ROOM_CARD_NAMES[i])){
                roomsDealt.add(Names.ROOM_CARD_NAMES[i]);
            }
        }

    }

    /* Decide whether the bot should ask a question in the current room */
    private boolean decideToAsk(Room currentRoom){
        if (prevRoomQuestioned == null || !currentRoom.toString().equals(prevRoomQuestioned.toString())) {
            return true;
        }
        return false;
    }

    /* Decide whether the bot should use a secret passage */
    private boolean decideToTakePassage(Room currentRoom){
        Room passageRoom = currentRoom.getPassageDestination();
        if(!unseenRooms.contains(passageRoom.toString())) {
            return false;
        } else {
            return true;
        }
    }

    //method to return a room nearest to the current location of the token
    //from an arraylist of rooms
    private Room nearestRoom(ArrayList<String> rooms){
        Coordinates start = this.player.getToken().getPosition(); // current location of the bot
        Room nearest = null;

        //if we know the murder room
        //alternate going between the murder room and its nearest room to ask questions
        if (rooms.size() == 1) {
            String murderRoom = rooms.get(0); // get the murder room
            if (!prevRoomQuestioned.toString().equals(murderRoom)) {
                return map.getRoom(murderRoom);
            }
            ArrayList<String> roomsNotPrev = new ArrayList<String>(Arrays.asList(Names.ROOM_NAMES));
            roomsNotPrev.remove(rooms.get(0).toString()); // remove previous room questioned in

            rooms = roomsNotPrev;
        }
        
        if (prevRoomQuestioned != null) {
            rooms.remove(prevRoomQuestioned.toString());
        }

        // find the neared room in the rooms list heuristically from the current position
        int minDistance = 1000;
        for(int i = 0; i < rooms.size(); i++){
            Room room = map.getRoom(rooms.get(i));
                int currMinDistance = heuristic(start, room.getDoorCoordinates(getClosestDoor(start, room)));
                if (currMinDistance < minDistance) {
                    nearest = map.getRoom(rooms.get(i));
                    minDistance = currMinDistance;
                }
        }

        return nearest;
    }

    public String getVersion () {
        return "0.1";   // change on a new release
    }

    public void notifyPlayerName(String playerName) {
        // Add your code here
    }

    public void notifyTurnOver(String playerName, String position) {
        // Add your code here
    }

    public void notifyQuery(String playerName, String query) {
        // Add your code here
    }

    public void notifyReply(String playerName, boolean cardShown) {
        // Add your code here
    }
}
