/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */

// --- JCite ---
package org.forgerock.openig.doc;

import org.forgerock.openig.filter.GenericFilter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;

import java.io.IOException;

/**
 * Filter to set a header in the incoming request and in the outgoing response.
 */
public class SampleFilter extends GenericFilter {

    /** Header name. */
    String name;

    /** Header value. */
    String value;

    /**
     * Set a header in the incoming request and in the outgoing response.
     * A configuration example looks something like the following.
     *
     * <pre>
     * {
     *     "name": "SampleFilter",
     *     "type": "SampleFilter",
     *     "config": {
     *         "name": "X-Greeting",
     *         "value": "Hello world"
     *     }
     * }
     * </pre>
     *
     * @param exchange          Wraps request and response.
     * @param next              Next filter or handler in the chain.
     * @throws HandlerException Failure when handling the exchange.
     * @throws IOException      I/O exception when handling the exchange.
     */
    @Override
    public void filter(Exchange exchange, Handler next)
            throws HandlerException, IOException {

        // Set header in the request.
        exchange.request.getHeaders().putSingle(name, value);

        // Pass to the next filter or handler in the chain.
        next.handle(exchange);

        // Set header in the response.
        exchange.response.getHeaders().putSingle(name, value);
    }

    /**
     * Create and initialize the filter, based on the configuration.
     * The filter object is stored in the heap.
     */
    public static class Heaplet extends GenericHeaplet {

        /**
         * Create the filter object in the heap,
         * setting the header name and value for the filter,
         * based on the configuration.
         *
         * @return                  The filter object.
         * @throws HeapException    Failed to create the object.
         */
        @Override
        public Object create() throws HeapException {

            SampleFilter filter = new SampleFilter();
            filter.name  = config.get("name").required().asString();
            filter.value = config.get("value").required().asString();

            return filter;
        }
    }
}
// --- JCite ---
