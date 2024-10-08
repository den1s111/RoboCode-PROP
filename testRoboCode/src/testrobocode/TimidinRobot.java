/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package testrobocode;
import robocode.*;
/**
 *
 * @author denis
 */
public class TimidinRobot extends AdvancedRobot{

    @Override
    public void run()
    {
        turnLeft(getHeading());
        while(true)
        {
            setTurnRight(10000);
            setTurnGunRight(90);
            setAhead(2000);
            execute();
        }
    }
    
    @Override
    public void onScannedRobot(ScannedRobotEvent e)
    {
        fire(1);
    }
    
    @Override
    public void onHitByBullet(HitByBulletEvent e)
    {
        turnLeft(180);
    }
    
}
