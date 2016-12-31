package org.firstinspires.ftc.teamcode.navigation;

import com.qualcomm.ftccommon.DbgLog;
import com.qualcomm.hardware.modernrobotics.ModernRoboticsI2cRangeSensor;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.teamcode.FTCRobot;
import org.firstinspires.ftc.teamcode.attachments.BeaconClaim;
import org.firstinspires.ftc.teamcode.drivesys.DriveSystem;
import org.firstinspires.ftc.teamcode.util.FTCi2cDeviceState;
import org.firstinspires.ftc.teamcode.util.JsonReaders.JsonReader;
import org.firstinspires.ftc.teamcode.util.JsonReaders.NavigationOptionsReader;
import org.json.JSONObject;



/*
 * Copyright (c) 2016 Robocracy 9773
 */

public class Navigation {
    FTCRobot robot;
    LinearOpMode curOpMode;
    JSONObject navOptObj;
    public LineFollow lf;
    public NavxMicro navxMicro;
    public EncoderNavigation encoderNav;
    public ModernRoboticsI2cRangeSensor rangeSensor;
    public double minDistance=15.0; // in cm
    public double lfMaxSpeed=1.0, straightDrMaxSpeed=1.0, turnMaxSpeed=1.0;
    public double driveSysTeleopMaxSpeed=1.0;
    public FTCi2cDeviceState rangeSensorState;

    public enum SpinDirection {CLOCKWISE, COUNTERCLOCKWISE, NONE}

    public Navigation(FTCRobot robot, LinearOpMode curOpMode, String navOptionStr) {
        this.robot = robot;
        this.curOpMode = curOpMode;

        NavigationOptionsReader navOption = new NavigationOptionsReader(JsonReader.navigationFile,
                navOptionStr);
        this.navOptObj = navOption.jsonRoot;

        if (navOption.lineFollowerExists()) {
            this.lf = new LineFollow(robot, navOption.getLightSensorName(),
                    navOption.getLFvariableDouble("lowSpeed"),
                    navOption.getLFvariableDouble("highSpeed"),
                    navOption.getLFvariableDouble("timeOut"),
                    navOption.getLFvariableDouble("white"),
                    navOption.getLFvariableDouble("black"));
        }
        else {
            this.lf = null;
        }

        if (navOption.imuExists()) {
            this.navxMicro = new NavxMicro(curOpMode, robot, this, navOption.getIMUDIMname(),
                    navOption.getIMUportNum(), navOption.getIMUVariableDouble("driveSysInitialPower"),
                    navOption.getIMUVariableDouble("angleTolerance"), navOption.getIMUVariableDouble("straightPID_kp"),
                    navOption.getIMUVariableDouble("turnPID_kp"), navOption.getIMUVariableDouble("PID_minSpeed"),
                    navOption.getIMUVariableDouble("PID_maxSpeed"));
        }
        else {
            this.navxMicro = null;
        }

        if (navOption.rangeSensorExists()) {
            this.rangeSensor = curOpMode.hardwareMap.get(ModernRoboticsI2cRangeSensor.class, "rangeSensor1");
            //this.rangeSensorState = new FTCi2cDeviceState((I2cDeviceSynch)rangeSensor);
        }
         else {
            this.rangeSensor = null;
        }

        if (navOption.encoderVarsExist()) {
            this.lfMaxSpeed = navOption.getLineFollowMaxSpeed();
            this.straightDrMaxSpeed = navOption.getStraightLineMaxSpeed();
            this.turnMaxSpeed = navOption.getTurningMaxSpeed();
            this.driveSysTeleopMaxSpeed = navOption.getDoubleDriveSysEncVar("DriveSysTeleOpMaxSpeed");
        }

        this.encoderNav = new EncoderNavigation(robot, robot.driveSystem, curOpMode, this);
    }

    public void printRangeSensorValue() {
        DbgLog.msg("range sensor distance in cm = %f", rangeSensor.getDistance(DistanceUnit.CM));
    }

    /**
     * Initialize the navigation system just after pressing the play button.
     */
    public void initForPlay() {
        navxMicro.setNavxStatus();
    }

    public double distanceBetweenAngles(double angle1, double angle2) {
        // Both angle1 and angle2 are assumed to be positive numbers between 0 and 360
        // The returnValue is between 0 and 180.
        double angleDistance= Math.abs(angle1 - angle2);

        if (angleDistance > 180) {
            angleDistance = 360 - angleDistance;
        }

        return (angleDistance);
    }

