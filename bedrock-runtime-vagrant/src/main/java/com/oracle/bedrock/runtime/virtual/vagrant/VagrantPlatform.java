/*
 * File: VagrantPlatform.java
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * The contents of this file are subject to the terms and conditions of 
 * the Common Development and Distribution License 1.0 (the "License").
 *
 * You may not use this file except in compliance with the License.
 *
 * You can obtain a copy of the License by consulting the LICENSE.txt file
 * distributed with this file, or by consulting https://oss.oracle.com/licenses/CDDL
 *
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file LICENSE.txt.
 *
 * MODIFICATIONS:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 */

package com.oracle.bedrock.runtime.virtual.vagrant;

import com.oracle.bedrock.Option;
import com.oracle.bedrock.OptionsByType;
import com.oracle.bedrock.options.Timeout;
import com.oracle.bedrock.runtime.Application;
import com.oracle.bedrock.runtime.LocalPlatform;
import com.oracle.bedrock.runtime.Platform;
import com.oracle.bedrock.runtime.console.PipedApplicationConsole;
import com.oracle.bedrock.runtime.options.Argument;
import com.oracle.bedrock.runtime.options.Arguments;
import com.oracle.bedrock.runtime.options.Console;
import com.oracle.bedrock.runtime.options.DisplayName;
import com.oracle.bedrock.runtime.options.Executable;
import com.oracle.bedrock.runtime.options.WorkingDirectory;
import com.oracle.bedrock.runtime.remote.SecureKeys;
import com.oracle.bedrock.runtime.remote.options.HostName;
import com.oracle.bedrock.runtime.remote.options.StrictHostChecking;
import com.oracle.bedrock.runtime.virtual.CloseAction;
import com.oracle.bedrock.runtime.virtual.VirtualPlatform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Platform} implementation that represents
 * an O/S running in a virtual machine managed by the Vagrant.
 * <p>
 * Copyright (c) 2014. All Rights Reserved. Oracle Corporation.<br>
 * Oracle is a registered trademark of Oracle Corporation and/or its affiliates.
 *
 * @author Jonathan Knight
 */
public class VagrantPlatform extends VirtualPlatform
{
    /**
     * The default command used to run the Vagrant command line interface.
     */
    public static final String DEFAULT_VAGRANT_COMMAND = "vagrant";

    /**
     * The command to use to run the Vagrant command line interface.
     */
    private String vagrantCommand = getDefaultVagrantCommand();

    /**
     * The working directory for the {@link VagrantPlatform}.
     */
    private File workingDirectory;


    /**
     * Construct a new {@link VagrantPlatform}.
     *
     * @param name     the name of this {@link VagrantPlatform}
     * @param builder  the {@link VagrantFileBuilder} to use to build the
     *                 Vagranfile for the VM
     * @param options  the {@link Option}s for the {@link VagrantPlatform}
     */
    public VagrantPlatform(String             name,
                           VagrantFileBuilder builder,
                           Option...          options)
    {
        this(name, builder, 22, options);
    }


