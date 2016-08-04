package org.swissbib.linked;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 28.07.16
 */
public class TestRunner {

    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(CypherEncoderTest.class);
        for (Failure f : result.getFailures()) {
            System.out.println(f.toString());
        }
    }

}
