/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.atom.event;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleMessage;
import org.mule.module.client.MuleClient;
import org.mule.tck.FunctionalTestCase;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.protocol.client.AbderaClient;
import org.apache.abdera.protocol.client.ClientResponse;

public class AtomEventTestCase extends FunctionalTestCase
{
    private Repository repository;

    @Override
    protected String getConfigResources()
    {
        return "atom-builder-conf.xml";
    }

    @Override
    protected void doTearDown() throws Exception
    {
        clearJcrRepository();
        super.doTearDown();
    }

    public void testCustomerProvider() throws Exception
    {
        repository = (Repository) muleContext.getRegistry().lookupObject("jcrRepository");

        MuleClient client = new MuleClient(muleContext);
        client.send("vm://in", createOutboundMessage());

        Thread.sleep(1000);

        AbderaClient aClient = new AbderaClient();
        ClientResponse res = aClient.get("http://localhost:9003/events");

        Document<Feed> doc = res.getDocument();
        Feed feed = doc.getRoot();

        assertEquals(1, feed.getEntries().size());
        Entry e = feed.getEntries().get(0);

        assertEquals("Mmm feeding", e.getContent());
        assertEquals("Foo Bar", e.getTitle());
    }

    public void testMessageTransformation() throws Exception
    {
        MuleClient client = new MuleClient(muleContext);
        client.dispatch("vm://fromTest", createOutboundMessage());

        MuleMessage response = client.request("vm://toTest", RECEIVE_TIMEOUT);
        assertNotNull(response);

        String payload = response.getPayloadAsString();
        assertTrue(payload.contains("<author><name>Ross Mason</name></author>"));
    }

    private MuleMessage createOutboundMessage()
    {
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("title", "Foo Bar");

        return new DefaultMuleMessage("Mmm feeding", props, muleContext);
    }

    private void clearJcrRepository()
    {
        try
        {
            if (repository == null)
            {
                return;
            }
            Session session = repository.login(new SimpleCredentials("username", "password".toCharArray()));

            Node node = session.getRootNode();

            for (NodeIterator itr = node.getNodes(); itr.hasNext();)
            {
                Node child = itr.nextNode();
                if (!child.getName().equals("jcr:system"))
                {
                    child.remove();
                }
            }
            session.save();
            session.logout();
        }
        catch (PathNotFoundException t)
        {
            // ignore, we're shutting down anyway
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
    }
}