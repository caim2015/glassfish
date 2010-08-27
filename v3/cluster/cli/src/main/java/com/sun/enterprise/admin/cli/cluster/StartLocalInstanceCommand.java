/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.admin.cli.cluster;

import com.sun.enterprise.admin.launcher.GFLauncher;
import com.sun.enterprise.admin.launcher.GFLauncherException;
import com.sun.enterprise.admin.launcher.GFLauncherFactory;
import com.sun.enterprise.admin.launcher.GFLauncherInfo;
import com.sun.enterprise.universal.xml.MiniXmlParserException;
import java.io.*;

import org.jvnet.hk2.annotations.*;
import org.jvnet.hk2.component.*;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;

import com.sun.enterprise.admin.cli.*;
import com.sun.enterprise.util.ObjectAnalyzer;
import com.sun.enterprise.admin.cli.StartServerCommand;
import static com.sun.enterprise.admin.cli.CLIConstants.*;
/**
 * Start a local server instance.
 */
@Service(name = "start-local-instance")
@ExecuteOn(RuntimeType.DAS)
@Scoped(PerLookup.class)
public class StartLocalInstanceCommand extends SynchronizeInstanceCommand
                                        implements StartServerCommand {
    @Param(optional = true, defaultValue = "false")
    private boolean verbose;

    @Param(optional = true, defaultValue = "false")
    private boolean debug;

    @Param(optional = true, defaultValue = "false")
    private boolean upgrade;

    @Param(optional = true, defaultValue = "false")
    private boolean nosync;

    // handled by superclass
    //@Param(name = "instance_name", primary = true, optional = false)
    //private String instanceName0;

    private StartServerHelper helper;

    private String localPassword;
    
    @Override
    public RuntimeType getType() {
         return RuntimeType.INSTANCE;
    }

    @Override
    protected boolean mkdirs(File f) {
        // we definitely do NOT want dirs created for this instance if
        // they don't exist!
        return false;
    }


    @Override
    protected void validate() throws CommandException {
        super.validate();

        File dir = getServerDirs().getServerDir();

        if(!dir.isDirectory())
            throw new CommandException(Strings.get("Instance.noSuchInstance"));
    }

    /**
     */
    @Override
    protected int executeCommand() throws CommandException {

        logger.printDebugMessage(toString());

        if (nosync) {
            logger.printMessage(Strings.get("Instance.nosync"));
        } else {
            if (!synchronizeInstance()) {
                File domainXml =
                    new File(new File(instanceDir, "config"), "domain.xml");
                if (!domainXml.exists()) {
                    logger.printMessage(Strings.get("Instance.nodomainxml"));
                    return ERROR;
                }
                logger.printMessage(Strings.get("Instance.syncFailed"));
            }
        }

        try {
                 // createLauncher needs to go before the helper is created!!
            createLauncher();
            final String mpv = getMasterPassword();

            helper = new StartServerHelper(
                        logger,
                        programOpts.isTerse(),
                        getServerDirs(),
                        launcher,
                        mpv,
                        debug);

            if(!helper.prepareForLaunch())
                return ERROR;

            launcher.launch();

            if (verbose || upgrade) { // we can potentially loop forever here...
                while (true) {
                    int returnValue = launcher.getExitValue();

                    switch (returnValue) {
                        case RESTART_NORMAL:
                            logger.printMessage(Strings.get("restart"));
                            break;
                        case RESTART_DEBUG_ON:
                            logger.printMessage(Strings.get("restartChangeDebug", "on"));
                            info.setDebug(true);
                            break;
                        case RESTART_DEBUG_OFF:
                            logger.printMessage(Strings.get("restartChangeDebug", "off"));
                            info.setDebug(false);
                            break;
                        default:
                            return returnValue;
                    }
                    
                    if (CLIConstants.debugMode)
                        System.setProperty(CLIConstants.WALL_CLOCK_START_PROP,
                                            "" + System.currentTimeMillis());
                    launcher.relaunch();
                }

            } else {
                helper.waitForServer();
                helper.report();
                return SUCCESS;
            }
        } catch (GFLauncherException gfle) {
            throw new CommandException(gfle.getMessage());
        } catch (MiniXmlParserException me) {
            throw new CommandException(me);
        }
    }

    /**
     * Create a launcher for the instance specified by arguments to
     * this command.  The launcher is for a server of the specified type.
     * Sets the launcher and info fields.
     */
    @Override
    public void createLauncher()
                        throws GFLauncherException, MiniXmlParserException {
            launcher = GFLauncherFactory.getInstance(getType());
            info = launcher.getInfo();
            info.setInstanceName(instanceName);
            info.setInstanceRootDir(instanceDir);
            info.setVerbose(verbose || upgrade);
            info.setDebug(debug);
            info.setUpgrade(upgrade);

            info.setRespawnInfo(programOpts.getClassName(),
                            programOpts.getClassPath(),
                            programOpts.getProgramArguments());

            launcher.setup();
    }

    public String toString() {
        return ObjectAnalyzer.toStringWithSuper(this);
    }

    private GFLauncherInfo info;
    private GFLauncher launcher;
}
