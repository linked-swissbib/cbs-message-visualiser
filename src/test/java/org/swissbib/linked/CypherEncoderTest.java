package org.swissbib.linked;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 07.07.16
 */
public class CypherEncoderTest {
    @Test
    public void createCypherAncestorLookup() throws Exception {
        ArrayList<String> ids = new ArrayList<>();
        ids.add("123");
        ids.add("456");
        String lookup = CypherEncoder.createCypherAncestorLookup(ids, 789l);
        assertEquals("MATCH (r:RESOURCE)-[:HASLOCSIG]->(s:LOCALSIGNATURE) WHERE s.id IN [\"123\",\"456\"] AND NOT (r)-[:HASSUCCESSOR]->() AND id(r) <> 789 RETURN id(r)", lookup);
    }

}
