package org.swissbib.linked;

import java.util.HashMap;

/**
 * @author Sebastian Schüpbach
 * @version 0.1
 *          <p>
 *          Created on 26.07.16
 */
public class CommandlineArgsReader {

    private HashMap<String, String> args = new HashMap<>();

    public CommandlineArgsReader(String[] args) {
        String key = "";
        String value;
        for (String a : args) {
            if (a.startsWith("--")) {
                key = a.substring(2).split("=", 1)[0];
                value = a.split("=", 2)[1];
                this.args.put(key, value);
            } else if (a.startsWith("-")) {
                key = a.substring(1, 2);
                if (a.length() > 2) {
                    value = a.substring(2);
                    this.args.put(key, value);
                }
            } else {
                value = a;
                this.args.put(key, value);
            }
        }

    }

    public HashMap<String, String> parse() {
        return args;
    }

}
