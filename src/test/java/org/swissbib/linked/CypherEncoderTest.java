package org.swissbib.linked;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 28.07.16
 */
public class CypherEncoderTest {
    @Test
    public void commonWordsCoefficient() throws Exception {
        assertEquals("Comparison of 'Dunn Emmett R.' and 'Emmett R.' should return about 1.0", 1.0, CypherEncoder.commonWordsCoefficient("Dunn Emmett R.", "Emmett R."), 0.1);
        assertEquals("Comparison of 'Eidgenössische Technische Hochschule Zürich Institut für " +
                        "Orts-, Regional- und Landesplanung' and 'Institut für Orts-, Regional- und Landesplanung should return about 1.0",
                1, CypherEncoder.commonWordsCoefficient("Eidgenössische Technische Hochschule Zürich Institut für Orts-, Regional- und Landesplanung",
                        "Institut für Orts-, Regional- und Landesplanung"), 0.1);
        assertEquals("Comparison of 'Menna Mirko' and 'Borgese Giuseppe Antonio' should return about 0.0", 0.0,
                CypherEncoder.commonWordsCoefficient("Menna Mirko", "Borgese Giuseppe Antonio"), 0.1);
        assertNotEquals("Comparison of 'Schmuki Karl' and 'Karl Friedrich' should not return 0.0", 0.0,
                CypherEncoder.commonWordsCoefficient("Schmuki Karl", "Karl Friedrich"), 0.1);
        assertEquals("Comparison of 'Kanton Luzern' and 'Luzern (Kanton)' should return about 1.0", 1.0,
                CypherEncoder.commonWordsCoefficient("Kanton Luzern", "Luzern (Kanton)"), 0.1);
        assertTrue("Comparison of 'Schwerizer Autorinnen und Auotren Gruppe Olten' and 'Gruppe Olten' should return a number greater than 0.9",
                CypherEncoder.commonWordsCoefficient("Schweizer Autorinnen und Autoren Gruppe Olten", "Gruppe Olten") >= 0.9);
    }

}
