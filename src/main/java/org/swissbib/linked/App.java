package org.swissbib.linked;


/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 06.07.16
 */
public class App {

    public static void main(String[] args) {

        final String metamorphDefinition = "src/main/resources/transformations/main.xml";

        final String dbUri = args[0];
        final String neo4jDbDirectory = args[1];

        MfWorkflowWrapper mfWrapper = new MfWorkflowWrapper(metamorphDefinition, neo4jDbDirectory);

        MongoDbTraverser dbTraverser = new MongoDbTraverser(dbUri, mfWrapper);
        dbTraverser.traverse();
        dbTraverser.closeDb();

    }


}
