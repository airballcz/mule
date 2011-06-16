/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.ajax;

import org.mule.api.MuleMessage;
import org.mule.module.client.MuleClient;
import org.mule.tck.DynamicPortTestCase;
import org.mule.util.concurrent.Latch;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.jackson.map.ObjectMapper;
import org.cometd.Client;
import org.cometd.Message;
import org.cometd.MessageListener;
import org.mortbay.cometd.client.BayeuxClient;
import org.mortbay.jetty.client.Address;
import org.mortbay.jetty.client.HttpClient;

public class AjaxFunctionalJsonBindingsTestCase extends DynamicPortTestCase
{
    public static int SERVER_PORT = -1;

    private BayeuxClient client;

    @Override
    protected String getConfigResources()
    {
        return "ajax-embedded-functional-json-bindings-test.xml";
    }

    @Override
    protected void doSetUp() throws Exception
    {
        SERVER_PORT = getPorts().get(0);
        HttpClient http = new HttpClient();
        http.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);

        client = new BayeuxClient(http, new Address("localhost", SERVER_PORT), "/ajax/cometd");
        http.start();
        //need to start the client before you can add subscriptions
        client.start();
    }

    @Override
    protected void doTearDown() throws Exception
    {
        //9 times out of 10 this throws a "ava.lang.IllegalStateException: Not running" exception, it can be ignored
        //client.stop();
        // TODO DZ: it seems like you would want to do this, maybe causing port locking issues?
        try
        {
            client.stop();
        }
        catch (IllegalStateException e)
        {
            logger.info("caught an IllegalStateException during tearDown", e);
        }
        catch(Exception e1)
        {
            fail("unexpected exception during tearDown :" + e1.getMessage());
        }
    }

    public void testClientSubscribeWithJsonObjectResponse() throws Exception
    {
        final Latch latch = new Latch();

        final AtomicReference<String> data = new AtomicReference<String>();
        client.addListener(new MessageListener()
        {
            public void deliver(Client fromClient, Client toClient, Message message)
            {
                if (message.getData() != null)
                {
                    // This simulate what the browser would receive
                    data.set(message.toString());
                    latch.release();
                }
            }
        });
        client.subscribe("/test1");

        MuleClient muleClient = new MuleClient(muleContext);
        muleClient.dispatch("vm://in1", "Ross", null);
        assertTrue("data did not arrive in 10 seconds", latch.await(10, TimeUnit.SECONDS));

        assertNotNull(data.get());

        // parse the result string into java objects. different jvms return it in
        // different order, so we can't do a straight string comparison
        ObjectMapper mapper = new ObjectMapper();
        Map<?, ?> result = mapper.readValue(data.get(), Map.class);
        assertEquals("/test1", result.get("channel"));
        assertEquals("Ross", ((Map<?, ?>)result.get("data")).get("name"));
    }

    public void testClientPublishWithJsonObject() throws Exception
    {
        client.publish("/test2", "{\"name\":\"Ross\"}", null);
        MuleClient muleClient = new MuleClient(muleContext);
        MuleMessage msg = muleClient.request("vm://in2", 5000L);

        assertNotNull(msg);
        assertEquals("Received: DummyJsonBean{name='Ross'}", msg.getPayloadAsString());
    }

    @Override
    protected int getNumPortsToFind()
    {
        return 1;
    }
}