/**
 *
 * @author Denis Vera Iriyari
 */
package FollowTheTeamLeader;

import java.awt.Color;
import java.awt.Graphics2D;
import robocode.*;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FollowTheLeaderBot extends TeamRobot implements Serializable{
    private enum FaseRobot {HANDSHAKE, CONGA};
    private FaseRobot fase = FaseRobot.HANDSHAKE;       
    protected String teamLeader; // Nom del líder de l'equip
    protected String predecessor; // Nom del robot al qual segueix aquest robot
    protected double enemyX;
    protected double enemyY; 
    protected String enemyTarget; // L'enemic actual que l'equip està atacant
    protected int role = 0; // La posició del robot dins de l'equip (1 per TL, 2 pel segon, etc.)
    protected boolean clockwise = true; // Direcció del recorregut (horari/antihorari)
    private MyRobotStatus robotStatusGetter;
    private List<Map.Entry<Double, String>> sortedTeammatesAlive;
    private boolean movedToCC = false;
    private boolean leaderChosen = false;
    
    @Override
    public void run() {
        setColors(Color.YELLOW, Color.BLUE, Color.BLACK);
        //fase = FaseRobot.CONGA;
        //role=1;
        //goToClosestCorner();
        while(true)
        {
            switch(fase)
            {
                case HANDSHAKE:
                    if(!leaderChosen)chooseRandomLeader();
                    if(role == 1 && !movedToCC)
                    {
                        goToClosestCorner();
                        movedToCC = true;
                    }
                    if(role != 0)fase = FaseRobot.CONGA;
                    break;
                case CONGA:
                    if(role == 1) {
                        runTeamLeader();
                    }else {
                        runFollower();
                    }
                    break;
            }
            execute();
        }
    }
    
    private void goToClosestCorner() {
        double[][] corners = {
        {getBattleFieldWidth() * 0.1, getBattleFieldHeight() * 0.1},    
        {getBattleFieldWidth() * 0.9, getBattleFieldHeight() * 0.1},    
        {getBattleFieldWidth() * 0.1, getBattleFieldHeight() * 0.9},    
        {getBattleFieldWidth() * 0.9, getBattleFieldHeight() * 0.9} 
        };
        
        // Variables to track the closest corner
        double closestDistance = Double.MAX_VALUE;
        double targetX = 0;
        double targetY = 0;

        // Calculate the distance to each corner
        for (double[] corner : corners) {
            double distance = Point2D.distance(getX(), getY(), corner[0], corner[1]);
            if (distance < closestDistance) {
                closestDistance = distance;
                targetX = corner[0];
                targetY = corner[1];
            }
        }

        // Move to the closest corner
        goTo(targetX, targetY);
    }
    
    // Mètode per seleccionar un Team Leader aleatòriament
    private void chooseRandomLeader() {
        out.println("choosing random leader" + getName());
        if (role == 0) {         
            if(getName().equals("FollowTheTeamLeader.FollowTheLeaderBot* (1)")){
                out.println("leader choser");
                int randomIndex = (int) (Math.random() * getTeammates().length); // Selecció aleatòria
                String leaderName = getTeammates()[randomIndex]; // Obtain a teammate name
                try {      
                    broadcastMessage("CHOOSE_LEADER: " + leaderName);
                    
                    out.println("choose leader attempt " + leaderName);
                } catch (IOException ex) {
                    Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
                }                    
                

            }
        }
        leaderChosen = true;
        out.println("leader chosen");
    }

    // Mètode per a que el Team Leader lideri l'equip i segueixi la ruta
    public void runTeamLeader() {
        // Patrulla per les cantonades del camp de batalla
        moveInRectangle();

        // Cerca enemics i comparteix amb l'equip
        if(enemyTarget == null)
        {
            scanForEnemies();
        }

        // Cada 15 segons, canvia els rols dels robots i la direcció
        if (getTime() % (15 * 20) == 0) {
            changeRoles();
        }
    }

    // Move the Team Leader in a rectangular pattern along the borders of the arena
    private void moveInRectangle() {
        double[][] corners = {
        {getBattleFieldWidth() * 0.1, getBattleFieldHeight() * 0.1}, //BL   
        {getBattleFieldWidth() * 0.1, getBattleFieldHeight() * 0.9}, //TL   
        {getBattleFieldWidth() * 0.9, getBattleFieldHeight() * 0.9}, //TR
        {getBattleFieldWidth() * 0.9, getBattleFieldHeight() * 0.1}  //BR
        };
        
        // Find the closest corner to the robot's current position
        int currentCornerIndex = getClosestCorner(corners);
        
        while (true) {
        // Move to the current corner
        double targetX = corners[currentCornerIndex][0];
        double targetY = corners[currentCornerIndex][1];
        goTo(targetX, targetY);

        // Update the corner index for the next corner based on the direction
        if (clockwise) {
            currentCornerIndex = (currentCornerIndex + 1) % 4;  // Move to next corner clockwise
        } else {
            currentCornerIndex = (currentCornerIndex - 1 + 4) % 4;  // Move to next corner counterclockwise
        }
    }
    }
    
    private int getClosestCorner(double[][] corners) {
        double currentX = getX();
        double currentY = getY();
        int closestCorner = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < corners.length; i++) {
            double distance = Point2D.distance(currentX, currentY, corners[i][0], corners[i][1]);
            if (distance < minDistance) {
                minDistance = distance;
                closestCorner = i;
            }
        }

        return closestCorner;
    }

    // Cerca enemics i comparteix amb l'equip
    private void scanForEnemies() {
        turnRadarRight(360); // Fes un escaneig complet
        // Assuming this is the intended way to handle scanned events, it should be in onScannedRobot
    }
    
    @Override
    public void onScannedRobot(ScannedRobotEvent event)
    {
        if(getName().equals(teamLeader))
        {
            // Check if the scanned robot is NOT a teammate
            if (!isTeammate(event.getName())) 
            {
                try {
                    // Broadcast the enemy's coordinates (x, y) to the team
                    double enemyBearing = getHeading() + event.getBearing(); // Calculate the absolute bearing
                    enemyX = getX() + event.getDistance() * Math.sin(Math.toRadians(enemyBearing));
                    enemyY = getY() + event.getDistance() * Math.cos(Math.toRadians(enemyBearing));
                    
                    // Broadcast message with enemy coordinates
                    broadcastMessage("ENEMY_SPOTTED:" + event.getName() + ":" + enemyX + ":" + enemyY);
                } catch (IOException ex) {
                    Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    
    // Canvia els rols dels robots i la direcció de la ruta
    private void changeRoles() {
        clockwise = !clockwise; // Canviar la direcció del moviment
        role = getTeammates().length + 2 - role;
         
        Collections.reverse(sortedTeammatesAlive);
        teamLeader = sortedTeammatesAlive.get(0).getValue();
        
        if(getName().equals(sortedTeammatesAlive.get(0).getValue()))
        {
            predecessor = null;
        }
        for(int i=1; i<sortedTeammatesAlive.size(); i++)
        {
            if(getName().equals(sortedTeammatesAlive.get(i).getValue()))
            {
                predecessor = sortedTeammatesAlive.get(i-1).getValue();
            }
        }
    }

    // Mètode per als robots seguidors
    public void runFollower() {
        if (enemyTarget != null) {
            aimAndShoot(enemyX, enemyY);
        }

        if (predecessor != null) {
            // Anar cap al predecessor (el robot immediatament anterior)
            MyRobotStatus predecessorStatus = getRobotStatus(predecessor);
            if (predecessorStatus != null) {
                goTo(predecessorStatus.getX(), predecessorStatus.getY());
            }
        }

        // Canvi de rols cada 15 segons
        if (getTime() % (15 * 20) == 0) {
            changeRoles();
        }
    }

    // Moure's a un punt concret (x, y) evitant obstacles
    public void goTo(double x, double y) {
        double dx = x - getX();
        double dy = y - getY();
        double angleToTarget = Math.toDegrees(Math.atan2(dx, dy));
        turnRight(normalizeBearing(angleToTarget - getHeading()));
        ahead(Point2D.distance(getX(), getY(), dx, dy));
    }
    
    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        String deadRobotName = event.getName(); // Get the name of the dead robot
        boolean deadRobotAlly = false;
        String newPredecessor = null;
        
        // Check if the dead robot is a teammate
        for (String teammate : getTeammates()) {
            if (teammate.equals(deadRobotName)) {
                // The dead robot is a teammate
                Iterator<Map.Entry<Double, String>> iterator = sortedTeammatesAlive.iterator();
                deadRobotAlly = true;
                
                while (iterator.hasNext()) {
                    Map.Entry<Double, String> entry = iterator.next();
                    if (entry.getValue().equals(deadRobotName)) { // Compare the teammate's name
                        iterator.remove(); // Remove the entry if the name matches
                        
                        if(entry.getValue().equals(getName()))
                        {
                            predecessor = newPredecessor;
                        }
                        break; // Exit after removing to avoid modifying the list while iterating
                    }
                    
                    newPredecessor = entry.getValue();
                }
                
                break; // No need to check further once we've found the teammate
            }
        }
        if(!deadRobotAlly && enemyTarget != null)
        {
            enemyTarget = null;
        }
    }

    // Funció per normalitzar l'angle
    public double normalizeBearing(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
    public void aimAndShoot(double enemyX, double enemyY) {
        // Calculate the difference in X and Y coordinates
        double deltaX = enemyX - getX();
        double deltaY = enemyY - getY();

        // Calculate the angle to the enemy (in degrees)
        double angleToEnemy = Math.toDegrees(Math.atan2(deltaX, deltaY));

        // Normalize the bearing (the angle between the gun heading and the target)
        double gunTurn = normalizeBearing(angleToEnemy - getGunHeading());
        
        // Turn the gun to face the target
        turnGunRight(gunTurn);
        
        // Calculate the distance using the Euclidean distance formula
        double distanceToEnemy = Math.sqrt(Math.pow(enemyX - getX(), 2) + Math.pow(enemyY - getY(), 2));
        double firePower = Math.min(3, Math.max(0.1, 500 / distanceToEnemy));
        // Once the gun is aimed at the target, shoot
        fire(firePower);  // You can change the firepower depending on strategy
    }
    // Establir la jerarquia dels robots en funció de la distància al TL
    public void establishHierarchy() {
        if (role == 1) {
            try {
                List<Double> distances = new ArrayList<>();
                for (String teammate : getTeammates()) {
                    MyRobotStatus status = getRobotStatus(teammate);
                    if (status != null) {
                        distances.add(Point2D.distance(getX(), getY(), status.getX(), status.getY()));
                    }
                }
                assignRolesBasedOnDistance(distances);
            } catch (Exception e) {
                out.println("Error en establir la jerarquia.");
            }
        }
    }

    // Assignar rols segons distància
    public void assignRolesBasedOnDistance(List<Double> distances) {
        Map<Double, String> distanceMap = new HashMap<>();
        distanceMap.put(0.0,getName());
        for (int i = 0; i < distances.size(); i++) {
            distanceMap.put(distances.get(i), getTeammates()[i]);
        }

        sortedTeammatesAlive = new ArrayList<>(distanceMap.entrySet());
        Collections.sort(sortedTeammatesAlive, Map.Entry.comparingByKey()); // Ordenar per distància

        for (int i = 1; i < sortedTeammatesAlive.size(); i++) {
            String robotName = sortedTeammatesAlive.get(i).getValue();
            int assignedRole = i + 1;
            try {
                broadcastMessage("ASSIGN_ROLE:" + robotName + ":" + assignedRole);
            } catch (IOException ex) {
                Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        try {
            sendMessage("SORTED_TEAMMATES_LIST", (Serializable) sortedTeammatesAlive);
        } catch (IOException ex) {
            Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // Obtenir l'estat dels robots aliats
    public List<MyRobotStatus> getOthersRobotStatuses() {
        List<MyRobotStatus> statuses = new ArrayList<>();
        for (String teammate : getTeammates()) {
            MyRobotStatus status = getRobotStatus(teammate);
            if (status != null) {
                statuses.add(status);
            }
        }
        return statuses;
    }

    // Mètode per obtenir l'estat d'un robot aliat
    private MyRobotStatus getRobotStatus(String teammateName) {
        try {
            broadcastMessage("REQUEST_STATUS:" + teammateName);  // Send a status request
        } catch (IOException e) {
            out.println("Failed to send status request to " + teammateName);
        }
        // Busy-wait loop for response
        long startTime = System.currentTimeMillis();
        while (robotStatusGetter == null) {
            if (System.currentTimeMillis() - startTime > 5000) {  // Timeout after 5 seconds
                out.println("Timeout waiting for status from " + teammateName);
                return null;
            }
        }
        MyRobotStatus tempStatus = robotStatusGetter;
        robotStatusGetter = null;
        return tempStatus;
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        out.println("message received");
        if (event.getMessage() instanceof String) {
            String message = (String) event.getMessage();
            
            
            
            if (message.startsWith("CHOOSE_LEADER:")) {
                
                
                
                String chosenLeader = message.split(":")[1];
                
                out.println("leader received:" + chosenLeader.substring(1));
                
                teamLeader = chosenLeader;
                
                if (getName().equals(chosenLeader)) {
                    out.println("I am the leader");
                    role = 1; // Aquest robot serà el Team Leader
                    establishHierarchy();
                }              
            } else if (message.startsWith("ENEMY_SPOTTED:")) {
                String[] parts = message.split(":");
                if(enemyTarget == null) enemyTarget = parts[1];
                enemyX = Double.parseDouble(parts[2]);
                enemyY = Double.parseDouble(parts[3]);
            } else if (message.startsWith("REQUEST_STATUS:")) {
                if(getName().equals(message.split(":")[1]))
                {
                    MyRobotStatus status = new MyRobotStatus(getName(), getX(), getY());
                    try {
                        sendMessage(event.getSender(), status);  // Send back status to requester                    
                    } catch (IOException ex) {
                        Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } else if (message.startsWith("ASSIGN_ROLE:")) {
                String[] parts = message.split(":");
                if(parts[1].equals(getName())) role = Integer.parseInt(parts[2]);
            }
        } else if(event.getMessage() instanceof MyRobotStatus){
            robotStatusGetter = (MyRobotStatus) event.getMessage();
        } /*else if(event.getMessage() instanceof List<?>){
            if(role != 1)sortedTeammatesAlive = (List<Map.Entry<Double, String>>) event.getMessage();
        }*/
    }
    
    @Override
    public void onPaint(Graphics2D g)
    {   
        if(getName().equals(teamLeader))
        {
            int r = 60;
            g.setColor(Color.green);
            g.drawOval((int) getX()-r, (int)getY()-r, 2*r, 2*r);
        }
    }
    
    public class MyRobotStatus implements Serializable
    {
        private double x, y;
        private String name;
        
        public MyRobotStatus(String name, double x, double y)
        {
            this.name = name;
            this.x = x;
            this.y = y;
        }
        
        public String getName()
        {
            return name;
        }
        
        public double getX()
        {
            return x;
        }
        
        public double getY()
        {
            return y;
        }
    }
}
