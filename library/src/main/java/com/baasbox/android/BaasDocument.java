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
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.baasbox.android;


import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.baasbox.android.impl.Logger;
import com.baasbox.android.impl.Util;
import com.baasbox.android.json.JsonArray;
import com.baasbox.android.json.JsonObject;
import com.baasbox.android.json.JsonStructure;
import com.baasbox.android.net.HttpRequest;
import com.baasbox.android.net.HttpResponse;

import java.util.*;

/**
 * Represents a BaasBox document.
 * <p>
 * A document is a schema less JSON like entity that belongs to a given collection on the server.
 * </p>
 * <p>
 * Documents can be created, stored and retrieved from the server, either synchronously or asynchronously,
 * through the provided methods.
 * </p>
 * <p>
 * Unlike a raw JSON document, some key names at the top level are reserved: you cannot assign or create properties whose names begin with
 * an underscore or an at sign, the <em>id</em> field is also reserved.
 * </p>
 * <p>
 * When a document is bound to an entity on the server it's 'id' can be retrieved through {@link #getId()}.
 * Documents are versioned by BaasBox and updates with incompatible versions will fail, uless you ignore the
 * versioning explicitly through {@link com.baasbox.android.SaveMode#IGNORE_VERSION}.
 * </p>
 *
 * @author Andrea Tortorella
 * @since 0.7.3
 */
public final class BaasDocument extends BaasObject implements Iterable<Map.Entry<String, Object>>, Parcelable {
// ------------------------------ FIELDS ------------------------------

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

    private final JsonWrapper data;
    private final String collection;
    private String id;
    private String author;
    private String creation_date;
    private String rid;
    private long version;
    private BaasACL acl;

// --------------------------- CONSTRUCTORS ---------------------------

    /**
     * Returns a new BaasDocument from it's raw json representation
     * @param data
     * @return a new BaasDocument
     */
    public static BaasDocument from(JsonObject data){
        return new BaasDocument(data);
    }

    public static BaasDocument create(String collection,String id){
        BaasDocument doc = new BaasDocument(collection);
        doc.id = id;
        return doc;
    }

    public static BaasDocument create(String collection){
        return new BaasDocument(collection);
    }

    public static BaasDocument create(String collection,JsonObject initialData){
        return new BaasDocument(collection,initialData);
    }


    BaasDocument(JsonObject o) {
        super();
        JsonWrapper data = new JsonWrapper(o);
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
        this.rid=data.getString("@rid");
        data.remove("@rid");
        this.data = data;
    }


    @Override
    public final boolean isDocument() {
        return true;
    }

    @Override
    public final boolean isFile() {
        return false;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = data.copy();
        json.put("@class", collection);
        json.put("id", id);
        json.put("_author", author);
        json.put("_creation_date", creation_date);
        json.put("@version", version);
        json.put("@rid", rid);
        return json;
    }

    /**
     * Creates a new unbound empty document belonging to the given <code>collection</code>
     *
     * @param collection a non empty collection name.
     * @throws java.lang.IllegalArgumentException if collection name is empty
     */
    public BaasDocument(String collection) {
        this(collection, (JsonObject) null);
    }

    BaasDocument(Parcel source) {
        this.collection = source.readString();
        this.id = Util.readOptString(source);
        this.version = source.readLong();
        this.author = Util.readOptString(source);
        this.creation_date = Util.readOptString(source);
        this.rid= Util.readOptString(source);
        this.data = source.readParcelable(JsonWrapper.class.getClassLoader());
    }

    /**
     * Creates a new unbound document that belongs to the given <code>collection</code>
     * and with fields initialized from the <code>data</code> {@link com.baasbox.android.json.JsonObject}.
     * <p/>
     * Data cannot contain reserved property names at the top level.
     * Note that the JSON data is copied in the document, so modifications to the original instance will
     * not be reflected by the document.
     *
     * @param collection a non empty collection name.
     * @param data       a possibly null {@link com.baasbox.android.json.JsonObject}
     * @throws java.lang.IllegalArgumentException if collection name is empty or data contains reserved fields
     */
    public BaasDocument(String collection, JsonObject data) {
        super();
        if (collection == null || collection.length() == 0)
            throw new IllegalArgumentException("collection name cannot be null");
        this.collection = collection;
        data = checkObject(data);
        //fixme we copy the data to avoid insertion of forbidden fields, but this is a costly operation
        this.data = new JsonWrapper(data);
        this.data.setDirty(true);
    }

