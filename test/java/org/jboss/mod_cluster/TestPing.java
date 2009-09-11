/*
 *  mod_cluster
 *
 *  Copyright(c) 2009 Red Hat Middleware, LLC,
 *  and individual contributors as indicated by the @authors tag.
 *  See the copyright.txt in the distribution for a
 *  full listing of individual contributors.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library in the file COPYING.LIB;
 *  if not, write to the Free Software Foundation, Inc.,
 *  59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 *
 * @author Jean-Frederic Clere
 * @version $Revision$
 */

package org.jboss.mod_cluster;

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.catalina.Engine;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.Service;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardServer;

public class TestPing extends TestCase {

    /* Test that the sessions are really sticky */
    public void testPing() {

        boolean clienterror = false;
        StandardServer server = Maintest.getServer();
        JBossWeb service = null;
        JBossWeb service2 = null;
        LifecycleListener cluster = null;

        System.out.println("Testping Started");
        try {

            service = new JBossWeb("node1",  "localhost");
            service.addConnector(8011);
            server.addService(service);
 
            service2 = new JBossWeb("node2",  "localhost");
            service2.addConnector(8010);
            server.addService(service2);

            cluster = Maintest.createClusterListener("224.0.1.105", 23364, false);
            server.addLifecycleListener(cluster);

            // Debug Stuff
            // Maintest.listServices();

        } catch(IOException ex) {
            ex.printStackTrace();
            fail("can't start service");
        }

        // start the server thread.
        ServerThread wait = new ServerThread(3000, server);
        wait.start();
         
        // Wait until httpd as received the nodes information.
        String proxy = null;
        int tries = 0;
        while (proxy == null && tries<20) {
            String result = Maintest.doProxyPing(cluster, null);
            if (result != null) {
                if (!Maintest.checkProxyPing(result))
                    fail("can't find PING-RSP in proxy response");
                break; // Done.
            }
            try {
                Thread me = Thread.currentThread();
                me.sleep(5000);
                tries++;
            } catch (Exception ex) {
            }
        }
        if (tries == 20) {
            fail("can't find proxy");
        }

        // PING the 2 nodes...
        String [] nodes = new String[2];
        nodes[0] = "node1";
        nodes[1] = "node2";
        int countinfo = 0;
        while (countinfo < 20) {
            int i;
            for (i=0; i< nodes.length; i++) {
                String result = Maintest.doProxyPing(cluster, nodes[i]);
                if (result == null)
                    fail("Maintest.doProxyPing failed");
                if (!Maintest.checkProxyPing(result))
                    break;
            }
            if (i == nodes.length)
                break;
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            countinfo++;
        }
        if (countinfo == 20)
            fail("can't find node(s) PING-RSP in proxy response");

        // Try a not existing node.
        String result = Maintest.doProxyPing(cluster, "NONE");
        if (result == null)
           fail("Maintest.doProxyPing failed");
        if (Maintest.checkProxyPing(result))
           fail("doProxyPing on not existing node should have failed");

        // Stop the jboss and remove the services.
        try {
            wait.stopit();
            wait.join();

            server.removeService(service);
            server.removeService(service2);
            server.removeLifecycleListener(cluster);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail("can't stop service");
        }

        // Wait until httpd as received the stop messages.
        countinfo = 0;
        nodes = null;
        while ((!Maintest.checkProxyInfo(cluster, nodes)) && countinfo < 20) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            countinfo++;
        }
        System.gc();
        System.out.println("TestPing Done");
    }
}