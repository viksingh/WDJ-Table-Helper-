package com.sap.demo.tc.wd.tut.table.sofi.wd.comp.tutorial.tutorial.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;

import com.sap.dictionary.runtime.IDataType;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDAbstractDropDownByIndex;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDAbstractDropDownByKey;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDAbstractInputField;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDAbstractTableColumn;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDCaption;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDCheckBox;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDLink;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDProgressIndicator;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDRadioButton;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTable;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTableCellEditor;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTableColumn;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTableColumnGroup;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTextEdit;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTextView;
import com.sap.tc.webdynpro.progmodel.api.IWDAttributeInfo;
import com.sap.tc.webdynpro.progmodel.api.IWDAttributePointer;
import com.sap.tc.webdynpro.progmodel.api.IWDNode;
import com.sap.tc.webdynpro.progmodel.api.IWDNodeElement;
import com.sap.tc.webdynpro.progmodel.api.IWDNodeInfo;
import com.sap.tc.webdynpro.progmodel.api.IWDViewElement;

/**
 * Base class for TableSOrter and TableFilter. Maintains the table, contains
 * common functionality like determining the table columns, path handling,
 * metadata determination, attribute access...
 */
class TableHelper
{
  /**
   * Tokenizes the input string according to the given delimiters. The
   * delimiters will be left out. Example: tokenize("Hello_World", "_") results
   * ["Hello", "World"]
   */
  static String[] tokenize(String input, String delim) {
    StringTokenizer tokenizer = new StringTokenizer(input, delim);
    String[] tokens = new String[tokenizer.countTokens()];
    int index = 0;
    while (tokenizer.hasMoreTokens()) {
      tokens[index] = tokenizer.nextToken();
      index++;
    }
    return tokens;
  }

  /** the table to be filtered */
  final IWDTable table;

  TableHelper(IWDTable table) {
    if (table == null)
      throw new IllegalArgumentException("Table must be given");
    if (table.bindingOfDataSource() == null)
      throw new IllegalArgumentException("Data source of table with id '"
              + table.getId() + "' must be bound");
    this.table = table;
  }

  /**
   * Creates an Accessor.
   * 
   * @param attributeName the attribute name, may contain dots so that child
   *                nodes are accessed (as the Table also does)
   * @param useVisibleText if <code>true</code> the accessor delivers the visible
   *                text instead of the real value (i.e. it uses
   *                <code>getAttributeAsText</code> instead of
   *                <code>getAttributevalue</code>)
   */
  final Accessor<?> createAccessor(String attributeName, boolean useVisibleText) {
    if (attributeName == null) {
      return null;
    }
    return useVisibleText ? new TextAccessor(attributeName) : new ValueAccessor<Object>(attributeName);
  }

  /**
   * Creates an Accessor.
   *
   * @param subNodes a collection of node names (may be <code>null</code>), if
   *                set the accessor first descends to the given child nodes
   *                before accessing the attribute
   * @param attributeName the attribute name, may not contain the dots
   * @param useVisibleText if <code>true</code> the accessor delivers the visible
   *                text instead of the real value (i.e. it uses
   *                <code>getAttributeAsText</code> instead of
   *                <code>getAttributevalue</code>)
   */
  final Accessor<?> createAccessor(Collection<String> subNodes, String attributeName, boolean useVisibleText) {
    String[] subnodes = subNodes.toArray(new String[subNodes.size()]);
    return useVisibleText ? new TextAccessor(subnodes, attributeName) : new ValueAccessor<Object>(subnodes, attributeName);
  }

  /**
   * Determines the primary property's binding path of the attribute relative to
   * the data source.
   * 
   * @param column the table column
   * @return the path or <code>null</code> if there is no binding or it is not
   *         below the data source.
   */
  String getRelativeBindingOfPrimaryProperty(IWDTableColumn column) {
    String dataSourcePrefix = table.bindingOfDataSource() + '.';
    String bindingOfPrimaryProperty = bindingOfPrimaryProperty(
            column.getTableCellEditor());
    if (bindingOfPrimaryProperty != null
            && bindingOfPrimaryProperty.startsWith(dataSourcePrefix)) {
      return bindingOfPrimaryProperty.substring(dataSourcePrefix.length());
    }
    return null;
  }

