Mapping to POJOs couldn't be easier!
====================================

Since MongoDB uses BSON, a binary form of JSON, to store its documents, a JSON mapper is a perfect mechanism for mapping Java objects to MongoDB documents.  And the best Java JSON mapper is Jackson.  Jackson's parsing/generating interface fits serialising to MongoDBs documents like a glove.  Its plugins, custom creators, serialisers, views, pluggable annotators and so on give this mapping library a massive head start, making it powerful, performant, and robust.

Project documentation
---------------------

The official documentation for this project lives [here](http://mongojack.org).

Mailing lists
-------------

The MongoDB Jackson Mapper users mailing list is hosted [here](http://groups.google.com/group/mongo-jackson-mapper).

Quick start
-----------

### Mongo driver compatibility

- Version 2.3.0 and earlier are compatible only with the 2.x series mongo-java-driver.
- Version 2.5.0 to 2.10.0 are compatible with the 3.x series mongodb-driver using the legacy 3.x series APIs.
- Version 3.0.0 and later are compatible with versions 3.11.x and later of the mongo driver using mongodb-driver-sync without the legacy APIs.

### Installation

#### Using Maven
The quickest and easiest way to start using MongoJack is to use Maven. To do that, add the following to your dependencies list:

    <dependency>
      <groupId>org.mongojack</groupId>
      <artifactId>mongojack</artifactId>
      <version>2.8.0</version>
    </dependency>

### Writing code

Inserting objects is done like this:

    JacksonDBCollection<MyObject, String> coll = JacksonDBCollection.wrap(dbCollection, MyObject.class,
            String.class);
    MyObject myObject = ...
    WriteResult<MyObject, String> result = coll.insert(myObject);
    String id = result.getSavedId();
    MyObject savedObject = result.getSavedObject();

Both the object itself and the id of the object are strongly typed.  If the id is generated, you can easily obtain it from the write result.  Finding an object by ID is also simple:

    MyObject foundObject = coll.findOneById(id);

Querying can be done using chained query builder methods on the DBCursor:

    DBCursor<MyObject> cursor = coll.find().is("prop", "value");
    if (cursor.hasNext()) {
        MyObject firstObject = cursor.next();
    }

The collection, cursor and write result interfaces are very similar to the standard Java MongoDB driver. Most methods have been copied across, with generic typing added where appropriate, and overloading to use the generic type where sometimes the generic type is not powerful enough, such as for queries and specifying fields for partial objects.

When it comes to mapping your objects, generally all you need to use is the Jackson annotations, such as `@JsonProperty` and `@JsonCreator`.  If you want a type of `ObjectId`, you have two options, either make your field be of type `ObjectId`, or you can also use `String`, as long as you annotate *both* the serialising and deserialising properties with `@org.mongojack.ObjectId`.  For example:

    public class MyObject {
      private String id;
      @ObjectId
      @JsonProperty("_id")
      public String getId() {
        return id;
      }
      @ObjectId
      @JsonProperty("_id")
      public void setId(String id) {
        this.id = id;
      }
    }

Now your id property will be stored in the database as an object ID, and you can let MongoDB generate it for you.  You might not like annotating your ids with `@JsonProperty("_id")`, the mapper supports `@javax.persistence.Id` as a short hand for this:

    public class MyObject {
      @Id
      public Long id;
    }

Another useful implication of this is if you want to use the same object for database objects and objects to return on the web, you can name the id whatever you want for the web, and you don't need to use Jackson views to specify which property gets mapped to what name for the database and for the web.

The only limitation to using the id annotation is if you are using `@Creator` annotated constructors or factory methods, because `@javax.persistence.Id` is not supported on method parameters.  For this reason, the mapper provides the annotation `@org.mongojack.Id`, and it can be used like so:

    public class MyObject {
      private final String id;
      @JsonCreator
      public MyObject(@Id @ObjectId id) {
        this.id = id;
      }
      @Id
      @ObjectId
      public String getId() {
        return id;
      }
    }

As you can see, immutable objects are also supported because Jackson supports them, something that most other frameworks don't support.

If you're using your data objects for both storage and web views, you might want to take advantage of Jacksons views feature, so that generated/transient properties aren't persisted, and properties that you don't want leaked and serialised to the web.  The mapper supports this easily, by letting you pass in a view to the wrap method:

    JacksonDBCollection<MyObject, String> coll = JacksonDBCollection.wrap(DBCollection dbCollection, MyObject.class,
            String.class, DatabaseView.class);

Of course, if you really want to control things and Jackson's annotations aren't enough, the wrap method is also overloaded to accept an `ObjectMapper`.  For convenience, you should add the object ID module in order to get the object id and id annotation mapping features:

    ObjectMapper myObjectMapper = ...
    myObjectMapper.withModule(org.mongojack.internal.MongoJackModule.INSTANCE);
    JacksonDBCollection<MyObject, String> coll = JacksonDBCollection.wrap(DBCollection dbCollection, MyObject.class,
            String.class, myObjectMapper);

Using Mongo Java 3.0+ API
-----------
MongoJack now supports usage of the java mongo driver's 3.0 API. There are two ways to use this feature.

1) Use the JacksonCodecRegistry class
2) Use the JacksonMongoCollection class

