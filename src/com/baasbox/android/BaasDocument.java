/*
 * Copyright (C) 2014. BaasBox
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions andlimitations under the License.
 */

package com.baasbox.android;


import android.os.Parcel;
import android.os.Parcelable;
import com.baasbox.android.impl.NetworkTask;
import com.baasbox.android.json.JsonArray;
import com.baasbox.android.json.JsonObject;
import com.baasbox.android.json.JsonStructure;
import com.baasbox.android.net.HttpRequest;
import org.apache.http.HttpResponse;

import java.util.*;

/**
 * Represents a document entity that belong to a collection
 * on the server.
 * Created by Andrea Tortorella on 02/01/14.
 */
public class BaasDocument extends BaasObject<BaasDocument> implements Iterable<Map.Entry<String, Object>>, Parcelable {

    private final JsonObject data;
    private final String collection;
    private String id;
    private String author;
    private String creation_date;
    private long version;


    BaasDocument(JsonObject data) {
        super();
        this.collection = data.getString("@class");
        data.remove("@class");
        this.id = data.getString("id");
        data.remove("id");
        this.author = data.getString("_author");
        data.remove("_author");
        this.creation_date = data.getString("_creation_date");
        data.remove("_creation_date");
        this.version = data.getLong("@version");
        data.remove("@version");
        data.remove("@rid");
        this.data = data;
    }


    void update(JsonObject data) {
        if (!this.collection.equals(data.getString("@class"))) {
            throw new IllegalStateException("cannot update a document from a different collection than " + this.collection +
                    ": was " + data.getString("@class", ""));
        }
        data.remove("@class");
        this.id = data.getString("id");
        data.remove("id");
        this.author = data.getString("_author");
        data.remove("_author");
        this.creation_date = data.getString("_creation_date");
        data.remove("_creation_date");
        this.version = data.getLong("@version");
        data.remove("@version");
        data.remove("@rid");
        this.data.merge(data);
    }

    /**
     * Creates a new local empty document belonging to <code>collection</code>
     *
     * @param collection
     */
    public BaasDocument(String collection) {
        this(collection, null);
    }

    /**
     * Creates a new local document with fields belonging to data.
     * Note that data is copied in the collection.
     *
     * @param collection
     * @param data
     */
    public BaasDocument(String collection, JsonObject data) {
        super();
        if (collection == null || collection.length() == 0)
            throw new IllegalArgumentException("collection name cannot be null");
        this.collection = collection;
        data = checkObject(data);
        //fixme we copy the data to avoid insertion of forbidden fields, but this is a costly operation
        this.data = data == null ? new JsonObject() : data.copy();
    }

    private static JsonObject cleanObject(JsonObject data) {
        if (data == null) return new JsonObject();
        data.remove("id");
        for (String k : data.getFieldNames()) {
            char f = k.charAt(0);
            switch (f) {
                case '@':
                case '_':
                    data.remove(k);
                    break;
            }
        }
        return data;
    }


    private static JsonObject checkObject(JsonObject data) {
        if (data == null) return null;
        if (data.contains("id")) throw new IllegalArgumentException("key 'id' is reserved");
        for (String k : data.getFieldNames()) {
            char f = k.charAt(0);
            switch (f) {
                case '@':
                case '_':
                    throw new IllegalArgumentException("key names starting with '_' or '@' are reserved");
            }
        }
        return data;
    }

    private static String checkKey(String key) {
        if (key == null || key.length() == 0)
            throw new IllegalArgumentException("key cannot be empty");
        if ("id".equals(key)) throw new IllegalArgumentException("key 'id' is reserved");
        char f = key.charAt(0);
        if (f == '@' || f == '_')
            throw new IllegalArgumentException("key names starting with '_' or '@' are reserved");
        return key;
    }

    /**
     * Associate <code>name</code> key to the {@link java.lang.String} <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a non <code>null</code> {@link java.lang.String}
     * @return this document with the new mapping created
     */
    public BaasDocument putString(String name, String value) {
        data.putString(checkKey(name), value);
        return this;
    }