    private static JsonWrapper checkObject(JsonObject data) {
        if (data == null) return null;
        if (data.contains("id")) throw new IllegalArgumentException("key 'id' is reserved");
        for (String k : data.fields()) {
            char f = k.charAt(0);
            switch (f) {
                case '@':
                case '_':
                    throw new IllegalArgumentException("key names starting with '_' or '@' are reserved");
            }
        }
        return new JsonWrapper(data);
    }

    /**
     * Creates a new unbound document that belongs to the given <code>collection</code>
     * and with fields initialized from the <code>values</code> {@link android.content.ContentValues}
     * values are converted to a {@link com.baasbox.android.json.JsonObject}
     * using {@link com.baasbox.android.json.JsonObject#from(android.content.ContentValues)}
     *
     * @param collection a non empty collection name.
     * @param values     a possibly null {@link android.content.ContentValues}
     * @throws java.lang.IllegalArgumentException if collection name is empty or values contains reserved fields names
     */
    public BaasDocument(String collection, ContentValues values) {
        super();
        if (collection == null || collection.length() == 0)
            throw new IllegalArgumentException("collection name cannot be null");
        this.collection = collection;
        this.data = values == null ? new JsonWrapper() : checkObject(JsonObject.from(values));
    }


    ///--------------------- REQUESTS ------------------------------

    /**
     * Asynchronously retrieves the list of documents readable to the user in <code>collection</code>.
     *
     * @param collection the collection to retrieve not <code>null</code>
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken fetchAll(String collection, BaasHandler<List<BaasDocument>> handler) {
        return fetchAll(collection, null, RequestOptions.DEFAULT, handler);
    }

    /**
     * Asynchronously retrieves the list of documents readable to the user that match <code>filter</code>
     * in <code>collection</code>
     *
     * @param collection the collection to retrieve not <code>null</code>
     * @param filter     a filter to apply to the request
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken fetchAll(String collection, BaasQuery.Criteria filter, BaasHandler<List<BaasDocument>> handler) {
        return fetchAll(collection, filter, RequestOptions.DEFAULT, handler);
    }

    /**
     * Asynchronously retrieves the list of documents readable to the user
     * in <code>collection</code>
     *
     * @param collection the collection to retrieve not <code>null</code>
     * @param flags {@link RequestOptions}
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken fetchAll(String collection, BaasQuery.Criteria filter, int flags, BaasHandler<List<BaasDocument>> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (collection == null) throw new IllegalArgumentException("collection cannot be null");
        Fetch f = new Fetch(box, collection, filter, flags, handler);
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
    public static BaasResult<List<BaasDocument>> fetchAllSync(String collection, BaasQuery.Criteria filter) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (collection == null) throw new IllegalArgumentException("collection cannot be null");
        Fetch f = new Fetch(box, collection, filter, RequestOptions.DEFAULT, null);
        return box.submitSync(f);
    }

    /**
     * Asynchronously retrieves the number of documents readable to the user in <code>collection</code>.
     *
     * @param collection the collection to doCount not <code>null</code>
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken count(String collection, BaasHandler<Long> handler) {
        return doCount(collection, null, RequestOptions.DEFAULT, handler);
    }

    /**
     * Asynchronously retrieves the number of documents readable to the user that match the <code>filter</code>
     * in <code>collection</code>.
     *
     * @param collection the collection to doCount not <code>null</code>
     * @param filter     a {@link BaasQuery.Criteria} to apply to the request. May be <code>null</code>
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken count(String collection, BaasQuery.Criteria filter, BaasHandler<Long> handler) {
        return doCount(collection, filter, RequestOptions.DEFAULT, handler);
    }

    /**
     * Asynchronously retrieves the number of documents readable to the user in <code>collection</code>
     *
     * @param collection the collection to doCount not <code>null</code>
     * @param flags {@link RequestOptions}
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken count(String collection, int flags, BaasHandler<Long> handler) {
        return doCount(collection, null, flags, handler);
    }

    /**
     * Asynchronously retrieves the number of documents readable to the user that match the <code>filter</code>
     * in <code>collection</code>
     *
     * @param collection the collection to doCount not <code>null</code>
     * @param filter     a {@link BaasQuery.Criteria} to apply to the request. May be <code>null</code>
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    private static RequestToken doCount(String collection, BaasQuery.Criteria filter, int flags, final BaasHandler<Long> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        filter = filter==null?BaasQuery.builder().count(true).criteria()
                             :filter.buildUpon().count(true).criteria();
        
        if (collection == null) throw new IllegalArgumentException("collection cannot be null");

        Count count = new Count(box, collection, filter, flags, handler);
        return box.submitAsync(count);
    }

    /**
     * Synchronously retrieves the number of document readable to the user in <code>collection</code>
     *
     * @param collection the collection to doCount not <code>null</code>
     * @return the result of the request
     */
    public static BaasResult<Long> countSync(String collection) {
        return countSync(collection, null);
    }