    /**
     * Calculates whether the robot has to spin clockwise or counter clockwise to go from
     * currentYaw to targetYaw
     * @param curYaw
     * @param targetYaw
     * @return CLOCKWISE, COUNTERCLOCKWISE, NONE
     */
    public SpinDirection getSpinDirection (double curYaw, double targetYaw) {
        SpinDirection direction = SpinDirection.NONE;
        double diffYaw = targetYaw - curYaw;

        double degreesToTurn = diffYaw>180 ? diffYaw-360 : diffYaw<-180 ? diffYaw+360 : diffYaw;

        if (degreesToTurn < 0) {
            direction = SpinDirection.COUNTERCLOCKWISE;
        } else {
            direction = SpinDirection.CLOCKWISE;
        }
        return (direction);
    }

    /**
     * Converts the given targetYaw into the angle to turn.  Returns a value between [-180, +180]
     * @param curYaw  current yaw value
     * @param targetYaw  target yaw value
     *      The targetYaw is with respect to the initial autonomous starting position
     *      The initial orientation of the robot at the beginning of the autonomous period
     *      is '0'. targetYaw is between 0 to 360 degrees.

     * @return degreesToTurn
     */
    public double getDegreesToTurn(double curYaw, double targetYaw) {
        double diffYaw = targetYaw - curYaw;
        double degreesToTurn = diffYaw>180 ? diffYaw-360 : diffYaw<-180 ? diffYaw+360 : diffYaw;
        return (degreesToTurn);
    }

    public void goStraightToDistance(double inches, double degrees,
                                     float speed, boolean driveBackwards) {
        // First, disable the color sensor
        //robot.beaconClaimObj.disableColorSensor();
        // If navx is working, using navx's goStraightPID() method, else use driveSystem's
        // driveToDistance method
        NavigationChecks navChecks = new NavigationChecks(robot, curOpMode, this);
        NavigationChecks.EncoderCheckForDistance encodercheck = navChecks.new EncoderCheckForDistance(inches);
        NavigationChecks.OpmodeInactiveCheck check2 = navChecks.new OpmodeInactiveCheck();
        navChecks.addNewCheck(encodercheck);
        navChecks.addNewCheck(check2);
        if (navxMicro.navxIsWorking()) {
            DbgLog.msg("Navx is working");
            NavigationChecks.CheckRobotTilting tiltingCheck = navChecks.new CheckRobotTilting(10);
            while (!navChecks.stopNavigation()) {
                robot.navigation.navxMicro.navxGoStraightPID(driveBackwards, degrees);
                if (tiltingCheck.stopNavigation()) {
                    // Move backward for 15 inches
                    robot.driveSystem.reverse();
                    robot.driveSystem.driveToDistance(speed, 15);
                    robot.driveSystem.reverse();
                }
            }
            robot.driveSystem.stop();
            // Update the encoderNav's current yaw with that of navxMicro
            encoderNav.setCurrentYaw(navxMicro.getModifiedYaw());
        } else {
            DbgLog.msg("Navx is not working");
            // Use purely encoder based navigation
            DbgLog.msg("Speed: %f, distance: %f", speed, inches);
            if (driveBackwards) {
                robot.driveSystem.reverse();
            }
            encodercheck.reset(); // just in case drive system is reversed
            while (!navChecks.stopNavigation()) {
                robot.driveSystem.drive(speed, 0);
            }
            robot.driveSystem.stop();
            if (driveBackwards) {
                robot.driveSystem.reverse();
            }
        }
    }

    public void goStraightToWhiteLine(double degrees, float motorSpeed, boolean driveBackwards) {
        NavigationChecks navChecks = new NavigationChecks(robot, curOpMode, this);
        NavigationChecks.CheckForWhiteLine check1 = navChecks.new CheckForWhiteLine();
        NavigationChecks.OpmodeInactiveCheck check2 = navChecks.new OpmodeInactiveCheck();
        navChecks.addNewCheck(check1);
        navChecks.addNewCheck(check2);
        // Start beacon color scanning
        //robot.beaconClaimObj.startBeaconScanning();
        if (navxMicro.navxIsWorking()) {
            NavigationChecks.CheckRobotTilting check3 = navChecks.new CheckRobotTilting(10);
            navChecks.addNewCheck(check3);
            while (!navChecks.stopNavigation()) {
                robot.navigation.navxMicro.navxGoStraightPID(driveBackwards, degrees);
                //robot.beaconClaimObj.updateBeaconScanValues();
            }
            robot.driveSystem.stop();
            // Update the encoderNav's current yaw with that of navxMicro
            encoderNav.setCurrentYaw(navxMicro.getModifiedYaw());
        } else {
            // Use purely encoder based navigation
            if (driveBackwards) {
                robot.driveSystem.reverse();
            }
            while (!navChecks.stopNavigation()) {
                robot.driveSystem.drive(motorSpeed, 0);
                //robot.beaconClaimObj.updateBeaconScanValues();
            }
            robot.driveSystem.stop();
            if (driveBackwards) {
                robot.driveSystem.reverse();
            }
        }
        // Print the first and second scanned values for information purpose only
        //robot.beaconClaimObj.printBeaconScanningData();
        //robot.beaconClaimObj.stopBeaconScanning();
    }

