/*
 * Copyright 2011 VZ Netzwerke Ltd
 * Copyright 2014 devbliss GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mongojack;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mongojack.mock.MockObject;
import org.mongojack.mock.MockObjectWithWriteReadOnlyFields;

public class TestJacksonCodecRegistry extends MongoDBTestBase {
    private com.mongodb.client.MongoCollection<MockObject> coll;

    @Before
    public void setup() {
        com.mongodb.client.MongoCollection<?> collection = getMongoCollection("testCollection", Document.class);
        JacksonCodecRegistry jacksonCodecRegistry = new JacksonCodecRegistry(ObjectMapperConfigurer.configureObjectMapper(new ObjectMapper()), collection.getCodecRegistry(), uuidRepresentation);
        jacksonCodecRegistry.addCodecForClass(MockObject.class);
        coll = collection.withDocumentClass(MockObject.class).withCodecRegistry(jacksonCodecRegistry);
    }

    @Test
    public void testQuery() {
        MockObject o1 = new MockObject("1", "ten", 10);
        MockObject o2 = new MockObject("2", "ten", 10);
        coll.insertOne(o1);
        coll.insertOne(o2);
        coll.insertOne(new MockObject("twenty", 20));

        List<MockObject> results = coll
                .find(new Document("string", "ten")).into(new ArrayList<>());
        assertThat(results, hasSize(2));
        assertThat(results, contains(o1, o2));
    }

    @Test
    public void testCustomSerialization() {
        long millis = 123456789L;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(millis);
        MockObject o1 = new MockObject("1", "ten", 10);
        o1.calendar = calendar;

        coll.insertOne(o1);

        List<MockObject> results = coll.find(new Document("string", "ten")).into(new ArrayList<>());
        assertThat(results, hasSize(1));
        assertEquals(calendar, results.get(0).calendar);
    }

    @Test
    public void testSerializationWithWriteOrReadOnlyFields() {
        MongoCollection<MockObjectWithWriteReadOnlyFields> customColl = coll.withDocumentClass(MockObjectWithWriteReadOnlyFields.class);

        MockObjectWithWriteReadOnlyFields tDocument = new MockObjectWithWriteReadOnlyFields("1", "2", "3");
        customColl.insertOne(tDocument);

        Document result = coll.withDocumentClass(Document.class).find(new Document("_id", "1")).first();
        assertNotNull(result);
        assertEquals("1", result.getString("_id"));
        assertEquals("2", result.getString("someReadOnlyField"));
        assertNull(result.getString("someWriteOnlyField"));
    }

    @Test
    public void testDeserializationWithWriteOrReadOnlyFields() {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", "1");
        map.put("someReadOnlyField", "2");
        map.put("someWriteOnlyField", "3");
        coll.withDocumentClass(Document.class)
            .insertOne(new Document(map));

        MongoCollection<MockObjectWithWriteReadOnlyFields> customColl = coll.withDocumentClass(MockObjectWithWriteReadOnlyFields.class);
        MockObjectWithWriteReadOnlyFields result = customColl
            .find(new Document("_id", "1")).first();
        assertNotNull(result);
        assertEquals("3", result.getSomeWriteOnlyField());
        assertNull(result.getSomeReadOnlyField());
    }

    @Test
    public void testRemove() {
        coll.insertOne(new MockObject("ten", 10));
        coll.insertOne(new MockObject("ten", 100));
        MockObject object = new MockObject("1", "twenty", 20);
        coll.insertOne(object);

        coll.deleteMany(new Document("string", "ten"));

        List<MockObject> remaining = coll.find().into(new ArrayList<>());
        assertThat(remaining, Matchers.hasSize(1));
        assertThat(remaining, contains(object));
    }

    @Test
    public void testRemoveById() {
        coll.insertOne(new MockObject("id1", "ten", 10));
        coll.insertOne(new MockObject("id2", "ten", 100));
        MockObject object = new MockObject("id3", "twenty", 20);
        coll.insertOne(object);

        coll.deleteOne(new Document("_id", "id3"));

        List<MockObject> remaining = coll.find().into(new ArrayList<>());
        assertThat(remaining, Matchers.hasSize(2));
        assertThat(remaining, Matchers.not(contains(object)));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testFindAndModify() {
        coll.insertOne(new MockObject("id1", "ten", 10));
        coll.insertOne(new MockObject("id2", "ten", 10));

        MockObject result1 = coll.findOneAndUpdate(new Document("_id", "id1"), new Document("$set", new Document("integer", 20).append("string",
                "twenty")), new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        assertThat(result1.integer, Matchers.equalTo(20));
        assertThat(result1.string, Matchers.equalTo("twenty"));

        MockObject result2 = coll.findOneAndUpdate(new Document("_id", "id2"), new Document("$set", new Document("integer", 30).append("string",
                "thirty")), new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        assertThat(result2.integer, Matchers.equalTo(30));
        assertThat(result2.string, Matchers.equalTo("thirty"));
    }

}