### Using JacksonCodecRegistry
The java mongo 3.0 and higher driver supports the usage of codecs to map to specific types. MongoJack provides a Codec Registry which can be used for this purpose. Some example code making use of the JacksonCodecRegistry can be seen below:
	
	MongoClient mongo = new MongoClient();
	MongoDatabase mongoDatabase = mongo.getDatabase(testDatabaseName);
	JacksonCodecRegistry jacksonCodecRegistry = new JacksonCodecRegistry();
	jacksonCodecRegistry.addCodecForClass(MyObject.class);
	MongoCollection<?> coll = mongoDatabase.getCollection("testCollection");
	MongoCollection<MyObject> collection = coll.withDocumentClass(MyObject.class).withCodecRegistry(jacksonCodecRegistry);

The first two lines above get the database using the mongo driver. The third line constructs a new JacksonCodecRegistry. The fourth line tells the JacksonCodecRegistry to create a codec that will use Jackson for serialifation/deserialization for the class MyObject. The fifth line gets a MongoCollection from the MongoDatabase, and the sixth tells the MongoCollection to use  the MyObject class and work with the JacksonCodecRegsitry setup on lines three and four. JacksonCodecRegistry includes the default Mongo codecs, so it will also be capable of serializing and deserializing the Document and other default classes.

### Using JacksonMongoCollection
JacksonMongoCollection attempts to mimic most of the functionality of the JacksonDBCollection, while using the newer driver API. To use a JacksonMongoCollection the user will first need to initialize it using the builder.

	JacksonMongoCollection<MyObject> collection = JacksonMongoCollection.<MyObject> builder().withView(MyView.class).build(collection, MyObject.class);
	
The builder pattern allows us to pass in optional parameters like a view without having 10 different constructors. The build method requires the MongoCollection to be wrapped, and the type that this should use for serialization and deserialization. This automatically creates the appropriate JacksonCodecRegistry internally.

Usage largely follow the same pattern as the JacksonDBCollection with a few exceptions.

Old:
	
	MockObject o1 = new MockObject("1", "ten", 10);
	MockObject o2 = new MockObject("2", "ten", 10);
	coll.insert(o1, o2, new MockObject("twenty", 20));
	List<MockObject> results = collection.find(new BasicDBObject("string", "ten")).toArray();

New:

	MockObject o1 = new MockObject("1", "ten", 10);
	MockObject o2 = new MockObject("2", "ten", 10);
	coll.insert(o1, o2, new MockObject("twenty", 20));
	List<MockObject> results = collection.find(new Document("string", "ten")).into(new ArrayList<>());
 
The biggest difference is that Documents are used instead of DBObjects. MongoJacks DBQuery, DBUpdate, and Aggregation helpers should all still work with the new JacksonMongoCollection. There are some methods which have been removed because they don't make sense with the mongo java driver's new API, or are no longer exposed through MongoCollection like they previously were with DBCollection.

Releasing
-----------

This section is relevant only for project maintainers.

NOTE: [do not release from any location which load balances outgoing HTTP requests between internet connections](https://issues.sonatype.org/browse/OSSRH-6262)

Make sure you have the file `~/.m2/settings.xml`:

    <settings>
      <servers>
        <server>
          <id>sonatype-nexus-staging</id>
          <username></username>
          <password></password>
        </server>
        <server>
          <id>github-project-site</id>
          <username>git</username>
        </server>
      </servers>
    </settings>

Now run the following:

    mvn release:prepare
    mvn release:perform

Then log into oss.sonatype.org, close the repository, and release the repository.

To deploy the latest version of the website:

    mvn site:site
    mvn site:deploy
