/*
 * ModeShape (http://www.modeshape.org)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors.
 *
 * ModeShape is free software. Unless otherwise indicated, all code in ModeShape
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * ModeShape is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.modeshape.jcr.cache;

/**
 * An exception signalling that a failure occurred within a document store.
 */
public class DocumentStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String key;

    /**
     * @param key the key for the document
     */
    public DocumentStoreException( String key ) {
        super(key);
        this.key = key;
    }

    /**
     * @param key the key for the document
     * @param message the message
     */
    public DocumentStoreException( String key,
                                   String message ) {
        super(message);
        this.key = key;
    }

    /**
     * @param key the key for the document
     * @param cause the cause of this exception
     */
    public DocumentStoreException( String key,
                                   Throwable cause ) {
        super(key.toString(), cause);
        this.key = key;
    }

    /**
     * @param key the key for the document
     * @param message the message
     * @param cause the cause of this exception
     */
    public DocumentStoreException( String key,
                                   String message,
                                   Throwable cause ) {
        super(message, cause);
        this.key = key;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    /**
     * Get the document key
     * 
     * @return the key for the document
     */
    public String getKey() {
        return key;
    }
}