    /**
     * Construct a new {@link VagrantPlatform}.
     *
     * @param name     the name of this {@link VagrantPlatform}
     * @param builder  the {@link VagrantFileBuilder} to use to build
     *                 the Vagrantfile for the VM
     * @param port     the remote port that will be used to SSH into
     *                 this {@link VirtualPlatform}
     * @param options  the {@link Option}s for the {@link VagrantPlatform}
     */
    public VagrantPlatform(String             name,
                           VagrantFileBuilder builder,
                           int                port,
                           Option...          options)
    {
        super(name, null, port, null, null, options);

        // ----- configure the default options -----

        getOptions().addIfAbsent(CloseAction.Destroy);
        getOptions().addIfAbsent(Timeout.after(5, TimeUnit.MINUTES));
        getOptions().add(StrictHostChecking.disabled());
        getOptions().add(DisplayName.of(name));

        // ----- establish the working directory for the platform -----

        WorkingDirectory directory = getOptions().getOrSetDefault(WorkingDirectory.class,
                                                                  WorkingDirectory.subDirectoryOf(new File(".")));

        File base = directory.resolve(this, getOptions());

        this.workingDirectory = base == null ? new File(".", name) : new File(base, name);

        if (!workingDirectory.exists())
        {
            if (!workingDirectory.mkdirs())
            {
                throw new RuntimeException("Could not create working directory: " + directory);
            }
        }

        // ----- build the vagrant file defining the platform -----

        try
        {
            // build the vagrant file
            File               vagrantFile = new File(workingDirectory, "Vagrantfile");
            Optional<HostName> hostName    = builder.create(vagrantFile, getOptions());

            // remember the host name (when defined)
            if (hostName.isPresent())
            {
                getOptions().add(hostName.get());
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to create VagrantFile at " + workingDirectory, e);
        }

        // start the Vagrant VM
        start();
    }


    /**
     * Obtain the command used to run the Vagrant command line interface.
     *
     * @return the command used to run the Vagrant command line interface
     */
    public String getVagrantCommand()
    {
        return vagrantCommand;
    }


    /**
     * Set the command used to run the Vagrant command line interface.
     *
     * @param vagrantCommand  the command used to run the Vagrant command
     *                        line interface
     */
    public void setVagrantCommand(String vagrantCommand)
    {
        this.vagrantCommand = vagrantCommand;
    }


    /**
     * Obtain the location of this {@link VagrantPlatform}'s VagrantFile.
     *
     * @return the location of this {@link VagrantPlatform}'s VagrantFile
     */
    public File getWorkingDirectory()
    {
        return workingDirectory;
    }


    /**
     * Obtain the host name of the public network interface on the VM
     *
     * @return the host name of the public network interface on the VM
     */
    public String getPublicHostName()
    {
        return getOptions().get(HostName.class).get();
    }


    @Override
    public void close() throws IOException
    {
        close(new Option[0]);
    }


    @Override
    public void close(Option... closeOptions) throws IOException
    {
        OptionsByType optionsByType = OptionsByType.of(getOptions()).addAll(getDefaultOptions());

        optionsByType.addAll(closeOptions);

        CloseAction action = optionsByType.getOrDefault(CloseAction.class, CloseAction.Destroy);

        switch (action)
        {
        case None :
            return;

        case Destroy :
        case PowerButton :
            optionsByType.add(Arguments.of("destroy", "--force"));
            break;

        case Shutdown :
            optionsByType.add(Argument.of("halt"));
            break;

        case SaveState :
            optionsByType.add(Argument.of("suspend"));
            break;

        default :
            throw new IllegalArgumentException("Unsupported CloseAction " + action);
        }

        execute(optionsByType);
    }


    /**
     * Start this {@link VagrantPlatform}.
     * When this method returns the virtual machine this {@link VagrantPlatform}
     * represents will be in a running state.
     */
    public void start()
    {
        OptionsByType options = getDefaultOptions().add(Argument.of("up"));

        execute(options);

        Properties sshProperties = detectSSH();

        try
        {
            HostName hostName = getOptions().get(HostName.class);

            // If no public host name has been specified then use the
            // settings configured by Vagrant
            if (hostName == null || hostName.get().isEmpty())
            {
                // Important:  At this point all we know is that we can connect to the local loopback
                // NAT'd address so we can SSH into the Vagrant Box.   We don't know what the
                // address of the Vagrant Box is.  It may not even have an address we can access.
                this.address        = InetAddress.getLoopbackAddress();
                this.port           = Integer.parseInt(sshProperties.getProperty("Port"));
                this.userName       = sshProperties.getProperty("User");
                this.authentication = SecureKeys.fromPrivateKeyFile(sshProperties.getProperty("IdentityFile"));
            }
            else
            {
                this.address        = InetAddress.getByName(hostName.get());
                this.userName       = sshProperties.getProperty("User");
                this.authentication = SecureKeys.fromPrivateKeyFile(sshProperties.getProperty("IdentityFile"));
            }
        }
        catch (UnknownHostException e)
        {
            throw new RuntimeException("Error setting public InetAddress", e);
        }
    }


    /**
     * Detect the SSH settings for the NAT port forwarding that Vagrant
     * has configured on the VM and set them into this {@link VagrantPlatform}.
     *
     * @return the SSH properties configured by Vagrant
     */
    protected Properties detectSSH()
    {
        OptionsByType optionsByType = getDefaultOptions().add(Argument.of("ssh-config"));

        LocalPlatform platform      = LocalPlatform.get();

        try (PipedApplicationConsole console = new PipedApplicationConsole();
            Application application = platform.launch(Application.class,
                                                      optionsByType.add(Console.of(console)).asArray()))
        {
            application.waitFor();
            application.close();

            Properties     sshProperties = new Properties();
            BufferedReader reader        = console.getOutputReader();
            String         line          = reader.readLine();

            while (line != null)
            {
                line = line.trim();

                int index = line.indexOf(']');

                index = line.indexOf(':', index);
                line  = line.substring(index + 1).trim();
                index = line.indexOf(' ');

                if (index > 0)
                {
                    String key   = line.substring(0, index).trim();
                    String value = line.substring(index + 1).trim();

                    if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
                    {
                        value = value.substring(1, value.length() - 1);
                    }

                    sshProperties.setProperty(key, value);
                }

                try
                {
                    line = reader.readLine();
                }
                catch (IOException e)
                {
                    line = null;
                }
            }

            return sshProperties;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error attempting to detect VM's SSH settings", e);
        }
    }


    /**
     * Execute the application defined by the specified {@link OptionsByType}.
     *
     * @param optionsByType  the {@link OptionsByType}
     */
    protected void execute(OptionsByType optionsByType)
    {
        LocalPlatform platform = LocalPlatform.get();
        Timeout       timeout  = optionsByType.getOrDefault(Timeout.class, Timeout.after(5, TimeUnit.MINUTES));

        try (Application application = platform.launch(Application.class, optionsByType.asArray()))
        {
            application.waitFor(timeout);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error executing Vagrant command", e);
        }
    }


    /**
     * Options the default {@link OptionsByType} to use when launching Vagrant.
     *
     * @return the default {@link OptionsByType}
     */
    protected OptionsByType getDefaultOptions()
    {
        return OptionsByType.of(Executable.named(vagrantCommand),
                                WorkingDirectory.at(workingDirectory),
                                Timeout.after(5, TimeUnit.MINUTES),
                                DisplayName.of("Vagrant"));
    }


    /**
     * Get the default Vagrant command to use to execute the Vagrant CLI commands.
     *
     * @return the default Vagrant command
     */
    public static String getDefaultVagrantCommand()
    {
        return System.getProperty("vagrant.command", DEFAULT_VAGRANT_COMMAND);
    }
}