    public void driveUntilAllianceBeacon(boolean driveBackwards, double motorSpeed, double degrees,
                                         double maxDistance1, double maxDistance2) {
        // Determine the first and second colors
        double maxDistance=0.0;
        BeaconClaim.BeaconColor[] beaconColors = robot.beaconClaimObj.getBeaconColor();
        if (robot.autonomousActions.allianceColor.equalsIgnoreCase("red")) {
            if (beaconColors[1] == BeaconClaim.BeaconColor.BLUE) {
                maxDistance = maxDistance1;
            } else if (beaconColors[1] == BeaconClaim.BeaconColor.RED) {
                maxDistance = maxDistance2;
            }
        } else if (robot.autonomousActions.allianceColor.equalsIgnoreCase("blue")) {
            if (beaconColors[1] == BeaconClaim.BeaconColor.RED) {
                maxDistance = maxDistance2;
            } else if (beaconColors[1] == BeaconClaim.BeaconColor.BLUE) {
                maxDistance = maxDistance1;
            }
        }

        NavigationChecks navChecks = new NavigationChecks(robot, curOpMode, this);
        NavigationChecks.OpmodeInactiveCheck opmodeCheck = navChecks.new OpmodeInactiveCheck();
        navChecks.addNewCheck(opmodeCheck);
        NavigationChecks.EncoderCheckForDistance distanceCheck = navChecks.new EncoderCheckForDistance(maxDistance);
        navChecks.addNewCheck(distanceCheck);
        if (navxMicro.navxIsWorking()) {
            while (!navChecks.stopNavigation()) {
                robot.navigation.navxMicro.navxGoStraightPID(driveBackwards, degrees);
            }
            robot.driveSystem.stop();
            // Update the encoderNav's current yaw with that of navxMicro
            encoderNav.setCurrentYaw(navxMicro.getModifiedYaw());
        } else {
            // Use purely encoder based navigation
            if (driveBackwards) {
                robot.driveSystem.reverse();
            }
            distanceCheck.reset(); // just in case drive system is reversed
            while (!navChecks.stopNavigation()) {
                robot.driveSystem.drive((float)motorSpeed, 0);
            }
            robot.driveSystem.stop();
            if (driveBackwards) {
                robot.driveSystem.reverse();
            }
        }
    }