    /**
     * Synchronously retrieves the number of document readable to the user that match <code>filter</code>
     * in <code>collection</code>
     *
     * @param collection the collection to doCount not <code>null</code>
     * @param filter     a filter to apply to the request
     * @return the result of the request
     */
    public static BaasResult<Long> countSync(String collection, BaasQuery.Criteria filter) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (collection == null) throw new IllegalArgumentException("collection cannot be null");
        filter = filter==null?BaasQuery.builder().count(true).criteria()
                             :filter.buildUpon().count(true).criteria();
        Count request = new Count(box, collection, filter, RequestOptions.DEFAULT, null);
        return box.submitSync(request);
    }

    /**
     * Asynchronously fetches the document identified by <code>id</code> in <code>collection</code>
     *
     * @param collection the collection to retrieve the document from. Not <code>null</code>
     * @param id         the id of the document to retrieve. Not <code>null</code>
     * @param withAcl if true will fetch acl
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken fetch(String collection, String id,boolean withAcl, BaasHandler<BaasDocument> handler) {
        return doFetch(collection, id, withAcl, RequestOptions.DEFAULT, handler);
    }
    
    /**
     * Asynchronously fetches the document identified by <code>id</code> in <code>collection</code>
     *
     * @param collection the collection to retrieve the document from. Not <code>null</code>
     * @param id         the id of the document to retrieve. Not <code>null</code>
     * @param handler    a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken fetch(String collection, String id, BaasHandler<BaasDocument> handler) {
        return doFetch(collection, id, false, RequestOptions.DEFAULT, handler);
    }

    
    private static RequestToken doFetch(String collection, String id,boolean withAcl, int flags, BaasHandler<BaasDocument> handler) {
        if (collection == null) throw new IllegalArgumentException("collection cannot be null");
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        BaasDocument doc = new BaasDocument(collection);
        doc.id = id;
        return doc.doRefresh(withAcl, flags, handler);
    }

    private RequestToken doRefresh(boolean withAcl,int flags, BaasHandler<BaasDocument> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (handler == null) throw new IllegalArgumentException("handler cannot be null");
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        Refresh refresh = new Refresh(box, this,withAcl, flags, handler);
        return box.submitAsync(refresh);
    }

    /**
     * Synchronously fetches a document from the server
     *
     * @param collection the collection to retrieve the document from. Not <code>null</code>
     * @param id         the id of the document to retrieve. Not <code>null</code>
     * @return the result of the request
     */
    public static BaasResult<BaasDocument> fetchSync(String collection,String id){
        return fetchSync(collection,id,false);
    }
    
    /**
     * Synchronously fetches a document from the server
     *
     * @param collection the collection to retrieve the document from. Not <code>null</code>
     * @param id         the id of the document to retrieve. Not <code>null</code>
     * @param withAcl if true will fetch acl
     * @return the result of the request
     */
    public static BaasResult<BaasDocument> fetchSync(String collection, String id,boolean withAcl) {
        if (collection == null) throw new IllegalArgumentException("collection cannot be null");
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        BaasDocument doc = new BaasDocument(collection);
        doc.id = id;
        return doc.refreshSync(withAcl);
    }



    /**
     * Asynchronously refresh the content of this document.
     * *
     * @param withAcl if true will fetch acl
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     * @throws java.lang.IllegalStateException if this document has no id
     */
    public RequestToken refresh(boolean withAcl,BaasHandler<BaasDocument> handler){
        return doRefresh(withAcl,RequestOptions.DEFAULT,handler);
    }

    /**
     * Asynchronously refresh the content of this document.
     *
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     * @throws java.lang.IllegalStateException if this document has no id
     */
    public RequestToken refresh(BaasHandler<BaasDocument> handler) {
        return doRefresh(false,RequestOptions.DEFAULT, handler);
    }



    /**
     * Synchronously refresh the content of this document
     *
     * @return the result of the request
     * @throws java.lang.IllegalStateException if this document has no id
     */
    public BaasResult<BaasDocument> refreshSync(){
        return refreshSync(false);
    }
    
    /**
     * Synchronously refresh the content of this document
     *
     * @param withAcl if true will fetch acl
     * @return the result of the request
     * @throws java.lang.IllegalStateException if this document has no id
     */
    public BaasResult<BaasDocument> refreshSync(boolean withAcl) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        Refresh refresh = new Refresh(box, this,withAcl, RequestOptions.DEFAULT, null);
        return box.submitSync(refresh);
    }


    /**
     * Asynchronously deletes the document with {@code id} from {@code collection} 
     * @param collection the collection of the document
     * @param id the id of the document
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken delete(String collection, String id, BaasHandler<Void> handler) {
        return delete(collection, id, RequestOptions.DEFAULT, handler);
    }

    /**
     * Asynchronously deletes the document with {@code id} from {@code collection}
     * @param collection the collection of the document
     * @param id the id of the document
     * @param flags {@link RequestOptions}
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the asynchronous request
     */
    public static RequestToken delete(String collection, String id, int flags, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (collection == null) throw new IllegalArgumentException("collection cannot be null");
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        Delete delete = new Delete(box, collection, id, flags, handler);
        return box.submitAsync(delete);
    }

    /**
     * Syncrhonously deletes the document with {@code id} from {@code collection} 
     * @param collection the collection of the document
     * @param id the id of the document
     * @return the result of the request
     */
    public static BaasResult<Void> deleteSync(String collection, String id) {
        if (collection == null) throw new IllegalArgumentException("collection cannot be null");
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        BaasBox box = BaasBox.getDefaultChecked();
        Delete delete = new Delete(box, collection, id, RequestOptions.DEFAULT, null);
        return box.submitSync(delete);
    }

    /**
     * Asynchronously deletes this document on the serve 
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the request
     */
    public RequestToken delete(BaasHandler<Void> handler) {
        return delete(RequestOptions.DEFAULT, handler);
    }


    /**
     * Asyncrhonously deletes this document on the server.
     * @param flags {@link com.baasbox.android.RequestOptions}
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the request
     */
    public RequestToken delete(int flags, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        Delete delete = new Delete(box, this, flags, handler);
        return box.submitAsync(delete);
    }

    /**
     * Syncrhonously deletes this document on the server.
     * @return the result of the request
     */
    public BaasResult<Void> deleteSync() {
        if (id == null)
            throw new IllegalStateException("this document is not bound to any remote entity");
        BaasBox box = BaasBox.getDefaultChecked();
        Delete delete = new Delete(box, this, RequestOptions.DEFAULT, null);
        return box.submitSync(delete);
    }

    /**
     * Asynchronously saves this document on the server ignoring its version.
     * @param acl {@link com.baasbox.android.BaasACL} the initial acl settings
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the request.
     */
    public RequestToken save(BaasACL acl,BaasHandler<BaasDocument> handler){
        return save(SaveMode.IGNORE_VERSION,acl,handler);
    }

    /**
     * Asynchronously saves this document on the server ignoring its version.
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the request.
     */
    public RequestToken save(BaasHandler<BaasDocument> handler) {
        return save(SaveMode.IGNORE_VERSION,null, RequestOptions.DEFAULT, handler);
    }

    /**
     * Asynchronously saves this document on the server.
     * @param mode {@link com.baasbox.android.SaveMode}
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the request.
     */
    public RequestToken save(SaveMode mode, BaasHandler<BaasDocument> handler) {
        return save(mode,null, RequestOptions.DEFAULT, handler);
    }


    /**
     * Asynchronously saves this document on the server, with initial {@link com.baasbox.android.BaasACL}.
     * @param mode {@link com.baasbox.android.SaveMode}
     * @param acl the initial acl settings 
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the request.
     */
    public RequestToken save(SaveMode mode,BaasACL acl, BaasHandler<BaasDocument> handler) {
        return save(mode,acl, RequestOptions.DEFAULT, handler);
    }

    /**
     * Asynchronously saves this document on the server, with initial {@link com.baasbox.android.BaasACL}.
     * @param mode {@link com.baasbox.android.SaveMode}
     * @param acl the initial acl settings
     * @param flags {@link com.baasbox.android.RequestOptions}
     * @param handler a callback to be invoked with the result of the request
     * @return a {@link com.baasbox.android.RequestToken} to handle the request.
     */
    public RequestToken save(SaveMode mode,BaasACL acl, int flags, BaasHandler<BaasDocument> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (mode == null) throw new IllegalArgumentException("mode cannot be null");
        Save save = new Save(box, mode,acl, this, flags, handler);
        return box.submitAsync(save);
    }


    /**
     * Synchronously saves the document on the server ignoring it's version 
     * @return the result of the request
     */
    public BaasResult<BaasDocument> saveSync() {
        return saveSync(SaveMode.IGNORE_VERSION,null);
    }

    /**
     * Synchronously saves the document on the server
     * @param mode {@link com.baasbox.android.SaveMode}
     * @return the result of the request
     */
    public BaasResult<BaasDocument> saveSync(SaveMode mode){
        return saveSync(mode,null);
    }

    /**
     * Synchronously saves the document on the server with initial acl
     * @param acl {@link com.baasbox.android.BaasACL} the initial acl settings
     * @return the result of the request
     */
    public BaasResult<BaasDocument> saveSync(BaasACL acl){
        return saveSync(SaveMode.IGNORE_VERSION,acl);
    }

    /**
     * Synchronously saves the document on the server with initial acl
     * @param mode {@link com.baasbox.android.SaveMode}
     * @param acl {@link com.baasbox.android.BaasACL} the initial acl settings
     * @return the result of the request
     */
    public BaasResult<BaasDocument> saveSync(SaveMode mode,BaasACL acl) {
        BaasBox box = BaasBox.getDefaultChecked();
        if (mode == null) throw new IllegalArgumentException("mode cannot be null");
        Save save = new Save(box, mode,acl, this, RequestOptions.DEFAULT, null);
        return box.submitSync(save);
    }

    // --------------------- GETTER / SETTER METHODS ---------------------

    @Override
    public final String getAuthor() {
        return author;
    }

    /**
     * Returns the collection to which this document belongs.
     *
     * @return the name of the collection
     */
    public final String getCollection() {
        return collection;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final long getVersion() {
        return version;
    }

    /**
     * Returns the acl of this document. It may return null if the acl is not known
     * You can refresh the acl with {@link com.baasbox.android.BaasDocument#refresh(boolean, BaasHandler)}
     * @return {@link com.baasbox.android.BaasACL}
     */
    public BaasACL getAcl(){return this.acl;}
    
// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Iterable ---------------------

    /**
     * Returns an {@link java.util.Iterator} over the mappings of this document
     *
     * @return an iterator over the mappings of this document
     */
    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return data.iterator();
    }

