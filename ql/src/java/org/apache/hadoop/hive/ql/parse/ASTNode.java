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

package org.apache.hadoop.hive.ql.parse;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.antlr.runtime.Token;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.Tree;
import org.apache.hadoop.hive.ql.lib.Node;

/**
 *
 */
public class ASTNode extends CommonTree implements Node,Serializable {
  private static final long serialVersionUID = 1L;

  private transient ASTNodeOrigin origin;

  public ASTNode() {
  }

  /**
   * Constructor.
   *
   * @param t
   *          Token for the CommonTree Node
   */
  public ASTNode(Token t) {
    super(t);
  }

  public ASTNode(ASTNode node) {
    super(node);
    this.origin = node.origin;
  }

  @Override
  public Tree dupNode() {
    return new ASTNode(this);
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hadoop.hive.ql.lib.Node#getChildren()
   */
  @Override
  public ArrayList<Node> getChildren() {
    if (super.getChildCount() == 0) {
      return null;
    }

    ArrayList<Node> ret_vec = new ArrayList<Node>();
    for (int i = 0; i < super.getChildCount(); ++i) {
      ret_vec.add((Node) super.getChild(i));
    }

    return ret_vec;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hadoop.hive.ql.lib.Node#getName()
   */
  public String getName() {
    return (Integer.valueOf(super.getToken().getType())).toString();
  }

  /**
   * @return information about the object from which this ASTNode originated, or
   *         null if this ASTNode was not expanded from an object reference
   */
  public ASTNodeOrigin getOrigin() {
    return origin;
  }

  /**
   * Tag this ASTNode with information about the object from which this node
   * originated.
   */
  public void setOrigin(ASTNodeOrigin origin) {
    this.origin = origin;
  }

  public String dump() {
    StringBuilder sb = new StringBuilder("\n");
    dump(sb, "");
    return sb.toString();
  }

  private StringBuilder dump(StringBuilder sb, String ws) {
    sb.append(ws);
    sb.append(toString());
    sb.append("\n");

    ArrayList<Node> children = getChildren();
    if (children != null) {
      for (Node node : getChildren()) {
        if (node instanceof ASTNode) {
          ((ASTNode) node).dump(sb, ws + "   ");
        } else {
          sb.append(ws);
          sb.append("   NON-ASTNODE!!");
          sb.append("\n");
        }
      }
    }
    return sb;
  }

  public static String escapeWhitespace(String s, boolean escapeSpaces) {
    StringBuilder buf = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c == ' ' && escapeSpaces) buf.append('\u00B7');
      else if (c == '\t') buf.append("\\t");
      else if (c == '\n') buf.append("\\n");
      else if (c == '\r') buf.append("\\r");
      else buf.append(c);
    }
    return buf.toString();
  }

  public static String dumpGraphviz(ASTNode astNode) {
    List<ASTNode[]> relations = new ArrayList<>();
    Map<ASTNode, Integer> nodeMap = new LinkedHashMap<>();
    int p = 0;

    String buf = "digraph ast{ \n";
    buf += "  node [shape=plaintext]; \n";
    buf += "  \n";

    Stack<ASTNode> toVisit = new Stack<>();
    toVisit.push(astNode);
    nodeMap.put(astNode, p++);
    while (!toVisit.empty()) {
      ASTNode currNode = toVisit.pop();
      if (nodeMap.get(currNode) == null) {
        nodeMap.put(currNode, p++);
      }
      List<? extends Node> children = currNode.getChildren();
      if (children != null) {
        for (Node child : currNode.getChildren()) {
          toVisit.push((ASTNode) child);
          if (nodeMap.get(child) == null)
            nodeMap.put((ASTNode) child, p++);
          relations.add(new ASTNode[]{currNode, (ASTNode) child});
        }
      }
    }

    for (Map.Entry<ASTNode, Integer> en : nodeMap.entrySet()) {
      String nodeName = en.getKey() != null && en.getKey().getToken() != null ? en.getKey().toString() : "Nil";
      buf += "  p" + en.getValue() + "[label=\"" + escapeWhitespace(nodeName, false) + " \"]" + ";  \n";
    }
    for (Node[] r : relations) {
      buf += "  p" + nodeMap.get(r[0]) + " -> p" + nodeMap.get(r[1]) + "; \n";
    }
    buf += "} \n";
    return buf;
  }
}
