package org.firstinspires.ftc.teamcode.util;

/*
 * Copyright (c) 2015 Titan Robotics Club (http://www.titanrobotics.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import com.qualcomm.hardware.modernrobotics.ModernRoboticsI2cRangeSensor;
import com.qualcomm.robotcore.hardware.I2cController;
import com.qualcomm.robotcore.hardware.I2cControllerPortDevice;
import com.qualcomm.robotcore.hardware.I2cDeviceSynch;

public class FTCi2cDeviceState
{
    private I2cDeviceSynch syncDevice = null;
    private I2cController i2cController = null;
    private ModernRoboticsI2cRangeSensor rangeSensor = null;
    private int port = 0;
    private I2cController.I2cPortReadyCallback deviceCallback = null;
    private boolean deviceEnabled = true;

    public FTCi2cDeviceState(I2cControllerPortDevice device)
    {
        i2cController = device.getI2cController();
        port = device.getPort();
        deviceCallback = i2cController.getI2cPortReadyCallback(port);
        deviceEnabled = true;
    }   //FtcI2cDeviceState

    public FTCi2cDeviceState(I2cDeviceSynch device)
    {
        this.syncDevice = device;
    }   //FtcI2cDeviceState

    public boolean isEnabled()
    {
        return deviceEnabled;
    }   //isEnabled

    public void setEnabled(boolean enabled)
    {
        if (deviceEnabled != enabled) {
            if (enabled) {
                if (syncDevice != null) {
                    syncDevice.engage();
                } else {
                    i2cController.registerForI2cPortReadyCallback(deviceCallback, port);
                }
            } else {
                if (syncDevice != null) {
                    syncDevice.disengage();
                } else {
                    i2cController.deregisterForPortReadyCallback(port);
                }
            }
            deviceEnabled = enabled;
        }
    }   //setEnabled
}