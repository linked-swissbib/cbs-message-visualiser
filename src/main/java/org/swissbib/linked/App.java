package org.swissbib.linked;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 06.07.16
 */
public class App {

    private final static Logger LOG = LoggerFactory.getLogger(CypherEncoder.class);

    public static void main(String[] args) {

        final String metamorphDefinition = "src/main/resources/transformations/main.xml";

        String dbUri = "";
        String neo4jDbDirectory = "";
        byte density = 0;
        double jwd = 0.7;
        CommandlineArgsReader cmdReader = new CommandlineArgsReader(args);
        for (Map.Entry<String, String> e : cmdReader.parse().entrySet()) {
            switch (e.getKey()) {
                case "i":
                case "input":
                    dbUri = e.getValue();
                    LOG.info("Set MongoDB uri to {}", e.getValue());
                    break;
                case "o":
                case "output":
                    neo4jDbDirectory = e.getValue();
                    LOG.info("Set directory to embedded Neo4j DB to {}", e.getValue());
                    break;
                case "d":
                case "density":
                    density = Byte.parseByte(e.getValue());
                    LOG.info("Set density level to {}", e.getValue());
                    break;
                case "j":
                case "jwd":
                    jwd = Double.parseDouble(e.getValue());
                    LOG.info("Set Jaro-Winkler distance threshold to {}", e.getValue());
                    break;
            }
        }


        MfWorkflowWrapper mfWrapper = new MfWorkflowWrapper(metamorphDefinition, neo4jDbDirectory, density, jwd);

        MongoDbTraverser dbTraverser = new MongoDbTraverser(dbUri, mfWrapper);
        dbTraverser.traverse();
        dbTraverser.closeDb();

    }


}
