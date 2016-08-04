package org.swissbib.linked;

import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.xml.MarcXmlHandler;
import org.culturegraph.mf.stream.converter.xml.XmlDecoder;
import org.culturegraph.mf.stream.pipe.IdChangePipe;
import org.culturegraph.mf.stream.source.StringReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 06.07.16
 */
class MfWorkflowWrapper {

    private final static Logger LOG = LoggerFactory.getLogger(MfWorkflowWrapper.class);

    private final StringReader stringReader = new StringReader();
    private final CypherEncoder cypherEncoder = new CypherEncoder();


    MfWorkflowWrapper(String metamorphDefinition, String neo4jDbDirectory, Byte density, Double jwd) {

        XmlDecoder xmlDecoder = new XmlDecoder();
        MarcXmlHandler marcHandler = new MarcXmlHandler();
        IdChangePipe idChange = new IdChangePipe("id");
        LOG.debug("Reading in Metamorph definition at {}", metamorphDefinition);
        Metamorph metamorph = new Metamorph(metamorphDefinition);
        LOG.debug("Setting Neo4j Embedded Database directory to {}", neo4jDbDirectory);
        cypherEncoder.setDensity(density);
        cypherEncoder.setJwdThreshold(jwd);
        cypherEncoder.setDbDir(neo4jDbDirectory);

        stringReader.setReceiver(xmlDecoder)
                .setReceiver(marcHandler)
                .setReceiver(metamorph)
                .setReceiver(idChange)
                .setReceiver(cypherEncoder);
    }


    void transform(String record, String action, String timestamp) {
        cypherEncoder.setAction(action);
        cypherEncoder.setTimestamp(timestamp);
        stringReader.process(record);
    }

}
