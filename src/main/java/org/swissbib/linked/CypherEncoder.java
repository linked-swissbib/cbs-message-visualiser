package org.swissbib.linked;

import org.apache.commons.lang3.StringUtils;
import org.culturegraph.mf.framework.DefaultStreamPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.function.Function;

/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 06.07.16
 */
class CypherEncoder<T> extends DefaultStreamPipe<ObjectReceiver<T>> {

    private final static Logger LOG = LoggerFactory.getLogger(CypherEncoder.class);
    private final static byte LOW_DENSITY = 1;
    private final static byte MEDIUM_DENSITY = 2;
    private final static byte HIGH_DENSITY = 3;

    private String action;
    private String timestamp;
    private GraphDatabaseService graphDb;
    private Transaction tx;
    private Node resourceNode;
    private ArrayList<String> locSigs = new ArrayList<>();
    private String nodeId;
    private byte density = 1;
    private double jwdThreshold = 0.7;
    private Boolean ignoreRecord = false;
    private HashMap<String, HashSet<Node>> neighborNodes = new HashMap<>();

    CypherEncoder() {
        neighborNodes.put("organisation", new HashSet<>());
        neighborNodes.put("person", new HashSet<>());
    }

    void setAction(String action) {
        this.action = action.split("/")[3];
    }

    void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    void setDensity(byte density) {
        this.density = density;
    }

    void setJwdThreshold(double jwdThreshold) {
        this.jwdThreshold = jwdThreshold;
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
        if (density >= LOW_DENSITY) {
            if (!graphDb.schema().getIndexes(lsbLabels.DELETE).iterator().hasNext())
                graphDb.schema().constraintFor(lsbLabels.DELETE).assertPropertyIsUnique("id").create();
            if (!graphDb.schema().getIndexes(lsbLabels.CREATE).iterator().hasNext())
                graphDb.schema().constraintFor(lsbLabels.CREATE).assertPropertyIsUnique("id").create();
        }
        tx.success();
        tx.close();
    }

    public void startRecord(String s) {
        ignoreRecord = false;
        nodeId = s;
        tx = graphDb.beginTx();
        Label actionLbl = null;
        switch (action) {
            case "delete":
                actionLbl = lsbLabels.DELETE;
                if (density >= HIGH_DENSITY) {
                    // A creation of a :DELETE node in high density mode must always fail
                    ignoreRecord = true;
                }
                break;
            case "create":
                actionLbl = lsbLabels.CREATE;
                break;
            case "replace":
                actionLbl = lsbLabels.REPLACE;
                break;
            default:
                break;
        }
        resourceNode = graphDb.createNode(lsbLabels.RESOURCE, actionLbl);
        try {
            resourceNode.setProperty("id", s);
            resourceNode.setProperty("time", timestamp);
        } catch (ConstraintViolationException e) {
            ignoreRecord = true;
            switch (action) {
                case "delete":
                    LOG.info("Skipping record with id {}: An already deleted record can't be deleted a second time!", s);
                    break;
                case "create":
                    LOG.info("Skipping record with id {}: An already created record can't be created a second time!", s);
                    break;
            }
        }
    }