    /**
     * Returns the value mapped to <code>name</code> as a {@link java.lang.String}
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param name      a non <code>null</code> key
     * @param otherwise a default value
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public String getString(String name, String otherwise) {
        return data.getString(name, otherwise);
    }


    /**
     * Returns the value mapped to <code>name</code> as a {@link java.lang.String}
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public String getString(String name) {
        return data.getString(name);
    }

    /**
     * Associate <code>name</code> key to the <code>boolean</code> <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a <code>boolean</code> value
     * @return this document with the new mapping created
     */
    public BaasDocument putBoolean(String name, boolean value) {
        data.putBoolean(checkKey(name), value);
        return this;
    }

    /**
     * Returns the value mapped to <code>name</code> as a {@link java.lang.Boolean}
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public Boolean getBoolean(String name) {
        return data.getBoolean(name);
    }


    /**
     * Returns the value mapped to <code>name</code> as a <code>boolean</code>
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param otherwise a <code>boolean</code> default
     * @param name      a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public boolean getBoolean(String name, boolean otherwise) {
        return data.getBoolean(name, otherwise);
    }

    /**
     * Associate <code>name</code> key to the <code>long</code> <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a <code>long</code> value
     * @return this document with the new mapping created
     */
    public BaasDocument putLong(String name, long value) {
        data.putLong(checkKey(name), value);
        return this;
    }

    /**
     * Returns the value mapped to <code>name</code> as a {@link java.lang.Long}
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public Long getLong(String name) {
        return data.getLong(name);
    }

    /**
     * Returns the value mapped to <code>name</code> as a <code>long</code>
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param otherwise a <code>long</code> default
     * @param name      a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public long getLong(String name, long otherwise) {
        return data.getLong(name, otherwise);
    }

    /**
     * Associate <code>name</code> key to the <code>double</code> <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a <code>double</code> value
     * @return this document with the new mapping created
     */
    public BaasDocument putDouble(String name, double value) {
        data.putDouble(checkKey(name), value);
        return this;
    }

    /**
     * Returns the value mapped to <code>name</code> as a <code>double</code>
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param otherwise a <code>double</code> default
     * @param name      a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public double getDouble(String name, double otherwise) {
        return data.getDouble(name, otherwise);
    }

    /**
     * Returns the value mapped to <code>name</code> as a {@link java.lang.Double}
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public Double getDouble(String name) {
        return data.getDouble(name);
    }

    /**
     * Returns the value mapped to <code>name</code> as a <code>int</code>
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param otherwise a <code>int</code> default
     * @param name      a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public int getInt(String name, int otherwise) {
        return data.getInt(name, otherwise);
    }


    /**
     * Returns the value mapped to <code>name</code> as a {@link java.lang.Integer}
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public Integer getInt(String name) {
        return data.getInt(name);
    }

    /**
     * Returns the value mapped to <code>name</code> as a <code>float</code>
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param otherwise a <code>float</code> default
     * @param name      a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public float getFloat(String name, float otherwise) {
        return data.getFloat(name, otherwise);
    }


    /**
     * Returns the value mapped to <code>name</code> as a {@link java.lang.Float}
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public Float getFloat(String name) {
        return data.getFloat(name);
    }

    /**
     * Puts an explicit mapping to from <code>name</code> to <code>null</code>
     * in this document.
     * <p/>
     * This is different from not having the mapping at all, to completely remove
     * the mapping use instead {@link com.baasbox.android.BaasDocument#remove(String)}
     *
     * @param name a non <code>null</code> key
     * @return this document with the new mapping created
     * @see com.baasbox.android.BaasDocument#remove(String)
     */
    public BaasDocument putNull(String name) {
        data.putNull(checkKey(name));
        return this;
    }

