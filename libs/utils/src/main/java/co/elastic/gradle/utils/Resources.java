package co.elastic.gradle.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;


public class Resources {
    public static String getResourceContents(String resourcePath) {
      try (
          BufferedReader reader = new BufferedReader(new InputStreamReader(Resources.class.getResourceAsStream(resourcePath)))
      ) {
          StringBuilder b = new StringBuilder();
          for (String line = reader.readLine(); line != null; line = reader.readLine()) {
              if (b.length() != 0) {
                  b.append('\n');
              }
              b.append(line);
          }

          return b.toString();
      } catch (IOException e) {
          throw new UncheckedIOException("Error trying to read classpath resource: " + resourcePath, e);
      }
    }
  }