    public void endRecord() {

        if (!ignoreRecord) {
            if (action.equals("replace") || action.equals("delete")) {
                String id = getPredecessor();
                if (id != null) {
                    Node related = graphDb.getNodeById(Long.parseLong(id));
                    if (density < HIGH_DENSITY) {
                        resourceNode.createRelationshipTo(related, lsbRelations.SUCCEEDS);
                    }
                    relateAncestorContributors(related, lsbRelations.HASPERSON);
                    relateAncestorContributors(related, lsbRelations.HASORGANISATION);
                    if (density >= MEDIUM_DENSITY && action.equals("replace")) {
                        Function<Node, HashSet<String>> addRelation = n -> {
                            HashSet<String> hs = new HashSet<>();
                            for (Relationship rela : n.getRelationships(lsbRelations.HASORGANISATION, lsbRelations.HASPERSON, lsbRelations.HASSYSNO, lsbRelations.HASWORK)) {
                                hs.add(rela.getEndNode().getProperty("id").toString());
                            }
                            return hs;
                        };
                        if (addRelation.apply(resourceNode).equals(addRelation.apply(related))) {
                            ignoreRecord = true;
                            LOG.info("Abort creation of :REPLACE-labelled node {} with CBS id {} since density level set to 2 or above. " +
                                            "I.e. a :REPLACE-node has to have a different set of direct neighbors than its predecessor to be considered.",
                                    resourceNode.getId(), nodeId);
                        } else if (density >= HIGH_DENSITY) {
                            LOG.info("Merging resource node {} with node {}", resourceNode.getId(), related.getId());
                            List<RelationshipType> relaTypes = new ArrayList<>();
                            relaTypes.add(lsbRelations.HASORGANISATION);
                            relaTypes.add(lsbRelations.HASPERSON);
                            relaTypes.add(lsbRelations.HASWORK);
                            relaTypes.add(lsbRelations.HASSYSNO);
                            List<Long> neighborsDel = new ArrayList<>();
                            List<Long> neighborsNew = new ArrayList<>();
                            for (RelationshipType rT : relaTypes) {
                                // Get all neighbor nodes for ancestor with specific label
                                HashSet<Node> relatedNeighbors = new HashSet<>();
                                for (Relationship r : related.getRelationships(rT)) {
                                    relatedNeighbors.add(r.getEndNode());
                                }
                                // Get all neighbor nodes for actual node with specific label
                                HashSet<Node> actualNeighbors = new HashSet<>();
                                for (Relationship r : resourceNode.getRelationships(rT)) {
                                    actualNeighbors.add(r.getEndNode());
                                }
                                // Get nodes which are new ("left") or are not used anymore ("right")
                                HashMap<String, HashSet<Node>> difference = symmetricDifference(relatedNeighbors, actualNeighbors);
                                for (Node n : difference.get("left")) {
                                    related.createRelationshipTo(n, rT);
                                }
                                // Remove relationships to nodes which have no relationship to new resource node. Additionally,
                                // if a node has no relationships anymore, delete it.
                                // FIXME: Old contributor nodes with a successor won't be deleted in the moment
                                for (Node n : difference.get("right")) {
                                    neighborsDel.add(n.getId());
                                }
                                // Create relationships to new nodes
                                for (Relationship relationship : resourceNode.getRelationships()) {
                                    neighborsNew.add(relationship.getEndNode().getId());
                                    related.createRelationshipTo(relationship.getEndNode(), rT);
                                    relationship.delete();
                                }
                            }
                            Map<String, Object> paramsDel = new HashMap<>();
                            paramsDel.put("related", related.getId());
                            paramsDel.put("neighbors", neighborsDel);
                            graphDb.execute("MATCH (s:RESOURCE)-[x]->(t) " +
                                    "WHERE id(s) = {related} AND id(t) IN {neighbors} " +
                                    "WITH x, t " +
                                    "WHERE NOT (t)<--() " +
                                    "DELETE x, t", paramsDel);
                            Map<String, Object> paramsNew = new HashMap<>();
                            paramsNew.put("neighbors", neighborsNew);
                            graphDb.execute("MATCH (t) WHERE id(t) IN {neighbors} AND NOT (t)<--() DELETE t", paramsNew);
                            resourceNode.delete();
                        }
                    }

                }
            } else {
                String id = getAncestor();
                if (id != null) {
                    Node related = graphDb.getNodeById(Long.parseLong(id));
                    resourceNode.createRelationshipTo(related, lsbRelations.INHERITS);
                    relateAncestorContributors(related, lsbRelations.HASPERSON);
                    relateAncestorContributors(related, lsbRelations.HASORGANISATION);
                }
            }
        }
        if (ignoreRecord) {
            LOG.debug("Roll back last changes.");
            tx.failure();
        } else {
            LOG.debug("Commit last changes.");
            tx.success();
        }
        tx.close();
        locSigs.clear();
        neighborNodes.get("person").clear();
        neighborNodes.get("organisation").clear();
    }

    /**
     * Depending on the key of the literal, a new node of some kind and a relation from the resource-node to it are created.
     *
     * @param k Key of literal
     * @param v Value of literal
     */
    public void literal(String k, String v) {
        if (!ignoreRecord) {
            switch (k) {
                case "organisation":
                    String[] orgaValue = v.split("##");
                    if (orgaValue.length > 1) {
                        Node orgaNode = mergeNode(lsbLabels.ORGANISATION, orgaValue[0], orgaValue[1]);
                        resourceNode.createRelationshipTo(orgaNode, lsbRelations.HASORGANISATION);
                        neighborNodes.get("organisation").add(orgaNode);
                        LOG.debug("Create relation between resource-node {} and organisation-node {} ", resourceNode.getId(), orgaNode.getId());
                        if (orgaValue[1].startsWith("http:")) {
                            LOG.warn("Possible anomaly in organisation string for resource {}: {}", nodeId, v);
                        }
                    } else {
                        LOG.warn("Anomaly in organisation string for resource {}: {}", nodeId, v);
                    }
                    break;
                case "person":
                    String[] personValue = v.split("##");
                    if (personValue.length > 1) {
                        Node personNode = mergeNode(lsbLabels.PERSON, personValue[0], personValue[1]);
                        resourceNode.createRelationshipTo(personNode, lsbRelations.HASPERSON);
                        neighborNodes.get("person").add(personNode);
                        LOG.debug("Create relation between resource-node {} and person-node {} ", resourceNode.getId(), personNode.getId());
                        if (personValue[1].startsWith("http:")) {
                            LOG.warn("Possible anomaly in organisation string for resource {}: {}", nodeId, v);
                        }
                    } else {
                        LOG.warn("Anomaly in person string for resource {}: {}", nodeId, v);
                    }
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
                    LOG.debug("Add property {}={} to resource-node {}", k, v, resourceNode.getId());
                    break;
            }
        }
    }

