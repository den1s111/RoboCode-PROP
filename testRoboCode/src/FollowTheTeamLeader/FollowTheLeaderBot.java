/**
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
    protected String teamLeader; //Name of the leader
    protected String predecessor; //Name of the robot this robot is following (null if i am leader)
    protected double enemyX; //Current enemy's X coord
    protected double enemyY; //Current enemy's Y coord
    protected String enemyTarget; //The current target which the followers will shoot
    protected int role = 0; //Robot's role inside the team
    protected boolean clockwise = true; //Pathing direction
    private MyRobotStatus robotStatusGetter; //Var to handle the receiving of a robot's status
    private List<Map.Entry<Double, String>> sortedTeammatesAlive; //Teammates list sorted by the initial distance to the leader
    private boolean leaderChosen = false; //True if the leader is chosen, false otherwise
    private int teammateCounter = 0; //Var to handle the hierarchy establishment
    private boolean hierarchyEstablished = false; //True if the hierarchy is established, false otherwise
    private List<Double> distances = new ArrayList<>(); //Saves the initial distances between followers and leader
    private long lastRoleChangeTime = 0; //To keep track of the last time roles were changed
    private static final int ROLE_CHANGE_INTERVAL = 450; //15 seconds in ticks (30 ticks/second)
    private int deadAllies = 0; //Saves the number of ally robots that have died during battle
    
    /**
     * Handles the main rules of the robot changing between states
     * when needed according to the exercise requirements going through
     * the hierarchy establishing fase to getting to the closest corner
     * all the way through the 'CONGA' fase in which everything is handled
     * by the 'run' functions
     */
    @Override
    public void run() 
    {
        setColors(Color.ORANGE, Color.BLACK, Color.BLACK);
        while(true)
        {
            switch(fase)
            {
                case HANDSHAKE:
                    if(!leaderChosen)chooseRandomLeader();
  
                    if(role == 1 && !hierarchyEstablished)
                    {
                        MyRobotStatus tempStatus = getRobotStatus(getTeammates()[teammateCounter]);
                        while(tempStatus == null){}
                        
                        double distance = Point2D.distance(getX(), getY(), tempStatus.getX(), tempStatus.getY());
                        distances.add(distance);
                        out.println("Teammate " + tempStatus.getName() + " distance registered: " + distance);
                        
                        teammateCounter++;
                        if(teammateCounter == getTeammates().length)
                        {
                            assignRolesBasedOnDistance(distances);
                            hierarchyEstablished = true;
                            out.println("TEAMMATES SIZE: " + getTeammates().length);
                        }
                    }

                    if(hierarchyEstablished){
                        if(role==1) goToClosestCorner();
                        fase = FaseRobot.CONGA;
                    }
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
    
    //Randomly chooses a leader for the team
    private void chooseRandomLeader() 
    {
        out.println("----CHOOSING RANDOM LEADER----");
        if (role == 0) 
        {         
            //Only one robot has access to this function so that not evry robot
            //decides in a different leader for the team
            if(getName().equals("FollowTheTeamLeader.FollowTheLeaderBot* (1)"))
            {
                out.println("----I AM THE LEADER CHOSER----");
                int randomIndex = (int) (Math.random() * getTeammates().length); // Selecció aleatòria
                String leaderName = getTeammates()[randomIndex]; // Obtain a teammate name
                try {      
                    broadcastMessage("CHOOSE_LEADER:" + leaderName);
                } catch (IOException ex) {
                    Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
                }                    
            }
        }
        leaderChosen = true;
        out.println("----LEADER CHOSEN----");
    }
    
    //Moves the leader to the closest corner in the HANDSHAKE fase
    private void goToClosestCorner() 
    {
        double[][] corners = {
        {getBattleFieldWidth() * 0.1, getBattleFieldHeight() * 0.1},    
        {getBattleFieldWidth() * 0.9, getBattleFieldHeight() * 0.1},    
        {getBattleFieldWidth() * 0.1, getBattleFieldHeight() * 0.9},    
        {getBattleFieldWidth() * 0.9, getBattleFieldHeight() * 0.9} 
        };
        
        //Variables to track the closest corner
        double closestDistance = Double.MAX_VALUE;
        double targetX = 0;
        double targetY = 0;

        //Calculate the distance to each corner
        for (double[] corner : corners) 
        {
            double distance = Point2D.distance(getX(), getY(), corner[0], corner[1]);
            if (distance < closestDistance) 
            {
                closestDistance = distance;
                targetX = corner[0];
                targetY = corner[1];
            }
        }
        //Move to the closest corner
        goTo(targetX, targetY);
    }

    /**
     * Handles the leader's behavior
     */
    public void runTeamLeader() 
    {
        moveInRectangle();  
    }

    /**
     * Handles the follower's behavior
     */
    public void runFollower() 
    {
        //If a target is set, shoot at it
        if (enemyTarget != null) 
        {
            aimAndShoot(enemyX, enemyY);
        }

        //Follow the predecessor
        MyRobotStatus predecessorStatus = getRobotStatus(predecessor);
        if (predecessorStatus != null) 
        {
            double distanceToPredecessor = Point2D.distance(getX(), getY(), predecessorStatus.getX(), predecessorStatus.getY());
            //Comment this print since it overflows the logs
            //out.println("----PREDECESSOR NAME: " + predecessor + "----");
            
            //Security threshold
            if(distanceToPredecessor > 200) goTo(predecessorStatus.getX(), predecessorStatus.getY());
            else setAhead(0);
        }
        //Constsntly check the time in order to make the role swap
        checkAndChangeRoles();
    }
    
    //Changes the robot's roles and the pathing direction
    private void changeRoles() 
    {
        out.println("----CHANGING ROLES----");
        //Change the direction of the movement
        clockwise = !clockwise; 
        //Reassign roles with a function that will always set the opposite role
        //to the original one
        role = (getTeammates().length - deadAllies) + 2 - role; 

        //Reassemble the list by reversing it's order
        Collections.reverse(sortedTeammatesAlive);
        //Reassign the team leader
        teamLeader = sortedTeammatesAlive.get(0).getValue();
        
        //Reassign predecessors
        if(getName().equals(sortedTeammatesAlive.get(0).getValue()))
        {
            predecessor = null;
            out.println("----I AM THE NEW LEADER----");
        }
        for(int i=1; i<sortedTeammatesAlive.size(); i++)
        {
            if(getName().equals(sortedTeammatesAlive.get(i).getValue()))
            {
                out.println("----MY NEW ROLE: "+ role + "----");
                predecessor = sortedTeammatesAlive.get(i-1).getValue();
            }
        }
    }
    
    //Checks the time to make the roles swap in the ideal timing
    //Generated with ChatGPT
    private void checkAndChangeRoles() 
    {
        long currentTime = getTime(); //Get the current game time (in ticks)

        if (currentTime - lastRoleChangeTime >= ROLE_CHANGE_INTERVAL) {
            changeRoles(); //Call the function to change roles
            lastRoleChangeTime = currentTime; //Update the last role change time
        }
    }
    
    //Move the Team Leader in a rectangular pattern along the borders of the arena
    private void moveInRectangle() 
    {
        
        double[][] corners = {
        {getBattleFieldWidth() * 0.1, getBattleFieldHeight() * 0.1}, //BL   
        {getBattleFieldWidth() * 0.1, getBattleFieldHeight() * 0.9}, //TL   
        {getBattleFieldWidth() * 0.9, getBattleFieldHeight() * 0.9}, //TR
        {getBattleFieldWidth() * 0.9, getBattleFieldHeight() * 0.1}  //BR
        };
        
        //Find the closest corner to the robot's current position
        int currentCornerIndex = getClosestCorner(corners);
        
        //While being the leader, it will constantly move in rectangle with its direction
        //depending on the clockwise var while constantly scanning for enemies for the
        //followers to have a target to shoot or to constantly send the target's coords
        while (role == 1) 
        {             
            //Move to the current corner
            double targetX = corners[currentCornerIndex][0];
            double targetY = corners[currentCornerIndex][1];
            goTo(targetX, targetY);

            scanForEnemies();

            //Update the corner index for the next corner based on the direction
            if (clockwise) 
            {
                currentCornerIndex = (currentCornerIndex + 1) % 4;  //Move to next corner clockwise
            } else 
            {
                currentCornerIndex = (currentCornerIndex - 1 + 4) % 4;  //Move to next corner counterclockwise
            }
            checkAndChangeRoles();
        }
    }
    
    //Returns the closes corner index
    //Function used by the leader in the HANDSHAKE fase
    private int getClosestCorner(double[][] corners) 
    {
        double currentX = getX();
        double currentY = getY();
        int closestCorner = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < corners.length; i++) 
        {
            double distance = Point2D.distance(currentX, currentY, corners[i][0], corners[i][1]);
            if (distance < minDistance) 
            {
                minDistance = distance;
                closestCorner = i;
            }
        }
        return closestCorner;
    }

    //Look for enemies
    private void scanForEnemies() 
    {
        turnRadarRight(360); //
    }

    /**
     * Handles what to do in case a robot is scanned
     * Only the leader will scan robot's and set a target
     * @param event
     */
    @Override
    public void onScannedRobot(ScannedRobotEvent event)
    {   
        if(role == 1)
        {
            // Check if the scanned robot is NOT a teammate
            if (!isTeammate(event.getName())) 
            {
                //Only send enemy's status if we are not targeting anyone or if it is the enemy we are
                //already targeting, we will only swap on to the next enemy once the current one dies
                if(enemyTarget == null || enemyTarget == event.getName())
                {
                    try {
                        //Save enemy's status
                        double enemyBearing = getHeading() + event.getBearing(); // Calculate the absolute bearing
                        enemyX = getX() + event.getDistance() * Math.sin(Math.toRadians(enemyBearing));
                        enemyY = getY() + event.getDistance() * Math.cos(Math.toRadians(enemyBearing));
                        enemyTarget = event.getName();
                        
                        aimAndShoot(enemyX, enemyY);
                        
                        //Broadcast message with enemy's status
                        out.println("ENEMY SPOTTED!");
                        broadcastMessage("ENEMY_SPOTTED:" + event.getName() + ":" + enemyX + ":" + enemyY);
                    } catch (IOException ex) {
                        Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
    }

    /**
     * When hit by a robot, we aim at him and shoot (id it is not ally), after that, we redesign the pathing
     * to the furthest corner by moving back and around the enemy (only the leader does this)
     * Copied from my 'TimidinRobot' class (Slightly modified)
     * @param event
     */
    @Override
    public void onHitRobot(HitRobotEvent event)
    {
        double bearing = event.getBearing();
        if(!isTeammate(event.getName()))
        {
            turnGunRight(normalizeBearing(getHeading() + bearing - getGunHeading()));
            fire(20);
        }
        //If the enemy is blocking our trajectory, we move back and around
        //If the enemy collision was not disturbing the pathing we just keep
        //moving to the corner
        if(bearing > -90 && bearing <= 90 && role == 1)
        {
            back(40);
            moveAroundEnemy(-100);            
        }
    }

    /**
     * Method to move around the enemy in order to avoid being blocked while
     * trying to reach the furthest corner
     * Copied from my 'TimidinRobot' class
     * @param distance
     */
    public void moveAroundEnemy(double distance) 
    {
        //Check the distance from the walls to decide which side to move
        double battlefieldWidth = getBattleFieldWidth();
        double battlefieldHeight = getBattleFieldHeight();
        double margin = 50; //Safety margin to avoid hitting the wall

        //If close to the right wall, move left, otherwise move right
        if (getX() > battlefieldWidth - margin) {
            //Turn slightly left to avoid the right wall and move backward/forward
            turnRight(-45); //Turn left 45 degrees
        } else if (getX() < margin) {
            //Turn slightly right to avoid the left wall and move backward/forward
            turnRight(45); //Turn right 45 degrees
        } else if (getY() > battlefieldHeight - margin) {
            //Turn slightly left if too close to the top wall
            turnRight(-45);
        } else if (getY() < margin) {
            //Turn slightly right if too close to the bottom wall
            turnRight(45);
        } else {
            //If not near walls, choose a random side to move around the enemy
            turnRight((Math.random() > 0.5) ? 45 : -45);
        }

        //Move in the chosen direction with a backward/forward distance
        ahead(distance);
    }

    /**
     * Move to a certain coord
     * Generated with chatGPT
     * @param x
     * @param y
     */
    public void goTo(double x, double y) 
    {
        double dx = x - getX();
        double dy = y - getY();
        double angleToTarget = Math.toDegrees(Math.atan2(dx, dy)); //Calculate angle to target
        double distance = Math.hypot(dx, dy); //Calculate distance to target

        //Turn towards the target in one motion
        double turnAngle = normalizeBearing(angleToTarget - getHeading());
        turnRight(turnAngle); //Only turn once, not inside the loop

        //Move towards the target in one smooth motion
        ahead(distance); //Move the entire distance in one go
    }

    /**
     * Handles any robot's death (ally and enemy)
     * @param event
     */
    @Override
    public void onRobotDeath(RobotDeathEvent event) 
    {
        String deadRobotName = event.getName(); //Get the name of the dead robot     
        
        //If the robot is an enemy and it was the robot we were targeting, we reset the enemyTarget var
        //in order to target a new enemy
        if(!isTeammate(deadRobotName))
        {
            out.println("===-ENEMY ROBOT DEAD-===");
            if(deadRobotName.equals(enemyTarget)) enemyTarget = null;
        }
        //If an ally died, we update the list that stores the teammates sorted while still keeping the
        //original logic and update every robot's rol and predecessor
        else
        {
            deadAllies += 1;
            out.println("===-ALLY ROBOT DEAD-===");
            boolean deadRobotAllyRemoved = false;
            String newPredecessor = null;
            Iterator<Map.Entry<Double, String>> iterator = sortedTeammatesAlive.iterator();
            
            while(iterator.hasNext())
            {
                out.println("ITERATION IN THE ALLY'S DEATH");
                
                Map.Entry<Double, String> entry = iterator.next();
                if (entry.getValue().equals(deadRobotName)) 
                { //Compare the teammate's name
                    out.println("DEAD ALLY REMOVED FROM THE LIST");
                    iterator.remove(); //Remove the entry if the name matches
                    deadRobotAllyRemoved = true;
                    
                    //Update the predecessor in so that it fits with the robot that now has no predecessor (it died)
                    if(entry.getValue().equals(getName()))
                    {
                        predecessor = newPredecessor;
                    }
                }               
                newPredecessor = entry.getValue();
                
                //Update the role of the robots that are next tot he dead one in the list (the precious ones remain the same)
                if(deadRobotAllyRemoved && getName().equals(entry.getValue()))
                {
                    role--;
                    out.println("----MY NEW ROLE AFTER THE ALLY'S DEATH: " + role + "----");
                    if(role == 1) predecessor = null;
                    else predecessor = sortedTeammatesAlive.get(role-2).getValue();
                    out.println("----MY NEW PREDECESSOR AFTER THE ALLY'S DEATH: " + predecessor + "----");
                }              
            }
            teamLeader = sortedTeammatesAlive.get(0).getValue();
        }
    }

    /**
     * Normalize angle
     * Generated with ChatGPT
     * @param angle
     * @return normalized angle
     */
    public double normalizeBearing(double angle) 
    {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    /**
     * Basic function to aim and shoot at the enemy target decided by the leader
     * Generated with ChatGPT
     * @param enemyX
     * @param enemyY
     */
    public void aimAndShoot(double enemyX, double enemyY) 
    {
        //Calculate the difference in X and Y coordinates
        double deltaX = enemyX - getX();
        double deltaY = enemyY - getY();

        //Calculate the angle to the enemy (in degrees)
        double angleToEnemy = Math.toDegrees(Math.atan2(deltaX, deltaY));

        //Normalize the bearing (the angle between the gun heading and the target)
        double gunTurn = normalizeBearing(angleToEnemy - getGunHeading());
        
        //Turn the gun to face the target
        turnGunRight(gunTurn);
        
        //Calculate the distance using the Euclidean distance formula
        double distanceToEnemy = Math.sqrt(Math.pow(enemyX - getX(), 2) + Math.pow(enemyY - getY(), 2));
        double firePower = Math.min(3, Math.max(0.1, 500 / distanceToEnemy));
        //Once the gun is aimed at the target, shoot
        fire(firePower);  //You can change the firepower depending on strategy
    }

    /**
     * Roles are assigned to every teammate based on it's initial distance to the leader
     * @param distances
     */
    public void assignRolesBasedOnDistance(List<Double> distances) 
    {
        out.println("----ASSIGNING ROLES BASED ON DISTANCE----");
        Map<Double, String> distanceMap = new HashMap<>();
        distanceMap.put(0.0,getName());
        for (int i = 0; i < distances.size(); i++) 
        {
            distanceMap.put(distances.get(i), getTeammates()[i]);
        }

        sortedTeammatesAlive = new ArrayList<>(distanceMap.entrySet());
        //Sorted  by distance (closest to the leader -> furthest to the leader)
        Collections.sort(sortedTeammatesAlive, Map.Entry.comparingByKey());
        String sortedTeammatesToString = ("SORTED_TEAMMATES_LIST:" + sortedTeammatesAlive.get(0).getKey() 
                + "," + sortedTeammatesAlive.get(0).getValue());

        for (int i = 1; i < sortedTeammatesAlive.size(); i++) 
        {
            String robotName = sortedTeammatesAlive.get(i).getValue();
            String tempPredecessor = sortedTeammatesAlive.get(i - 1).getValue();
            int assignedRole = i + 1;
            
            sortedTeammatesToString += (":" + sortedTeammatesAlive.get(i).getKey() 
                    + "," + sortedTeammatesAlive.get(i).getValue());
            
            //Assigning role for every teammate
            try {
                broadcastMessage("ASSIGN_ROLE:" + robotName + ":" + assignedRole + ":" + tempPredecessor);
            } catch (IOException ex) {
                Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        //Send the teammates list sorted to the follower robots
        try {
            broadcastMessage(sortedTeammatesToString);
        } catch (IOException ex) {
            Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Method that obtains an specific ally's status
    private MyRobotStatus getRobotStatus(String teammateName) 
    {
        //Comment this print since it overflows the logs
        //out.println("----REQUESTING " + teammateName + " STATUS----");
        
        try {
            broadcastMessage("REQUEST_STATUS:" + teammateName);  //Send a status request
        } catch (IOException e) {
            out.println("Failed to send status request to " + teammateName);
        }
        
       //Wait for the response, with a timeout
        long startTime = System.currentTimeMillis();
        long timeout = 3000;  //3 seconds timeout for waiting for the response

        //Wait until we receive the robot status
        while (robotStatusGetter == null) 
        {
            if (System.currentTimeMillis() - startTime > timeout) 
            {
                out.println("----TIMEOUT WAITING FOR " + teammateName + "'S STATUS----");
                return null;  //Return null if timed out
            }
            execute();  //Keep the robot active while waiting
        }

        //Reset robotStatusGetter after using it
        MyRobotStatus status = robotStatusGetter;
        robotStatusGetter = null;
        
        //Comment this print since it overflows the logs
        //out.println("----RECEIVED ROBOT " + teammateName + " COORDS: (" + status.getX() + "," + status.getY() + ")----");
        return status;
    }
    
    /**
     * Handles every possible message receivied
     * @param event
     */
    @Override
    public void onMessageReceived(MessageEvent event) 
    {
        //Comment this print since it overflows the logs
        //out.println("----MESSAGE RECEIVED----: " + event.getMessage());
        String message = (String) event.getMessage();
        
        if (event.getMessage() instanceof String) 
        {
            //Team leader chosen
            if (message.startsWith("CHOOSE_LEADER:")) 
            {
                teamLeader = message.split(":")[1];
                if ((getName()).equals(message.split(":")[1])) 
                {
                    out.println("----I AM THE LEADER----");
                    role = 1; //Role assigned to the leader
                }
            }
            //The robot requested by the message will send it's status
            else if (message.startsWith("REQUEST_STATUS:")) 
            {
                if(getName().equals(message.split(":")[1]))
                {
                    try {
                        //Comment this print since it overflows the logs
                        //out.println("----SENDING MY STATUS----");
                        broadcastMessage("MY_STATUS:" + getName() + ":" + getX() + ":" + getY());  //Send back status to requester                    
                    } catch (IOException ex) {
                        Logger.getLogger(FollowTheLeaderBot.class.getName()).log(Level.SEVERE, null, ex);
                    }                   
                }
            }
            //Receive the status of a certain robot that was asked to share it
            else if (message.startsWith("MY_STATUS:")) 
            {
                //Comment this print since it overflows the logs
                //out.println("----STATUS RECEIVED----");
                String[] parts = message.split(":");
                //Create a new MyRobotStatus object with the received data
                MyRobotStatus status = new MyRobotStatus(parts[1], Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                //Update robotStatusGetter
                robotStatusGetter = status;
            }
            //Assign role to the followers in the HANDSHAKE fase
            else if (message.startsWith("ASSIGN_ROLE:")) 
            {
                out.println("----ASSIGNING ROLE PROCESS----");
                hierarchyEstablished = true;
                String[] parts = message.split(":");
                if(getName().equals(parts[1]))
                {
                    role = Integer.parseInt(parts[2]);
                    predecessor = parts[3];
                    out.println("----ROLE AND PREDECESSOR SUCCESSFULLY ASSIGNED----");
                    out.println("----MY ROLE: " + role + "----");
                }
            }
            //Save the enemy target coords in order to shoot at it
            else if (message.startsWith("ENEMY_SPOTTED:")) 
            {
                String[] parts = message.split(":");
                enemyTarget = parts[1];
                enemyX = Double.parseDouble(parts[2]);
                enemyY = Double.parseDouble(parts[3]);
            }
            //Receive a list with all the teammates sorted by the initial distance to the leader
            else if (message.startsWith("SORTED_TEAMMATES_LIST:")) 
            {
                String[] parts = message.split(":");
                sortedTeammatesAlive = new ArrayList<>();
                for(int i=1; i< parts.length; i++)
                {                   
                    sortedTeammatesAlive.add(new AbstractMap.SimpleEntry<>(Double.parseDouble(parts[i].split(",")[0]), parts[i].split(",")[1]));
                }
                out.println("----SORTED TEAMMATES LIST RECEIVED----");
            }
        }
    }

    /**
     * Paints a radius to enlight only the leader
     * @param g
     */
    @Override
    public void onPaint(Graphics2D g)
    {   
        if(role == 1)
        {
            int r = 60;
            g.setColor(Color.green);
            g.drawOval((int) getX()-r, (int)getY()-r, 2*r, 2*r);
        }
    }
    
    /**
     * Class to save and share a robot's status
     */
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

        public void setX(double x) 
        {
            this.x = x;
        }

        public void setY(double y)
        {
            this.y = y;
        }

        public void setName(String name) 
        {
            this.name = name;
        }
        
    }
}
