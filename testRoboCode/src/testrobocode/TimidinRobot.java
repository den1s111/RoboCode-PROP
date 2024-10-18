package testrobocode;
import robocode.*;
import java.awt.geom.Point2D;
import java.awt.Color;
import java.awt.Graphics2D;
/**
 *
 * @author Denis Vera Iriyari
 */
public class TimidinRobot extends AdvancedRobot{
    //Robot possible states defined
    private enum StateRobot {SEARCHING, MOVING, SHOOTING};
    private StateRobot state;
    
    private double targetDirection;
    private double furthestX, furthestY;
    
    
    @Override
    public void run()
    {
        //Set colors
        setColors(Color.YELLOW, Color.BLUE, Color.BLACK);
        //Start in searching/scanning state
        state = StateRobot.SEARCHING;
        
        while(true)
        {
            switch(state)
            {
                case SEARCHING:
                    lookForEnemy();
                    break;
                case MOVING:
                    moveToCorner();
                    break;
                case SHOOTING:
                    //If robot is in shooting mode, it keeps scanning for enemies
                    lookForEnemy();
            }
            //Execute every set action
            execute();
        }
    }
    
    //Fase 0: Detect enemy in order to calculate the furthest corner
    private void lookForEnemy()
    {
        //Fully rotate the radar to the right direction in order to look for enemies
        turnRadarRight(360);
    }
    
    @Override
    public void onScannedRobot(ScannedRobotEvent event)
    {
        //Calculate enemy's direction
        targetDirection = getHeading() + event.getBearing();
        double distanceToEnemy = event.getDistance();
        
        //FASE 2
        //If the robot's state is on SHOOTIING mode, it will aim the scanned robot and shoot
        //with a power based on the distance between the robots, then it will keep looking for enemies
        if(state == StateRobot.SHOOTING)
        {
            turnGunRight(normalizeBearing(targetDirection - getGunHeading()));
            double firePower = Math.min(3, Math.max(0.1, 500 / distanceToEnemy));
            fire(firePower);
            lookForEnemy();
        }
        
        //FASE 0
        //If the robot's state is not SHOOTING it can only mean it is SEARCHING since it is completing
        //the fase 0 in which we look for the enemy and get the distance to the furthest corner from it
        else
        {
            //Calculate the furthest corner from the detected robot
            FurthestCorner(targetDirection, distanceToEnemy);

            //Change robot's state to MOVING
            state = StateRobot.MOVING;
        } 
    }
    
    //Returns the furthest corner from the enemy
    private void FurthestCorner(double direction, double distance)
    {
        //Obtain the enemy's coords
        double enemyX = getX() + distance * Math.sin(Math.toRadians(direction));
        double enemyY = getY() + distance * Math.cos(Math.toRadians(direction));
        
        //Set the furthest corner coords
        furthestX = (enemyX < getBattleFieldWidth() / 2) ? getBattleFieldWidth() : 0;
        furthestY = (enemyY < getBattleFieldHeight() / 2) ? getBattleFieldHeight() : 0;
    }
    
    //FASE 1
    //Move the robot to the furthest corner
    private void moveToCorner()
    {
        double angleToCorner = calculateDirection(furthestX, furthestY);

        //Turn robot and move forward to the corner
        turnRight(angleToCorner - getHeading());  
        ahead(Point2D.distance(getX(), getY(), furthestX, furthestY));
        
        //If we haven't reached the X or Y coordinate yet, and we hit a wall
        //we call handleWallCollision to solve pathing bugs so that the robot
        //successfully makes it to the desired destination
        if (Math.abs(getX() - furthestX) > 30 || Math.abs(getY() - furthestY) > 30) {
            handleWallCollision();
        }

        //If the robot reaches the corner, change the state to SHOOTING
        if (Math.abs(getX() - furthestX) < 30 && Math.abs(getY() - furthestY) < 30) {
            state = StateRobot.SHOOTING;
        }    
    }
    
    //This method handles the situation when the robot gets stuck near a wall
    private void handleWallCollision() {
        //If the robot has not yet reached the X coordinate, move towards the X coordinate
        if (Math.abs(getX() - furthestX) > 30) {
            double angleToX = calculateDirection(furthestX, getY());
            turnRight(normalizeBearing(angleToX - getHeading()));
            ahead(Math.abs(getX() - furthestX));  //Move only along the X axis
        }

        //If the robot has not yet reached the Y coordinate, move towards the Y coordinate
        if (Math.abs(getY() - furthestY) > 30) {
            double angleToY = calculateDirection(getX(), furthestY);
            turnRight(normalizeBearing(angleToY - getHeading()));
            ahead(Math.abs(getY() - furthestY));  //Move only along the Y axis
        }
    }
    
    //Calculate the angle in which the robot has to move in order to get to the corner
    private double calculateDirection(double targetX, double targetY)
    {
        double tempX = targetX - getX();
        double tempY = targetY - getY();
        return Math.toDegrees(Math.atan2(tempX, tempY));
    }
    
    //When hit by a robot, we aim at him and shoot, after that, we redesign the pathing
    //to the furthest corner by moving back and around the enemy
    @Override
    public void onHitRobot(HitRobotEvent event)
    {
        double bearing = event.getBearing();
        turnGunRight(normalizeBearing(getHeading() + bearing - getGunHeading()));
        fire(20);
        
        //If the enemy is blocking our trajectory, we move back and around
        //If the enemy collision was not disturbing the pathing we just keep
        //moving to the corner
        if(bearing > -90 && bearing <= 90)
        {
            back(40);
            moveAroundEnemy(-100);
            
        }
        moveToCorner();
    }
    
    //Method to move around the enemy in order to avoid being blocked while
    //trying to reach the furthest corner
    public void moveAroundEnemy(double distance) {
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
    
    @Override
    public void onPaint(Graphics2D g)
    {
        int r = 60;
        g.setColor(Color.green);
        g.drawOval((int) getX()-r, (int)getY()-r, 2*r, 2*r);
    }
    
    //Helper function to normalize the angle between -180 and 180 degrees
    public double normalizeBearing(double angle) {
        while (angle > 180) 
        {
            angle -= 360;
        }
        while (angle < -180) 
        {
            angle += 360;
        }
        return angle;
}
}