    public void setRobotOrientation(double targetYaw, double motorSpeed) {
        // Create a NavigationChecks object to set the terminating conditions
        NavigationChecks navigationChecks = new NavigationChecks(robot, curOpMode, this);
        // Calculate the timeout based on the targetYaw and currentYaw
        // at the rate of 100 milliseconds per degree of rotation at full speed
        double curYaw = encoderNav.getCurrentYaw();
        long timeoutMillis = (long) Math.abs(this.distanceBetweenAngles(targetYaw, curYaw) * 100 / motorSpeed);
        NavigationChecks.TimeoutCheck check1 = navigationChecks.new TimeoutCheck(timeoutMillis);
        navigationChecks.addNewCheck(check1);
        NavigationChecks.OpmodeInactiveCheck check2 = navigationChecks.new OpmodeInactiveCheck();
        navigationChecks.addNewCheck(check2);

        DriveSystem.ElapsedEncoderCounts elapsedEncoderCounts = robot.driveSystem.getNewElapsedCountsObj();
        elapsedEncoderCounts.reset();

        // If the navx is working, then set the robot orientation with navx
        if (navxMicro.navxIsWorking()) {
            curOpMode.telemetry.addData("Set Robot Orientation", "Using Navx");
            curOpMode.telemetry.update();
            // The difference between the encoder-based degrees and navx based degrees can easily
            // go upto 10 degrees even when navx is working well.  So, we should not have too low
            // value for the CheckWhileTurning constructor.  Ensure that we do not check for less
            // 30 degree deviation between the encoder-based and navx-based angles.
            double degreesToCheck = Math.max(this.distanceBetweenAngles(targetYaw, curYaw) /2, 30);
            NavigationChecks.CheckNavxWhileTurning check3 = navigationChecks.new
                    CheckNavxWhileTurning(degreesToCheck);
            navigationChecks.addNewCheck(check3);
            navxMicro.setRobotOrientation(targetYaw, motorSpeed, navigationChecks);
            if ((navigationChecks.stopNavCriterion != null) &&
                    (navigationChecks.stopNavCriterion.navcheck == NavigationChecks.NavChecksSupported.CROSSCHECK_NAVX_WITH_ENCODERS)){
                double encoder_degreesTurned = elapsedEncoderCounts.getDegreesTurned();
                encoderNav.updateCurrentYaw(encoder_degreesTurned);
                elapsedEncoderCounts.reset();
                curOpMode.telemetry.addData("Set Robot Orientation", "Not Using Navx");
                curOpMode.telemetry.update();
                navigationChecks.removeCheck(check3);
                encoderNav.setRobotOrientation(targetYaw, motorSpeed, navigationChecks);
                encoder_degreesTurned = elapsedEncoderCounts.getDegreesTurned();
                encoderNav.updateCurrentYaw(encoder_degreesTurned);
            } else if (navigationChecks.stopNavCriterion == null) {
                // navx worked without any problems; Set the encoderNav's currentYaw to the navx yaw value
                encoderNav.setCurrentYaw(navxMicro.getModifiedYaw());
            }
        }
        else {
            // First, do the encoder based turning.
            curOpMode.telemetry.addData("Set Robot Orientation", "Not Using Navx");
            curOpMode.telemetry.update();
            encoderNav.setRobotOrientation(targetYaw, motorSpeed, navigationChecks);
            encoderNav.updateCurrentYaw(elapsedEncoderCounts.getDegreesTurned());
            DbgLog.msg("currYaw: %f", encoderNav.getCurrentYaw());
        }
    }

    public void shiftRobot(double distance, double moveDistance, boolean isForward, double speed){
        NavigationChecks navigationChecks = new NavigationChecks(robot, curOpMode, this);
        NavigationChecks.TimeoutCheck timeoutCheck = navigationChecks.new TimeoutCheck(10000);
        NavigationChecks.OpmodeInactiveCheck opmodeCheck = navigationChecks.new OpmodeInactiveCheck();

        navigationChecks.addNewCheck(timeoutCheck);
        navigationChecks.addNewCheck(opmodeCheck);

        DriveSystem.ElapsedEncoderCounts elapsedEncoderCounts = robot.driveSystem.getNewElapsedCountsObj();
        elapsedEncoderCounts.reset();

        if (navxMicro.navxIsWorking()) {
            curOpMode.telemetry.addData("Set Robot Orientation", "Using Navx");
            curOpMode.telemetry.update();
            NavigationChecks.CheckNavxWhileTurning checkNavxWhileTurning = navigationChecks.new CheckNavxWhileTurning(90);
            navigationChecks.addNewCheck(checkNavxWhileTurning);
            navxMicro.shiftRobot(distance, moveDistance, isForward, speed, navigationChecks);
            if ((navigationChecks.stopNavCriterion != null) &&
                    navigationChecks.stopNavCriterion.navcheck == NavigationChecks.NavChecksSupported.CROSSCHECK_NAVX_WITH_ENCODERS){
                double encoder_degreesTurned = elapsedEncoderCounts.getDegreesTurned();
                encoderNav.updateCurrentYaw(encoder_degreesTurned);
                elapsedEncoderCounts.reset();
                navigationChecks.removeCheck(checkNavxWhileTurning);
            } else if (navigationChecks.stopNavCriterion == null) {
                // navx worked without any problems; Set the encoderNav's currentYaw to the navx yaw value
                encoderNav.setCurrentYaw(navxMicro.getModifiedYaw());
            }
        }
        else {
            curOpMode.telemetry.addData("Set Robot Orientation", "Not Using Navx");
            curOpMode.telemetry.update();
            encoderNav.shiftRobot(distance, moveDistance, isForward, speed, navigationChecks);
            encoderNav.updateCurrentYaw(elapsedEncoderCounts.getDegreesTurned());
        }
    }
}