    /**
     * Creates a new node with property id. If node already exists, creation is skipped.
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
     * Creates a new node with property id and name. If node already exists, creation is skipped.
     *
     * @param concept Label of node which should be merged
     * @param v1      Value of property id
     * @param v2      Value of property name
     * @return New node or node which matched query
     */
    private Node mergeNode(Label concept, String v1, String v2) {
        Node n = graphDb.createNode(concept);
        try {
            n.setProperty("id", v1);
            n.setProperty("name", v2);
            n.setProperty("time", timestamp);
            LOG.debug("Node with label {} and properties id={} and time={} has been created.", concept.toString(), v1, timestamp);
        } catch (ConstraintViolationException e) {
            LOG.debug("Node with label {} and property id={} already exists. Thus no new node is created.", concept.toString(), v1);
            n.delete();
            n = graphDb.findNode(concept, "id", v1);
        }
        return n;
    }

    /**
     * An inheritance is the assumption that a newly created/nominated master record partially "inherits" the content of
     * a recently deleted one. To be a possible heir some requirements have to be fulfilled:
     * - The ancestor node is labeled :DELETE
     * - The CBS id of the two nodes are not the same
     * If there are several deleted nodes which match the mentioned criteria, only the latest is taken.
     *
     * @return Id of the latest relation or null
     */
    private String getAncestor() {
        StringBuilder serializedSysNo = new StringBuilder();
        int counter = 0;
        for (String e : locSigs) {
            if (counter > 0) {
                serializedSysNo.append(",");
            }
            serializedSysNo.append("\"").append(e).append("\"");
            counter++;
        }
        StringBuilder sb = new StringBuilder("MATCH (r:RESOURCE:DELETE)-[:HASSYSNO]->(s:SYSNO) WHERE s.id IN [");
        sb.append(serializedSysNo)
                .append("] AND id(r) <> ")
                .append(resourceNode.getId())
                .append(" RETURN id(r) ORDER BY id(r) DESC");
        Result r = graphDb.execute(sb.toString());
        LOG.trace("New cypher query for looking up the latest ancestor has been generated: {}", sb.toString());
        String id;
        if (r.hasNext()) {
            id = r.next().get("id(r)").toString();
            LOG.debug("A related node for resource-node {} has been found: {}", resourceNode.getId(), id);
        } else {
            id = null;
            LOG.debug("No related node for resource-node {} has been found.", resourceNode.getId());
        }
        return id;
    }

    /**
     * Returns the id of a node with same CBS id and no successor (i.e. a direct predecessor)
     *
     * @return Id of the ancestor or null
     */
    private String getPredecessor() {
        Result r = graphDb.execute("MATCH (r:RESOURCE {id: '" + nodeId +
                "'}) WHERE id(r) <> " + resourceNode.getId() + " AND NOT (r)<-[:SUCCEEDS]-(:RESOURCE) AND NOT r:DELETE RETURN id(r)");
        String id;
        if (r.hasNext()) {
            id = r.next().get("id(r)").toString();
            LOG.debug("An ancestor for resource-node {} has been found: {}", resourceNode.getId(), id);
        } else {
            id = null;
            LOG.debug("No ancestor for resource-node {} has been found.", resourceNode.getId());
        }
        return id;
    }