    /**
     * Checks if <code>name</code> maps explicitly to <code>null</code>
     *
     * @param name a non <code>null</code> key
     * @return <code>true</code> if the document contains a mapping from <code>name</code> to <code>null</code>
     * <code>false</code> otherwise
     */
    public boolean isNull(String name) {
        return data.isNull(name);
    }

    /**
     * Associate <code>name</code> key to the {@link com.baasbox.android.json.JsonArray} <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a non <code>null</code> {@link com.baasbox.android.json.JsonArray}
     * @return this document with the new mapping created
     */
    public BaasDocument putArray(String name, JsonArray value) {
        data.putArray(checkKey(name), value);
        return this;
    }


    /**
     * Returns the value mapped to <code>name</code> as a {@link com.baasbox.android.json.JsonArray}
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public JsonArray getArray(String name) {
        return data.getArray(name);
    }


    /**
     * Returns the value mapped to <code>name</code> as a {@link com.baasbox.android.json.JsonArray}
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param name      a non <code>null</code> key
     * @param otherwise a default value
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public JsonArray getArray(String name, JsonArray otherwise) {
        return data.getArray(name, otherwise);
    }


    /**
     * Associate <code>name</code> key to the {@link com.baasbox.android.json.JsonObject} <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a non <code>null</code> {@link com.baasbox.android.json.JsonObject}
     * @return this document with the new mapping created
     */
    public BaasDocument putObject(String name, JsonObject value) {
        data.putObject(checkKey(name), value);
        return this;
    }

    /**
     * Returns the value mapped to <code>name</code> as a {@link com.baasbox.android.json.JsonObject}
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public JsonObject getObject(String name) {
        return data.getObject(name);
    }


    /**
     * Returns the value mapped to <code>name</code> as a {@link com.baasbox.android.json.JsonObject}
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param name      a non <code>null</code> key
     * @param otherwise a default value
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public JsonObject getObject(String name, JsonObject otherwise) {
        return data.getObject(name, otherwise);
    }

    /**
     * Associate <code>name</code> key to the {@link com.baasbox.android.json.JsonStructure} <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a non <code>null</code> {@link com.baasbox.android.json.JsonStructure}
     * @return this document with the new mapping created
     * @see com.baasbox.android.BaasDocument#putArray(String, com.baasbox.android.json.JsonArray)
     * @see com.baasbox.android.BaasDocument#putObject(String, com.baasbox.android.json.JsonObject)
     */
    public BaasDocument putStructure(String name, JsonStructure value) {
        data.putStructure(checkKey(name), value);
        return this;
    }

    /**
     * Returns the value mapped to <code>name</code> as a {@link com.baasbox.android.json.JsonStructure}
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public JsonStructure getStructure(String name) {
        return data.getStructure(name);
    }


    /**
     * Returns the value mapped to <code>name</code> as a {@link com.baasbox.android.json.JsonStructure}
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param name      a non <code>null</code> key
     * @param otherwise a default value
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public JsonStructure getStructure(String name, JsonStructure otherwise) {
        return data.getStructure(name, otherwise);
    }

    /**
     * Associate <code>name</code> key to the <code>byte[]</code> <code>value</code>
     * in this document.
     * Binary data will be encoded using base64 during serialization.
     *
     * @param name  a non <code>null</code> key
     * @param value a non <code>null</code> <code>byte[]</code> array
     * @return this document with the new mapping created
     */
    public BaasDocument putBinary(String name, byte[] value) {
        data.putBinary(checkKey(name), value);
        return this;
    }

    /**
     * Returns the value mapped to <code>name</code> as a <code>byte[]</code> array
     * or <code>null</code> if the mapping is absent.
     *
     * @param name a non <code>null</code> key
     * @return the value mapped to <code>name</code> or <code>null</code>
     */
    public byte[] getBinary(String name) {
        return data.getBinary(name);
    }


    /**
     * Returns the value mapped to <code>name</code> as a <code>byte[]</code> array
     * or <code>otherwise</code> if the mapping is absent.
     *
     * @param name      a non <code>null</code> key
     * @param otherwise a default value
     * @return the value mapped to <code>name</code> or <code>otherwise</code>
     */
    public byte[] getBinary(String name, byte[] otherwise) {
        return data.getBinary(name, otherwise);
    }

