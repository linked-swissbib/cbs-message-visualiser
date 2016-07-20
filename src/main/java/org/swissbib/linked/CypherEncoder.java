package org.swissbib.linked;

import org.culturegraph.mf.framework.DefaultStreamPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;

/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 06.07.16
 */
class CypherEncoder<T> extends DefaultStreamPipe<ObjectReceiver<T>> {

    private final static Logger LOG = LoggerFactory.getLogger(CypherEncoder.class);

    private String action;
    private String timestamp;
    private GraphDatabaseService graphDb;
    private Transaction tx;
    private Node resourceNode;
    private ArrayList<String> locSigs = new ArrayList<>();

    void setAction(String action) {
        this.action = action;
    }

    void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    void setDbDir(String dbDir) {
        graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(new File(dbDir))
                // TODO: Check possible further tweakings
                .setConfig(GraphDatabaseSettings.pagecache_memory, "24g")
                .newGraphDatabase();
        tx = graphDb.beginTx();
        if (!graphDb.schema().getIndexes(lsbLabels.PERSON).iterator().hasNext())
            graphDb.schema().constraintFor(lsbLabels.PERSON).assertPropertyIsUnique("id").create();
        if (!graphDb.schema().getIndexes(lsbLabels.ORGANISATION).iterator().hasNext())
            graphDb.schema().constraintFor(lsbLabels.ORGANISATION).assertPropertyIsUnique("id").create();
        if (!graphDb.schema().getIndexes(lsbLabels.RESOURCE).iterator().hasNext())
            graphDb.schema().indexFor(lsbLabels.RESOURCE).on("id").create();
        if (!graphDb.schema().getIndexes(lsbLabels.SYSNO).iterator().hasNext())
            graphDb.schema().constraintFor(lsbLabels.SYSNO).assertPropertyIsUnique("id").create();
        if (!graphDb.schema().getIndexes(lsbLabels.WORK).iterator().hasNext())
            graphDb.schema().constraintFor(lsbLabels.WORK).assertPropertyIsUnique("id").create();
        tx.success();
        tx.close();
    }

    public void startRecord(String s) {
        tx = graphDb.beginTx();
        Label actionLbl = null;
        switch (action.split("/")[3]) {
            case "delete":
                actionLbl = lsbLabels.DELETE;
                break;
            case "create":
                actionLbl = lsbLabels.CREATE;
                break;
            case "replace":
                actionLbl = lsbLabels.UPDATE;
                break;
            default:
                break;
        }
        resourceNode = graphDb.createNode(lsbLabels.RESOURCE, actionLbl);
        resourceNode.setProperty("id", s);
        resourceNode.setProperty("time", timestamp);
    }

    public void endRecord() {
        String id = getAncestor();
        if (id != null) {
            Node ancestor = graphDb.getNodeById(Long.parseLong(id));
            ancestor.createRelationshipTo(resourceNode, lsbRelations.HASSUCCESSOR);
        }
        tx.success();
        tx.close();
        locSigs.clear();
    }


    /**
     * Depending on the key of the literal, a new node of some kind and a relation from the resource-node to it are created.
     * @param k Key of literal
     * @param v Value of literal
     */
    public void literal(String k, String v) {
        switch (k) {
            case "organisation":
                Node orgaNode = mergeNode(lsbLabels.ORGANISATION, v);
                resourceNode.createRelationshipTo(orgaNode, lsbRelations.HASORGANISATION);
                LOG.debug("Create relation between resource-node {} and organisation-node {} ", resourceNode.getId(), orgaNode.getId());
                break;
            case "person":
                Node personNode = mergeNode(lsbLabels.PERSON, v);
                resourceNode.createRelationshipTo(personNode, lsbRelations.HASPERSON);
                LOG.debug("Create relation between resource-node {} and person-node {} ", resourceNode.getId(), personNode.getId());
                break;
            case "work":
                Node workNode = mergeNode(lsbLabels.WORK, v);
                resourceNode.createRelationshipTo(workNode, lsbRelations.HASWORK);
                LOG.debug("Create relation between resource-node {} and work-node {} ", resourceNode.getId(), workNode.getId());
                break;
            case "sysno":
                Node locsigNode = mergeNode(lsbLabels.SYSNO, v);
                resourceNode.createRelationshipTo(locsigNode, lsbRelations.HASSYSNO);
                LOG.debug("Create relation between resource-node {} and local-signature-node {} ", resourceNode.getId(), locsigNode.getId());
                locSigs.add(v);
                break;
            case "title":
                resourceNode.setProperty(k, v);
                LOG.debug("Add property {}={} to resource-node {}", k, v, resourceNode.getId() );
                break;
        }

    }

    /**
     * Creates a new node. If node already exists, creation is skipped.
     *
     * @param concept Label of node which should be merged
     * @param v       Value of property id
     * @return New node or node which matched query
     */
    private Node mergeNode(Label concept, String v) {
        Node n = graphDb.createNode(concept);
        try {
            n.setProperty("id", v);
            n.setProperty("time", timestamp);
            LOG.debug("Node with label {} and properties id={} and time={} has been created.", concept.toString(), v, timestamp);
        } catch (ConstraintViolationException e) {
            LOG.debug("Node with label {} and property id={} already exists. Thus no new node is created.", concept.toString(), v);
            n.delete();
            n = graphDb.findNode(concept, "id", v);
        }
        return n;
    }

    /**
     * Returns the latest ancestor of the current resource-node.
     * @return Id of the latest ancestor
     */
    private String getAncestor() {
        Result r = graphDb.execute(createCypherAncestorLookup(locSigs, resourceNode.getId()));
        String id;
        if (r.hasNext()) {
            id = r.next().get("id(r)").toString();
            LOG.debug("An ancestor for resource-node {} has been found: {}", resourceNode.getId(), id);
        } else {
            id = null;
            LOG.debug("No ancestor node for resource-node {} has been found.", resourceNode.getId());
        }
        return id;
    }

    /**
     * Returns the cypher query required to look up possible ancestors to resource-nodes.
     * @param locSigs List of local signatures of the current node
     * @return Query
     */
    static String createCypherAncestorLookup(ArrayList<String> locSigs, long resourceNodeId) {
        StringBuilder sb = new StringBuilder("MATCH (r:RESOURCE)-[:HASSYSNO]->(s:SYSNO) WHERE s.id IN [");
        int counter = 0;
        for (String e : locSigs) {
            if (counter > 0) {
                sb.append(",");
            }
            sb.append("\"").append(e).append("\"");
            counter++;
        }
        sb.append("] AND NOT (r)-[:HASSUCCESSOR]->() AND id(r) <> ");
        sb.append(resourceNodeId);
        sb.append(" RETURN id(r)");
        LOG.trace("New cypher query for looking up the latest ancestor has been generated: {}", sb.toString());
        return sb.toString();
    }


    private enum lsbLabels implements Label {
        RESOURCE, PERSON, ORGANISATION, SYSNO, WORK, CREATE, DELETE, UPDATE
    }

    private enum lsbRelations implements RelationshipType {
        HASORGANISATION, HASPERSON, HASSYSNO, HASWORK, HASSUCCESSOR
    }

}