    /**
     * Checks if an ancestor / predecessor resource node has the same related person / organisation nodes (i.e. same IDs).
     * If not, it tries to infer the relations between old and new person / organisation nodes.
     *
     * @param ancestor Ancestor / predecessor resource node
     */
    private void relateAncestorContributors(Node ancestor, RelationshipType relaType) {
        HashSet<Node> ancestorNeighborNodes = new HashSet<>();
        HashMap<String, HashSet<Node>> ancestorChanges = new HashMap<>();
        for (Relationship r : ancestor.getRelationships(relaType)) {
            ancestorNeighborNodes.add(r.getEndNode());
        }
        if (relaType == lsbRelations.HASORGANISATION) {
            ancestorChanges = symmetricDifference(neighborNodes.get("organisation"), ancestorNeighborNodes);
        } else if (relaType == lsbRelations.HASPERSON) {
            ancestorChanges = symmetricDifference(neighborNodes.get("person"), ancestorNeighborNodes);
        }

        if ((ancestorChanges.get("left").size() > 0 && ancestorChanges.get("right").size() >= 0)) {
            Iterator<Node> iActual = ancestorChanges.get("left").iterator();
            Iterator<Node> iAncestor = ancestorChanges.get("right").iterator();
            while (iActual.hasNext()) {
                Node newContrib = iActual.next();
                Node mostSimilarNode = null;
                double maxJwd = 0;
                while (iAncestor.hasNext()) {
                    Node oldContrib = iAncestor.next();
                    Double jwd = StringUtils.getJaroWinklerDistance(
                            (newContrib.getProperty("name") == null ? "" : newContrib.getProperty("name").toString()),
                            (oldContrib.getProperty("name") == null ? "" : oldContrib.getProperty("name").toString()));
                    if (jwd >= maxJwd) {
                        mostSimilarNode = oldContrib;
                        maxJwd = jwd;
                    }
                }
                if (mostSimilarNode != null) {
                    if (maxJwd >= jwdThreshold) {
                        newContrib.createRelationshipTo(mostSimilarNode, lsbRelations.INHERITS);
                        LOG.info("New contributor node with id={} and name={} is quite similar (Jaro-Winkler-Distance over {}" +
                                        " to outdated contributor node with id={} and name={}. It is supposed that" +
                                        " the new node is a successor of the old one.", newContrib.getProperty("id"), newContrib.getProperty("name"),
                                jwdThreshold, mostSimilarNode.getProperty("id"), mostSimilarNode.getProperty("name"));
                    } else {
                        if (commonWordsCoefficient(newContrib.getProperty("name").toString(), mostSimilarNode.getProperty("name").toString()) >= 0.9) {
                            newContrib.createRelationshipTo(mostSimilarNode, lsbRelations.INHERITS);
                            LOG.info("The name tokens of new contributor node with id={} are a subset of the one of the outdated contributor node (or vice versa)." +
                                    "Thus it is supposed that the new node is a successor of the old one");
                            LOG.info("\"{}\",\"{}\",\"{}\"", newContrib.getProperty("name"), mostSimilarNode.getProperty("name"), 1.1);
                        }
                    }
                }

            }

        }
    }

    /**
     * Compares the element of two HashSets and returns a HashMap with the relative complements of the first HashSet in
     * the second (key "right") and the second in the first (key "left"), respectively.
     *
     * @param var1 First HashSet
     * @param var2 Second HashSet
     * @return The differences of both HashSets indexed with "left" and "right"
     */
    private static HashMap<String, HashSet<Node>> symmetricDifference(HashSet<Node> var1, HashSet<Node> var2) {
        HashMap<String, HashSet<Node>> res = new HashMap<>();
        res.put("left", new HashSet<>());
        res.put("right", new HashSet<>());
        if (!var1.equals(var2)) {
            if (var1.size() == 0) {
                var2.forEach(res.get("right")::add);
            } else if (var2.size() == 0) {
                var1.forEach(res.get("left")::add);
            } else {
                var1.stream().filter(n -> !var2.contains(n)).forEach(res.get("left")::add);
                var2.stream().filter(n -> !var1.contains(n)).forEach(res.get("right")::add);
            }
        }
        return res;
    }

    /**
     * Computes the ratio of single words of the String with less words contained in the String with more words.
     *
     * @param var1 First String
     * @param var2 Second String
     * @return Ratio of words
     */
    static double commonWordsCoefficient(String var1, String var2) {
        if (var1 == null || var2 == null) {
            return (double) 0;
        } else {
            String unneededChars = "[()+%/]";
            List<String> var1List = Arrays.asList(var1.toLowerCase().replaceAll(unneededChars, "").split("[ -]"));
            List<String> var2List = Arrays.asList(var2.toLowerCase().replaceAll(unneededChars, "").split("[ -]"));
            if (var1List.size() > var2List.size()) {
                return ((double) var2List.stream().filter(var1List::contains).count()) / var2List.size();
            } else {
                return ((double) var1List.stream().filter(var2List::contains).count()) / var1List.size();
            }
        }
    }

    private enum lsbLabels implements Label {
        RESOURCE, PERSON, ORGANISATION, SYSNO, WORK, CREATE, DELETE, REPLACE
    }

    private enum lsbRelations implements RelationshipType {
        HASORGANISATION, HASPERSON, HASSYSNO, HASWORK, INHERITS, SUCCEEDS
    }

}