    /**
     * Removes the mapping with <code>name</code> key from the document.
     *
     * @param name a non <code>null</code> key
     * @return the value that was mapped to <code>name</code> if present or <code>null</code>
     */
    public Object remove(String name) {
        return data.remove(name);
    }

    /**
     * Checks if this document contains a mapping with <code>name</code> key
     *
     * @param name a non <code>null</code> key
     * @return <code>true</code> if the document contains the mapping <code>false</code> otherwise
     */
    public boolean contains(String name) {
        return data.contains(name);
    }

    /**
     * Returns a {@link java.util.Set<java.lang.String>} of all the keys contained in this document
     *
     * @return a set of the keys contained in this document
     */
    public Set<String> getFieldNames() {
        return data.getFieldNames();
    }

    /**
     * Returns the number of mappings contained in this document.
     *
     * @return the number of mappings contained in this document.
     */
    public int size() {
        return data.size();
    }

    /**
     * Removes all the mappings from this document
     *
     * @return this document with no mappings
     */
    public BaasDocument clear() {
        data.clear();
        return this;
    }

    /**
     * Returns a {@link com.baasbox.android.json.JsonArray} representation
     * of the values contained in this document.
     *
     * @return a {@link com.baasbox.android.json.JsonArray} representation
     * of the values
     */
    public JsonArray values() {
        return data.values();
    }

    /**
     * Merges the content of <code>other</code> into this
     * document overwriting any mapping for wich other contains a key.
     * Note that other is copied before merging.
     *
     * @param other {@link com.baasbox.android.json.JsonObject}
     * @return this document with <code>other</code> mappings merged in
     */
    public BaasDocument merge(JsonObject other) {
        JsonObject o = checkObject(other);
        data.merge(o == null ? o : o.copy());
        return this;
    }


