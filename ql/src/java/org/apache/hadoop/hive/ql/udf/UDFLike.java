/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.udf;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDF;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedExpressions;
import org.apache.hadoop.hive.ql.exec.vector.expressions.FilterStringColLikeStringScalar;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.Text;

/**
 * UDFLike.
 *
 */
@Description(name = "like",
    value = "_FUNC_(str, pattern) - Checks if str matches pattern",
    extended = "Example:\n"
    + "  > SELECT a.* FROM srcpart a WHERE a.hr _FUNC_ '%2' LIMIT 1;\n"
    + "  27      val_27  2008-04-08      12")
@VectorizedExpressions({FilterStringColLikeStringScalar.class})
public class UDFLike extends UDF {

  // Doing characters comparison directly instead of regular expression
  // matching for simple patterns like "%abc%".
  private enum PatternType {
    NONE, // "abc"
    BEGIN, // "abc%"
    END, // "%abc"
    MIDDLE, // "%abc%"
    COMPLEX, // all other cases, such as "ab%c_de"
  }

  private class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private long cacheSize;

    public LRUCache(long cacheSize) {
      this.cacheSize = cacheSize;
    }

    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      return size() >= cacheSize;
    }
  }

  private class LikeObject {
    private Pattern p = null;
    private PatternType type = PatternType.NONE;
    private Text simplePattern = new Text();
  }

  private long likePatternCacheSize = 1000;
  private final LRUCache<Text,LikeObject> likePatternMap = new LRUCache<>(likePatternCacheSize);

  private final BooleanWritable result = new BooleanWritable();

  public UDFLike() {
  }

  public static String likePatternToRegExp(String likePattern) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < likePattern.length(); i++) {
      // Make a special case for "\\_" and "\\%"
      char n = likePattern.charAt(i);
      if (n == '\\'
          && i + 1 < likePattern.length()
          && (likePattern.charAt(i + 1) == '_' || likePattern.charAt(i + 1) == '%')) {
        sb.append(likePattern.charAt(i + 1));
        i++;
        continue;
      }

      if (n == '_') {
        sb.append(".");
      } else if (n == '%') {
        sb.append(".*");
      } else {
        sb.append(Pattern.quote(Character.toString(n)));
      }
    }
    return sb.toString();
  }

  /**
   * Parses the likePattern. Based on it is a simple pattern or not, the
   * function might change two member variables. {@link LikeObject#type} will be changed
   * to the corresponding pattern type; {@link LikeObject#simplePattern} will record the
   * string in it for later pattern matching if it is a simple pattern.
   * <p>
   * Examples: <blockquote>
   *
   * <pre>
   * parseSimplePattern("%abc%") changes {@link LikeObject#type} to PatternType.MIDDLE
   * and changes {@link LikeObject#simplePattern} to "abc"
   * parseSimplePattern("%ab_c%") changes {@link LikeObject#type} to PatternType.COMPLEX
   * and does not change {@link LikeObject#simplePattern}
   * </pre>
   *
   * </blockquote>
   *
   * @param likePattern
   *          the input LIKE query pattern
   */
  private LikeObject parseSimplePattern(String likePattern) {
    int length = likePattern.length();
    int beginIndex = 0;
    int endIndex = length;
    char lastChar = 'a';
    String strPattern = new String();
    LikeObject likeObject = new LikeObject();
    likeObject.type = PatternType.NONE;

    for (int i = 0; i < length; i++) {
      char n = likePattern.charAt(i);
      if (n == '_') { // such as "a_b"
        if (lastChar != '\\') { // such as "a%bc"
          likeObject.type = PatternType.COMPLEX;
          break;
        } else { // such as "abc\%de%"
          strPattern += likePattern.substring(beginIndex, i - 1);
          beginIndex = i;
        }
      } else if (n == '%') {
        if (i == 0) { // such as "%abc"
          likeObject.type = PatternType.END;
          beginIndex = 1;
        } else if (i < length - 1) {
          if (lastChar != '\\') { // such as "a%bc"
            likeObject.type = PatternType.COMPLEX;
            break;
          } else { // such as "abc\%de%"
            strPattern += likePattern.substring(beginIndex, i - 1);
            beginIndex = i;
          }
        } else {
          if (lastChar != '\\') {
            endIndex = length - 1;
            if (likeObject.type == PatternType.END) { // such as "%abc%"
              likeObject.type = PatternType.MIDDLE;
            } else {
              likeObject.type = PatternType.BEGIN; // such as "abc%"
            }
          } else { // such as "abc\%"
            strPattern += likePattern.substring(beginIndex, i - 1);
            beginIndex = i;
            endIndex = length;
          }
        }
      }
      lastChar = n;
    }

    if (likeObject.type == PatternType.COMPLEX) {
      likeObject.p = Pattern.compile(likePatternToRegExp(likePattern));
    } else {
      strPattern += likePattern.substring(beginIndex, endIndex);
      likeObject.simplePattern.set(strPattern);
    }
    return likeObject;
  }

  private static boolean find(Text s, Text sub, int startS, int endS) {
    byte[] byteS = s.getBytes();
    byte[] byteSub = sub.getBytes();
    int lenSub = sub.getLength();
    boolean match = false;
    for (int i = startS; (i < endS - lenSub + 1) && (!match); i++) {
      match = true;
      for (int j = 0; j < lenSub; j++) {
        if (byteS[j + i] != byteSub[j]) {
          match = false;
          break;
        }
      }
    }
    return match;
  }

  public BooleanWritable evaluate(Text s, Text likePattern) {
    if (s == null || likePattern == null) {
      return null;
    }

    LikeObject likeObject = likePatternMap.get(likePattern);
    if(likeObject==null){
      String strLikePattern = likePattern.toString();
      likeObject = parseSimplePattern(strLikePattern);
      likePatternMap.put(likePattern, likeObject);
    }
    Pattern p =likeObject.p;
    PatternType type = likeObject.type;
    Text simplePattern = likeObject.simplePattern;

    if (type == PatternType.COMPLEX) {
      Matcher m = p.matcher(s.toString());
      result.set(m.matches());
    } else {
      int startS = 0;
      int endS = s.getLength();
      // if s is shorter than the required pattern
      if (endS < simplePattern.getLength()) {
        result.set(false);
        return result;
      }
      switch (type) {
      case BEGIN:
        endS = simplePattern.getLength();
        break;
      case END:
        startS = endS - simplePattern.getLength();
        break;
      case NONE:
        if (simplePattern.getLength() != s.getLength()) {
          result.set(false);
          return result;
        }
        break;
      }
      result.set(find(s, simplePattern, startS, endS));
    }
    return result;
  }

}