  /**
   * Determines all columns of the table.
   * @return a collection of the table columns
   */
  Collection<IWDTableColumn> getColumns() {
    Collection<IWDTableColumn> columns = new ArrayList<IWDTableColumn>();
    @SuppressWarnings("unchecked")
    Iterator<IWDAbstractTableColumn> iter = (Iterator<IWDAbstractTableColumn>)table.iterateGroupedColumns();
    addColumns(iter, columns);
//    traverseColumns(new ColumnVisitor<Collection<IWDTableColumn>>() {
//      void visit(IWDTableColumn column, Collection<IWDTableColumn> columns) {
//        columns.add(column);
//      }
//    }, columns);
    return columns;
  }

//  <T> void traverseColumns(ColumnVisitor<T> visitor, T payload) {
//    @SuppressWarnings("unchecked")
//    Iterator<IWDAbstractTableColumn> iter = (Iterator<IWDAbstractTableColumn>)table.iterateGroupedColumns();
//    traverseColumns(iter, visitor, payload);
//  }

  /**
   * Returns the binding of the given table cell editor's property that is
   * considered "primary" or <code>null</code> if no such binding exists or no
   * such property can be determined.
   */
  private final String bindingOfPrimaryProperty(IWDTableCellEditor editor) {
    return editor instanceof IWDViewElement ? 
            bindingOfPrimaryProperty((IWDViewElement)editor) : null;
  }

  /**
   * Returns the binding of the given view element's property that is considered
   * "primary" or <code>null</code> if no such binding exists or no such
   * property can be determined.
   */
  private final String bindingOfPrimaryProperty(IWDViewElement element) {
    if (element instanceof IWDAbstractDropDownByIndex)
      return ((IWDAbstractDropDownByIndex)element).bindingOfTexts();
    if (element instanceof IWDAbstractDropDownByKey)
      return ((IWDAbstractDropDownByKey)element).bindingOfSelectedKey();
    if (element instanceof IWDAbstractInputField)
      return ((IWDAbstractInputField)element).bindingOfValue();
    if (element instanceof IWDCaption)
      return ((IWDCaption)element).bindingOfText();
    if (element instanceof IWDCheckBox)
      return ((IWDCheckBox)element).bindingOfChecked();
    if (element instanceof IWDLink)
      return ((IWDLink)element).bindingOfText();
    if (element instanceof IWDProgressIndicator)
      return ((IWDProgressIndicator)element).bindingOfPercentValue();
    if (element instanceof IWDRadioButton)
      return ((IWDRadioButton)element).bindingOfSelectedKey();
    if (element instanceof IWDTextEdit)
      return ((IWDTextEdit)element).bindingOfValue();
    if (element instanceof IWDTextView)
      return ((IWDTextView)element).bindingOfText();

    return null;
  }

  private void addColumns(Iterator<IWDAbstractTableColumn> columnIterator, Collection<IWDTableColumn> columns) {
    while (columnIterator.hasNext()) {
      IWDAbstractTableColumn abstractColumn = columnIterator.next();
      if (abstractColumn instanceof IWDTableColumn) {
        columns.add((IWDTableColumn)abstractColumn);
      } else if (abstractColumn instanceof IWDTableColumnGroup) {
        // it's a column group -> visit the columns of the column group
        IWDTableColumnGroup columnGroup = (IWDTableColumnGroup)abstractColumn;
        @SuppressWarnings("unchecked")
        Iterator<IWDAbstractTableColumn> iter = (Iterator<IWDAbstractTableColumn>)columnGroup.iterateColumns();
        addColumns(iter, columns);
      }
    }
  }

//  private <T> void traverseColumns(Iterator<IWDAbstractTableColumn> columnIterator, ColumnVisitor<T> visitor, T payload) {
//    while (columnIterator.hasNext()) {
//      IWDAbstractTableColumn abstractColumn = columnIterator.next();
//      if (abstractColumn instanceof IWDTableColumn) {
//        visitor.visit((IWDTableColumn)abstractColumn, payload);
//      } else if (abstractColumn instanceof IWDTableColumnGroup) {
//        // it's a column group -> visit the columns of the column group
//        IWDTableColumnGroup columnGroup = (IWDTableColumnGroup)abstractColumn;
//        @SuppressWarnings("unchecked")
//        Iterator<IWDAbstractTableColumn> iter = (Iterator<IWDAbstractTableColumn>)columnGroup.iterateColumns();
//        traverseColumns(iter, visitor, payload);
//      }
//    }
//  }