    /**
     * Returns an {@link java.util.Iterator} over the mappings of this document
     *
     * @return an iterator over the mappings of this document
     */
    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return data.iterator();
    }

    /**
     * Returns the id of this document
     *
     * @return
     */
    @Override
    public final String getId() {
        return id;
    }

    /**
     * Returns the author of this document
     *
     * @return
     */
    @Override
    public final String getAuthor() {
        return author;
    }

    /**
     * Returns the collection to which this document belongs
     *
     * @return
     */
    public final String getCollection() {
        return collection;
    }

    /**
     * The creation date of this document as a {@link java.lang.String}
     *
     * @return
     */
    @Override
    public final String getCreationDate() {
        return creation_date;
    }


    /**
     * Returns the version number of this document
     *
     * @return a <code>long</code> representing the version of this data
     */
    @Override
    public final long getVersion() {
        return version;
    }

    ///--------------------- REQUESTS ------------------------------

    /**
     * Asynchronously retrieves the list of documents readable to the user in <code>collection</code>.
     * This method uses default {@link com.baasbox.android.Priority#NORMAL} and not tag.
     *
     * @param collection the collection to retrieve not <code>null</code>
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken fetchAll(String collection, BaasHandler<List<BaasDocument>> handler) {
        return fetchAll(collection, null, null, handler);
    }

    /**
     * Asynchronously retrieves the list of documents readable to the user that match <code>filter</code>
     * in <code>collection</code>
     * This method uses default {@link com.baasbox.android.Priority#NORMAL} and no tag.
     *
     * @param collection the collection to retrieve not <code>null</code>
     * @param filter     a filter to apply to the request
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken fetchAll(String collection, Filter filter, BaasHandler<List<BaasDocument>> handler) {
        return fetchAll(collection, filter, null, handler);
    }

    /**
     * Asynchronously retrieves the list of documents readable to the user
     * in <code>collection</code>
     *
     * @param collection the collection to retrieve not <code>null</code>
     * @param priority   at which this request will be executed
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken fetchAll(String collection, Filter filter, Priority priority, BaasHandler<List<BaasDocument>> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (collection == null) throw new NullPointerException("collection cannot be null");
        Fetch f = new Fetch(box, collection, filter, priority, handler);
        return box.submitAsync(f);
    }

    public static BaasResult<List<BaasDocument>> fetchAllSync(String collection) {
        return fetchAllSync(collection, null);
    }

    /**
     * Synchronously retrieves the list of documents readable to the user
     * in <code>collection</code>
     *
     * @param collection the collection to retrieve not <code>null</code>
     * @return the result of the request
     */
    public static BaasResult<List<BaasDocument>> fetchAllSync(String collection, Filter filter) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (collection == null) throw new NullPointerException("collection cannot be null");
        Fetch f = new Fetch(box, collection, filter, null, null);
        return box.submitSync(f);
    }

    /**
     * Asynchronously retrieves the number of documents readable to the user in <code>collection</code>.
     * This method use default {@link com.baasbox.android.Priority#NORMAL} and no <code>tag</code>.
     *
     * @param collection the collection to count not <code>null</code>
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken count(String collection, BaasHandler<Long> handler) {
        return count(collection, null, null, handler);
    }

    /**
     * Asynchronously retrieves the number of documents readable to the user that match the <code>filter</code>
     * in <code>collection</code>.
     * This method use default {@link com.baasbox.android.Priority#NORMAL} and no <code>tag</code>.
     *
     * @param collection the collection to count not <code>null</code>
     * @param filter     a {@link com.baasbox.android.Filter} to apply to the request. May be <code>null</code>
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken count(String collection, Filter filter, BaasHandler<Long> handler) {
        return count(collection, filter, null, handler);
    }

    /**
     * Asynchronously retrieves the number of documents readable to the user in <code>collection</code>
     *
     * @param collection the collection to count not <code>null</code>
     * @param priority   at which this request will be executed
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken count(String collection, Priority priority, BaasHandler<Long> handler) {
        return count(collection, null, priority, handler);
    }


    /**
     * Asynchronously retrieves the number of documents readable to the user that match the <code>filter</code>
     * in <code>collection</code>
     *
     * @param collection the collection to count not <code>null</code>
     * @param filter     a {@link com.baasbox.android.Filter} to apply to the request. May be <code>null</code>
     * @param priority   at which this request will be executed
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    private static RequestToken count(String collection, Filter filter, Priority priority, BaasHandler<Long> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (collection == null) throw new NullPointerException("collection cannot be null");
        Count count = new Count(box, collection, filter, priority, handler);
        return box.submitAsync(count);
    }


    /**
     * Synchronously retrieves the number of document readable to the user in <code>collection</code>
     *
     * @param collection the collection to count not <code>null</code>
     * @return the result of the request
     */
    public static BaasResult<Long> counSync(String collection) {
        return countSync(collection, null);
    }

    /**
     * Synchronously retrieves the number of document readable to the user that match <code>filter</code>
     * in <code>collection</code>
     *
     * @param collection the collection to count not <code>null</code>
     * @param filter     a filter to apply to the request
     * @return the result of the request
     */
    public static BaasResult<Long> countSync(String collection, Filter filter) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (collection == null) throw new NullPointerException("collection cannot be null");
        Count request = new Count(box, collection, filter, null, null);
        return box.submitSync(request);
    }

    /**
     * Asynchronously fetches the document identified by <code>id</code> in <code>collection</code>
     * This requests uses default {@link com.baasbox.android.Priority#NORMAL} and no tag.
     *
     * @param collection the collection to retrieve the document from. Not <code>null</code>
     * @param id         the id of the document to retrieve. Not <code>null</code>
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken fetch(String collection, String id, BaasHandler<BaasDocument> handler) {
        return fetch(collection, id, null, handler);
    }

    private static RequestToken fetch(String collection, String id, Priority priority, BaasHandler<BaasDocument> handler) {
        if (collection == null) throw new NullPointerException("collection cannot be null");
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        BaasDocument doc = new BaasDocument(collection);
        doc.id = id;
        return doc.refresh(priority, handler);
    }

    /**
     * Asynchronously refresh the content of this document.
     *
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     * @throws java.lang.IllegalStateException if this document has no id
     */
    public RequestToken refresh(BaasHandler<BaasDocument> handler) {
        return refresh(null, handler);
    }

    /**
     * Asynchronously refresh the content of this document.
     *
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     * @throws java.lang.IllegalStateException if this document has no id
     */
    private RequestToken refresh(Priority priority, BaasHandler<BaasDocument> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (handler == null) throw new NullPointerException("handler cannot be null");
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        Refresh refresh = new Refresh(box, this, priority, handler);
        return box.submitAsync(refresh);
    }

    /**
     * Synchronously fetches a document from the server
     *
     * @param collection the collection to retrieve the document from. Not <code>null</code>
     * @param id         the id of the document to retrieve. Not <code>null</code>
     * @return the result of the request
     */
    public static BaasResult<BaasDocument> fetchSync(String collection, String id) {
        if (collection == null) throw new NullPointerException("collection cannot be null");
        if (id == null) throw new NullPointerException("id cannot be null");
        BaasDocument doc = new BaasDocument(collection);
        doc.id = id;
        return doc.refreshSync();
    }

    /**
     * Synchronously refresh the content of this document
     *
     * @return the result of the request
     * @throws java.lang.IllegalStateException if this document has no id
     */
    public BaasResult<BaasDocument> refreshSync() {
        BaasBox box = BaasBox.getDefaultChecked();
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        Refresh refresh = new Refresh(box, this, null, null);
        return box.submitSync(refresh);
    }


    public RequestToken delete(BaasHandler<Void> handler) {
        return delete(null, handler);
    }

    public RequestToken delete(Priority priority, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        Delete delete = new Delete(box, this, priority, handler);
        return box.submitAsync(delete);
    }


    public static RequestToken delete(String collection, String id, BaasHandler<Void> handler) {
        return delete(collection, id, null, handler);
    }

    public static RequestToken delete(String collection, String id, Priority priority, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (collection == null) throw new NullPointerException("collection cannot be null");
        if (id == null) throw new NullPointerException("id cannot be null");
        Delete delete = new Delete(box, collection, id, priority, handler);
        return box.submitAsync(delete);
    }

    public BaasResult<Void> deleteSync() {
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        BaasBox box = BaasBox.getDefaultChecked();
        Delete delete = new Delete(box, this, null, null);
        return box.submitSync(delete);
    }

    public static BaasResult<Void> deleteSync(String collection, String id) {
        if (collection == null) throw new NullPointerException("collection cannot be null");
        if (id == null) throw new NullPointerException("id cannot be null");
        BaasBox box = BaasBox.getDefaultChecked();
        Delete delete = new Delete(box, collection, id, null, null);
        return box.submitSync(delete);
    }

    private final static class Delete extends NetworkTask<Void> {

        private final BaasDocument document;
        private final String id;
        private final String collection;

        protected Delete(BaasBox box, String collection, String id, Priority priority, BaasHandler<Void> handler) {
            super(box, priority, handler);
            this.document = null;
            this.collection = collection;
            this.id = id;
        }

        protected Delete(BaasBox box, BaasDocument document, Priority priority, BaasHandler<Void> handler) {
            super(box, priority, handler);
            this.document = document;
            this.collection = document.collection;
            this.id = document.id;
        }

        @Override
        protected Void onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            if (document != null) document.id = null;
            return null;
        }

        @Override
        protected Void onSkipRequest() throws BaasException {
            throw new BaasException("document is not bound to an instance on the server");
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            if (id == null) {
                return null;
            } else {
                String endpoint = box.requestFactory.getEndpoint("document/?/?", collection, id);
                return box.requestFactory.delete(endpoint);
            }
        }
    }

    public static RequestToken create(String collection, Priority priority, BaasHandler<BaasDocument> handler) {
        if (collection == null) throw new NullPointerException("collection cannot be null");
        BaasDocument doc = new BaasDocument(collection);
        return doc.save(SaveMode.IGNORE_VERSION, priority, handler);
    }

    public static RequestToken create(String collection, BaasHandler<BaasDocument> handler) {
        if (collection == null) throw new NullPointerException("collection cannot be null");
        BaasDocument doc = new BaasDocument(collection);
        return doc.save(SaveMode.IGNORE_VERSION, null, handler);
    }

    public RequestToken save(SaveMode mode, BaasHandler<BaasDocument> handler) {
        return save(mode, null, handler);
    }

    public RequestToken save(SaveMode mode, Priority priority, BaasHandler<BaasDocument> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (mode == null) throw new NullPointerException("mode cannot be null");
        Save save = new Save(box, mode, this, priority, handler);
        return box.submitAsync(save);
    }

    public BaasResult<BaasDocument> saveSync(SaveMode mode) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (mode == null) throw new NullPointerException("mode cannot be null");
        Save save = new Save(box, mode, this, null, null);
        return box.submitSync(save);
    }

    public static BaasResult<BaasDocument> createSync(String collection) {
        BaasDocument doc = new BaasDocument(collection);
        return doc.saveSync(SaveMode.IGNORE_VERSION);
    }

    @Override
    public RequestToken revokeAll(Grant grant, String role, Priority priority, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, false, true, collection, id, role, grant, priority, handler);
        return box.submitAsync(access);
    }

    @Override
    public BaasResult<Void> revokeAllSync(Grant grant, String role) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, false, true, collection, id, role, grant, null, null);
        return box.submitSync(access);
    }

    @Override
    public RequestToken revoke(Grant grant, String username, Priority priority, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, false, false, collection, id, username, grant, priority, handler);
        return box.submitAsync(access);
    }

    @Override
    public BaasResult<Void> revokeSync(Grant grant, String username) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, false, false, collection, id, username, grant, null, null);
        return box.submitSync(access);
    }

    @Override
    public RequestToken grantAll(Grant grant, String role, Priority priority, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, true, true, collection, id, role, grant, priority, handler);
        return box.submitAsync(access);
    }


    @Override
    public BaasResult<Void> grantAllSync(Grant grant, String role) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, true, true, collection, id, role, grant, null, null);
        return box.submitSync(access);
    }

    @Override
    public RequestToken grant(Grant grant, String username, Priority priority, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, true, false, collection, id, username, grant, priority, handler);
        return box.submitAsync(access);
    }

    @Override
    public BaasResult<Void> grantSync(Grant grant, String username) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, true, false, collection, id, username, grant, null, null);
        return box.submitSync(access);
    }

    private static final class Save extends NetworkTask<BaasDocument> {
        private final BaasDocument document;
        private final SaveMode mode;
        private JsonObject data;

        protected Save(BaasBox box, SaveMode mode, BaasDocument document, Priority priority, BaasHandler<BaasDocument> handler) {
            super(box, priority, handler);
            this.document = document;
            this.data = document.data.copy();
            this.mode = mode;
        }

        @Override
        protected BaasDocument onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            JsonObject data = parseJson(response, box).getObject("data");
            document.update(data);
            return document;
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            String collection = document.collection;
            String id = document.id;
            if (id == null) {
                String endpoint = box.requestFactory.getEndpoint("document/?", collection);
                return box.requestFactory.post(endpoint, data);
            } else {
                String endpoint = box.requestFactory.getEndpoint("document/?/?", collection, id);
                if (mode == SaveMode.CHECK_VERSION) {
                    data.putLong("@version", document.version);
                }
                return box.requestFactory.put(endpoint, data);

            }
        }

    }

    private final static class Access extends BaasObject.Access {

        protected Access(BaasBox box, boolean add, boolean isRole, String collection, String id, String to, Grant grant, Priority priority, BaasHandler<Void> handler) {
            super(box, add, isRole, collection, id, to, grant, priority, handler);
        }

        @Override
        protected String userGrant(RequestFactory factory, Grant grant, String collection, String id, String to) {
            return factory.getEndpoint("document/?/?/?/user/?", collection, id, grant.action, to);
        }

        @Override
        protected String roleGrant(RequestFactory factory, Grant grant, String collection, String id, String to) {
            return factory.getEndpoint("document/?/?/?/role/?", collection, id, grant.action, to);
        }
    }

    private final static class Refresh extends NetworkTask<BaasDocument> {
        private final BaasDocument document;

        protected Refresh(BaasBox box, BaasDocument doc, Priority priority, BaasHandler<BaasDocument> handler) {
            super(box, priority, handler);
            this.document = doc;
        }

        @Override
        protected BaasDocument onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            JsonObject object = parseJson(response, box).getObject("data");
            document.update(object);
            return document;
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            String endpoint = box.requestFactory.getEndpoint("document/?/?", document.collection, document.id);
            return box.requestFactory.get(endpoint);
        }
    }

    private final static class Fetch extends NetworkTask<List<BaasDocument>> {
        private final String collection;
        private final RequestFactory.Param[] filter;

        protected Fetch(BaasBox box, String collection, Filter filter, Priority priority, BaasHandler<List<BaasDocument>> handler) {
            super(box, priority, handler);
            this.collection = collection;
            this.filter = filter == null ? null : filter.toParams();
        }

        @Override
        protected List<BaasDocument> onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            JsonObject data = parseJson(response, box).getObject("data");
            if (data == null) {
                return Collections.emptyList();
            } else {
                ArrayList<BaasDocument> res = new ArrayList<BaasDocument>();
                for (Object obj : data) {
                    res.add(new BaasDocument((JsonObject) obj));
                }
                return res;
            }
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            String ep = box.requestFactory.getEndpoint("document/?", collection);
            if (filter == null) {
                return box.requestFactory.get(ep);
            } else {
                return box.requestFactory.get(ep, filter);
            }
        }
    }

    private final static class Count extends NetworkTask<Long> {
        private final String collection;
        private final RequestFactory.Param[] params;

        protected Count(BaasBox box, String collection, Filter filter, Priority priority, BaasHandler<Long> handler) {
            super(box, priority, handler);
            this.collection = collection;
            this.params = filter == null ? null : filter.toParams();
        }

        @Override
        protected Long onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            return parseJson(response, box).getObject("data").getLong("count");
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            String ep = box.requestFactory.getEndpoint("document/?/count", collection);
            if (params == null) {
                return box.requestFactory.get(ep, params);
            } else {
                return box.requestFactory.get(ep);
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(collection);
        writeOptString(dest, id);
        dest.writeLong(version);
        writeOptString(dest, author);
        writeOptString(dest, creation_date);
        dest.writeParcelable(data, 0);
    }

    BaasDocument(Parcel source) {
        this.collection = source.readString();
        this.id = readOptString(source);
        this.version = source.readLong();
        this.author = readOptString(source);
        this.creation_date = readOptString(source);
        this.data = source.readParcelable(JsonObject.class.getClassLoader());
    }

    public static Creator<BaasDocument> CREATOR = new Creator<BaasDocument>() {
        @Override
        public BaasDocument createFromParcel(Parcel source) {

            return new BaasDocument(source);
        }

        @Override
        public BaasDocument[] newArray(int size) {
            return new BaasDocument[size];
        }
    };

    private final static String readOptString(Parcel p) {
        boolean read = p.readByte() == 1;
        if (read) {
            return p.readString();
        }
        return null;
    }

    private final static void writeOptString(Parcel p, String s) {
        if (s == null) {
            p.writeByte((byte) 0);
        } else {
            p.writeByte((byte) 1);
            p.writeString(s);
        }
    }
}
