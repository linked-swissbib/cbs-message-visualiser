package org.swissbib.linked;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Sorts.ascending;

/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 06.07.16
 */
class MongoDbTraverser {

    private final static Logger LOG = LoggerFactory.getLogger(MongoDbTraverser.class);

    private MongoClient mongoClient;
    private MongoCollection<Document> collection;
    private MfWorkflowWrapper mfWrapper;

    MongoDbTraverser(String dbUri, MfWorkflowWrapper mfWrapper) {
        this.mfWrapper = mfWrapper;
        Pattern pattern = Pattern.compile("^(mongodb://.*:\\d{2,5})/(\\w*)/(\\w*)$");
        Matcher matcher = pattern.matcher(dbUri);
        if (matcher.find()) {
            mongoClient = new MongoClient(new MongoClientURI(matcher.group(1)));
            MongoDatabase db = mongoClient.getDatabase(matcher.group(2));
            collection = db.getCollection(matcher.group(3));
        } else {
            LOG.error("{} is not a valid URI to the MongoDB.", dbUri);
            throw new IllegalArgumentException("{} is not a valid URI.");
        }
    }

    void closeDb() {
        ServerAddress address = mongoClient.getAddress();
        LOG.info("Connection to MongoDB on {}:{} closed.", address.getHost(), address.getPort());
    }

    void traverse() {
        try (MongoCursor<Document> cursor = collection.find().iterator()) {
            while (cursor.hasNext()) {
                Document d = cursor.next();
                LOG.debug("Sending document {} into Metafacture pipe.", d.get("id"));
                mfWrapper.transform(d.get("record").toString(),
                        d.get("action").toString(),
                        d.get("timestamp").toString());
            }
        }
    }

}