  /**
   * Basic accessor class allowing access to an attribute value (abstract yet),
   * determination of the IWDAttributeInfo and IDataType.
   */
  static abstract class Accessor<T>
  {
    /**
     * List of child node names (Description of the path from the given context
     * node to the specified attribute)
     */
    private final String subNodes[];

    /** The name of the attribute */
    private final String attributeName;

    Accessor(String[] subNodes, String attributeName) {
      this.subNodes = subNodes;
      this.attributeName = attributeName;
    }

    Accessor(String attributeName) {
      String[] attributePath = tokenize(attributeName, ".");
      if (attributePath.length > 1) {
        this.subNodes = new String[attributePath.length - 1];
        System.arraycopy(attributePath, 0, subNodes, 0, subNodes.length);
      } else {
        this.subNodes = null;
      }
      this.attributeName = attributePath[attributePath.length - 1];
    }

    Accessor(Accessor<?> accessor) {
      this.subNodes = accessor.subNodes;
      this.attributeName = accessor.attributeName;
    }

    /**
     * Returns the attribute value at the given element by first descending to
     * the correct element and then calling {@link #get(IWDNodeElement, String)}.
     * May return <code>null</code> when the child path can not be followed due
     * to missing elements or incorrect lead selection.
     */
    final T get(IWDNodeElement element) {
      if (subNodes != null) {
        for (String subNode : subNodes) {
          IWDNode childNode = element.node(subNode);
          if (childNode == null) {
            return null;
          }
          element = childNode.getCurrentElement();
          if (element == null) {
            return null;
          }
        }
      }
      return get(element, attributeName);
    }
    
    abstract T get(IWDNodeElement element, String attributeName);

    final IWDAttributeInfo getAttributeInfo(IWDNodeInfo nodeInfo) {
      if (subNodes != null) {
        for (String subNode : subNodes) {
          nodeInfo = nodeInfo.getChild(subNode);
        }
      }
      return nodeInfo.getAttribute(attributeName);
    }

    final IWDAttributePointer getAttributePointer(IWDNodeElement element) {
      if (subNodes != null) {
        for (String subNode : subNodes) {
          IWDNode childNode = element.node(subNode);
          if (childNode == null) {
            return null;
          }
          element = childNode.getCurrentElement();
          if (element == null) {
            return null;
          }
        }
      }
      return element.getAttributePointer(attributeName);
    }

    final IDataType getDataType(IWDNodeInfo nodeInfo) {
      return getAttributeInfo(nodeInfo).getDataType();
    }
  }

  static class ValueAccessor<T> extends Accessor<T>
  {
    ValueAccessor(String[] subNodes, String attributeName) {
      super(subNodes, attributeName);
    }

    ValueAccessor(String attributeName) {
      super(attributeName);
    }

    ValueAccessor(Accessor<?> accessor) {
      super(accessor);
    }

    @Override
    @SuppressWarnings("unchecked")
    T get(IWDNodeElement element, String attributeName) {
      return (T)element.getAttributeValue(attributeName);
    }
  }
  
  static class TextAccessor extends Accessor<String>
  {
    TextAccessor(String[] subNodes, String attributeName) {
      super(subNodes, attributeName);
    }

    TextAccessor(String attributeName) {
      super(attributeName);
    }

    TextAccessor(Accessor<?> accessor) {
      super(accessor);
    }

    @Override
    String get(IWDNodeElement element, String attributeName) {
      return element.getAttributeAsText(attributeName);
    }
  }
  
//  static abstract class ColumnVisitor<T> {
//    abstract void visit(IWDTableColumn column, T payload);
//  }
}