// --------------------- Interface Parcelable ---------------------

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(collection);
        Util.writeOptString(dest, id);
        dest.writeLong(version);
        Util.writeOptString(dest, author);
        Util.writeOptString(dest, creation_date);
        Util.writeOptString(dest, rid);
        dest.writeParcelable(data, 0);

    }

// -------------------------- OTHER METHODS --------------------------

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
     * Checks if this document contains a mapping with <code>name</code> key
     *
     * @param name a non <code>null</code> key
     * @return <code>true</code> if the document contains the mapping <code>false</code> otherwise
     */
    public boolean contains(String name) {
        return data.contains(name);
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

    @Override
    public final String getCreationDate() {
        return creation_date;
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
     * Returns a {@link java.util.Set<java.lang.String>} of all the keys contained in this document
     *
     * @return a set of the keys contained in this document
     */
    public Set<String> fields() {
        return data.fields();
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

    @Override
    public RequestToken grant(Grant grant, String username, int flags, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, true, false, collection, id, username, grant, flags, handler);
        return box.submitAsync(access);
    }

    @Override
    public RequestToken grantAll(Grant grant, String role, int flags, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, true, true, collection, id, role, grant, flags, handler);
        return box.submitAsync(access);
    }

    @Override
    public BaasResult<Void> grantAllSync(Grant grant, String role) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, true, true, collection, id, role, grant, RequestOptions.DEFAULT, null);
        return box.submitSync(access);
    }

    @Override
    public BaasResult<Void> grantSync(Grant grant, String username) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, true, false, collection, id, username, grant, RequestOptions.DEFAULT, null);
        return box.submitSync(access);
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
     * Merges the content of <code>other</code> into this
     * document overwriting any mapping for wich other contains a key.
     * Note that other is copied before merging.
     *
     * @param other {@link com.baasbox.android.json.JsonObject}
     * @return this document with <code>other</code> mappings merged in
     */
    public BaasDocument merge(JsonObject other) {
        JsonObject o = checkObject(other);
        data.merge(o);
        return this;
    }



    /**
     * Associate <code>name</code> key to the {@link com.baasbox.android.json.JsonArray} <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a {@link com.baasbox.android.json.JsonArray}
     * @return this document with the new mapping created
     */
    public BaasDocument put(String name, JsonArray value) {
        data.put(checkKey(name), value);
        return this;
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
     * Associate <code>name</code> key to the <code>byte[]</code> <code>value</code>
     * in this document.
     * Note that binary data is encoded using base64 and added as strings in the object.
     *
     * @param name  a non <code>null</code> key
     * @param value a  <code>byte[]</code> array
     * @return this document with the new mapping created
     */
    public BaasDocument put(String name, byte[] value) {
        data.put(checkKey(name), value);
        return this;
    }

    /**
     * Associate <code>name</code> key to the <code>boolean</code> <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a <code>boolean</code> value
     * @return this document with the new mapping created
     */
    public BaasDocument put(String name, boolean value) {
        data.put(checkKey(name), value);
        return this;
    }


    /**
     * Associate <code>name</code> key to the <code>double</code> <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a <code>double</code> value
     * @return this document with the new mapping created
     */
    public BaasDocument put(String name, double value) {
        data.put(checkKey(name), value);
        return this;
    }

    /**
     * Associate <code>name</code> key to the <code>long</code> <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a <code>long</code> value
     * @return this document with the new mapping created
     */
    public BaasDocument put(String name, long value) {
        data.put(checkKey(name), value);
        return this;
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
     * Associate <code>name</code> key to the {@link com.baasbox.android.json.JsonObject} <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a {@link com.baasbox.android.json.JsonObject}
     * @return this document with the new mapping created
     */
    public BaasDocument put(String name, JsonObject value) {
        data.put(checkKey(name), value);
        return this;
    }

    /**
     * Associate <code>name</code> key to the {@link java.lang.String} <code>value</code>
     * in this document.
     *
     * @param name  a non <code>null</code> key
     * @param value a  {@link java.lang.String}
     * @return this document with the new mapping created
     */
    public BaasDocument put(String name, String value) {
        data.put(checkKey(name), value);
        return this;
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

    @Override
    public RequestToken revoke(Grant grant, String username, int flags, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, false, false, collection, id, username, grant, flags, handler);
        return box.submitAsync(access);
    }

    @Override
    public RequestToken revokeAll(Grant grant, String role, int flags, BaasHandler<Void> handler) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, false, true, collection, id, role, grant, flags, handler);
        return box.submitAsync(access);
    }

    @Override
    public BaasResult<Void> revokeAllSync(Grant grant, String role) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, false, true, collection, id, role, grant, RequestOptions.DEFAULT, null);
        return box.submitSync(access);
    }

    @Override
    public BaasResult<Void> revokeSync(Grant grant, String username) {
        BaasBox box = BaasBox.getDefaultChecked();
        Access access = new Access(box, false, false, collection, id, username, grant, RequestOptions.DEFAULT, null);
        return box.submitSync(access);
    }





    public RequestToken fetchLinked(String relationship,BaasHandler<List<BaasObject>> handler){
        return fetchLinked(relationship, BaasLink.Direction.TO, BaasQuery.Criteria.ANY, RequestOptions.DEFAULT, handler);
    }

    public RequestToken fetchLinekd(String relationship,BaasLink.Direction direction,BaasHandler<List<BaasObject>> handler){
        return fetchLinked(relationship, direction, BaasQuery.Criteria.ANY, RequestOptions.DEFAULT, handler);
    }

    public RequestToken fetchLinked(String relationship,BaasLink.Direction direction,BaasQuery.Criteria criteria,BaasHandler<List<BaasObject>> handler){
        return fetchLinked(relationship,direction,criteria,RequestOptions.DEFAULT,handler);
    }

    public RequestToken fetchLinked(String relationship,
                                    BaasLink.Direction direction,
                                    BaasQuery.Criteria criteria,
                                    int flags,
                                    BaasHandler<List<BaasObject>> handler){
        BaasBox box = BaasBox.getDefault();
        if (TextUtils.isEmpty(relationship)) throw new IllegalArgumentException("Link relationship is empty");
        if (TextUtils.isEmpty(id)||TextUtils.isEmpty(collection)) throw  new IllegalStateException("document is not boundded to the server");
        if (direction == null) direction = BaasLink.Direction.TO;
        if (criteria == null) criteria = BaasQuery.Criteria.ANY;
        FetchLinked fetchLinked = new FetchManyLinked(box,flags,collection,id,relationship,direction,criteria,handler);
        return box.submitAsync(fetchLinked);
    }

    public BaasResult<List<BaasObject>> fetchLinkedSync(String relationship){
        return fetchLinkedSync(relationship, BaasLink.Direction.TO);
    }

    public BaasResult<List<BaasObject>> fetchLinkedSync(String relationship,BaasLink.Direction direction){
        return fetchLinkedSync(relationship,direction,BaasQuery.Criteria.ANY);
    }

    public BaasResult<List<BaasObject>> fetchLinkedSync(String relationship,BaasLink.Direction direction,BaasQuery.Criteria criteria){
        BaasBox box = BaasBox.getDefault();
        if (TextUtils.isEmpty(relationship)) throw new IllegalArgumentException("Link relationship is empty");
        if (TextUtils.isEmpty(id)||TextUtils.isEmpty(collection)) throw  new IllegalStateException("document is not boundded to the server");
        if (direction == null) direction = BaasLink.Direction.TO;
        if (criteria == null) criteria = BaasQuery.Criteria.ANY;
        FetchManyLinked fetchLinekd = new FetchManyLinked(box,RequestOptions.DEFAULT,collection,id,relationship,direction,criteria,null);
        return box.submitSync(fetchLinekd);
    }

   
    /**
     * Returns the number of mappings contained in this document.
     *
     * @return the number of mappings contained in this document.
     */
    public int size() {
        return data.size();
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
        this.rid=data.getString("@rid");
        data.remove("@rid");
        
        // acl
        //[{"@version":4,"@class":"ORole","name":"anonymous","isrole":true},{"@version":1,"@class":"OUser","name":"user2"}]
        JsonArray allowRead = data.getArray("_allowRead");
        JsonArray allowUpdate = data.getArray("_allowUpdate");
        JsonArray allowDelete = data.getArray("_allowDelete");
        
        data.remove("_allowRead");
        data.remove("_allowUpdate");
        data.remove("_allowDelete");
        if (allowRead != null|| allowUpdate!=null||allowDelete != null) {
            BaasACL acl = BaasACL.fromDocumentAcl(allowRead, allowUpdate, allowDelete);
            //
            this.acl = acl;
        }
        this.data.merge(data);
        this.data.setDirty(false);
    }

    @Override
    public boolean isDirty() {
        return data.isDirty();
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

// -------------------------- INNER CLASSES --------------------------

    private static final class Delete extends NetworkTask<Void> {
        private final BaasDocument document;
        private final String id;
        private final String collection;

        protected Delete(BaasBox box, String collection, String id, int flags, BaasHandler<Void> handler) {
            super(box, flags, handler);
            this.document = null;
            this.collection = collection;
            this.id = id;
        }

        protected Delete(BaasBox box, BaasDocument document, int flags, BaasHandler<Void> handler) {
            super(box, flags, handler);
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
                String endpoint = box.requestFactory.getEndpoint("document/{}/{}", collection, id);
                return box.requestFactory.delete(endpoint);
            }
        }
    }

    private static final class Save extends NetworkTask<BaasDocument> {
        private final BaasDocument document;
        private final SaveMode mode;
        private JsonObject data;
        private BaasACL acl;
        
        protected Save(BaasBox box, SaveMode mode,BaasACL acl, BaasDocument document, int flags, BaasHandler<BaasDocument> handler) {
            super(box, flags, handler);
            this.document = document;
            this.data = document.data.copy();
            this.acl = acl;
            this.mode = mode;
        }

        @Override
        protected BaasDocument onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            JsonObject jsonData = parseJson(response, box).getObject("data");
            document.update(jsonData);
            if (acl!=null){
                document.acl = acl;
            }
            return document;
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            String coll = document.collection;
            String docId = document.id;
            if (acl != null){
                JsonArray readGrants =acl.arrayForGrant(Grant.READ);
                JsonArray updateGrants = acl.arrayForGrant(Grant.UPDATE);
                JsonArray deleteGrants = acl.arrayForGrant(Grant.DELETE);
                if (readGrants!=null){
                    data.put("_allowRead",readGrants);
                }
                if (updateGrants != null){
                    data.put("_allowUpdate",updateGrants);
                }
                if (deleteGrants != null){
                    data.put("_allowDelete",deleteGrants);
                }
            }
            
            if (docId == null) {
                String endpoint = box.requestFactory.getEndpoint("document/{}", coll);
                return box.requestFactory.post(endpoint, data);
            } else {
                String endpoint = box.requestFactory.getEndpoint("document/{}/{}", coll, docId);
                if (mode == SaveMode.CHECK_VERSION) {
                    data.put("@version", document.version);
                }
                return box.requestFactory.put(endpoint, data);
            }
        }
    }

    private static final class Access extends BaasObject.Access {
        protected Access(BaasBox box, boolean add, boolean isRole, String collection, String id, String to, Grant grant, int flags, BaasHandler<Void> handler) {
            super(box, add, isRole, collection, id, to, grant,flags, handler);
        }

        @Override
        protected String userGrant(RequestFactory factory, Grant grant, String collection, String id, String to) {
            return factory.getEndpoint("document/{}/{}/{}/user/{}", collection, id, grant.action, to);
        }

        @Override
        protected String roleGrant(RequestFactory factory, Grant grant, String collection, String id, String to) {
            return factory.getEndpoint("document/{}/{}/{}/role/{}", collection, id, grant.action, to);
        }
    }

    private static final class Refresh extends NetworkTask<BaasDocument> {
        private final BaasDocument document;

        private RequestFactory.Param aclParam;

        protected Refresh(BaasBox box, BaasDocument doc,boolean withAcl, int flags, BaasHandler<BaasDocument> handler) {
            super(box, flags, handler);
            this.document = doc;
            aclParam = withAcl? new RequestFactory.Param("withAcl","true"):null;
        }

        @Override
        protected BaasDocument onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            JsonObject object = parseJson(response, box).getObject("data");
            document.update(object);
            return document;
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            String endpoint = box.requestFactory.getEndpoint("document/{}/{}", document.getCollection(), document.getId());
            if (aclParam!=null){
                return box.requestFactory.get(endpoint,aclParam);
            } else {
                return box.requestFactory.get(endpoint);
            }
        }
    }

    private static final class Fetch extends NetworkTask<List<BaasDocument>> {
        private final String collection;
        private final RequestFactory.Param[] filter;

        protected Fetch(BaasBox box, String collection, BaasQuery.Criteria filter, int flags, BaasHandler<List<BaasDocument>> handler) {
            super(box, flags, handler);
            this.collection = collection;
            this.filter = filter == null ? null : filter.toParams();
        }

        @Override
        protected List<BaasDocument> onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            JsonArray jsonData = parseJson(response, box).getArray("data");
            Logger.debug("received: " + jsonData);
            if (jsonData == null) {
                return Collections.emptyList();
            } else {
                List<BaasDocument> res = new ArrayList<BaasDocument>();
                for (Object obj : jsonData) {
                    res.add(new BaasDocument((JsonObject) obj));
                }
                return res;
            }
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            String ep = box.requestFactory.getEndpoint("document/{}", collection);
            if (filter == null) {
                return box.requestFactory.get(ep);
            } else {
                return box.requestFactory.get(ep, filter);
            }
        }
    }


    private static abstract class FetchLinked<T> extends NetworkTask<T> {

        private final String collection;
        private final String id;
        private final String relationship;
        private RequestFactory.Param[] criteria;

        protected FetchLinked(BaasBox box, int flags,String collection,String id,String relationship,BaasLink.Direction direction,BaasQuery.Criteria criteria, BaasHandler<T> handler) {
            super(box, flags, handler);
            this.collection = collection;
            this.id = id;
            this.relationship = relationship;
            RequestFactory.Param dirParam = new RequestFactory.Param("linkDir",direction.name().toLowerCase());
            if (criteria == null) {
                this.criteria = new RequestFactory.Param[]{dirParam};
            } else {
                RequestFactory.Param[] rp = criteria.toParams();
                if (rp != null) {
                    this.criteria = new RequestFactory.Param[rp.length + 1];
                    System.arraycopy(rp, 0, this.criteria, 1, rp.length);
                } else {
                    this.criteria= new RequestFactory.Param[1];
                }
                this.criteria[0] = dirParam;
            }
        }


        protected BaasObject parseObject(JsonObject object) {
            if (object == null) return null;
            BaasObject ret;
            if (object.contains("@class")){
                ret = new BaasDocument(object);
            } else {
                BaasFile file = new BaasFile();
                file.update(object);
                ret = file;
            }
            return ret;
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            String ep = box.requestFactory.getEndpoint("document/{}/{}/{}", collection, id, relationship);
            if (criteria != null) {
                return box.requestFactory.get(ep,criteria);
            } else {
                return box.requestFactory.get(ep);
            }
        }
    }

    private static final class FetchManyLinked extends FetchLinked<List<BaasObject>> {

        protected FetchManyLinked(BaasBox box, int flags, String collection, String id, String relationship, BaasLink.Direction direction, BaasQuery.Criteria criteria, BaasHandler<List<BaasObject>> handler) {
            super(box, flags, collection, id, relationship, direction, criteria, handler);
        }

        @Override
        protected List<BaasObject> onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            JsonArray entries = parseJson(response, box).get("data");
            ArrayList<BaasObject> ret = new ArrayList<>();
            for (Object o: entries) {
                JsonObject object = (JsonObject)o;
                BaasObject val = parseObject(object);
                ret.add(val);
            }
            return ret;
        }
    }

    private static final class Count extends NetworkTask<Long> {
        private final String collection;
        private final RequestFactory.Param[] params;

        protected Count(BaasBox box, String collection, BaasQuery.Criteria filter, int flags, BaasHandler<Long> handler) {
            super(box, flags, handler);
            this.collection = collection;
            this.params = filter == null ? null : filter.toParams();
        }

        @Override
        protected Long onOk(int status, HttpResponse response, BaasBox box) throws BaasException {
            JsonObject entries = parseJson(response, box);
            
            return entries.getArray("data").getObject(0).getLong("count");
        }

        @Override
        protected HttpRequest request(BaasBox box) {
            String ep = box.requestFactory.getEndpoint("document/{}", collection);
            if (params != null) {
                return box.requestFactory.get(ep, params);
            } else {
                return box.requestFactory.get(ep);
            }
        }
    }
}
