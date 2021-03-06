package org.swissbib.linked;

import org.culturegraph.mf.morph.functions.AbstractSimpleStatelessFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author Günter Hipler
 * @version 0.1
 *          <p>
 *          Created on 6/30/16
 */
abstract class HashGenerator extends AbstractSimpleStatelessFunction {

    static final String NO_HASH = "NO_HASH";
    static final String VALUES_OK = "OK";

    private static final Logger hashError;


    private static final Pattern charsToReplace = Pattern.compile(",| *", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);


    static {
        hashError = LoggerFactory.getLogger("hashError");

    }


    String generateId(String name) throws URISyntaxException {
        String normalizedName;
        //decompose unicode characters eg. é -> e´
        if (!Normalizer.isNormalized(name, Normalizer.Form.NFD)) {
            normalizedName = Normalizer.normalize(name, Normalizer.Form.NFD);
        } else {
            normalizedName = name;
        }
        //remove diacritical marks
        normalizedName = normalizedName.replaceAll("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+", "");
        //transform to lower case characters
        normalizedName = normalizedName.toLowerCase();
        //URL generation
        UUID uuid = UUID.nameUUIDFromBytes(normalizedName.getBytes(Charset.forName("UTF-8")));
        //return new URI(this.URI_PREFIX + uuid.toString());
        return uuid.toString();
    }


    String concatenateAndNormalizeValueParts(List<String> valueParts) {

        StringBuilder normalizedValueParts = new StringBuilder();
        for (String valuePart : valueParts) {
            normalizedValueParts.append(charsToReplace.matcher(valuePart).replaceAll(""));
        }

        return normalizedValueParts.toString();
    }


}
